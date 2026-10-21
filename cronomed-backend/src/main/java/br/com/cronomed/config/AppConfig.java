package br.com.cronomed.config;

import java.time.Clock;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableAsync
public class AppConfig {

    /** Relogio injetavel para facilitar testes. Toda a regra de tempo usa UTC. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Agendamento com ShedLock: com varias instancias da API atras do balanceador,
     * apenas uma executa cada rotina por intervalo (secao 3.4 - RNF10).
     */
    @Configuration
    @EnableScheduling
    @EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
    @ConditionalOnProperty(prefix = "cronomed.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
    public static class SchedulingConfig {

        @Bean
        public LockProvider lockProvider(DataSource dataSource) {
            return new JdbcTemplateLockProvider(
                    JdbcTemplateLockProvider.Configuration.builder()
                            .withJdbcTemplate(new JdbcTemplate(dataSource))
                            .usingDbTime()
                            .build());
        }
    }
}
