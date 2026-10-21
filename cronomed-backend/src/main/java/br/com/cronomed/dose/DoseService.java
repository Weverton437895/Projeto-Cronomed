package br.com.cronomed.dose;

import br.com.cronomed.common.ApiException;
import br.com.cronomed.common.Source;
import br.com.cronomed.config.AppProperties;
import br.com.cronomed.device.Compartment;
import br.com.cronomed.device.CompartmentRepository;
import br.com.cronomed.device.IotDevice;
import br.com.cronomed.medication.Medication;
import br.com.cronomed.medication.MedicationRepository;
import br.com.cronomed.patient.AccessService;
import br.com.cronomed.stock.StockService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro e conciliacao de eventos de dose (RF25-RF28).
 *
 * <p>Regras centrais:
 * <ul>
 *   <li>A abertura da caixa (sensor) NUNCA baixa estoque; apenas registra o acesso fisico.</li>
 *   <li>A baixa so ocorre na confirmacao do usuario (CONFIRMED_YES) e no maximo uma vez por dose
 *       (a linha da dose e bloqueada e TAKEN e terminal).</li>
 *   <li>Idempotencia por UUID do evento: reenvios apos queda de rede sao descartados.</li>
 * </ul>
 */
@Service
public class DoseService {

    public enum RecordResult {
        APPLIED, DUPLICATE
    }

    /** Entrada de um evento originado por pessoa (app, site ou cuidador). */
    public record UserEventInput(UUID eventId, UUID scheduledDoseId, DoseEventType type, Instant occurredAt,
                                 Long elapsedMs, Long seq, String justification) {
    }

    /** Comando interno ja resolvido (paciente/medicamento conhecidos). */
    public record Command(UUID eventId, UUID scheduledDoseId, UUID patientId, UUID medicationId, DoseEventType type,
                          Source source, UUID actorUserId, UUID deviceId, Integer compartmentIndex,
                          Instant occurredAt, Long clientSeq, String justification) {
    }

    private final ScheduledDoseRepository doses;
    private final DoseEventRepository events;
    private final MedicationRepository medications;
    private final CompartmentRepository compartments;
    private final StockService stock;
    private final AccessService access;
    private final AppProperties props;
    private final Clock clock;

