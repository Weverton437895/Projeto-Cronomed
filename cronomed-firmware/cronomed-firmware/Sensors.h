// Sensores de abertura (reed switch) com debounce: um por compartimento.
#pragma once

#include <Arduino.h>

namespace Sensors {

void begin();

/** Detecta a proxima abertura estavel (fechado -> aberto). Retorna true e preenche comp (1..N). */
bool pollOpening(uint8_t* comp);

}  // namespace Sensors
