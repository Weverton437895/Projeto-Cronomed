package br.com.cronomed.dose;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoseEventRepository extends JpaRepository<DoseEvent, UUID> {

    List<DoseEvent> findByScheduledDoseIdOrderByOccurredAt(UUID scheduledDoseId);

    @Query("select e from DoseEvent e where e.patientId = :patientId and e.occurredAt >= :from and e.occurredAt < :to "
            + "order by e.occurredAt desc")
    Page<DoseEvent> findHistory(@Param("patientId") UUID patientId, @Param("from") Instant from,
                                @Param("to") Instant to, Pageable pageable);
}
