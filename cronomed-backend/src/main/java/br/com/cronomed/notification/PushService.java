package br.com.cronomed.notification;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Envio de push aos aparelhos dos usuarios. As notificacoes FCM sao complementares e "best effort":
 * o alarme principal e sempre local (ESP32 e AlarmManager do Android) - ver secao 3.1-e.
 */
public interface PushService {

    /** title/body nulos => mensagem silenciosa apenas com dados (ex.: sinal de sincronizacao). */
    void send(Collection<UUID> userIds, String title, String body, Map<String, String> data);
}
