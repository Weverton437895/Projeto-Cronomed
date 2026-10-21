package br.com.cronomed.patient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessGrantRepository extends JpaRepository<AccessGrant, UUID> {

    Optional<AccessGrant> findByPatientIdAndGranteeUserIdAndRevokedAtIsNull(UUID patientId, UUID granteeUserId);

    List<AccessGrant> findByPatientIdAndRevokedAtIsNull(UUID patientId);

    Optional<AccessGrant> findByIdAndPatientId(UUID id, UUID patientId);
}
