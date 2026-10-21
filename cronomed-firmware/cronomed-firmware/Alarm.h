// Alarmes locais: LED da cor do compartimento + buzzer, disparados pelo relogio (RTC/NTP) a partir do plano
// salvo na flash. Funcionam mesmo sem Wi-Fi (RF19, RF21, RNF12).
#pragma once

#include <Arduino.h>

namespace Alarm {

void begin();

/** Chame com frequencia no loop. now = epoch UTC atual (0 se o relogio ainda nao esta valido). */
void update(uint32_t now);

/** Animacao curta de teste de hardware na inicializacao (acende cada LED com a sua cor). */
void selfTest();

}  // namespace Alarm
