// Sensores de abertura (reed switch) com debounce: um por compartimento.
#pragma once

#include <Arduino.h>

namespace Sensors {

void begin();

/** Proxima mudanca estavel de estado. Retorna true e preenche comp (1..N) e opened
 *  (true = fechado->aberto, false = aberto->fechado). */
bool poll(uint8_t* comp, bool* opened);

}  // namespace Sensors
