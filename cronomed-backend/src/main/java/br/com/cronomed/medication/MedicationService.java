package br.com.cronomed.medication;

import br.com.cronomed.common.ApiException;
import br.com.cronomed.common.Source;
import br.com.cronomed.device.Compartment;
import br.com.cronomed.device.CompartmentRepository;
import br.com.cronomed.device.DeviceRepository;
import br.com.cronomed.device.DeviceService;
import br.com.cronomed.device.IotDevice;
import br.com.cronomed.device.PlanChangedEvent;
import br.com.cronomed.dose.DoseGenerator;
import br.com.cronomed.medication.MedicationDtos.MedicationRequest;
import br.com.cronomed.medication.MedicationDtos.MedicationResponse;
import br.com.cronomed.medication.MedicationDtos.ScheduleInput;
import br.com.cronomed.medication.MedicationDtos.ScheduleView;
import br.com.cronomed.patient.AccessService;
import br.com.cronomed.patient.Patient;
import br.com.cronomed.stock.StockService;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro de medicamentos (RF05-RF13). Conflitos de edicao:
 * <ul>
 *   <li>Portal web (sincrono): exige a versao atual; se o dado mudou (ex.: pelo app) responde 409 STALE_DATA.</li>
 *   <li>App (offline): a alteracao com timestamp mais recente prevalece; empate => prevalece o app (RF33).</li>
 * </ul>
 */
@Service
public class MedicationService {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    public enum ApplyStatus {
        APPLIED, STALE
    }

    public record ApplyResult(ApplyStatus status, MedicationResponse current) {
    }

