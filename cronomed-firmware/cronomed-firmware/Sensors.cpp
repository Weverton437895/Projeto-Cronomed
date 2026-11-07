#include "Sensors.h"

#include "config.h"

namespace {

bool g_stableOpen[NUM_COMPARTMENTS];
bool g_lastRawOpen[NUM_COMPARTMENTS];
uint32_t g_lastChange[NUM_COMPARTMENTS];

bool readOpen(int i) { return digitalRead(REED_PINS[i]) == REED_OPEN_LEVEL; }

}  // namespace

namespace Sensors {

void begin() {
  for (int i = 0; i < NUM_COMPARTMENTS; i++) {
    pinMode(REED_PINS[i], INPUT_PULLUP);
    g_stableOpen[i] = readOpen(i);   // se ligou com a tampa aberta, isso nao gera evento
    g_lastRawOpen[i] = g_stableOpen[i];
    g_lastChange[i] = millis();
    LOGF("[sensor] compartimento %d inicia %s (GPIO %d)\n", i + 1, g_stableOpen[i] ? "ABERTO" : "FECHADO", REED_PINS[i]);
  }
}

bool poll(uint8_t* comp, bool* opened) {
  uint32_t now = millis();
  for (int i = 0; i < NUM_COMPARTMENTS; i++) {
    bool raw = readOpen(i);
    if (raw != g_lastRawOpen[i]) {
      g_lastRawOpen[i] = raw;
      g_lastChange[i] = now;
      continue;
    }
    if (raw != g_stableOpen[i] && (now - g_lastChange[i]) >= REED_DEBOUNCE_MS) {
      g_stableOpen[i] = raw;
      LOGF("[sensor] compartimento %d %s (GPIO %d)\n", i + 1, raw ? "ABERTO" : "FECHADO", REED_PINS[i]);
      *comp = (uint8_t)(i + 1);
      *opened = raw;
      return true;
    }
  }
  return false;
}

}  // namespace Sensors
