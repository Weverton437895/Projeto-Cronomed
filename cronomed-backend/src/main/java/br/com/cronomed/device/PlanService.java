package br.com.cronomed.device;

import br.com.cronomed.config.AppProperties;
import br.com.cronomed.dose.ScheduledDose;
import br.com.cronomed.dose.ScheduledDoseRepository;
import br.com.cronomed.medication.Medication;
import br.com.cronomed.medication.MedicationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monta o plano de horarios enviado a caixa. So dados numericos (indice, cor RGB, epoch UTC, UUID da dose):
 * o cadastro completo (nomes, dosagens) permanece apenas no servidor (RNF10). A caixa grava o plano na
 * LittleFS e dispara os alarmes sozinha pelo RTC sincronizado via NTP.
 */
@Service
public class PlanService {

    public record PlanCompartment(int index, int rgb) {
    }

    public record PlanDose(UUID id, int c, long t) {
    }

    /** toleranceSeconds: janela apos o horario em que o alarme intensifica (bipes mais rapidos - RF20). */
    public record DevicePlan(long version, long serverTime, int toleranceSeconds, List<PlanCompartment> compartments,
                             List<PlanDose> doses) {
    }

    private final CompartmentRepository compartments;
    private final MedicationRepository medications;
    private final ScheduledDoseRepository doses;
    private final AppProperties props;
    private final Clock clock;

    public PlanService(CompartmentRepository compartments, MedicationRepository medications,
                       ScheduledDoseRepository doses, AppProperties props, Clock clock) {
        this.compartments = compartments;
        this.medications = medications;
        this.doses = doses;
        this.props = props;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DevicePlan buildForDevice(UUID deviceId) {
        Instant now = clock.instant();
        List<Compartment> assigned = compartments.findByDeviceIdOrderByIndexNumber(deviceId).stream()
                .filter(c -> c.getPatientId() != null).toList();
        List<PlanCompartment> planCompartments = assigned.stream()
                .map(c -> new PlanCompartment(c.getIndexNumber(), c.rgb())).toList();

        List<PlanDose> planDoses = new ArrayList<>();
        if (!assigned.isEmpty()) {
            Map<UUID, Integer> indexByCompartment = assigned.stream()
                    .collect(Collectors.toMap(Compartment::getId, Compartment::getIndexNumber));
            List<Medication> meds = medications.findByCompartmentIdInAndDeletedAtIsNull(indexByCompartment.keySet())
                    .stream().filter(Medication::isActive).toList();
            if (!meds.isEmpty()) {
                Map<UUID, Integer> indexByMedication = new HashMap<>();
                for (Medication m : meds) {
                    indexByMedication.put(m.getId(), indexByCompartment.get(m.getCompartmentId()));
                }
                Instant from = now.minus(Duration.ofMinutes(props.getDose().getToleranceMinutes()));
                Instant to = now.plus(Duration.ofDays(props.getDose().getPlanHorizonDays()));
                for (ScheduledDose d : doses.findPendingForPlan(indexByMedication.keySet(), from, to)) {
                    planDoses.add(new PlanDose(d.getId(), indexByMedication.get(d.getMedicationId()),
                            d.getScheduledAt().getEpochSecond()));
                }
            }
        }
        return new DevicePlan(now.toEpochMilli(), now.getEpochSecond(),
                props.getDose().getToleranceMinutes() * 60, planCompartments, planDoses);
    }
}
