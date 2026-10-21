package br.com.cronomed.device;

import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "cronomed.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DeviceMonitorJob {

    private static final Logger log = LoggerFactory.getLogger(DeviceMonitorJob.class);

    private final DeviceMonitorService monitor;

    public DeviceMonitorJob(DeviceMonitorService monitor) {
        this.monitor = monitor;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    @SchedulerLock(name = "deviceMonitor", lockAtMostFor = "PT2M", lockAtLeastFor = "PT20S")
    public void checkOfflineDevices() {
        for (UUID id : monitor.findOfflineDeviceIds()) {
            try {
                monitor.markOffline(id);
            } catch (RuntimeException ex) {
                log.error("Falha ao marcar caixa {} como offline", id, ex);
            }
        }
    }
}
