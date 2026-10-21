package br.com.cronomed.auth;

import br.com.cronomed.device.DeviceRepository;
import br.com.cronomed.device.DeviceStatus;
import br.com.cronomed.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica a requisicao a partir do JWT (header Authorization: Bearer ou cookie HttpOnly do portal web).
 * Rejeita tokens revogados (lista negra), de versao antiga (troca de senha) e de dispositivos revogados.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    public static final String ACCESS_COOKIE = "access_token";

    private final JwtService jwtService;
    private final RevokedTokenRepository revokedTokens;
    private final UserRepository users;
    private final DeviceRepository devices;

    public JwtAuthFilter(JwtService jwtService, RevokedTokenRepository revokedTokens,
                         UserRepository users, DeviceRepository devices) {
        this.jwtService = jwtService;
        this.revokedTokens = revokedTokens;
        this.users = users;
        this.devices = devices;
    }

    public static String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (ACCESS_COOKIE.equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                authenticate(token);
            } catch (JwtException | IllegalArgumentException ex) {
                log.debug("JWT invalido: {}", ex.getMessage());
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String token) {
        Claims claims = jwtService.parse(token);
        if (revokedTokens.existsById(claims.getId())) {
            return;
        }
        String type = claims.get("typ", String.class);
        UUID subject = UUID.fromString(claims.getSubject());

        if (JwtService.TYPE_ACCESS.equals(type)) {
            Integer ver = claims.get("ver", Integer.class);
            users.findById(subject).ifPresent(user -> {
                if (ver != null && ver == user.getTokenVersion()) {
                    setAuth(new AuthPrincipal(subject, false), "ROLE_USER");
                }
            });
        } else if (JwtService.TYPE_DEVICE.equals(type)) {
            devices.findById(subject).ifPresent(device -> {
                if (device.getStatus() == DeviceStatus.ACTIVE) {
                    setAuth(new AuthPrincipal(subject, true), "ROLE_DEVICE");
                }
            });
        }
    }

    private void setAuth(AuthPrincipal principal, String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
