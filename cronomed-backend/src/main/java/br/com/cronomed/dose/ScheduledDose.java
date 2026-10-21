package br.com.cronomed.dose;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ocorrencia concreta de uma dose (um medicamento num instante UTC). O ID e deterministico
 * (schedule + instante), entao regerar o plano nunca duplica; e o mesmo UUID que a caixa, o app e o
 * servidor usam para cruzar abertura (sensor) e confirmacao (usuario).
 */
@Entity
@Table(name = "scheduled_doses")
@Getter
@Setter
@NoArgsConstructor
public class ScheduledDose {

    @Id
    private UUID id;

    @Column(name = "medication_id", nullable = false)
    private UUID medicationId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "schedule_id")
    private UUID scheduleId;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private DoseStatus status;

    /** Quantidade a baixar do estoque (copia da posologia no momento da geracao). */
    @Column(name = "dose_quantity", nullable = false)
    private int doseQuantity;

    @Column(name = "due_notified_at")
    private Instant dueNotifiedAt;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "stock_deducted", nullable = false)
    private boolean stockDeducted;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
