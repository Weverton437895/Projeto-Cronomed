package br.com.cronomed.device;

import java.util.Set;
import java.util.UUID;

/**
 * Publicado (e tratado apos o commit) sempre que algo que afeta o cronograma muda: medicamento, horario,
 * compartimento, reabastecimento. Dispara a distribuicao previa do plano de horarios para a caixa (MQTT)
 * e a sinalizacao de sincronizacao para os apps (RF32).
 */
public record PlanChangedEvent(UUID patientId, Set<UUID> deviceIds) {

    public static PlanChangedEvent of(UUID patientId) {
        return new PlanChangedEvent(patientId, Set.of());
    }
}
