package br.com.cronomed.auth;

import br.com.cronomed.auth.AuthDtos.ForgotPasswordRequest;
import br.com.cronomed.auth.AuthDtos.LoginRequest;
import br.com.cronomed.auth.AuthDtos.RefreshRequest;
import br.com.cronomed.auth.AuthDtos.RegisterRequest;
import br.com.cronomed.auth.AuthDtos.ResetPasswordRequest;
import br.com.cronomed.auth.AuthDtos.SessionResponse;
import br.com.cronomed.auth.AuthService.AuthResult;
import br.com.cronomed.common.RequestInfo;
import br.com.cronomed.config.AppProperties;
import br.com.cronomed.user.UserDtos.UserResponse;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conta e acesso (RF01-RF03). Mobile recebe os tokens no corpo (guardados em EncryptedSharedPreferences);
 * o portal web (header X-Client-Type: web) recebe cookies HttpOnly + Secure + SameSite=Strict.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public static final String REFRESH_COOKIE = "refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final JwtService jwtService;
    private final AppProperties props;

    public AuthController(AuthService authService, JwtService jwtService, AppProperties props) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.props = props;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(authService.register(request)));
    }

    @PostMapping("/login")
    public SessionResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http,
                                 HttpServletResponse response) {
        AuthResult result = authService.login(request.email(), request.password(), request.clientLabel());
        return toResponse(result, http, response);
    }

    @PostMapping("/refresh")
    public SessionResponse refresh(@RequestBody(required = false) RefreshRequest request, HttpServletRequest http,
                                   HttpServletResponse response) {
        String raw = request != null ? request.refreshToken() : null;
        if (raw == null || raw.isBlank()) {
            raw = readCookie(http, REFRESH_COOKIE);
        }
        AuthResult result = authService.refresh(raw);
        return toResponse(result, http, response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest request,
                                       HttpServletRequest http, HttpServletResponse response) {
        String raw = request != null ? request.refreshToken() : null;
        if (raw == null || raw.isBlank()) {
            raw = readCookie(http, REFRESH_COOKIE);
        }
        String jti = null;
        java.time.Instant exp = null;
        String access = JwtAuthFilter.extractToken(http);
        if (access != null) {
            try {
                Claims claims = jwtService.parse(access);
                jti = claims.getId();
                exp = claims.getExpiration().toInstant();
            } catch (RuntimeException ignored) {
                // token ja invalido/expirado: nada a revogar
            }
        }
        authService.logout(raw, jti, exp);
        clearCookies(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<Void> forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.email(), request.code(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ helpers

    private SessionResponse toResponse(AuthResult result, HttpServletRequest http, HttpServletResponse response) {
        UserResponse user = UserResponse.from(result.user());
        if (RequestInfo.isWeb(http)) {
            response.addHeader(HttpHeaders.SET_COOKIE, cookie(JwtAuthFilter.ACCESS_COOKIE, result.accessToken(),
                    "/", props.getJwt().getAccessTtl().toSeconds()).toString());
            response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, result.refreshToken(),
                    REFRESH_COOKIE_PATH, props.getJwt().getRefreshTtl().toSeconds()).toString());
            return new SessionResponse(user, null, null, null, result.expiresInSeconds());
        }
        return new SessionResponse(user, result.accessToken(), result.refreshToken(), "Bearer",
                result.expiresInSeconds());
    }

    private ResponseCookie cookie(String name, String value, String path, long maxAgeSeconds) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(props.getSecurity().isCookieSecure())
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAgeSeconds)
                .build();
    }

    private void clearCookies(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(JwtAuthFilter.ACCESS_COOKIE, "", "/", 0).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, "", REFRESH_COOKIE_PATH, 0).toString());
    }

    private static String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
