package br.com.cronomed.dashboard;

import br.com.cronomed.alert.Alert;
import br.com.cronomed.alert.AlertRepository;
import br.com.cronomed.audit.AuditService;
import br.com.cronomed.common.PageResponse;
import br.com.cronomed.dashboard.DashboardDtos.AlertView;
import br.com.cronomed.dashboard.DashboardDtos.DashboardResponse;
import br.com.cronomed.dashboard.DashboardDtos.MedicationSummary;
import br.com.cronomed.dashboard.DashboardDtos.Summary;
import br.com.cronomed.dose.DoseDtos.DoseView;
import br.com.cronomed.dose.DoseDtos.EventView;
import br.com.cronomed.dose.DoseEvent;
import br.com.cronomed.dose.DoseEventRepository;
import br.com.cronomed.dose.DoseStatus;
import br.com.cronomed.dose.DoseViewMapper;
import br.com.cronomed.dose.ScheduledDose;
import br.com.cronomed.dose.ScheduledDoseRepository;
import br.com.cronomed.medication.Medication;
import br.com.cronomed.medication.MedicationRepository;
import br.com.cronomed.medication.Schedule;
import br.com.cronomed.medication.ScheduleRepository;
import br.com.cronomed.patient.AccessService;
import br.com.cronomed.patient.Patient;
import br.com.cronomed.stock.StockService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard de adesao e estoque (RF38) e relatorio exportavel. Todo acesso e registrado na trilha de
 * auditoria (quem visualizou os dados de saude - LGPD Art. 6, X) e respeita a permissao vigente (RF40/RF41).
 */
@Service
public class DashboardService {

    private static final int DEFAULT_DAYS = 30;
    private static final DateTimeFormatter LOCAL_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AccessService access;
    private final ScheduledDoseRepository doses;
    private final DoseEventRepository events;
    private final MedicationRepository medications;
    private final ScheduleRepository schedules;
    private final AlertRepository alerts;
    private final StockService stock;
    private final DoseViewMapper mapper;
    private final AuditService audit;
    private final Clock clock;

    public DashboardService(AccessService access, ScheduledDoseRepository doses, DoseEventRepository events,
                            MedicationRepository medications, ScheduleRepository schedules, AlertRepository alerts,
                            StockService stock, DoseViewMapper mapper, AuditService audit, Clock clock) {
        this.access = access;
        this.doses = doses;
        this.events = events;
        this.medications = medications;
        this.schedules = schedules;
        this.alerts = alerts;
        this.stock = stock;
        this.mapper = mapper;
        this.audit = audit;
        this.clock = clock;
    }

