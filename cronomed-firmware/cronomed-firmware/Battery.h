#pragma once

#include <Arduino.h>

namespace Battery {

void begin();

/** Nivel 0-100, ou -1 se nao ha bateria/medicao (o heartbeat segue sem o campo). */
int percent();

}  // namespace Battery
