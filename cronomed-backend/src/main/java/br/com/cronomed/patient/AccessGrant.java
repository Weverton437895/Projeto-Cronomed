package br.com.cronomed.patient;

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

/** Permissao/compartilhamento: o titular autoriza um terceiro a ver ou gerenciar os dados de um paciente. */
@Entity
@Table(name = "access_grants")
@Getter
@Setter
@NoArgsConstructor
public class AccessGrant {

    @Id
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "grantee_user_id", nullable = false)
    private UUID granteeUserId;

    @Column(name = "granted_by", nullable = false)
    private UUID grantedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 10)
    private GrantLevel accessLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Relationship relationship;

    /** Momento do consentimento explicito do titular (LGPD Art. 11, I). */
    @Column(name = "consent_at", nullable = false)
    private Instant consentAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
