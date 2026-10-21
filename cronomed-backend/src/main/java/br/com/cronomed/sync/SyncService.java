package br.com.cronomed.sync;

import br.com.cronomed.common.ApiException;
import br.com.cronomed.common.Source;
import br.com.cronomed.config.AppProperties;
import br.com.cronomed.device.CompartmentRepository;
import br.com.cronomed.device.DeviceDtos.CompartmentResponse;
import br.com.cronomed.dose.DoseDtos.DoseView;
import br.com.cronomed.dose.DoseService;
import br.com.cronomed.dose.DoseService.RecordResult;
import br.com.cronomed.dose.DoseService.UserEventInput;
import br.com.cronomed.dose.DoseViewMapper;
import br.com.cronomed.dose.ScheduledDoseRepository;
import br.com.cronomed.medication.MedicationDtos.MedicationRequest;
import br.com.cronomed.medication.MedicationDtos.MedicationResponse;
import br.com.cronomed.medication.MedicationRepository;
import br.com.cronomed.medication.MedicationService;
import br.com.cronomed.medication.MedicationService.ApplyResult;
import br.com.cronomed.medication.MedicationService.ApplyStatus;
import br.com.cronomed.patient.AccessService;
import br.com.cronomed.patient.Patient;
import br.com.cronomed.patient.PatientDtos.PatientResponse;
import br.com.cronomed.patient.PatientRepository;
import br.com.cronomed.sync.SyncDtos.EventChange;
import br.com.cronomed.sync.SyncDtos.ItemResult;
import br.com.cronomed.sync.SyncDtos.MedicationChange;
import br.com.cronomed.sync.SyncDtos.PullResponse;
import br.com.cronomed.sync.SyncDtos.PushRequest;
import br.com.cronomed.sync.SyncDtos.PushResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sincronizacao do app Android (RF31-RF33, RNF12): o app descarrega a fila offline (push) em ordem
 * cronologica e baixa as mudancas do servidor (pull). Cada item e processado em sua propria transacao e e
 * idempotente: falhas parciais nunca duplicam nem perdem dados, e o app so remove da fila o que for confirmado.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);
    private static final Duration PULL_OVERLAP = Duration.ofSeconds(5);

    private final DoseService doseService;
    private final MedicationService medicationService;
    private final PatientRepository patients;
    private final MedicationRepository medications;
    private final CompartmentRepository compartments;
    private final ScheduledDoseRepository doses;
    private final DoseViewMapper doseMapper;
    private final AccessService access;
    private final AppProperties props;
    private final Clock clock;

    public SyncService(DoseService doseService, MedicationService medicationService, PatientRepository patients,
                       MedicationRepository medications, CompartmentRepository compartments,
                       ScheduledDoseRepository doses, DoseViewMapper doseMapper, AccessService access,
                       AppProperties props, Clock clock) {
        this.doseService = doseService;
        this.medicationService = medicationService;
        this.patients = patients;
        this.medications = medications;
        this.compartments = compartments;
        this.doses = doses;
        this.doseMapper = doseMapper;
        this.access = access;
        this.props = props;
        this.clock = clock;
    }

    // NAO transacional: cada item usa a transacao do respectivo servico.
    public PushResponse push(UUID userId, PushRequest req) {
        List<ItemResult> eventResults = new ArrayList<>();
        List<EventChange> events = new ArrayList<>(req.events() == null ? List.of() : req.events());
        events.sort(Comparator.comparing((EventChange e) -> e.seq() == null ? Long.MAX_VALUE : e.seq()));
        for (EventChange e : events) {
            eventResults.add(processEvent(userId, e));
        }

        List<ItemResult> medResults = new ArrayList<>();
        List<MedicationChange> meds = new ArrayList<>(req.medications() == null ? List.of() : req.medications());
        meds.sort(Comparator.comparing((MedicationChange m) ->
                m.clientUpdatedAt() == null ? Instant.MAX : m.clientUpdatedAt()));
        for (MedicationChange c : meds) {
            medResults.add(processMedication(userId, c));
        }
        return new PushResponse(eventResults, medResults, clock.instant());
    }

    private ItemResult processEvent(UUID userId, EventChange e) {
        try {
            RecordResult r = doseService.recordUserEvent(userId, Source.MOBILE, new UserEventInput(e.eventId(),
                    e.scheduledDoseId(), e.type(), e.occurredAt(), e.elapsedMs(), e.seq(), e.justification()));
            return new ItemResult(e.eventId(), r == RecordResult.APPLIED ? "APPLIED" : "DUPLICATE", null, null);
        } catch (ApiException ex) {
            return new ItemResult(e.eventId(), "REJECTED", ex.getCode(), null);
        } catch (RuntimeException ex) {
            log.error("Falha transitoria ao sincronizar evento {}", e.eventId(), ex);
            return new ItemResult(e.eventId(), "RETRY", "TRANSIENT_ERROR", null);
        }
    }

    private ItemResult processMedication(UUID userId, MedicationChange c) {
        try {
            MedicationRequest base = c.data();
            MedicationRequest req = base == null
                    ? new MedicationRequest(c.id(), null, null, null, null, 0, null, null, null, null, null, null,
                    null, c.clientUpdatedAt())
                    : new MedicationRequest(c.id(), base.patientId(), base.name(), base.measure(), base.dosage(),
                    base.doseQuantity(), base.stockAcquired(), base.minDaysAlert(), base.observations(),
                    base.compartmentId(), base.active(), base.schedules(), base.version(),
                    c.clientUpdatedAt() != null ? c.clientUpdatedAt() : base.clientUpdatedAt());
            if (!c.deleted() && base == null) {
                return new ItemResult(c.id(), "REJECTED", "MISSING_DATA", null);
            }
            ApplyResult result = medicationService.applyMobileChange(userId, req, c.deleted());
            if (result.status() == ApplyStatus.STALE) {
                return new ItemResult(c.id(), "STALE", "SERVER_HAS_NEWER", result.current());
            }
            return new ItemResult(c.id(), "APPLIED", null, result.current());
        } catch (ApiException ex) {
            return new ItemResult(c.id(), "REJECTED", ex.getCode(), null);
        } catch (RuntimeException ex) {
            log.error("Falha transitoria ao sincronizar medicamento {}", c.id(), ex);
            return new ItemResult(c.id(), "RETRY", "TRANSIENT_ERROR", null);
        }
    }

    /**
     * Mudancas do servidor desde "since" (use o serverTime do ultimo pull; ausente = sincronizacao completa).
     * Inclui exclusoes logicas (deleted=true) para o app apagar do cache local.
     */
    @Transactional(readOnly = true)
    public PullResponse pull(UUID userId, Instant since) {
        Instant now = clock.instant();
        boolean full = since == null;
        Instant from = full ? Instant.EPOCH : since.minus(PULL_OVERLAP);

        List<Patient> accessible = patients.findAccessible(userId);
        List<UUID> ids = accessible.stream().map(Patient::getId).toList();
        List<PatientResponse> patientViews = accessible.stream()
                .map(p -> PatientResponse.from(p, access.levelOf(userId, p))).toList();
        if (ids.isEmpty()) {
            return new PullResponse(now, full, patientViews, List.of(), List.of(), List.of());
        }
        List<MedicationResponse> meds = medications.findByPatientIdInAndUpdatedAtGreaterThanEqual(ids, from).stream()
                .map(medicationService::toResponse).toList();
        List<CompartmentResponse> comps = compartments.findByPatientIdIn(ids).stream()
                .map(CompartmentResponse::from).toList();
        Instant windowStart = now.minus(Duration.ofDays(3));
        Instant windowEnd = now.plus(Duration.ofDays(props.getDose().getPlanHorizonDays() + 1L));
        List<DoseView> doseViews = doseMapper.doseViews(doses.findForSync(ids, from, windowStart, windowEnd));
        return new PullResponse(now, full, patientViews, meds, comps, doseViews);
    }
}
