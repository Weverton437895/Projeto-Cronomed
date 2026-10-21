package br.com.cronomed.auth;

import br.com.cronomed.common.ApiException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Acesso ao usuario/dispositivo autenticado na requisicao corrente. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal p) {
            return p;
        }
        throw ApiException.unauthorized("Autenticacao necessaria");
    }

    public static UUID userId() {
        AuthPrincipal p = principal();
        if (p.device()) {
            throw ApiException.forbidden("Operacao nao permitida para dispositivos");
        }
        return p.id();
    }

    public static UUID deviceId() {
        AuthPrincipal p = principal();
        if (!p.device()) {
            throw ApiException.forbidden("Operacao exclusiva de dispositivos");
        }
        return p.id();
    }
}
