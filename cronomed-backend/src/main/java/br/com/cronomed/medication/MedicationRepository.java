package br.com.cronomed.medication;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicationRepository extends JpaRepository<Medication, UUID> {

    List<Medication> findByPatientIdAndDeletedAtIsNullOrderByName(UUID patientId);

    Optional<Medication> findByCompartmentId(UUID compartmentId);

    List<Medication> findByCompartmentIdInAndDeletedAtIsNull(Collection<UUID> compartmentIds);

    long countByPatientIdAndCompartmentIdIsNotNullAndDeletedAtIsNull(UUID patientId);

    /** Para sincronizacao: inclui exclusoes logicas (tombstones) alteradas desde o ultimo pull. */
    List<Medication> findByPatientIdInAndUpdatedAtGreaterThanEqual(Collection<UUID> patientIds, Instant since);

    @Query("select distinct m.patientId from Medication m where m.active = true and m.deletedAt is null")
    List<UUID> findPatientIdsWithActiveMedication();

    List<Medication> findByActiveTrueAndDeletedAtIsNull();
}
