package br.com.cronomed.alert;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    boolean existsByTypeAndRelatedIdAndResolvedAtIsNull(AlertType type, UUID relatedId);

    Page<Alert> findByPatientIdOrderByCreatedAtDesc(UUID patientId, Pageable pageable);

    List<Alert> findTop10ByPatientIdOrderByCreatedAtDesc(UUID patientId);

    @Modifying
    @Query("update Alert a set a.resolvedAt = :now where a.relatedId = :relatedId and a.resolvedAt is null "
            + "and a.type in :types")
    int resolve(@Param("relatedId") UUID relatedId, @Param("types") List<AlertType> types, @Param("now") Instant now);
}
