package br.com.cronomed.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades da aplicacao (prefixo "cronomed"). Valores sensiveis (JWT_SECRET, senhas)
 * vem de variaveis de ambiente - nunca do codigo (RNF05).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "cronomed")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Security security = new Security();
    private Mqtt mqtt = new Mqtt();
    private Fcm fcm = new Fcm();
    private Dose dose = new Dose();
    private Device device = new Device();
    private Cors cors = new Cors();
    private Mail mail = new Mail();
    private Scheduling scheduling = new Scheduling();

    @Getter
    @Setter
    public static class Jwt {
        /** Chave HMAC (HS256). Minimo 32 caracteres. Vem de variavel de ambiente. */
        private String secret;
        private String issuer = "cronomed";
        private Duration accessTtl = Duration.ofMinutes(15);
        private Duration refreshTtl = Duration.ofDays(7);
        private Duration deviceTtl = Duration.ofHours(24);
    }

    @Getter
    @Setter
    public static class Security {
        /** Cookie "Secure" (somente HTTPS). Deixar false apenas em desenvolvimento local. */
        private boolean cookieSecure = true;
        private int bcryptStrength = 10;
        private int maxFailedLogins = 5;
        private Duration lockDuration = Duration.ofMinutes(15);
        private Duration otpTtl = Duration.ofMinutes(10);
        private int otpMaxAttempts = 5;
    }

    @Getter
    @Setter
    public static class Mqtt {
        private boolean enabled = false;
        private String brokerUrl = "tcp://localhost:1883";
        private String username;
        private String password;
        private String clientId = "cronomed-api";
        private String topicPrefix = "cronomed/devices";
    }

    @Getter
    @Setter
    public static class Fcm {
        private boolean enabled = false;
        private String credentialsPath;
    }

    @Getter
    @Setter
    public static class Dose {
        /** Janela de tolerancia apos o horario programado (RNF04: 1 hora). */
        private int toleranceMinutes = 60;
        /** Folga extra para aguardar sincronizacao offline antes de marcar dose como perdida. */
        private int reconcileGraceMinutes = 5;
        /** Quantos dias futuros de doses sao pre-gerados e distribuidos aos clientes. */
        private int planHorizonDays = 7;
    }

    @Getter
    @Setter
    public static class Device {
        private int offlineAfterMinutes = 5;
        private int batteryCriticalPercent = 20;
        private int maxPerOwner = 5;
        private int maxPatientsPerOwner = 5;
    }

    @Getter
    @Setter
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));
    }

    @Getter
    @Setter
    public static class Mail {
        /** Se false, o OTP e apenas registrado no log (somente desenvolvimento). */
        private boolean enabled = false;
        private String from = "no-reply@cronomed.local";
    }

    @Getter
    @Setter
    public static class Scheduling {
        private boolean enabled = true;
    }
}
