package br.com.cronomed.notification;

import br.com.cronomed.config.AppProperties;
import br.com.cronomed.user.PushToken;
import br.com.cronomed.user.PushTokenRepository;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Push via Firebase Cloud Messaging. Falhas de entrega geram log com o identificador do token e
 * incrementam metricas Prometheus (cronomed.fcm.sent / cronomed.fcm.failed) - secao 3.4-b.
 */
@Service
@ConditionalOnProperty(prefix = "cronomed.fcm", name = "enabled", havingValue = "true")
public class FcmPushService implements PushService {

    private static final Logger log = LoggerFactory.getLogger(FcmPushService.class);

    private final AppProperties props;
    private final PushTokenRepository tokens;
    private final Counter sent;
    private final Counter failed;

    public FcmPushService(AppProperties props, PushTokenRepository tokens, MeterRegistry registry) {
        this.props = props;
        this.tokens = tokens;
        this.sent = Counter.builder("cronomed.fcm.sent").description("Pushes FCM enviados").register(registry);
        this.failed = Counter.builder("cronomed.fcm.failed").description("Pushes FCM com falha").register(registry);
    }

    @PostConstruct
    void init() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }
        String path = props.getFcm().getCredentialsPath();
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("cronomed.fcm.credentials-path (FCM_CREDENTIALS_PATH) e obrigatorio com FCM habilitado");
        }
        try (InputStream in = new FileInputStream(path)) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(in))
                    .build();
            FirebaseApp.initializeApp(options);
        }
    }

    @Override
    public void send(Collection<UUID> userIds, String title, String body, Map<String, String> data) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        List<PushToken> targets = tokens.findByUserIdIn(userIds);
        for (PushToken target : targets) {
            Message.Builder builder = Message.builder()
                    .setToken(target.getToken())
                    .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build());
            if (data != null) {
                builder.putAllData(data);
            }
            if (title != null) {
                builder.setNotification(Notification.builder().setTitle(title).setBody(body).build());
            }
            try {
                FirebaseMessaging.getInstance().send(builder.build());
                sent.increment();
            } catch (FirebaseMessagingException ex) {
                failed.increment();
                log.warn("Falha FCM (usuario={}, tokenId={}): {}", target.getUserId(), target.getId(),
                        ex.getMessagingErrorCode());
                MessagingErrorCode code = ex.getMessagingErrorCode();
                if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                    tokens.deleteByTokenValue(target.getToken()); // token expirado (app desinstalado)
                }
            } catch (RuntimeException ex) {
                failed.increment();
                log.error("Erro inesperado ao enviar FCM (tokenId={})", target.getId(), ex);
            }
        }
    }
}
