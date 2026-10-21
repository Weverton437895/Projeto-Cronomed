package br.com.cronomed.medication;

import br.com.cronomed.stock.StockService.StockStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MedicationDtos {

    private MedicationDtos() {
    }

    /** Horario de alarme: "HH:mm" (hora local do paciente) e dias da semana 1=segunda ... 7=domingo (vazio = todos). */
    public record ScheduleInput(
            @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Use o formato HH:mm") String time,
            List<@Min(1) @Max(7) Integer> daysOfWeek) {
    }

    /**
     * Cadastro/edicao de medicamento. id e opcional na criacao (o app offline gera o UUID para ser idempotente).
     * version: obrigatoria nas edicoes feitas pelo portal web (controle de concorrencia otimista).
     * clientUpdatedAt: data/hora da alteracao na origem (app), usada na regra de conflito "a mais recente prevalece".
     * Estoque nao e editavel aqui: so muda por confirmacao de dose ou reabastecimento.
     */
    public record MedicationRequest(
            UUID id,
            UUID patientId,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 30) String measure,
            @NotBlank @Size(max = 60) String dosage,
            @Min(1) @Max(100) int doseQuantity,
            @Min(0) @Max(100000) Integer stockAcquired,
            @Min(0) @Max(365) Integer minDaysAlert,
            @Size(max = 500) String observations,
            UUID compartmentId,
            Boolean active,
            @NotNull @Size(min = 1, max = 12) List<@Valid ScheduleInput> schedules,
            Long version,
            Instant clientUpdatedAt) {
    }

    public record RefillRequest(@Min(1) @Max(100000) int quantity) {
    }

    public record ScheduleView(UUID id, String time, List<Integer> daysOfWeek) {
    }

    public record MedicationResponse(
            UUID id,
            UUID patientId,
            UUID compartmentId,
            String name,
            String measure,
            String dosage,
            int doseQuantity,
            int stockAcquired,
            int stockCurrent,
            int minDaysAlert,
            Integer daysRemaining,
            StockStatus stockStatus,
            String observations,
            boolean active,
            boolean deleted,
            Long version,
            Instant clientUpdatedAt,
            Instant updatedAt,
            List<ScheduleView> schedules) {
    }
}
