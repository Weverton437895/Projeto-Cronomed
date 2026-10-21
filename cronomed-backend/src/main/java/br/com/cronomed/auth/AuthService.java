package br.com.cronomed.auth;

import br.com.cronomed.audit.AuditService;
import br.com.cronomed.common.ApiException;
import br.com.cronomed.config.AppProperties;
import br.com.cronomed.notification.MailService;
import br.com.cronomed.patient.Patient;
import br.com.cronomed.patient.PatientRepository;
import br.com.cronomed.user.User;
import br.com.cronomed.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    public record AuthResult(User user, String accessToken, String refreshToken, long expiresInSeconds) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PatientRepository patients;
    private final RefreshTokenRepository refreshTokens;
    private final RevokedTokenRepository revokedTokens;
    private final PasswordResetOtpRepository otps;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AppProperties props;
    private final Clock clock;
    private final AuditService audit;
    private final MailService mail;
    private final String dummyHash;

    public AuthService(UserRepository users, PatientRepository patients, RefreshTokenRepository refreshTokens,
                       RevokedTokenRepository revokedTokens, PasswordResetOtpRepository otps,
                       PasswordEncoder encoder, JwtService jwt, AppProperties props, Clock clock,
                       AuditService audit, MailService mail) {
        this.users = users;
        this.patients = patients;
        this.refreshTokens = refreshTokens;
        this.revokedTokens = revokedTokens;
        this.otps = otps;
        this.encoder = encoder;
        this.jwt = jwt;
        this.props = props;
        this.clock = clock;
        this.audit = audit;
        this.mail = mail;
        // Hash descartavel para igualar o tempo de resposta quando o e-mail nao existe.
        this.dummyHash = encoder.encode("cronomed-dummy-password");
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- cadastro (RF01)

    @Transactional
    public User register(AuthDtos.RegisterRequest req) {
        String email = normalizeEmail(req.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_IN_USE", "E-mail ja cadastrado");
        }
        Instant now = clock.instant();
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName(req.name().trim());
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(req.password()));
        user.setProfileType(req.profileType());
        user.setTokenVersion(0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        users.save(user);

        if (req.createSelfPatient()) {
            Patient p = new Patient();
            p.setId(UUID.randomUUID());
            p.setOwnerId(user.getId());
            p.setLinkedUserId(user.getId());
            p.setName(user.getName());
            p.setTimezone(Patient.DEFAULT_TIMEZONE);
            p.setCreatedAt(now);
            p.setUpdatedAt(now);
            patients.save(p);
        }
        audit.record(user.getId(), "USER_REGISTERED", "USER", user.getId(), "consentimento de termos aceito");
        return user;
    }

    // ---------------------------------------------------------------- login (RF02)

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult login(String rawEmail, String password, String clientLabel) {
        Instant now = clock.instant();
        Optional<User> found = users.findByEmail(normalizeEmail(rawEmail));
        if (found.isEmpty()) {
            encoder.matches(password, dummyHash);
            throw ApiException.unauthorized("Credenciais invalidas");
        }
        User user = found.get();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            throw new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                    "Muitas tentativas. Tente novamente mais tarde.");
        }
        if (!encoder.matches(password, user.getPasswordHash())) {
            int attempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(attempts);
            if (attempts >= props.getSecurity().getMaxFailedLogins()) {
                user.setLockedUntil(now.plus(props.getSecurity().getLockDuration()));
                user.setFailedAttempts(0);
            }
            users.save(user);
            throw ApiException.unauthorized("Credenciais invalidas");
        }
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        users.save(user);
        return issueTokens(user, clientLabel);
    }

    private AuthResult issueTokens(User user, String clientLabel) {
        Instant now = clock.instant();
        String access = jwt.createAccessToken(user);
        String refreshRaw = randomToken();
        RefreshToken rt = new RefreshToken();
        rt.setId(UUID.randomUUID());
        rt.setUserId(user.getId());
        rt.setTokenHash(sha256(refreshRaw));
        rt.setExpiresAt(now.plus(props.getJwt().getRefreshTtl()));
        rt.setClientLabel(clientLabel);
        rt.setCreatedAt(now);
        refreshTokens.save(rt);
        return new AuthResult(user, access, refreshRaw, jwt.accessTtlSeconds());
    }

    // ---------------------------------------------------------------- refresh (rotacao)

    @Transactional
    public AuthResult refresh(String refreshRaw) {
        if (refreshRaw == null || refreshRaw.isBlank()) {
            throw ApiException.unauthorized("Refresh token ausente");
        }
        RefreshToken stored = refreshTokens.findByTokenHash(sha256(refreshRaw))
                .orElseThrow(() -> ApiException.unauthorized("Refresh token invalido"));
        if (stored.getExpiresAt().isBefore(clock.instant())) {
            refreshTokens.delete(stored);
            throw ApiException.unauthorized("Refresh token expirado");
        }
        User user = users.findById(stored.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("Usuario inexistente"));
        String label = stored.getClientLabel();
        refreshTokens.delete(stored);
        return issueTokens(user, label);
    }

    // ---------------------------------------------------------------- logoff / revogacao

    @Transactional
    public void logout(String refreshRaw, String accessJti, Instant accessExp) {
        if (refreshRaw != null && !refreshRaw.isBlank()) {
            refreshTokens.deleteByHash(sha256(refreshRaw));
        }
        if (accessJti != null && accessExp != null && !revokedTokens.existsById(accessJti)) {
            revokedTokens.save(new RevokedToken(accessJti, accessExp));
        }
    }

    /** Encerra todas as sessoes do usuario (troca de senha etc.). */
    @Transactional
    public void revokeAllSessions(User user) {
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setUpdatedAt(clock.instant());
        users.save(user);
        refreshTokens.deleteAllByUser(user.getId());
    }

    @Transactional
    public void changePassword(UUID userId, String current, String next) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("Usuario"));
        if (!encoder.matches(current, user.getPasswordHash())) {
            throw ApiException.badRequest("WRONG_PASSWORD", "Senha atual incorreta");
        }
        user.setPasswordHash(encoder.encode(next));
        revokeAllSessions(user);
        audit.record(userId, "PASSWORD_CHANGED", "USER", userId, null);
    }

    // ---------------------------------------------------------------- recuperacao de senha (RF03)

    @Transactional
    public void forgotPassword(String rawEmail) {
        Optional<User> found = users.findByEmail(normalizeEmail(rawEmail));
        if (found.isEmpty()) {
            return; // resposta identica para nao revelar quais e-mails existem
        }
        User user = found.get();
        otps.invalidateAll(user.getId());
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        Instant now = clock.instant();
        PasswordResetOtp otp = new PasswordResetOtp();
        otp.setId(UUID.randomUUID());
        otp.setUserId(user.getId());
        otp.setCodeHash(encoder.encode(code));
        otp.setExpiresAt(now.plus(props.getSecurity().getOtpTtl()));
        otp.setCreatedAt(now);
        otps.save(otp);
        mail.sendPasswordResetCode(user.getEmail(), code);
        audit.record(user.getId(), "PASSWORD_RESET_REQUESTED", "USER", user.getId(), null);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void resetPassword(String rawEmail, String code, String newPassword) {
        ApiException invalid = ApiException.badRequest("INVALID_CODE", "Codigo invalido ou expirado");
        User user = users.findByEmail(normalizeEmail(rawEmail)).orElseThrow(() -> invalid);
        PasswordResetOtp otp = otps.findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> invalid);
        Instant now = clock.instant();
        if (otp.getExpiresAt().isBefore(now) || otp.getAttempts() >= props.getSecurity().getOtpMaxAttempts()) {
            otp.setUsed(true);
            otps.save(otp);
            throw invalid;
        }
        if (!encoder.matches(code, otp.getCodeHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            otps.save(otp);
            throw invalid;
        }
        otp.setUsed(true);
        otps.save(otp);
        user.setPasswordHash(encoder.encode(newPassword));
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        revokeAllSessions(user);
        audit.record(user.getId(), "PASSWORD_RESET_DONE", "USER", user.getId(), null);
    }

    // ---------------------------------------------------------------- utilitarios

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
