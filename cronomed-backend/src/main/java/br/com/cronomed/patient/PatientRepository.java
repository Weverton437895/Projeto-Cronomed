package br.com.cronomed.patient;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    long countByOwnerId(UUID ownerId);

    List<Patient> findByOwnerId(UUID ownerId);

    @Query("select p from Patient p where p.ownerId = :uid or p.linkedUserId = :uid "
            + "or p.id in (select g.patientId from AccessGrant g where g.granteeUserId = :uid and g.revokedAt is null) "
            + "order by p.name")
    List<Patient> findAccessible(@Param("uid") UUID userId);

    List<Patient> findByIdIn(Collection<UUID> ids);
}
