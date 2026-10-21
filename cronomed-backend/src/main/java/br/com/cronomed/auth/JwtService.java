package br.com.cronomed.auth;

import br.com.cronomed.config.AppProperties;
import br.com.cronomed.device.IotDevice;
import br.com.cronomed.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Emissao e validacao de JWT (HMAC SHA-256). A chave vem de variavel de ambiente (JWT_SECRET).
 * Access Token: 15 min. Tokens de dispositivo: claim typ=device.
 */
@Service
public class JwtService {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_DEVICE = "device";

    private final AppProperties props;
    private final Clock clock;
    private final SecretKey key;

    public JwtService(AppProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
        String secret = props.getJwt().getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "cronomed.jwt.secret (JWT_SECRET) deve ter no minimo 32 caracteres");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(props.getJwt().getIssuer())
                .subject(user.getId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.getJwt().getAccessTtl())))
                .claim("typ", TYPE_ACCESS)
                .claim("ver", user.getTokenVersion())
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public String createDeviceToken(IotDevice device) {
        Instant now = clock.instant();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(props.getJwt().getIssuer())
                .subject(device.getId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.getJwt().getDeviceTtl())))
                .claim("typ", TYPE_DEVICE)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** Valida assinatura, emissor e expiracao. Lanca JwtException se invalido. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(props.getJwt().getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long accessTtlSeconds() {
        return props.getJwt().getAccessTtl().toSeconds();
    }
}
