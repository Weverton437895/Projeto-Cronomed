package br.com.cronomed.device;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompartmentRepository extends JpaRepository<Compartment, UUID> {

    List<Compartment> findByDeviceIdOrderByIndexNumber(UUID deviceId);

    Optional<Compartment> findByDeviceIdAndIndexNumber(UUID deviceId, int indexNumber);

    List<Compartment> findByPatientId(UUID patientId);

    List<Compartment> findByPatientIdIn(Collection<UUID> patientIds);

    long countByDeviceIdAndPatientId(UUID deviceId, UUID patientId);

    @Query("select distinct c.deviceId from Compartment c where c.patientId = :patientId")
    List<UUID> findDeviceIdsByPatient(@Param("patientId") UUID patientId);

    @Query("select distinct c.patientId from Compartment c where c.deviceId = :deviceId and c.patientId is not null")
    List<UUID> findPatientIdsByDevice(@Param("deviceId") UUID deviceId);
}