    private Instant[] range(Instant from, Instant to) {
        Instant end = to != null ? to : clock.instant().plus(Duration.ofHours(1));
        Instant start = from != null ? from : end.minus(Duration.ofDays(DEFAULT_DAYS));
        if (!start.isBefore(end)) {
            throw br.com.cronomed.common.ApiException.badRequest("INVALID_RANGE", "Periodo invalido");
        }
        if (Duration.between(start, end).toDays() > 366) {
            throw br.com.cronomed.common.ApiException.badRequest("RANGE_TOO_LARGE", "Periodo maximo: 1 ano");
        }
        return new Instant[]{start, end};
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(UUID userId, UUID patientId, Instant from, Instant to) {
        Patient patient = access.requireView(userId, patientId);
        Instant[] r = range(from, to);
        audit.record(userId, "DASHBOARD_VIEW", "PATIENT", patientId, null);

        Map<UUID, Map<DoseStatus, Integer>> counts = new HashMap<>();
        for (Object[] row : doses.countByMedicationAndStatus(patientId, r[0], r[1])) {
            UUID medId = (UUID) row[0];
            DoseStatus status = (DoseStatus) row[1];
            int n = ((Number) row[2]).intValue();
            counts.computeIfAbsent(medId, k -> new EnumMap<>(DoseStatus.class)).put(status, n);
        }

        int taken = 0;
        int missed = 0;
        int declined = 0;
        int opened = 0;
        int pending = 0;
        List<MedicationSummary> perMedication = new java.util.ArrayList<>();
        for (Medication m : medications.findByPatientIdAndDeletedAtIsNullOrderByName(patientId)) {
            Map<DoseStatus, Integer> c = counts.getOrDefault(m.getId(), new EnumMap<>(DoseStatus.class));
            int t = c.getOrDefault(DoseStatus.TAKEN, 0);
            int ms = c.getOrDefault(DoseStatus.MISSED, 0);
            int d = c.getOrDefault(DoseStatus.DECLINED, 0);
            int o = c.getOrDefault(DoseStatus.OPENED_UNCONFIRMED, 0);
            int p = c.getOrDefault(DoseStatus.PENDING, 0);
            taken += t;
            missed += ms;
            declined += d;
            opened += o;
            pending += p;
            List<Schedule> sch = schedules.findByMedicationIdAndActiveTrue(m.getId());
            Integer days = stock.daysRemaining(m, sch);
            perMedication.add(new MedicationSummary(m.getId(), m.getName(), m.getDosage(), t, ms, d, o, p,
                    m.getStockCurrent(), days, stock.status(m, days)));
        }
        int closed = taken + missed + declined + opened;
        Double adherence = closed == 0 ? null : Math.round(1000.0 * taken / closed) / 1000.0;

        List<AlertView> recent = alerts.findTop10ByPatientIdOrderByCreatedAtDesc(patientId).stream()
                .map(a -> new AlertView(a.getId(), a.getType(), a.getMessage(), a.getCreatedAt(), a.getResolvedAt()))
                .toList();
        return new DashboardResponse(patientId, patient.getName(), r[0], r[1],
                new Summary(taken, missed, declined, opened, pending, adherence), perMedication, recent);
    }

    @Transactional(readOnly = true)
    public PageResponse<DoseView> doseHistory(UUID userId, UUID patientId, Instant from, Instant to, int page, int size) {
        access.requireView(userId, patientId);
        Instant[] r = range(from, to);
        var result = doses.findHistory(patientId, r[0], r[1], PageRequest.of(Math.max(0, page), clamp(size)));
        return new PageResponse<>(mapper.doseViews(result.getContent()), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    /** Historico bruto: aberturas fisicas (sensor) separadas das confirmacoes (RF28). */
    @Transactional(readOnly = true)
    public PageResponse<EventView> eventHistory(UUID userId, UUID patientId, Instant from, Instant to, int page, int size) {
        access.requireView(userId, patientId);
        Instant[] r = range(from, to);
        var result = events.findHistory(patientId, r[0], r[1], PageRequest.of(Math.max(0, page), clamp(size)));
        List<DoseEvent> content = result.getContent();
        return new PageResponse<>(mapper.eventViews(content), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PageResponse<AlertView> alertHistory(UUID userId, UUID patientId, int page, int size) {
        access.requireView(userId, patientId);
        var result = alerts.findByPatientIdOrderByCreatedAtDesc(patientId, PageRequest.of(Math.max(0, page), clamp(size)));
        List<AlertView> items = result.getContent().stream()
                .map((Alert a) -> new AlertView(a.getId(), a.getType(), a.getMessage(), a.getCreatedAt(), a.getResolvedAt()))
                .toList();
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    /** Relatorio de adesao em CSV (profissionais de saude / consultas). Horarios no fuso do paciente. */
    @Transactional(readOnly = true)
    public String reportCsv(UUID userId, UUID patientId, Instant from, Instant to) {
        Patient patient = access.requireView(userId, patientId);
        Instant[] r = range(from, to);
        audit.record(userId, "REPORT_EXPORT", "PATIENT", patientId, null);
        ZoneId zone = ZoneId.of(patient.getTimezone());
        List<ScheduledDose> list = doses.findAllInRange(patientId, r[0], r[1]);
        List<DoseView> views = mapper.doseViews(list);
        StringBuilder sb = new StringBuilder("data_hora_local,medicamento,dosagem,quantidade,status,aberta_em,confirmada_em\n");
        for (DoseView v : views) {
            sb.append(csv(LOCAL_FMT.format(v.scheduledAt().atZone(zone)))).append(',')
                    .append(csv(v.medicationName())).append(',')
                    .append(csv(v.dosage())).append(',')
                    .append(v.doseQuantity()).append(',')
                    .append(csv(v.status().name())).append(',')
                    .append(csv(v.openedAt() == null ? "" : LOCAL_FMT.format(v.openedAt().atZone(zone)))).append(',')
                    .append(csv(v.confirmedAt() == null ? "" : LOCAL_FMT.format(v.confirmedAt().atZone(zone))))
                    .append('\n');
        }
        return sb.toString();
    }

    /** Escapa CSV e neutraliza "injecao de formula" (celulas iniciadas por = + - @). */
    static String csv(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static int clamp(int size) {
        return Math.max(1, Math.min(size <= 0 ? 20 : size, 100));
    }
}
