package br.com.cronomed.patient;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Pessoa que toma os medicamentos. O titular (ownerId) e o administrador legal; linkedUserId e a conta
 * do proprio paciente quando ele usa o app de forma autonoma.
 */
@Entity
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
public class Patient {

    public static final String DEFAULT_TIMEZONE = "America/Sao_Paulo";

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "linked_user_id")
    private UUID linkedUserId;

    @Column(nullable = false, length = 120)
    private String name;

    /** Fuso usado para converter horarios locais de dose em instantes UTC. */
    @Column(nullable = false, length = 60)
    private String timezone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
