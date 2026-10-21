package br.com.cronomed.device;

import br.com.cronomed.alert.AlertService;
import br.com.cronomed.alert.AlertType;
import br.com.cronomed.common.AfterCommit;
import br.com.cronomed.common.ApiException;
import br.com.cronomed.config.AppProperties;
import br.com.cronomed.device.DeviceDtos.DeviceEventIn;
import br.com.cronomed.device.DeviceDtos.IngestResult;
import br.com.cronomed.device.DeviceDtos.RejectedEvent;
import br.com.cronomed.dose.DoseService;
import br.com.cronomed.notification.PushService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entrada de telemetria da caixa, compartilhada pelo MQTT e pelo gateway HTTP: eventos de abertura (sensor
 * reed switch), heartbeat (keep alive) e nivel de bateria (RF25, RF36, RNF08).
 */
@Service
public class DeviceIngestService {

    private static final Logger log = LoggerFactory.getLogger(DeviceIngestService.class);

    private final DeviceRepository devices;
    private final CompartmentRepository compartments;
    private final DoseService doseService;
    private final AlertService alerts;
    private final PushService push;
    private final AppProperties props;
    private final Clock clock;

    public DeviceIngestService(DeviceRepository devices, CompartmentRepository compartments, DoseService doseService,
                               AlertService alerts, PushService push, AppProperties props, Clock clock) {
        this.devices = devices;
        this.compartments = compartments;
        this.doseService = doseService;
        this.alerts = alerts;
        this.push = push;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Processa os eventos em ordem cronologica de sequencia. Cada evento roda na sua propria transacao
     * (DoseService.recordDeviceOpening): uma falha isolada nao descarta os demais e reenvios sao idempotentes.
     */
    public IngestResult ingestEvents(IotDevice device, List<DeviceEventIn> events) {
        List<DeviceEventIn> ordered = new ArrayList<>(events);
        ordered.sort(Comparator.comparing((DeviceEventIn e) -> e.seq() == null ? Long.MAX_VALUE : e.seq()));
        List<UUID> acked = new ArrayList<>();
        List<DeviceDtos.RejectedEvent> rejected = new ArrayList<>();
        for (DeviceEventIn e : ordered) {
            if (!"OPENED".equalsIgnoreCase(e.type()) && !"BOX_OPENED".equalsIgnoreCase(e.type())) {
                rejected.add(new RejectedEvent(e.id(), "UNSUPPORTED_TYPE"));
                continue;
            }
            try {
                doseService.recordDeviceOpening(device, e.id(), e.compartment(), e.doseId(), e.occurredAt(),
                        e.elapsedMs(), e.seq());
                acked.add(e.id());
            } catch (ApiException ex) {
                rejected.add(new RejectedEvent(e.id(), ex.getCode())); // erro permanente: nao adianta reenviar
            } catch (RuntimeException ex) {
                log.error("Falha ao processar evento {} da caixa {}", e.id(), device.getMacAddress(), ex);
                // erro transitorio: nao e confirmado, a caixa reenvia
            }
        }
        touch(device.getId(), null);
        return new IngestResult(acked, rejected);
    }

    /** Heartbeat (keep alive) com nivel de bateria opcional. */
    @Transactional
    public void heartbeat(UUID deviceId, Integer batteryPercent) {
        touch(deviceId, batteryPercent);
    }

    private void touch(UUID deviceId, Integer batteryPercent) {
        IotDevice d = devices.findById(deviceId).orElse(null);
        if (d == null || d.getStatus() != DeviceStatus.ACTIVE) {
            return;
        }
        d.setLastSeenAt(clock.instant());
        if (d.isOfflineNotified()) {
            d.setOfflineNotified(false);
            alerts.resolve(d.getId(), List.of(AlertType.DEVICE_OFFLINE));
        }
        if (batteryPercent != null) {
            d.setBatteryPercent(batteryPercent);
            int critical = props.getDevice().getBatteryCriticalPercent();
            if (batteryPercent <= critical && !d.isBatteryLowNotified()) {
                d.setBatteryLowNotified(true);
                notifyBatteryLow(d, batteryPercent);
            } else if (batteryPercent > critical + 10 && d.isBatteryLowNotified()) {
                d.setBatteryLowNotified(false);
                alerts.resolve(d.getId(), List.of(AlertType.BATTERY_LOW));
            }
        }
        devices.save(d);
    }

    /** RF36: avisa o titular e os responsaveis enquanto a caixa ainda tem energia para transmitir. */
    private void notifyBatteryLow(IotDevice d, int percent) {
        List<UUID> patientIds = compartments.findPatientIdsByDevice(d.getId());
        String message = "A bateria da caixa \"" + d.getName() + "\" esta em " + percent
                + "%. Conecte-a ao carregador.";
        if (patientIds.isEmpty()) {
            Set<UUID> owner = Set.of(d.getOwnerId());
            Map<String, String> data = Map.of("type", AlertType.BATTERY_LOW.name(), "deviceId", d.getId().toString());
            AfterCommit.run(() -> push.send(owner, "Bateria baixa", message, data));
            return;
        }
        for (UUID patientId : patientIds) {
            alerts.raiseOnce(patientId, AlertType.BATTERY_LOW, d.getId(), "Bateria baixa", message);
        }
    }
}
