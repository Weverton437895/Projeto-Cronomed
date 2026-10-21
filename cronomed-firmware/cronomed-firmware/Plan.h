// Plano de horarios recebido do servidor (so dados numericos: indice, cor RGB, UUID da dose, epoch UTC).
// E gravado na flash (LittleFS) para os alarmes continuarem funcionando sem Wi-Fi (RNF12).
#pragma once

#include <Arduino.h>

struct PlanDose {
  char id[37];
  uint8_t comp;      // 1..NUM_COMPARTMENTS
  uint32_t t;        // epoch UTC do horario programado
  bool done;         // true depois que a tampa foi aberta para esta dose
};

namespace Plan {

/** Monta o LittleFS e carrega o plano salvo (se existir). */
bool begin();

/** Aplica um plano recebido (JSON). Se persist=true, grava na flash. Retorna false se o JSON for invalido. */
bool apply(const uint8_t* payload, size_t len, bool persist);

uint64_t version();                    // epoch em milissegundos do momento em que o servidor gerou o plano
uint32_t serverTime();                 // epoch informado pelo servidor ao gerar o plano (0 se desconhecido)
uint32_t toleranceSeconds();
uint32_t colorFor(uint8_t comp);       // 0xRRGGBB (branco se nao houver cor definida)
size_t doseCount();

/** Dose pendente do compartimento cuja janela [t, t+tolerancia) esta aberta agora; nullptr se nao houver. */
PlanDose* findActive(uint8_t comp, uint32_t now);

/** Marca a dose como atendida (para o alarme) e persiste. */
void markDone(const char* doseId);

}  // namespace Plan
