package br.com.cronomed.dose;

import br.com.cronomed.dose.DoseService.RecordResult;
import br.com.cronomed.common.Source;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class DoseDtos {

    private DoseDtos() {
    }

    /** eventId: UUID gerado no cliente (chave de idempotencia). Se ausente, o servidor gera um. */
    public record DoseEventRequest(
            UUID eventId,
            @NotNull DoseEventType type,
            Instant occurredAt,
            @PositiveOrZero Long elapsedMs,
            @Size(max = 500) String justification) {
    }

    public record DoseEventResponse(UUID eventId, RecordResult result) {
    }

    public record DoseView(UUID id, UUID patientId, UUID medicationId, String medicationName, String dosage,
                           Instant scheduledAt, DoseStatus status, int doseQuantity, Instant openedAt,
                           Instant confirmedAt, Instant updatedAt) {
    }

    public record EventView(UUID id, UUID scheduledDoseId, UUID medicationId, String medicationName,
                            DoseEventType type, Source source, Instant occurredAt, String justification) {
    }
}
