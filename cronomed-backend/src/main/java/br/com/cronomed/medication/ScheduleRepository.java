package br.com.cronomed.medication;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

    List<Schedule> findByMedicationIdAndActiveTrue(UUID medicationId);

    List<Schedule> findByMedicationIdIn(Collection<UUID> medicationIds);

    @Modifying
    @Query("delete from Schedule s where s.medicationId = :medicationId")
    int deleteByMedication(@Param("medicationId") UUID medicationId);
}