    public DoseService(ScheduledDoseRepository doses, DoseEventRepository events, MedicationRepository medications,
                       CompartmentRepository compartments, StockService stock, AccessService access,
                       AppProperties props, Clock clock) {
        this.doses = doses;
        this.events = events;
        this.medications = medications;
        this.compartments = compartments;
        this.stock = stock;
        this.access = access;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Calibra o instante do evento com o relogio do servidor (referencia absoluta em UTC):
     * se o cliente informou o tempo decorrido desde o fato, usa-se agora - decorrido; senao
     * aceita o timestamp do cliente limitado a uma janela plausivel (corrige relogios desregulados).
     */
    public static Instant calibrate(Instant claimed, Long elapsedMs, Instant now) {
        if (elapsedMs != null && elapsedMs >= 0) {
            return now.minusMillis(elapsedMs);
        }
        if (claimed == null || claimed.isAfter(now.plusSeconds(60))) {
            return now;
        }
        Instant floor = now.minus(Duration.ofDays(31));
        return claimed.isBefore(floor) ? floor : claimed;
    }

    // -------------------------------------------------------------- eventos de pessoas (app/site)

    @Transactional
    public RecordResult recordUserEvent(UUID userId, Source source, UserEventInput in) {
        if (in.eventId() == null || in.scheduledDoseId() == null || in.type() == null) {
            throw ApiException.badRequest("INVALID_EVENT", "Evento incompleto");
        }
        if (in.type() != DoseEventType.CONFIRMED_YES && in.type() != DoseEventType.CONFIRMED_NO
                && in.type() != DoseEventType.POSTPONED) {
            throw ApiException.badRequest("INVALID_EVENT_TYPE", "Tipo de evento nao permitido para usuarios");
        }
        ScheduledDose dose = doses.findById(in.scheduledDoseId()).orElseThrow(() -> ApiException.notFound("Dose"));
        access.requireConfirm(userId, dose.getPatientId());
        Instant occurred = calibrate(in.occurredAt(), in.elapsedMs(), clock.instant());
        return record(new Command(in.eventId(), dose.getId(), dose.getPatientId(), dose.getMedicationId(), in.type(),
                source, userId, null, null, occurred, in.seq(), in.justification()));
    }

    // -------------------------------------------------------------- eventos da caixa (sensor)

    @Transactional
    public RecordResult recordDeviceOpening(IotDevice device, UUID eventId, int compartmentIndex, UUID doseId,
                                            Instant claimedAt, Long elapsedMs, Long seq) {
        Compartment c = compartments.findByDeviceIdAndIndexNumber(device.getId(), compartmentIndex)
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_COMPARTMENT", "Compartimento inexistente"));
        if (c.getPatientId() == null) {
            throw ApiException.badRequest("COMPARTMENT_UNASSIGNED", "Compartimento sem paciente associado");
        }
        Instant occurred = calibrate(claimedAt, elapsedMs, clock.instant());
        Medication med = medications.findByCompartmentId(c.getId()).filter(m -> !m.isDeleted()).orElse(null);
        UUID resolvedDose = doseId;
        if (resolvedDose == null && med != null) {
            Duration tol = Duration.ofMinutes(props.getDose().getToleranceMinutes());
            List<ScheduledDose> near = doses.findPendingBetween(med.getId(), occurred.minus(tol), occurred.plus(tol));
            resolvedDose = near.stream()
                    .min(Comparator.comparingLong(d -> Math.abs(Duration.between(d.getScheduledAt(), occurred).toMillis())))
                    .map(ScheduledDose::getId).orElse(null);
        }
        return record(new Command(eventId, resolvedDose, c.getPatientId(), med == null ? null : med.getId(),
                DoseEventType.BOX_OPENED, Source.DEVICE, null, device.getId(), compartmentIndex, occurred, seq, null));
    }

    // -------------------------------------------------------------- nucleo idempotente

    @Transactional
    public RecordResult record(Command c) {
        if (events.existsById(c.eventId())) {
            return RecordResult.DUPLICATE;
        }
        Instant now = clock.instant();
        ScheduledDose dose = null;
        if (c.scheduledDoseId() != null) {
            dose = doses.findByIdForUpdate(c.scheduledDoseId()).orElseThrow(() -> ApiException.notFound("Dose"));
            if (!dose.getPatientId().equals(c.patientId())
                    || (c.medicationId() != null && !dose.getMedicationId().equals(c.medicationId()))) {
                throw ApiException.badRequest("DOSE_MISMATCH", "Dose nao corresponde ao paciente/medicamento informado");
            }
        }

        DoseEvent ev = new DoseEvent();
        ev.setId(c.eventId());
        ev.setScheduledDoseId(dose == null ? null : dose.getId());
        ev.setMedicationId(dose != null ? dose.getMedicationId() : c.medicationId());
        ev.setPatientId(c.patientId());
        ev.setDeviceId(c.deviceId());
        ev.setCompartmentIndex(c.compartmentIndex());
        ev.setType(c.type());
        ev.setSource(c.source());
        ev.setActorUserId(c.actorUserId());
        ev.setOccurredAt(c.occurredAt());
        ev.setReceivedAt(now);
        ev.setClientSeq(c.clientSeq());
        ev.setJustification(truncate(c.justification(), 500));
        events.save(ev);

        if (dose == null) {
            return RecordResult.APPLIED; // abertura fora de horario: fica apenas no historico
        }
        switch (c.type()) {
            case BOX_OPENED -> {
                if (dose.getOpenedAt() == null) {
                    dose.setOpenedAt(c.occurredAt());
                }
            }
            case CONFIRMED_YES -> confirmTaken(dose, c.occurredAt());
            case CONFIRMED_NO -> {
                if (dose.getStatus() != DoseStatus.TAKEN) {
                    dose.setStatus(DoseStatus.DECLINED); // sem baixa de estoque
                }
            }
            case POSTPONED -> {
                // permanece pendente; a justificativa fica no historico
            }
            case MISSED -> {
                if (dose.getStatus() == DoseStatus.PENDING) {
                    dose.setStatus(DoseStatus.MISSED);
                }
            }
            case OPENED_UNCONFIRMED -> {
                if (dose.getStatus() == DoseStatus.PENDING) {
                    dose.setStatus(DoseStatus.OPENED_UNCONFIRMED);
                }
            }
        }
        dose.setUpdatedAt(now);
        doses.save(dose);
        return RecordResult.APPLIED;
    }

    private void confirmTaken(ScheduledDose dose, Instant occurredAt) {
        if (dose.getStatus() == DoseStatus.TAKEN) {
            return; // confirmacao duplicada (outro canal): nao baixa de novo
        }
        dose.setStatus(DoseStatus.TAKEN);
        dose.setConfirmedAt(occurredAt);
        if (!dose.isStockDeducted()) {
            Medication med = medications.findById(dose.getMedicationId()).orElse(null);
            if (med != null && !med.isDeleted()) {
                stock.deduct(med, dose.getDoseQuantity(), dose.getId());
            }
            dose.setStockDeducted(true);
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
