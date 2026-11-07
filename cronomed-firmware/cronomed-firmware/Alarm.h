// Alarmes locais: LED do compartimento + buzzer, disparados pelo relogio (RTC/NTP) a partir do plano
// salvo na flash. Funcionam mesmo sem Wi-Fi (RF19, RF21, RNF12).
#pragma once

#include <Arduino.h>

namespace Alarm {

void begin();

/** Chame com frequencia no loop. now = epoch UTC atual (0 se o relogio ainda nao esta valido). */
void update(uint32_t now);

/** Animacao curta de teste de hardware na inicializacao (acende cada LED, um de cada vez, e apita). */
void selfTest();

/**
 * Alarme que estourou o tempo limite (ALARM_TIMEOUT_S) sem a tampa ser aberta. Chame em loop ate retornar false:
 * cada chamada devolve um compartimento (1..N) e o UUID da dose (buffer de 37 bytes).
 */
bool takeMissed(uint8_t* comp, char* doseId);

/** Tampa fechada de novo: 2 piscadas + 2 bipes rapidos de confirmacao no compartimento comp (1..N). */
void confirmClosed(uint8_t comp);

/** Bipe/piscada unica e bloqueante (usada no feedback do botao de reset). */
void blip(uint16_t ms);

}  // namespace Alarm
