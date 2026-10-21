package br.com.cronomed.alert;

import br.com.cronomed.common.AfterCommit;
import br.com.cronomed.notification.PushService;
import br.com.cronomed.patient.AccessService;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cria alertas (visiveis no dashboard) e os envia por push ao titular, ao paciente e aos terceiros autorizados. */
@Service
public class AlertService {

    private final AlertRepository alerts;
    private final AccessService access;
    private final PushService push;
    private final Clock clock;

    public AlertService(AlertRepository alerts, AccessService access, PushService push, Clock clock) {
        this.alerts = alerts;
        this.access = access;
        this.push = push;
        this.clock = clock;
    }

    /** Cria o alerta apenas se nao houver outro aberto do mesmo tipo para o mesmo objeto. */
    @Transactional
    public boolean raiseOnce(UUID patientId, AlertType type, UUID relatedId, String title, String message) {
        if (relatedId != null && alerts.existsByTypeAndRelatedIdAndResolvedAtIsNull(type, relatedId)) {
            return false;
        }
        raise(patientId, type, relatedId, title, message);
        return true;
    }

    @Transactional
    public void raise(UUID patientId, AlertType type, UUID relatedId, String title, String message) {
        Alert alert = new Alert();
        alert.setId(UUID.randomUUID());
        alert.setPatientId(patientId);
        alert.setType(type);
        alert.setRelatedId(relatedId);
        alert.setMessage(message.length() > 300 ? message.substring(0, 300) : message);
        alert.setCreatedAt(clock.instant());
        alerts.save(alert);

        Set<UUID> recipients = access.recipients(patientId);
        Map<String, String> data = new HashMap<>();
        data.put("type", type.name());
        data.put("patientId", patientId.toString());
        if (relatedId != null) {
            data.put("relatedId", relatedId.toString());
        }
        AfterCommit.run(() -> push.send(recipients, title, message, data));
    }

    @Transactional
    public void resolve(UUID relatedId, List<AlertType> types) {
        alerts.resolve(relatedId, types, clock.instant());
    }
}
