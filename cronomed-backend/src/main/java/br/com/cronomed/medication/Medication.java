package br.com.cronomed.medication;

import br.com.cronomed.common.Source;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Medicamento de um paciente, associado a um compartimento (RF05-RF13). O estoque so muda por
 * confirmacao de dose ou reabastecimento (nunca por edicao direta), para evitar conflitos offline.
 */
@Entity
@Table(name = "medications")
@Getter
@Setter
@NoArgsConstructor
public class Medication {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "compartment_id")
    private UUID compartmentId;

    @Column(nullable = false, length = 120)
    private String name;

    /** Medida (ex.: mg, ml, comprimido). */
    @Column(nullable = false, length = 30)
    private String measure;

    /** Dosagem (ex.: "500 mg"). */
    @Column(nullable = false, length = 60)
    private String dosage;

    /** Posologia: quantidade (unidades) a tomar por vez. */
    @Column(name = "dose_quantity", nullable = false)
    private int doseQuantity;

    @Column(name = "stock_acquired", nullable = false)
    private int stockAcquired;

    @Column(name = "stock_current", nullable = false)
    private int stockCurrent;

    /** Alerta antecipado quando restam <= N dias de medicamento (RF23). */
    @Column(name = "min_days_alert", nullable = false)
    private int minDaysAlert;

    @Column(length = 500)
    private String observations;

    @Column(nullable = false)
    private boolean active;

    /** Exclusao logica (RF12): o historico de doses e preservado. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Controle de concorrencia otimista (portal web recusa edicao sobre dado desatualizado). */
    @Version
    @Column(nullable = false)
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_modified_source", nullable = false, length = 10)
    private Source lastModifiedSource;

    /** Data/hora da alteracao na origem, usada na regra "a mais recente prevalece" (RF33). */
    @Column(name = "client_updated_at", nullable = false)
    private Instant clientUpdatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
