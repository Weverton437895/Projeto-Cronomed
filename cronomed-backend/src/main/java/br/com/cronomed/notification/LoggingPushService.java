package br.com.cronomed.notification;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Implementacao usada quando o FCM esta desabilitado (desenvolvimento/testes): apenas registra em log. */
@Service
@ConditionalOnProperty(prefix = "cronomed.fcm", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingPushService implements PushService {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushService.class);

    @Override
    public void send(Collection<UUID> userIds, String title, String body, Map<String, String> data) {
        log.info("[PUSH simulado] usuarios={} titulo='{}' corpo='{}' dados={}", userIds, title, body, data);
    }
}