    private final MedicationRepository medications;
    private final ScheduleRepository schedules;
    private final CompartmentRepository compartments;
    private final DeviceRepository devices;
    private final DeviceService deviceService;
    private final AccessService access;
    private final StockService stock;
    private final DoseGenerator generator;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public MedicationService(MedicationRepository medications, ScheduleRepository schedules,
                             CompartmentRepository compartments, DeviceRepository devices, DeviceService deviceService,
                             AccessService access, StockService stock, DoseGenerator generator,
                             ApplicationEventPublisher publisher, Clock clock) {
        this.medications = medications;
        this.schedules = schedules;
        this.compartments = compartments;
        this.devices = devices;
        this.deviceService = deviceService;
        this.access = access;
        this.stock = stock;
        this.generator = generator;
        this.publisher = publisher;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ leitura

    @Transactional(readOnly = true)
    public List<MedicationResponse> list(UUID userId, UUID patientId) {
        access.requireView(userId, patientId);
        return medications.findByPatientIdAndDeletedAtIsNullOrderByName(patientId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public MedicationResponse get(UUID userId, UUID medicationId) {
        Medication m = load(medicationId);
        access.requireView(userId, m.getPatientId());
        return toResponse(m);
    }

    // ------------------------------------------------------------------ escrita (REST)

    @Transactional
    public MedicationResponse create(UUID userId, UUID patientId, MedicationRequest req, Source source) {
        Patient p = access.requireManage(userId, patientId);
        UUID id = req.id() != null ? req.id() : UUID.randomUUID();
        if (medications.existsById(id)) {
            throw ApiException.conflict("MEDICATION_EXISTS", "Medicamento ja cadastrado");
        }
        Instant now = clock.instant();
        Medication m = createInternal(p, id, req, source, clampClientTime(req.clientUpdatedAt(), now));
        return toResponse(m);
    }

    @Transactional
    public MedicationResponse update(UUID userId, UUID medicationId, MedicationRequest req, Source source) {
        Medication m = load(medicationId);
        Patient p = access.requireManage(userId, m.getPatientId());
        Instant now = clock.instant();
        Instant ts;
        if (source == Source.WEB) {
            if (req.version() == null || !req.version().equals(m.getVersion())) {
                throw ApiException.conflict("STALE_DATA",
                        "Este medicamento foi alterado por outro dispositivo. Atualize a tela e tente novamente.");
            }
            ts = now;
        } else {
            ts = clampClientTime(req.clientUpdatedAt(), now);
            if (ts.isBefore(m.getClientUpdatedAt())) {
                throw ApiException.conflict("STALE_DATA", "Existe uma alteracao mais recente no servidor.");
            }
        }
        updateInternal(m, p, req, source, ts);
        return toResponse(m);
    }

    @Transactional
    public void delete(UUID userId, UUID medicationId, Source source) {
        Medication m = load(medicationId);
        Patient p = access.requireManage(userId, m.getPatientId());
        softDelete(m, p, source, clock.instant());
    }

    @Transactional
    public MedicationResponse refill(UUID userId, UUID medicationId, int quantity) {
        Medication m = load(medicationId);
        access.requireManage(userId, m.getPatientId());
        stock.refill(m, quantity);
        medications.saveAndFlush(m);
        publisher.publishEvent(PlanChangedEvent.of(m.getPatientId())); // sinaliza sincronizacao aos apps
        return toResponse(m);
    }

    // ------------------------------------------------------------------ escrita (sincronizacao do app)

    /** Aplica uma alteracao vinda da fila offline do app, com resolucao de conflito por timestamp (RF33). */
    @Transactional
    public ApplyResult applyMobileChange(UUID userId, MedicationRequest req, boolean deleted) {
        if (req.id() == null) {
            throw ApiException.badRequest("INVALID_CHANGE", "id do medicamento e obrigatorio na sincronizacao");
        }
        Instant now = clock.instant();
        Instant ts = clampClientTime(req.clientUpdatedAt(), now);
        Medication existing = medications.findById(req.id()).orElse(null);
        if (existing == null) {
            if (deleted) {
                return new ApplyResult(ApplyStatus.APPLIED, null);
            }
            if (req.patientId() == null) {
                throw ApiException.badRequest("INVALID_CHANGE", "patientId e obrigatorio para criar medicamento");
            }
            Patient p = access.requireManage(userId, req.patientId());
            Medication created = createInternal(p, req.id(), req, Source.MOBILE, ts);
            return new ApplyResult(ApplyStatus.APPLIED, toResponse(created));
        }
        Patient p = access.requireManage(userId, existing.getPatientId());
        if (existing.isDeleted() || ts.isBefore(existing.getClientUpdatedAt())) {
            return new ApplyResult(ApplyStatus.STALE, toResponse(existing)); // o servidor tem versao mais recente
        }
        if (deleted) {
            softDelete(existing, p, Source.MOBILE, ts);
        } else {
            updateInternal(existing, p, req, Source.MOBILE, ts); // empate de timestamp: o app prevalece
        }
        return new ApplyResult(ApplyStatus.APPLIED, toResponse(existing));
    }

    // ------------------------------------------------------------------ nucleo

    private Medication createInternal(Patient p, UUID id, MedicationRequest req, Source source, Instant clientTs) {
        Instant now = clock.instant();
        Medication m = new Medication();
        m.setId(id);
        m.setPatientId(p.getId());
        copyFields(m, req);
        int acquired = req.stockAcquired() == null ? 0 : req.stockAcquired();
        m.setStockAcquired(acquired);
        m.setStockCurrent(acquired);
        m.setActive(req.active() == null || req.active());
        m.setLastModifiedSource(source);
        m.setClientUpdatedAt(clientTs);
        m.setCreatedAt(now);
        m.setUpdatedAt(now);
        bindCompartment(m, p, req.compartmentId());
        medications.save(m);
        replaceSchedules(m, req.schedules(), now);
        stock.recordInitial(m);
        generator.generateForMedication(m.getId());
        stock.evaluate(m);
        publisher.publishEvent(PlanChangedEvent.of(p.getId()));
        return m;
    }

    private void updateInternal(Medication m, Patient p, MedicationRequest req, Source source, Instant clientTs) {
        Instant now = clock.instant();
        boolean wasActive = m.isActive();
        boolean quantityChanged = m.getDoseQuantity() != req.doseQuantity();
        copyFields(m, req);
        if (req.active() != null) {
            m.setActive(req.active());
        }
        if (!Objects.equals(req.compartmentId(), m.getCompartmentId())) {
            bindCompartment(m, p, req.compartmentId());
        }
        boolean schedulesChanged = req.schedules() != null && replaceSchedules(m, req.schedules(), now);
        m.setLastModifiedSource(source);
        m.setClientUpdatedAt(clientTs);
        m.setUpdatedAt(now);
        medications.saveAndFlush(m); // flush: a resposta ja traz a nova versao (controle otimista do portal web)

        if (schedulesChanged || quantityChanged || wasActive != m.isActive()) {
            generator.cancelFuture(m.getId());
            if (m.isActive()) {
                generator.generateForMedication(m.getId());
            }
        }
        stock.evaluate(m);
        publisher.publishEvent(PlanChangedEvent.of(p.getId()));
    }

    private void softDelete(Medication m, Patient p, Source source, Instant clientTs) {
        Instant now = clock.instant();
        m.setDeletedAt(now);
        m.setActive(false);
        m.setCompartmentId(null);
        m.setLastModifiedSource(source);
        m.setClientUpdatedAt(clientTs);
        m.setUpdatedAt(now);
        medications.save(m);
        generator.cancelFuture(m.getId()); // o historico de doses ja registradas e preservado
        publisher.publishEvent(PlanChangedEvent.of(p.getId()));
    }

    private void copyFields(Medication m, MedicationRequest req) {
        m.setName(req.name().trim());
        m.setMeasure(req.measure().trim());
        m.setDosage(req.dosage().trim());
        m.setDoseQuantity(req.doseQuantity());
        m.setMinDaysAlert(req.minDaysAlert() == null ? 3 : req.minDaysAlert());
        m.setObservations(req.observations());
    }

    /** Associa o medicamento a um compartimento (RF05) validando posse, ocupacao e limites do hardware. */
    private void bindCompartment(Medication m, Patient p, UUID compartmentId) {
        if (compartmentId == null) {
            m.setCompartmentId(null);
            return;
        }
        if (compartmentId.equals(m.getCompartmentId())) {
            return;
        }
        Compartment c = compartments.findById(compartmentId)
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_COMPARTMENT", "Compartimento inexistente"));
        IotDevice device = devices.findById(c.getDeviceId())
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_COMPARTMENT", "Caixa inexistente"));
        if (!device.getOwnerId().equals(p.getOwnerId())) {
            throw ApiException.forbidden("A caixa nao pertence ao titular deste paciente");
        }
        medications.findByCompartmentId(compartmentId)
                .filter(other -> !other.isDeleted() && !other.getId().equals(m.getId()))
                .ifPresent(other -> {
                    throw ApiException.conflict("COMPARTMENT_IN_USE", "Este compartimento ja tem um medicamento");
                });
        if (c.getPatientId() == null) {
            deviceService.assignPatient(c, p, device);
            compartments.save(c);
        } else if (!c.getPatientId().equals(p.getId())) {
            throw ApiException.conflict("COMPARTMENT_IN_USE", "Compartimento reservado para outro paciente");
        }
        m.setCompartmentId(compartmentId);
    }

    /** Substitui os horarios se mudaram; devolve true quando houve alteracao efetiva. */
    private boolean replaceSchedules(Medication m, List<ScheduleInput> inputs, Instant now) {
        Map<String, Schedule> wanted = new LinkedHashMap<>();
        for (ScheduleInput in : inputs) {
            LocalTime time;
            try {
                time = LocalTime.parse(in.time());
            } catch (DateTimeException e) {
                throw ApiException.badRequest("INVALID_TIME", "Horario invalido: " + in.time());
            }
            int mask = toMask(in.daysOfWeek());
            Schedule s = new Schedule();
            s.setId(UUID.randomUUID());
            s.setMedicationId(m.getId());
            s.setTimeOfDay(time);
            s.setDaysMask(mask);
            s.setActive(true);
            s.setCreatedAt(now);
            wanted.put(time.format(HHMM) + "|" + mask, s);
        }
        Set<String> have = new HashSet<>();
        for (Schedule s : schedules.findByMedicationIdIn(List.of(m.getId()))) {
            have.add(s.getTimeOfDay().format(HHMM) + "|" + s.getDaysMask());
        }
        if (have.equals(wanted.keySet())) {
            return false;
        }
        schedules.deleteByMedication(m.getId());
        schedules.saveAll(wanted.values());
        return true;
    }

    static int toMask(List<Integer> days) {
        if (days == null || days.isEmpty()) {
            return Schedule.ALL_DAYS;
        }
        int mask = 0;
        for (Integer d : days) {
            if (d == null || d < 1 || d > 7) {
                throw ApiException.badRequest("INVALID_DAY", "Dia da semana invalido (use 1=segunda ... 7=domingo)");
            }
            mask |= 1 << (d - 1);
        }
        return mask;
    }

    static List<Integer> fromMask(int mask) {
        List<Integer> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            if ((mask & (1 << i)) != 0) {
                days.add(i + 1);
            }
        }
        return days;
    }

    private static Instant clampClientTime(Instant claimed, Instant now) {
        if (claimed == null || claimed.isAfter(now.plusSeconds(300))) {
            return now; // relogio ausente ou adiantado demais: usa o do servidor
        }
        return claimed;
    }

    private Medication load(UUID id) {
        return medications.findById(id).filter(m -> !m.isDeleted()).orElseThrow(() -> ApiException.notFound("Medicamento"));
    }

    public MedicationResponse toResponse(Medication m) {
        List<Schedule> sch = schedules.findByMedicationIdAndActiveTrue(m.getId());
        Integer days = stock.daysRemaining(m, sch);
        List<ScheduleView> views = sch.stream()
                .sorted((a, b) -> a.getTimeOfDay().compareTo(b.getTimeOfDay()))
                .map(s -> new ScheduleView(s.getId(), s.getTimeOfDay().format(HHMM), fromMask(s.getDaysMask())))
                .toList();
        return new MedicationResponse(m.getId(), m.getPatientId(), m.getCompartmentId(), m.getName(), m.getMeasure(),
                m.getDosage(), m.getDoseQuantity(), m.getStockAcquired(), m.getStockCurrent(), m.getMinDaysAlert(),
                days, stock.status(m, days), m.getObservations(), m.isActive(), m.isDeleted(), m.getVersion(),
                m.getClientUpdatedAt(), m.getUpdatedAt(), views);
    }
}
