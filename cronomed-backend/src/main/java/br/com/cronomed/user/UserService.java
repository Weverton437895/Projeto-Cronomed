package br.com.cronomed.user;

import br.com.cronomed.audit.AuditService;
import br.com.cronomed.common.ApiException;
import br.com.cronomed.user.UserDtos.PushTokenRequest;
import br.com.cronomed.user.UserDtos.UpdateProfileRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final PushTokenRepository pushTokens;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final Clock clock;

    public UserService(UserRepository users, PushTokenRepository pushTokens, PasswordEncoder encoder,
                       AuditService audit, Clock clock) {
        this.users = users;
        this.pushTokens = pushTokens;
        this.encoder = encoder;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public User get(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("Usuario"));
    }

    @Transactional
    public User update(UUID id, UpdateProfileRequest req) {
        User u = get(id);
        u.setName(req.name().trim());
        if (req.profileType() != null) {
            u.setProfileType(req.profileType());
        }
        u.setUpdatedAt(clock.instant());
        return users.save(u);
    }

    /** Registra/atualiza o token FCM do aparelho (se o token ja existia em outra conta, ele migra para esta). */
    @Transactional
    public void registerPushToken(UUID userId, PushTokenRequest req) {
        Instant now = clock.instant();
        PushToken t = pushTokens.findByToken(req.token()).orElseGet(() -> {
            PushToken n = new PushToken();
            n.setId(UUID.randomUUID());
            n.setToken(req.token());
            n.setCreatedAt(now);
            return n;
        });
        t.setUserId(userId);
        t.setPlatform(req.platform());
        t.setLastSeenAt(now);
        pushTokens.save(t);
    }

    @Transactional
    public void unregisterPushToken(String token) {
        pushTokens.deleteByTokenValue(token);
    }

    /**
     * Direito de exclusao (LGPD): apaga a conta e, por cascata no banco, pacientes, medicamentos, doses,
     * caixas, permissoes e filas. A trilha de auditoria e mantida de forma pseudonimizada (somente o UUID).
     */
    @Transactional
    public void deleteAccount(UUID userId, String password) {
        User u = get(userId);
        if (!encoder.matches(password, u.getPasswordHash())) {
            throw ApiException.badRequest("WRONG_PASSWORD", "Senha incorreta");
        }
        audit.record(userId, "ACCOUNT_DELETED", "USER", userId, null);
        users.delete(u);
    }
}
