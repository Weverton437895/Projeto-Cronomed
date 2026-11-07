package br.com.cronomed.dose;

import br.com.cronomed.alert.AlertService;
import br.com.cronomed.alert.AlertType;
import br.com.cronomed.common.ApiException;
import br.com.cronomed.common.Source;
import br.com.cronomed.medication.Medication;
import br.com.cronomed.medication.MedicationRepository;
import br.com.cronomed.patient.AccessService;
import br.com.cronomed.patient.Patient;
import br.com.cronomed.patient.PatientRepository;
import br.com.cronomed.notification.PushService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rotinas de retaguarda sobre doses (chamadas pelos jobs). Cada metodo e uma transacao propria para que
 * a falha em uma dose nao afete as demais.
 */
@Service
public class DoseMaintenanceService {

    private final ScheduledDoseRepository doses;
    private final DoseEventRepository events;
    private final MedicationRepository medications;
    private final PatientRepository patients;
    private final AlertService alerts;
    private final AccessService access;
    private final PushService push;
    private final Clock clock;

    public DoseMaintenanceService(ScheduledDoseRepository doses, DoseEventRepository events,
                                  MedicationRepository medications, PatientRepository patients,
                                  AlertService alerts, AccessService access, PushService push, Clock clock) {
        this.doses = doses;
        this.events = events;
        this.medications = medications;
        this.patients = patients;
        this.alerts = alerts;
        this.access = access;
        this.push = push;
        this.clock = clock;
    }

    /** Marca a dose como notificada (apenas uma vez) e envia o push inicial "hora da dose" (RF22, RNF04). */
    @Transactional
    public boolean notifyDue(UUID doseId) {
        ScheduledDose dose = doses.findByIdForUpdate(doseId).orElse(null);
        if (dose == null || dose.getStatus() != DoseStatus.PENDING || dose.getDueNotifiedAt() != null) {
            return false;
        }
        Instant now = clock.instant();
        dose.setDueNotifiedAt(now);
        dose.setUpdatedAt(now);
        doses.save(dose);

        Optional<Medication> med = medications.findById(dose.getMedicationId());
        Optional<Patient> patient = patients.findById(dose.getPatientId());
        if (med.isEmpty() || patient.isEmpty()) {
            return true;
        }
        Set<UUID> recipients = access.doseRecipients(dose.getPatientId());
        String body = med.get().getName() + " - " + med.get().getDosage() + " (" + patient.get().getName() + ")";
        Map<String, String> data = Map.of(
                "type", "DOSE_DUE",
                "doseId", dose.getId().toString(),
                "patientId", dose.getPatientId().toString(),
                "medicationId", dose.getMedicationId().toString());
        br.com.cronomed.common.AfterCommit.run(() -> push.send(recipients, "Hora do medicamento", body, data));
        return true;
    }

