package br.com.cronomed.auth;

import java.time.Clock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Remove refresh tokens expirados e entradas da lista negra que ja expiraram naturalmente. */
@Component
@ConditionalOnProperty(prefix = "cronomed.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TokenCleanupJob {

    private final RefreshTokenRepository refreshTokens;
    private final RevokedTokenRepository revokedTokens;
    private final Clock clock;

    public TokenCleanupJob(RefreshTokenRepository refreshTokens, RevokedTokenRepository revokedTokens, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.revokedTokens = revokedTokens;
        this.clock = clock;
    }

    @Scheduled(cron = "0 15 3 * * *")
    @SchedulerLock(name = "tokenCleanup", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void cleanup() {
        refreshTokens.deleteExpired(clock.instant());
        revokedTokens.deleteExpired(clock.instant());
    }
}
