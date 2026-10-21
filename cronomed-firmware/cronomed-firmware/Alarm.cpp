#include "Alarm.h"

#include <Adafruit_NeoPixel.h>

#include "Plan.h"
#include "config.h"

namespace {

Adafruit_NeoPixel strip(NUM_COMPARTMENTS, LED_PIN, NEO_GRB + NEO_KHZ800);

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

uint32_t scale(uint32_t rgb) {
  uint8_t r = (uint8_t)(((rgb >> 16) & 0xFF) * LED_BRIGHTNESS / 255);
  uint8_t g = (uint8_t)(((rgb >> 8) & 0xFF) * LED_BRIGHTNESS / 255);
  uint8_t b = (uint8_t)((rgb & 0xFF) * LED_BRIGHTNESS / 255);
  return strip.Color(r, g, b);
}

bool g_buzzerOn = false;
uint32_t g_lastColor[NUM_COMPARTMENTS];   // ultimo valor enviado aos LEDs (so atualiza a fita quando muda)

}  // namespace

namespace Alarm {

void begin() {
  strip.begin();
  strip.clear();
  strip.show();
  for (int i = 0; i < NUM_COMPARTMENTS; i++) g_lastColor[i] = 0;
  buzzerAttach();
  buzzerTone(false);
}

void selfTest() {
  for (int i = 0; i < NUM_COMPARTMENTS; i++) {
    strip.setPixelColor(i, scale(Plan::colorFor(i + 1)));
    strip.show();
    delay(120);
  }
  buzzerTone(true);
  delay(120);
  buzzerTone(false);
  delay(300);
  strip.clear();
  strip.show();
  for (int i = 0; i < NUM_COMPARTMENTS; i++) g_lastColor[i] = 0;
}

void update(uint32_t now) {
  uint32_t ms = millis();
  bool anyActive = false;
  bool anyFast = false;
  bool changed = false;

  for (int i = 0; i < NUM_COMPARTMENTS; i++) {
    PlanDose* d = Plan::findActive((uint8_t)(i + 1), now);
    uint32_t color = 0;
    if (d) {
      anyActive = true;
      bool fast = (now - d->t) >= ALARM_INTENSIFY_AFTER_S;
      anyFast = anyFast || fast;
      uint32_t period = fast ? ALARM_FAST_PERIOD_MS : ALARM_SLOW_PERIOD_MS;
      bool lit = (ms % period) < (period / 2);   // LED pisca no mesmo ritmo do bipe
      color = lit ? scale(Plan::colorFor((uint8_t)(i + 1))) : 0;
    }
    if (color != g_lastColor[i]) {
      g_lastColor[i] = color;
      strip.setPixelColor(i, color);
      changed = true;
    }
  }
  if (changed) strip.show();

  bool beep = false;
  if (anyActive) {
    uint32_t period = anyFast ? ALARM_FAST_PERIOD_MS : ALARM_SLOW_PERIOD_MS;
    beep = (ms % period) < ALARM_BEEP_MS;
  }
  if (beep != g_buzzerOn) {
    g_buzzerOn = beep;
    buzzerTone(beep);
  }
}

}  // namespace Alarm
