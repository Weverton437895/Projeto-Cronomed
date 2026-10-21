package br.com.cronomed.publicapi;

import br.com.cronomed.common.ApiException;
import br.com.cronomed.common.RequestInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints sem login para a landing page institucional (RF04): formulario de contato. */
@RestController
@RequestMapping("/api/v1/public")
public class PublicController {

    public record ContactRequest(@NotBlank @Size(max = 120) String name,
                                 @NotBlank @Email @Size(max = 180) String email,
                                 @NotBlank @Size(max = 2000) String message) {
    }

    private static final int MAX_PER_WINDOW = 5;
    private static final long WINDOW_SECONDS = 600;

    private final ContactMessageRepository repository;
    private final Clock clock;
    // Limitador simples por IP (memoria da instancia). Em producao, prefira rate limit no proxy/WAF.
    private final ConcurrentHashMap<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    public PublicController(ContactMessageRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @PostMapping("/contact")
    public ResponseEntity<Void> contact(@Valid @RequestBody ContactRequest req) {
        String ip = String.valueOf(RequestInfo.ip());
        Instant now = clock.instant();
        Deque<Instant> q = hits.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst().isBefore(now.minusSeconds(WINDOW_SECONDS))) {
                q.pollFirst();
            }
            if (q.size() >= MAX_PER_WINDOW) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                        "Muitas mensagens. Tente novamente em alguns minutos.");
            }
            q.addLast(now);
        }
        ContactMessage m = new ContactMessage();
        m.setId(UUID.randomUUID());
        m.setName(req.name().trim());
        m.setEmail(req.email().trim().toLowerCase());
        m.setMessage(req.message().trim());
        m.setCreatedAt(now);
        repository.save(m);
        return ResponseEntity.accepted().build();
    }
}
