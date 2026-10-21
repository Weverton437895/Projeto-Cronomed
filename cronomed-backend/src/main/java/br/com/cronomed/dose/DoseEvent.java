package br.com.cronomed.dose;

import br.com.cronomed.common.Source;
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

/** Linha do historico de doses. O ID (UUID gerado na origem) e a chave de idempotencia (RF28). */
@Entity
@Table(name = "dose_events")
@Getter
@Setter
@NoArgsConstructor
public class DoseEvent {

    @Id
    private UUID id;

    @Column(name = "scheduled_dose_id")
    private UUID scheduledDoseId;

    @Column(name = "medication_id")
    private UUID medicationId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "compartment_index")
    private Integer compartmentIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private DoseEventType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Source source;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    /** Instante calibrado pelo relogio do servidor (corrige desvio de relogio do cliente). */
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "client_seq")
    private Long clientSeq;

    @Column(length = 500)
    private String justification;
}
