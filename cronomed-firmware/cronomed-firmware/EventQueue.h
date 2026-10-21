// Fila persistente de eventos de abertura. O evento so sai da fila quando o servidor confirma (acks),
// entao nada se perde se a caixa ficar sem internet ou reiniciar (RNF12: ate 30 dias).
#pragma once

#include <Arduino.h>

namespace EventQueue {

bool begin();

/** Registra a abertura do compartimento (1..N). doseId pode ser nullptr quando nao ha dose na janela. */
void pushOpening(uint8_t comp, const char* doseId);

size_t size();

/** Monta o JSON {"events":[...]} com ate maxEvents eventos pendentes. Retorna false se a fila esta vazia. */
bool buildPayload(String& out, size_t maxEvents);

/** Remove da fila o evento confirmado (ou rejeitado em definitivo) pelo servidor. */
void removeById(const char* id);

/** Descarta eventos mais antigos que a retencao de 30 dias (so os que tem horario conhecido). */
void prune(uint32_t nowEpoch);

}  // namespace EventQueue
