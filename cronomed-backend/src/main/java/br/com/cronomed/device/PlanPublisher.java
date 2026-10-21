package br.com.cronomed.device;

import br.com.cronomed.device.mqtt.DeviceMessagePublisher;
import br.com.cronomed.device.mqtt.MqttConnectedEvent;
import br.com.cronomed.notification.PushService;
import br.com.cronomed.patient.AccessService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Distribuicao previa e assincrona dos cronogramas (secao 3.1-b): quando algo muda, depois do commit,
 * o plano e publicado (retido) para a caixa via MQTT e os apps recebem um push silencioso de sincronizacao.
 */
@Component
public class PlanPublisher {

    private static final Logger log = LoggerFactory.getLogger(PlanPublisher.class);

    private final DeviceRepository devices;
    private final CompartmentRepository compartments;
    private final PlanService planService;
    private final DeviceMessagePublisher devicePublisher;
    private final PushService push;
    private final AccessService access;
    private final ObjectMapper mapper;

    public PlanPublisher(DeviceRepository devices, CompartmentRepository compartments, PlanService planService,
                         DeviceMessagePublisher devicePublisher, PushService push, AccessService access,
                         ObjectMapper mapper) {
        this.devices = devices;
        this.compartments = compartments;
        this.planService = planService;
        this.devicePublisher = devicePublisher;
        this.push = push;
        this.access = access;
        this.mapper = mapper;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPlanChanged(PlanChangedEvent event) {
        try {
            Set<UUID> deviceIds = new HashSet<>(event.deviceIds());
            deviceIds.addAll(compartments.findDeviceIdsByPatient(event.patientId()));
            for (UUID id : deviceIds) {
                publishFor(id);
            }
            push.send(access.recipients(event.patientId()), null, null,
                    Map.of("type", "SYNC", "patientId", event.patientId().toString()));
        } catch (RuntimeException ex) {
            log.error("Falha ao distribuir plano (paciente {})", event.patientId(), ex);
        }
    }

    /** Ao (re)conectar no broker, republica o plano de todas as caixas ativas (evita ficar desatualizado). */
    @Async
    @EventListener
    public void onMqttConnected(MqttConnectedEvent event) {
        for (IotDevice d : devices.findAll()) {
            if (d.getStatus() == DeviceStatus.ACTIVE) {
                try {
                    publishFor(d.getId());
                } catch (RuntimeException ex) {
                    log.error("Falha ao republicar plano da caixa {}", d.getId(), ex);
                }
            }
        }
    }

    private void publishFor(UUID deviceId) {
        IotDevice device = devices.findById(deviceId).orElse(null);
        if (device == null || device.getStatus() != DeviceStatus.ACTIVE) {
            return;
        }
        try {
            String json = mapper.writeValueAsString(planService.buildForDevice(deviceId));
            devicePublisher.publishPlan(device, json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar plano", e);
        }
    }
}
