package br.com.cronomed.dose;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduledDoseRepository extends JpaRepository<ScheduledDose, UUID> {

    /** Bloqueia a linha para serializar confirmacoes concorrentes (evita baixa dupla de estoque). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ScheduledDose d where d.id = :id")
    Optional<ScheduledDose> findByIdForUpdate(@Param("id") UUID id);

    @Query("select d from ScheduledDose d where d.status = br.com.cronomed.dose.DoseStatus.PENDING "
            + "and d.dueNotifiedAt is null and d.scheduledAt <= :now and d.scheduledAt >= :since")
    List<ScheduledDose> findDueForNotification(@Param("now") Instant now, @Param("since") Instant since);

    @Query("select d from ScheduledDose d where d.status = br.com.cronomed.dose.DoseStatus.PENDING "
            + "and d.scheduledAt < :cutoff")
    List<ScheduledDose> findExpiredPending(@Param("cutoff") Instant cutoff);

    @Query("select d from ScheduledDose d where d.medicationId in :medIds "
            + "and d.status = br.com.cronomed.dose.DoseStatus.PENDING "
            + "and d.scheduledAt >= :from and d.scheduledAt <= :to order by d.scheduledAt")
    List<ScheduledDose> findPendingForPlan(@Param("medIds") Collection<UUID> medicationIds,
                                           @Param("from") Instant from, @Param("to") Instant to);

    @Query("select d from ScheduledDose d where d.medicationId = :medId "
            + "and d.status = br.com.cronomed.dose.DoseStatus.PENDING "
            + "and d.scheduledAt >= :from and d.scheduledAt <= :to order by d.scheduledAt")
    List<ScheduledDose> findPendingBetween(@Param("medId") UUID medicationId,
                                           @Param("from") Instant from, @Param("to") Instant to);

    @Query("select d from ScheduledDose d where d.patientId = :patientId "
            + "and d.status = br.com.cronomed.dose.DoseStatus.PENDING "
            + "and d.scheduledAt >= :from and d.scheduledAt < :to order by d.scheduledAt")
    List<ScheduledDose> findUpcoming(@Param("patientId") UUID patientId, @Param("from") Instant from,
                                     @Param("to") Instant to);

    @Query("select d from ScheduledDose d where d.patientId = :patientId "
            + "and d.scheduledAt >= :from and d.scheduledAt < :to "
            + "and d.status <> br.com.cronomed.dose.DoseStatus.CANCELLED order by d.scheduledAt desc")
    Page<ScheduledDose> findHistory(@Param("patientId") UUID patientId, @Param("from") Instant from,
                                    @Param("to") Instant to, Pageable pageable);

    @Query("select d from ScheduledDose d where d.patientId = :patientId "
            + "and d.scheduledAt >= :from and d.scheduledAt < :to "
            + "and d.status <> br.com.cronomed.dose.DoseStatus.CANCELLED order by d.scheduledAt")
    List<ScheduledDose> findAllInRange(@Param("patientId") UUID patientId, @Param("from") Instant from,
                                       @Param("to") Instant to);

    @Query("select d.medicationId, d.status, count(d) from ScheduledDose d "
            + "where d.patientId = :patientId and d.scheduledAt >= :from and d.scheduledAt < :to "
            + "group by d.medicationId, d.status")
    List<Object[]> countByMedicationAndStatus(@Param("patientId") UUID patientId, @Param("from") Instant from,
                                              @Param("to") Instant to);

    @Query("select d from ScheduledDose d where d.patientId in :patientIds and d.updatedAt >= :since "
            + "and d.scheduledAt >= :windowStart and d.scheduledAt <= :windowEnd")
    List<ScheduledDose> findForSync(@Param("patientIds") Collection<UUID> patientIds, @Param("since") Instant since,
                                    @Param("windowStart") Instant windowStart, @Param("windowEnd") Instant windowEnd);

    @Modifying
    @Query("update ScheduledDose d set d.status = br.com.cronomed.dose.DoseStatus.CANCELLED, d.updatedAt = :now "
            + "where d.medicationId = :medId and d.status = br.com.cronomed.dose.DoseStatus.PENDING "
            + "and d.scheduledAt > :now")
    int cancelFuturePending(@Param("medId") UUID medicationId, @Param("now") Instant now);
}
