package br.com.cronomed.device.mqtt;

import br.com.cronomed.device.IotDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Usado quando o MQTT esta desabilitado (dev/testes). A caixa pode obter o plano via HTTP (device-gateway). */
@Component
@ConditionalOnProperty(prefix = "cronomed.mqtt", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopDevicePublisher implements DeviceMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(NoopDevicePublisher.class);

    @Override
    public void publishPlan(IotDevice device, String json) {
        log.debug("[MQTT desabilitado] plano de {} nao publicado", device.getMacAddress());
    }

    @Override
    public void clearPlan(IotDevice device) {
        log.debug("[MQTT desabilitado] plano de {} nao limpo", device.getMacAddress());
    }

    @Override
    public void publishAck(IotDevice device, String json) {
        log.debug("[MQTT desabilitado] ack para {} descartado", device.getMacAddress());
    }
}