    /**
     * Encerra a janela de tolerancia de uma dose ainda pendente: caixa aberta sem confirmacao =>
     * OPENED_UNCONFIRMED; nem aberta nem confirmada => MISSED. Registra o evento, o desvio no dashboard
     * (sem baixa de estoque) e alerta o titular, os cuidadores e familiares (RF39, RNF04).
     */
    @Transactional
    public boolean closeExpired(UUID doseId) {
        ScheduledDose dose = doses.findByIdForUpdate(doseId).orElse(null);
        if (dose == null || dose.getStatus() != DoseStatus.PENDING) {
            return false;
        }
        Instant now = clock.instant();
        boolean opened = dose.getOpenedAt() != null;
        DoseStatus newStatus = opened ? DoseStatus.OPENED_UNCONFIRMED : DoseStatus.MISSED;
        dose.setStatus(newStatus);
        dose.setUpdatedAt(now);
        doses.save(dose);

        DoseEvent ev = new DoseEvent();
        ev.setId(UUID.randomUUID());
        ev.setScheduledDoseId(dose.getId());
        ev.setMedicationId(dose.getMedicationId());
        ev.setPatientId(dose.getPatientId());
        ev.setType(opened ? DoseEventType.OPENED_UNCONFIRMED : DoseEventType.MISSED);
        ev.setSource(Source.SERVER);
        ev.setOccurredAt(now);
        ev.setReceivedAt(now);
        events.save(ev);

        Patient patient = patients.findById(dose.getPatientId()).orElse(null);
        Medication med = medications.findById(dose.getMedicationId()).orElse(null);
        if (patient == null || med == null) {
            return true;
        }
        String hour = DateTimeFormatter.ofPattern("HH:mm")
                .format(dose.getScheduledAt().atZone(ZoneId.of(patient.getTimezone())));
        if (opened) {
            alerts.raiseOnce(patient.getId(), AlertType.DOSE_OPENED_UNCONFIRMED, dose.getId(),
                    "Caixa aberta sem confirmacao",
                    patient.getName() + " abriu o compartimento de " + med.getName() + " (" + hour
                            + ") mas a dose nao foi confirmada.");
        } else {
            alerts.raiseOnce(patient.getId(), AlertType.DOSE_MISSED, dose.getId(), "Dose nao registrada",
                    "A dose de " + med.getName() + " de " + patient.getName() + " das " + hour
                            + " nao foi confirmada.");
        }
        return true;
    }

    /**
     * A propria caixa informa que o alarme tocou e a tampa nao foi aberta no tempo limite (60 s). A dose passa a
     * MISSED na hora (sem esperar a janela de tolerancia), sem baixa de estoque, e o titular e os cuidadores sao
     * alertados. Idempotente por eventId. Retorna true quando a dose mudou de estado.
     */
    @Transactional
    public boolean recordNotOpened(UUID eventId, UUID doseId, UUID patientId, UUID deviceId, int compartment,
                                   Instant occurredAt, Long seq) {
        if (events.existsById(eventId)) {
            return false;
        }
        ScheduledDose dose = doses.findByIdForUpdate(doseId).orElseThrow(() -> ApiException.notFound("Dose"));
        if (!dose.getPatientId().equals(patientId)) {
            throw ApiException.badRequest("DOSE_MISMATCH", "Dose nao corresponde ao paciente do compartimento");
        }
        Instant now = clock.instant();
        DoseEvent ev = new DoseEvent();
        ev.setId(eventId);
        ev.setScheduledDoseId(dose.getId());
        ev.setMedicationId(dose.getMedicationId());
        ev.setPatientId(dose.getPatientId());
        ev.setDeviceId(deviceId);
        ev.setCompartmentIndex(compartment);
        ev.setType(DoseEventType.MISSED);
        ev.setSource(Source.DEVICE);
        ev.setOccurredAt(occurredAt != null ? occurredAt : now);
        ev.setReceivedAt(now);
        ev.setClientSeq(seq);
        ev.setJustification("Tampa do compartimento nao aberta em 60 segundos");
        events.save(ev);

        if (dose.getStatus() != DoseStatus.PENDING) {
            return false; // ja confirmada/recusada por outro canal
        }
        dose.setStatus(DoseStatus.MISSED);
        dose.setUpdatedAt(now);
        doses.save(dose);

        Patient patient = patients.findById(dose.getPatientId()).orElse(null);
        Medication med = medications.findById(dose.getMedicationId()).orElse(null);
        if (patient != null && med != null) {
            String hour = DateTimeFormatter.ofPattern("HH:mm")
                    .format(dose.getScheduledAt().atZone(ZoneId.of(patient.getTimezone())));
            alerts.raiseOnce(patient.getId(), AlertType.DOSE_MISSED, dose.getId(), "Dose nao registrada",
                    "A tampa do compartimento de " + med.getName() + " de " + patient.getName() + " (" + hour
                            + ") nao foi aberta e a dose foi perdida.");
        }
        return true;
    }
}
