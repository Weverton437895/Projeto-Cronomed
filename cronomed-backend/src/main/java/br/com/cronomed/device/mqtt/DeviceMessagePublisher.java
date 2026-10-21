package br.com.cronomed.device.mqtt;

import br.com.cronomed.device.IotDevice;

/** Saida de mensagens para a caixa (ESP32). Implementada por MQTT quando habilitado. */
public interface DeviceMessagePublisher {

    /** Publica o plano de horarios (JSON numerico) no topico retido da caixa. */
    void publishPlan(IotDevice device, String json);

    /** Remove o plano retido (caixa revogada). */
    void clearPlan(IotDevice device);

    /** Envia a confirmacao de recebimento de eventos (a caixa pode apagar os logs da LittleFS). */
    void publishAck(IotDevice device, String json);
}
