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
  }
}

bool pollOpening(uint8_t* comp) {
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
      if (raw) {   // fechado -> aberto
        *comp = (uint8_t)(i + 1);
        return true;
      }
    }
  }
  return false;
}

}  // namespace Sensors
