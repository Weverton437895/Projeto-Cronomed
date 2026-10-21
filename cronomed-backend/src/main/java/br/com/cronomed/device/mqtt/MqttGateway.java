package br.com.cronomed.device.mqtt;

import br.com.cronomed.config.AppProperties;
import br.com.cronomed.device.DeviceDtos.DeviceEventsRequest;
import br.com.cronomed.device.DeviceDtos.HeartbeatRequest;
import br.com.cronomed.device.DeviceDtos.IngestResult;
import br.com.cronomed.device.DeviceIngestService;
import br.com.cronomed.device.DeviceRepository;
import br.com.cronomed.device.DeviceStatus;
import br.com.cronomed.device.IotDevice;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Gateway MQTT das caixas (ESP32). Topicos (prefixo configuravel, MAC sem ":" em maiusculo):
 * <pre>
 *   {prefix}/{mac}/events      (caixa -> API)  {"events":[{id,compartment,doseId?,type:"OPENED",elapsedMs?,seq?}]}
 *   {prefix}/{mac}/heartbeat   (caixa -> API)  {"battery":87}   (keep alive + nivel de bateria)
 *   {prefix}/{mac}/plan        (API -> caixa)  plano de horarios, mensagem RETIDA
 *   {prefix}/{mac}/acks        (API -> caixa)  {"ackedIds":[...],"rejected":[...]}
 * </pre>
 * Em producao use MQTTS (ssl://...:8883). A autenticacao por caixa e a restricao de topicos ficam no broker
 * (usuario = MAC; ACL com %u) - ver docker/mosquitto.
 */
@Component
@ConditionalOnProperty(prefix = "cronomed.mqtt", name = "enabled", havingValue = "true")
public class MqttGateway implements DeviceMessagePublisher, MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttGateway.class);

    private final AppProperties props;
    private final DeviceRepository devices;
    private final DeviceIngestService ingest;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher events;
    private final Counter received;
    private final Counter rejectedMessages;

    private volatile MqttClient client;
    private volatile boolean stopped;
    private ExecutorService executor;

    public MqttGateway(AppProperties props, DeviceRepository devices, DeviceIngestService ingest, ObjectMapper mapper,
                       ApplicationEventPublisher events, MeterRegistry registry) {
        this.props = props;
        this.devices = devices;
        this.ingest = ingest;
        this.mapper = mapper;
        this.events = events;
        this.received = Counter.builder("cronomed.mqtt.received").description("Mensagens MQTT recebidas").register(registry);
        this.rejectedMessages = Counter.builder("cronomed.mqtt.rejected")
                .description("Mensagens MQTT rejeitadas (caixa desconhecida/revogada ou payload invalido)").register(registry);
    }

    @PostConstruct
    void start() {
        executor = Executors.newFixedThreadPool(4);
        Thread connector = new Thread(this::connectLoop, "mqtt-connector");
        connector.setDaemon(true);
        connector.start();
    }

    @PreDestroy
    void stop() {
        stopped = true;
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
            }
            if (client != null) {
                client.close();
            }
        } catch (MqttException e) {
            log.debug("Erro ao desconectar do broker", e);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /** Conecta com nova tentativa a cada 10 s: a API sobe mesmo que o broker ainda esteja indisponivel. */
    private void connectLoop() {
        AppProperties.Mqtt cfg = props.getMqtt();
        // clientId unico por instancia: com varias instancias cada uma recebe as mensagens; a
        // idempotencia por UUID (DoseService) garante processamento unico.
        String clientId = cfg.getClientId() + "-" + UUID.randomUUID().toString().substring(0, 8);
        while (!stopped) {
            try {
                MqttClient c = new MqttClient(cfg.getBrokerUrl(), clientId, new MemoryPersistence());
                c.setCallback(this);
                MqttConnectOptions opts = new MqttConnectOptions();
                opts.setAutomaticReconnect(true);
                opts.setCleanSession(true);
                opts.setKeepAliveInterval(60);
                opts.setConnectionTimeout(15);
                if (cfg.getUsername() != null && !cfg.getUsername().isBlank()) {
                    opts.setUserName(cfg.getUsername());
                    opts.setPassword(cfg.getPassword() == null ? new char[0] : cfg.getPassword().toCharArray());
                }
                client = c;
                c.connect(opts);
                log.info("Conectado ao broker MQTT {}", cfg.getBrokerUrl());
                return;
            } catch (MqttException e) {
                log.warn("Broker MQTT indisponivel ({}). Nova tentativa em 10 s.", e.getMessage());
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------------ callbacks do Paho

    @Override
    public void connectComplete(boolean reconnect, String serverUri) {
        // Chamadas bloqueantes do cliente sincrono nao podem rodar na thread de callback.
        executor.submit(() -> {
            try {
                String prefix = props.getMqtt().getTopicPrefix();
                client.subscribe(prefix + "/+/events", 1);
                client.subscribe(prefix + "/+/heartbeat", 1);
                log.info("Inscrito em {}/+/events e {}/+/heartbeat", prefix, prefix);
                events.publishEvent(new MqttConnectedEvent(reconnect));
            } catch (MqttException e) {
                log.error("Falha ao se inscrever nos topicos MQTT", e);
            }
        });
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("Conexao MQTT perdida: {}. Reconexao automatica ativa.", cause == null ? "?" : cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        byte[] payload = message.getPayload();
        executor.submit(() -> handle(topic, payload));
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // nada a fazer
    }

    // ------------------------------------------------------------------ processamento

    private void handle(String topic, byte[] payload) {
        received.increment();
        try {
            String prefix = props.getMqtt().getTopicPrefix() + "/";
            if (!topic.startsWith(prefix)) {
                return;
            }
            String[] parts = topic.substring(prefix.length()).split("/");
            if (parts.length != 2) {
                return;
            }
            Optional<IotDevice> found = devices.findByMacAddress(macFromTopicId(parts[0]));
            if (found.isEmpty() || found.get().getStatus() != DeviceStatus.ACTIVE) {
                rejectedMessages.increment();
                log.warn("Mensagem MQTT de caixa desconhecida ou revogada: {}", parts[0]);
                return;
            }
            IotDevice device = found.get();
            String json = new String(payload, StandardCharsets.UTF_8);
            switch (parts[1]) {
                case "events" -> {
                    DeviceEventsRequest req = mapper.readValue(json, DeviceEventsRequest.class);
                    IngestResult result = ingest.ingestEvents(device, req.events());
                    publishAck(device, mapper.writeValueAsString(result));
                }
                case "heartbeat" -> {
                    HeartbeatRequest hb = json.isBlank() ? new HeartbeatRequest(null)
                            : mapper.readValue(json, HeartbeatRequest.class);
                    ingest.heartbeat(device.getId(), hb.battery());
                }
                default -> log.debug("Topico MQTT ignorado: {}", topic);
            }
        } catch (Exception e) {
            rejectedMessages.increment();
            log.error("Falha ao processar mensagem MQTT do topico {}", topic, e);
        }
    }

    static String macFromTopicId(String id) {
        if (id.length() != 12) {
            return id;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i += 2) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(id, i, i + 2);
        }
        return sb.toString().toUpperCase();
    }

    // ------------------------------------------------------------------ publicacao

    @Override
    public void publishPlan(IotDevice device, String json) {
        publish(topic(device, "plan"), json.getBytes(StandardCharsets.UTF_8), true);
    }

    @Override
    public void clearPlan(IotDevice device) {
        publish(topic(device, "plan"), new byte[0], true);
    }

    @Override
    public void publishAck(IotDevice device, String json) {
        publish(topic(device, "acks"), json.getBytes(StandardCharsets.UTF_8), false);
    }

    private String topic(IotDevice device, String leaf) {
        return props.getMqtt().getTopicPrefix() + "/" + device.topicId() + "/" + leaf;
    }

    private void publish(String topic, byte[] payload, boolean retained) {
        MqttClient c = client;
        if (c == null || !c.isConnected()) {
            log.warn("MQTT desconectado: publicacao em {} adiada (sera refeita na reconexao)", topic);
            return;
        }
        try {
            c.publish(topic, payload, 1, retained);
        } catch (MqttException e) {
            log.error("Falha ao publicar em {}", topic, e);
        }
    }
}
