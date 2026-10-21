package br.com.cronomed.audit;

import br.com.cronomed.common.RequestInfo;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava trilha de auditoria (concessao/revogacao de permissoes, acesso a dashboards, revogacao de
 * dispositivos). Usa transacao propria para que o registro persista mesmo em operacoes de leitura.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorUserId, String action, String targetType, Object targetId, String details) {
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setActorUserId(actorUserId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId == null ? null : String.valueOf(targetId));
        log.setIp(RequestInfo.ip());
        log.setDetails(details != null && details.length() > 1000 ? details.substring(0, 1000) : details);
        log.setCreatedAt(clock.instant());
        repository.save(log);
    }
}
