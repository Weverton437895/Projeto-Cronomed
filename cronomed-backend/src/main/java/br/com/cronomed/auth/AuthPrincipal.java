package br.com.cronomed.auth;

import java.util.UUID;

/** Identidade autenticada na requisicao: usuario (app/site) ou dispositivo IoT. */
public record AuthPrincipal(UUID id, boolean device) {
}
