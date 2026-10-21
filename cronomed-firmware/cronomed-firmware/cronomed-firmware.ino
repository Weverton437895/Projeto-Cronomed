// ============================================================================
// CronoMed - Firmware da caixa de medicamentos (ESP32)
//
// - Recebe do servidor, via MQTT, o plano de horarios e grava na flash (LittleFS).
// - Dispara sozinha os alarmes (LED + buzzer) pelo relogio, mesmo sem internet.
// - Detecta a abertura de cada compartimento (reed switch) e envia o evento ao servidor,
//   guardando-o na flash ate o servidor confirmar.
// - Envia heartbeat com o nivel da bateria.
//
// Configuracao: config.h (pinos e tempos) e secrets.h (Wi-Fi e MQTT; copie de secrets.example.h).
// Contratos de mensagem: docs/API-E-MQTT.md do repositorio do backend.
// ============================================================================

#include "Alarm.h"
#include "Battery.h"
#include "EventQueue.h"
#include "Network.h"
#include "Plan.h"
#include "Sensors.h"
#include "config.h"
#include "util.h"

static uint32_t lastPrune = 0;

/** Tampa aberta: para o alarme da dose da janela atual (se houver) e registra o evento. */
static void handleOpening(uint8_t comp, uint32_t now) {
  PlanDose* dose = Plan::findActive(comp, now);
  const char* doseId = dose ? dose->id : nullptr;
  EventQueue::pushOpening(comp, doseId);
  if (dose) {
    Plan::markDone(dose->id);   // interrompe imediatamente o alarme (LED e buzzer)
  }
}

void setup() {
  Serial.begin(115200);
  delay(200);
  LOGF("\nCronoMed - iniciando\n");

  Plan::begin();
  EventQueue::begin();
  Sensors::begin();
  Battery::begin();
  Alarm::begin();
  Alarm::selfTest();
  Network::begin();
}

void loop() {
  Network::loop();

  uint32_t now = Util::nowEpoch();   // 0 enquanto o relogio nao estiver sincronizado
  Alarm::update(now);

  uint8_t comp;
  while (Sensors::pollOpening(&comp)) {
    handleOpening(comp, now);
  }

  if (millis() - lastPrune > 600000UL) {   // a cada 10 min
    lastPrune = millis();
    EventQueue::prune(now);
  }

  delay(2);
}
