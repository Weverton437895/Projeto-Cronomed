package br.com.cronomed.device;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceRepository extends JpaRepository<IotDevice, UUID> {

    Optional<IotDevice> findByMacAddress(String macAddress);

    List<IotDevice> findByOwnerIdOrderByCreatedAt(UUID ownerId);

    long countByOwnerId(UUID ownerId);

    @Query("select d from IotDevice d where d.status = br.com.cronomed.device.DeviceStatus.ACTIVE "
            + "and d.offlineNotified = false and d.lastSeenAt is not null and d.lastSeenAt < :cutoff")
    List<IotDevice> findOfflineCandidates(@Param("cutoff") Instant cutoff);
}
