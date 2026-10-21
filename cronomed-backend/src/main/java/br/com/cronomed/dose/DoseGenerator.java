package br.com.cronomed.dose;

import br.com.cronomed.config.AppProperties;
import br.com.cronomed.medication.Medication;
import br.com.cronomed.medication.MedicationRepository;
import br.com.cronomed.medication.Schedule;
import br.com.cronomed.medication.ScheduleRepository;
import br.com.cronomed.patient.Patient;
import br.com.cronomed.patient.PatientRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pre-gera as ocorrencias futuras de dose (horizonte de N dias) a partir dos horarios cadastrados.
 * E o "plano de horarios futuro" distribuido antecipadamente para a caixa e para o app, que passam a
 * disparar os alarmes sozinhos, sem depender do servidor no momento da dose (RNF02/RNF03).
 */
@Service
public class DoseGenerator {

    private final ScheduledDoseRepository doses;
    private final MedicationRepository medications;
    private final ScheduleRepository schedules;
    private final PatientRepository patients;
    private final AppProperties props;
    private final Clock clock;

    public DoseGenerator(ScheduledDoseRepository doses, MedicationRepository medications,
                         ScheduleRepository schedules, PatientRepository patients, AppProperties props, Clock clock) {
        this.doses = doses;
        this.medications = medications;
        this.schedules = schedules;
        this.patients = patients;
        this.props = props;
        this.clock = clock;
    }

    /** UUID deterministico: o mesmo horario do mesmo cronograma sempre gera o mesmo ID. */
    public static UUID doseId(UUID scheduleId, Instant at) {
        String key = scheduleId + "@" + at.getEpochSecond();
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    @Transactional
    public int generateForMedication(UUID medicationId) {
        Medication m = medications.findById(medicationId).orElse(null);
        if (m == null) {
            return 0;
        }
        Patient p = patients.findById(m.getPatientId()).orElse(null);
        return p == null ? 0 : generate(m, p);
    }

    @Transactional
    public int generateForPatient(UUID patientId) {
        Patient p = patients.findById(patientId).orElse(null);
        if (p == null) {
            return 0;
        }
        int total = 0;
        for (Medication m : medications.findByPatientIdAndDeletedAtIsNullOrderByName(patientId)) {
            total += generate(m, p);
        }
        return total;
    }

    /** Cancela e regera as doses futuras de todos os medicamentos do paciente (ex.: mudanca de fuso). */
    @Transactional
    public int regenerateForPatient(UUID patientId) {
        Instant now = clock.instant();
        for (Medication m : medications.findByPatientIdAndDeletedAtIsNullOrderByName(patientId)) {
            doses.cancelFuturePending(m.getId(), now);
        }
        return generateForPatient(patientId);
    }

    /** Cancela as doses futuras pendentes do medicamento (usado antes de regerar apos mudanca de horarios). */
    @Transactional
    public int cancelFuture(UUID medicationId) {
        return doses.cancelFuturePending(medicationId, clock.instant());
    }

    private int generate(Medication m, Patient p) {
        if (!m.isActive() || m.isDeleted()) {
            return 0;
        }
        ZoneId zone = ZoneId.of(p.getTimezone());
        Instant now = clock.instant();
        Instant from = now.minus(Duration.ofMinutes(props.getDose().getToleranceMinutes()));
        Instant to = now.plus(Duration.ofDays(props.getDose().getPlanHorizonDays()));
        LocalDate startDate = LocalDate.ofInstant(from, zone);
        LocalDate endDate = LocalDate.ofInstant(to, zone);

        Map<UUID, ScheduledDose> candidates = new LinkedHashMap<>();
        for (Schedule s : schedules.findByMedicationIdAndActiveTrue(m.getId())) {
            for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                if (!s.runsOn(d.getDayOfWeek())) {
                    continue;
                }
                Instant at = d.atTime(s.getTimeOfDay()).atZone(zone).toInstant();
                if (at.isBefore(from) || at.isAfter(to)) {
                    continue;
                }
                UUID id = doseId(s.getId(), at);
                ScheduledDose sd = new ScheduledDose();
                sd.setId(id);
                sd.setMedicationId(m.getId());
                sd.setPatientId(m.getPatientId());
                sd.setScheduleId(s.getId());
                sd.setScheduledAt(at);
                sd.setStatus(DoseStatus.PENDING);
                sd.setDoseQuantity(m.getDoseQuantity());
                sd.setStockDeducted(false);
                sd.setCreatedAt(now);
                sd.setUpdatedAt(now);
                candidates.put(id, sd);
            }
        }
        if (candidates.isEmpty()) {
            return 0;
        }
        Set<UUID> existing = new HashSet<>();
        for (ScheduledDose e : doses.findAllById(candidates.keySet())) {
            existing.add(e.getId());
            if (e.getStatus() == DoseStatus.CANCELLED && e.getScheduledAt().isAfter(now)) {
                // Mesmo horario do mesmo cronograma voltou a valer (ex.: medicamento reativado): reaproveita a linha.
                e.setStatus(DoseStatus.PENDING);
                e.setDoseQuantity(m.getDoseQuantity());
                e.setUpdatedAt(now);
                doses.save(e);
            }
        }
        List<ScheduledDose> toInsert = new ArrayList<>();
        for (Map.Entry<UUID, ScheduledDose> e : candidates.entrySet()) {
            if (!existing.contains(e.getKey())) {
                toInsert.add(e.getValue());
            }
        }
        doses.saveAll(toInsert);
        return toInsert.size();
    }
}
