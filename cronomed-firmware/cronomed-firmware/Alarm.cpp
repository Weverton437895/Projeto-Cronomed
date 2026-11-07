#include "Alarm.h"

#include "Plan.h"
#include "config.h"

namespace {

#if defined(ESP_ARDUINO_VERSION_MAJOR) && ESP_ARDUINO_VERSION_MAJOR >= 3
void buzzerAttach() { ledcAttach(BUZZER_PIN, BUZZER_FREQ_HZ, 8); }
void buzzerTone(bool on) { ledcWriteTone(BUZZER_PIN, on ? BUZZER_FREQ_HZ : 0); }
#else
const int BUZZER_CHANNEL = 0;
void buzzerAttach() {
  ledcSetup(BUZZER_CHANNEL, BUZZER_FREQ_HZ, 8);
  ledcAttachPin(BUZZER_PIN, BUZZER_CHANNEL);
}
void buzzerTone(bool on) { ledcWriteTone(BUZZER_CHANNEL, on ? BUZZER_FREQ_HZ : 0); }
#endif

inline void ledWrite(int i, bool on) {
  digitalWrite(LED_PINS[i], on ? LED_ON_LEVEL : !LED_ON_LEVEL);
}

bool g_buzzerOn = false;
bool g_ledState[NUM_COMPARTMENTS];

// Confirmacao de fechamento (nao bloqueante).
uint32_t g_confirmStart = 0;
uint8_t g_confirmComp = 0;
const uint32_t CONFIRM_TOTAL_MS = 4UL * CONFIRM_FLASH_MS;   // aceso, apagado, aceso, apagado

// Alarme tocando (por compartimento) e doses que estouraram o tempo limite sem abertura.
bool g_ringing[NUM_COMPARTMENTS];
char g_ringId[NUM_COMPARTMENTS][37];
bool g_missedPending[NUM_COMPARTMENTS];
char g_missedId[NUM_COMPARTMENTS][37];

}  // namespace

namespace Alarm {

void begin() {
  for (int i = 0; i < NUM_COMPARTMENTS; i++) {
    pinMode(LED_PINS[i], OUTPUT);
    ledWrite(i, false);
    g_ledState[i] = false;
  }
  buzzerAttach();
  buzzerTone(false);
}

void blip(uint16_t ms) {
  buzzerTone(true);
  delay(ms);
  buzzerTone(false);
}

void selfTest() {
  for (int i = 0; i < NUM_COMPARTMENTS; i++) {
    ledWrite(i, true);
    delay(120);
    ledWrite(i, false);
  }
  blip(120);
}

bool takeMissed(uint8_t* comp, char* doseId) {
  for (int i = 0; i < NUM_COMPARTMENTS; i++) {
    if (g_missedPending[i]) {
      g_missedPending[i] = false;
      *comp = (uint8_t)(i + 1);
      strncpy(doseId, g_missedId[i], 36);
      doseId[36] = 0;
      return true;
    }
  }
  return false;
}

void confirmClosed(uint8_t comp) {
  if (comp < 1 || comp > NUM_COMPARTMENTS) return;
  g_confirmComp = comp;
  g_confirmStart = millis();
  if (g_confirmStart == 0) g_confirmStart = 1;
}

void update(uint32_t now) {
  uint32_t ms = millis();
  bool anyActive = false;
  bool anyUrgent = false;

  // Confirmacao de fechamento tem prioridade sobre o resto durante ~320 ms.
  bool confirming = false;
  bool confirmOn = false;
  if (g_confirmStart != 0) {
    uint32_t el = ms - g_confirmStart;
    if (el < CONFIRM_TOTAL_MS) {
      confirming = true;
      confirmOn = ((el / CONFIRM_FLASH_MS) % 2) == 0;
    } else {
      g_confirmStart = 0;
      g_confirmComp = 0;
    }
  }

  for (int i = 0; i < NUM_COMPARTMENTS; i++) {
    PlanDose* d = Plan::findActive((uint8_t)(i + 1), now);
    bool led = false;
    uint32_t elapsed = d ? (now - d->t) : 0;
    if (d && elapsed < ALARM_TIMEOUT_S) {
      anyActive = true;
      g_ringing[i] = true;
      strncpy(g_ringId[i], d->id, 36);
      g_ringId[i][36] = 0;
      bool urgent = elapsed >= ALARM_INTENSIFY_AFTER_S;
      anyUrgent = anyUrgent || urgent;
      uint32_t half = urgent ? ALARM_LED_FAST_HALF_MS : ALARM_LED_SLOW_HALF_MS;
      led = ((ms / half) % 2) == 0;
    } else if (d && g_ringing[i] && strcmp(g_ringId[i], d->id) == 0) {
      // Tocou durante o tempo limite e a tampa nao foi aberta: para o alarme e avisa o loop principal.
      g_ringing[i] = false;
      g_missedPending[i] = true;
      strncpy(g_missedId[i], d->id, 36);
      g_missedId[i][36] = 0;
    } else if (!d) {
      g_ringing[i] = false;   // tampa aberta (dose atendida) ou fora da janela
    }
    if (confirming && g_confirmComp == i + 1) led = confirmOn;
    if (led != g_ledState[i]) {
      g_ledState[i] = led;
      ledWrite(i, led);
    }
  }

  bool beep = false;
  if (confirming) {
    beep = confirmOn;
  } else if (anyActive) {
    uint32_t period = anyUrgent ? ALARM_FAST_PERIOD_MS : ALARM_SLOW_PERIOD_MS;
    uint32_t beepMs = anyUrgent ? ALARM_FAST_BEEP_MS : ALARM_BEEP_MS;
    beep = (ms % period) < beepMs;
  }
  if (beep != g_buzzerOn) {
    g_buzzerOn = beep;
    buzzerTone(beep);
  }
}

}  // namespace Alarm
