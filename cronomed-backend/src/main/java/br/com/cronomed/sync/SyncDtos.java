package br.com.cronomed.sync;

import br.com.cronomed.device.DeviceDtos.CompartmentResponse;
import br.com.cronomed.dose.DoseDtos.DoseView;
import br.com.cronomed.dose.DoseEventType;
import br.com.cronomed.medication.MedicationDtos.MedicationRequest;
import br.com.cronomed.medication.MedicationDtos.MedicationResponse;
import br.com.cronomed.patient.PatientDtos.PatientResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SyncDtos {

    private SyncDtos() {
    }

    /**
     * Evento gerado offline no app (confirmacao/justificativa). eventId e a chave de idempotencia (UUID).
     * elapsedMs: tempo decorrido (relogio monotonico) desde o fato ate o envio - corrige relogios desregulados.
     */
    public record EventChange(
            @NotNull UUID eventId,
            @NotNull UUID scheduledDoseId,
            @NotNull DoseEventType type,
            Instant occurredAt,
            @PositiveOrZero Long elapsedMs,
            Long seq,
            @Size(max = 500) String justification) {
    }

    /** Criacao/edicao/exclusao de medicamento feita offline. Em exclusao, "data" e dispensado. */
    public record MedicationChange(
            @NotNull UUID id,
            boolean deleted,
            Instant clientUpdatedAt,
            @Valid MedicationRequest data) {
    }

    public record PushRequest(
            @Size(max = 500) List<@Valid EventChange> events,
            @Size(max = 200) List<@Valid MedicationChange> medications) {
    }

    /**
     * status: APPLIED | DUPLICATE (ja processado - pode descartar da fila) | STALE (o servidor tem versao mais
     * recente; "current" traz o estado vigente) | REJECTED (erro permanente - descartar) | RETRY (erro transitorio
     * - manter na fila e tentar com recuo exponencial).
     */
    public record ItemResult(UUID id, String status, String reason, MedicationResponse current) {
    }

    public record PushResponse(List<ItemResult> events, List<ItemResult> medications, Instant serverTime) {
    }

    public record PullResponse(Instant serverTime, boolean fullSync, List<PatientResponse> patients,
                               List<MedicationResponse> medications, List<CompartmentResponse> compartments,
                               List<DoseView> doses) {
    }
}
