package br.com.cronomed.device.mqtt;

/** Publicado quando o gateway conecta (ou reconecta) ao broker MQTT. */
public record MqttConnectedEvent(boolean reconnect) {
}
