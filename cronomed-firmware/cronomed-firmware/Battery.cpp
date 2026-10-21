#include "Battery.h"

#include "config.h"

namespace Battery {

void begin() {
#if BATTERY_PIN >= 0
  pinMode(BATTERY_PIN, INPUT);
  analogSetPinAttenuation(BATTERY_PIN, ADC_11db);
#endif
}

int percent() {
#if BATTERY_PIN < 0
  return -1;
#else
  uint32_t sum = 0;
  for (int i = 0; i < 16; i++) {
    sum += analogReadMilliVolts(BATTERY_PIN);
    delay(2);
  }
  float mv = (sum / 16.0f) * BATTERY_DIVIDER_RATIO;
  if (mv < 1000.0f) return -1;   // pino flutuando: nao ha bateria ligada
  float pct = (mv - BATTERY_EMPTY_MV) * 100.0f / (BATTERY_FULL_MV - BATTERY_EMPTY_MV);
  if (pct < 0) pct = 0;
  if (pct > 100) pct = 100;
  return (int)(pct + 0.5f);
#endif
}

}  // namespace Battery
