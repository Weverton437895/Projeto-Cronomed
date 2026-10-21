package br.com.cronomed.device;

import br.com.cronomed.alert.AlertService;
import br.com.cronomed.alert.AlertType;
import br.com.cronomed.common.AfterCommit;
import br.com.cronomed.config.AppProperties;
import br.com.cronomed.notification.PushService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deteccao de "desconexao offline de hardware": caixa sem keep alive alem do limite tolerado (secao 3.4-b). */
@Service
public class DeviceMonitorService {

    private final DeviceRepository devices;
    private final CompartmentRepository compartments;
    private final AlertService alerts;
    private final PushService push;
    private final AppProperties props;
    private final Clock clock;

    public DeviceMonitorService(DeviceRepository devices, CompartmentRepository compartments, AlertService alerts,
                                PushService push, AppProperties props, Clock clock) {
        this.devices = devices;
        this.compartments = compartments;
        this.alerts = alerts;
        this.push = push;
        this.props = props;
        this.clock = clock;
    }

    public List<UUID> findOfflineDeviceIds() {
        Instant cutoff = clock.instant().minus(Duration.ofMinutes(props.getDevice().getOfflineAfterMinutes()));
        return devices.findOfflineCandidates(cutoff).stream().map(IotDevice::getId).toList();
    }

    @Transactional
    public void markOffline(UUID deviceId) {
        IotDevice d = devices.findById(deviceId).orElse(null);
        if (d == null || d.isOfflineNotified() || d.getStatus() != DeviceStatus.ACTIVE) {
            return;
        }
        d.setOfflineNotified(true);
        devices.save(d);
        String message = "A caixa \"" + d.getName() + "\" esta sem comunicacao ha mais de "
                + props.getDevice().getOfflineAfterMinutes() + " minutos. Os alarmes locais continuam funcionando.";
        List<UUID> patientIds = compartments.findPatientIdsByDevice(d.getId());
        if (patientIds.isEmpty()) {
            Set<UUID> owner = Set.of(d.getOwnerId());
            Map<String, String> data = Map.of("type", AlertType.DEVICE_OFFLINE.name(), "deviceId", d.getId().toString());
            AfterCommit.run(() -> push.send(owner, "Caixa offline", message, data));
            return;
        }
        for (UUID patientId : patientIds) {
            alerts.raiseOnce(patientId, AlertType.DEVICE_OFFLINE, d.getId(), "Caixa offline", message);
        }
    }
}
