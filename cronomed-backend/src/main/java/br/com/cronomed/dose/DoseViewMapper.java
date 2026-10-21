package br.com.cronomed.dose;

import br.com.cronomed.dose.DoseDtos.DoseView;
import br.com.cronomed.dose.DoseDtos.EventView;
import br.com.cronomed.medication.Medication;
import br.com.cronomed.medication.MedicationRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Converte entidades de dose/evento em views, resolvendo nomes de medicamentos em lote. */
@Component
public class DoseViewMapper {

    private final MedicationRepository medications;

    public DoseViewMapper(MedicationRepository medications) {
        this.medications = medications;
    }

    private Map<UUID, Medication> load(Collection<UUID> ids) {
        Map<UUID, Medication> map = new HashMap<>();
        for (Medication m : medications.findAllById(ids)) {
            map.put(m.getId(), m);
        }
        return map;
    }

    public List<DoseView> doseViews(List<ScheduledDose> list) {
        Map<UUID, Medication> meds = load(list.stream().map(ScheduledDose::getMedicationId).collect(Collectors.toSet()));
        return list.stream().map(d -> {
            Medication m = meds.get(d.getMedicationId());
            return new DoseView(d.getId(), d.getPatientId(), d.getMedicationId(), m == null ? null : m.getName(),
                    m == null ? null : m.getDosage(), d.getScheduledAt(), d.getStatus(), d.getDoseQuantity(),
                    d.getOpenedAt(), d.getConfirmedAt(), d.getUpdatedAt());
        }).toList();
    }

    public List<EventView> eventViews(List<DoseEvent> list) {
        Map<UUID, Medication> meds = load(list.stream().map(DoseEvent::getMedicationId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        return list.stream().map(e -> {
            Medication m = e.getMedicationId() == null ? null : meds.get(e.getMedicationId());
            return new EventView(e.getId(), e.getScheduledDoseId(), e.getMedicationId(), m == null ? null : m.getName(),
                    e.getType(), e.getSource(), e.getOccurredAt(), e.getJustification());
        }).toList();
    }
}
