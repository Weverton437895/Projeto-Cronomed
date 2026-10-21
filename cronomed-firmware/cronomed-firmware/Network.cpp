#include "Network.h"

#include <ArduinoJson.h>
#include <PubSubClient.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <sys/time.h>

#include "Battery.h"
#include "EventQueue.h"
#include "Plan.h"
#include "config.h"
#include "secrets.h"
#include "util.h"

namespace {

WiFiClient g_plainClient;
WiFiClientSecure g_secureClient;
PubSubClient g_mqtt;

String g_topicEvents, g_topicHeartbeat, g_topicPlan, g_topicAcks;
bool g_ntpStarted = false;
uint32_t g_lastWifiTry = 0;
uint32_t g_lastMqttTry = 0;
uint32_t g_lastHeartbeat = 0;
uint32_t g_lastEventSend = 0;
bool g_sendEventsNow = false;

void startNtp() {
  configTime(0, 0, NTP_SERVER_1, NTP_SERVER_2);   // fuso UTC: a caixa so trabalha com epoch UTC
  g_ntpStarted = true;
}

/** Plano recebido: aplica e, se o relogio ainda nao estiver valido, usa a hora do servidor como apoio. */
void handlePlan(const uint8_t* payload, size_t len) {
  if (!Plan::apply(payload, len, true)) return;
  if (Util::nowEpoch() == 0 && Plan::serverTime() >= MIN_VALID_EPOCH) {
    struct timeval tv = {(time_t)Plan::serverTime(), 0};
    settimeofday(&tv, nullptr);
    LOGF("[hora] NTP indisponivel: relogio ajustado pela hora do servidor\n");
  }
}

/** Confirmacao do servidor: {"ackedIds":[...],"rejected":[{"id":"...","reason":"..."}]}. */
void handleAcks(const uint8_t* payload, size_t len) {
  JsonDocument doc;
  if (deserializeJson(doc, payload, len)) return;
  for (JsonVariantConst id : doc["ackedIds"].as<JsonArrayConst>()) {
    const char* s = id.as<const char*>();
    if (s) EventQueue::removeById(s);
  }
  // Rejeitado = erro permanente (ex.: compartimento sem paciente); reenviar so repetiria o erro.
  for (JsonObjectConst r : doc["rejected"].as<JsonArrayConst>()) {
    const char* s = r["id"] | "";
    if (s[0]) {
      LOGF("[mqtt] evento %s rejeitado: %s\n", s, (const char*)(r["reason"] | "?"));
      EventQueue::removeById(s);
    }
  }
  g_sendEventsNow = EventQueue::size() > 0;   // ainda ha eventos? envia o proximo lote
}

void onMessage(char* topic, byte* payload, unsigned int length) {
  if (g_topicPlan == topic) {
    handlePlan(payload, length);
  } else if (g_topicAcks == topic) {
    handleAcks(payload, length);
  }
}

void connectWifi() {
  uint32_t now = millis();
  if (WiFi.status() == WL_CONNECTED) return;
  if (g_lastWifiTry != 0 && now - g_lastWifiTry < WIFI_RETRY_MS) return;
  g_lastWifiTry = now;
  LOGF("[wifi] conectando em %s ...\n", WIFI_SSID);
  WiFi.disconnect();
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
}

void connectMqtt() {
  if (g_mqtt.connected() || WiFi.status() != WL_CONNECTED) return;
  uint32_t now = millis();
  if (g_lastMqttTry != 0 && now - g_lastMqttTry < MQTT_RECONNECT_MS) return;
  g_lastMqttTry = now;

  String id = Network::topicId();
  String clientId = "cronomed-" + id;
  LOGF("[mqtt] conectando em %s:%d como %s ...\n", MQTT_HOST, MQTT_PORT, id.c_str());
  if (g_mqtt.connect(clientId.c_str(), id.c_str(), MQTT_PASSWORD)) {
    LOGF("[mqtt] conectado\n");
    g_mqtt.subscribe(g_topicPlan.c_str(), 1);   // retido: o plano mais recente chega logo apos conectar
    g_mqtt.subscribe(g_topicAcks.c_str(), 1);
    g_lastHeartbeat = 0;   // envia heartbeat imediatamente
    g_sendEventsNow = true;
  } else {
    LOGF("[mqtt] falha (estado %d)\n", g_mqtt.state());
  }
}

void sendHeartbeat() {
  uint32_t now = millis();
  if (g_lastHeartbeat != 0 && now - g_lastHeartbeat < HEARTBEAT_MS) return;
  g_lastHeartbeat = now == 0 ? 1 : now;
  int pct = Battery::percent();
  String body = pct >= 0 ? String("{\"battery\":") + pct + "}" : String("{}");
  g_mqtt.publish(g_topicHeartbeat.c_str(), body.c_str());
}

void sendEvents() {
  if (EventQueue::size() == 0) return;
  uint32_t now = millis();
  bool due = g_sendEventsNow || (now - g_lastEventSend >= EVENT_RETRY_MS);
  if (!due) return;
  g_sendEventsNow = false;
  g_lastEventSend = now;
  String payload;
  if (!EventQueue::buildPayload(payload, EVENT_BATCH_MAX)) return;
  // PubSubClient publica com QoS 0; a garantia de entrega vem da fila persistente + acks do servidor
  // (o evento so sai da fila quando confirmado; reenvios sao idempotentes pelo UUID do evento).
  if (!g_mqtt.publish(g_topicEvents.c_str(), payload.c_str())) {
    LOGF("[mqtt] falha ao publicar eventos (%u bytes)\n", (unsigned)payload.length());
  }
}

}  // namespace

namespace Network {

String macAddress() { return WiFi.macAddress(); }

String topicId() {
  String mac = WiFi.macAddress();
  mac.replace(":", "");
  mac.toUpperCase();
  return mac;
}

bool mqttConnected() { return g_mqtt.connected(); }

void begin() {
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);

  String base = String(MQTT_TOPIC_PREFIX) + "/" + topicId();
  g_topicEvents = base + "/events";
  g_topicHeartbeat = base + "/heartbeat";
  g_topicPlan = base + "/plan";
  g_topicAcks = base + "/acks";

#if MQTT_USE_TLS
  #ifdef MQTT_CA_CERT
  g_secureClient.setCACert(MQTT_CA_CERT);
  #else
  g_secureClient.setInsecure();   // SOMENTE desenvolvimento
  #endif
  g_mqtt.setClient(g_secureClient);
#else
  g_mqtt.setClient(g_plainClient);
#endif
  g_mqtt.setServer(MQTT_HOST, MQTT_PORT);
  g_mqtt.setBufferSize(MQTT_BUFFER_BYTES);
  g_mqtt.setKeepAlive(30);
  g_mqtt.setCallback(onMessage);

  LOGF("============================================\n");
  LOGF("MAC desta caixa (cadastre no sistema): %s\n", macAddress().c_str());
  LOGF("Usuario MQTT: %s\n", topicId().c_str());
  LOGF("============================================\n");
}

void loop() {
  connectWifi();
  if (WiFi.status() == WL_CONNECTED) {
    if (!g_ntpStarted) startNtp();
    connectMqtt();
  }
  if (g_mqtt.connected()) {
    g_mqtt.loop();
    sendHeartbeat();
    sendEvents();
  }
}

}  // namespace Network
