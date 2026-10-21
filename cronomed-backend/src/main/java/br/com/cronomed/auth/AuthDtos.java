package br.com.cronomed.auth;

import br.com.cronomed.user.ProfileType;
import br.com.cronomed.user.UserDtos.UserResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 180) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotNull ProfileType profileType,
            /** Se true, cria tambem um perfil de paciente para o proprio usuario (uso autonomo). */
            boolean createSelfPatient,
            @AssertTrue(message = "E necessario aceitar os termos de uso e a politica de privacidade (LGPD)")
            boolean acceptTerms) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password,
            @Size(max = 80) String clientLabel) {
    }

    public record RefreshRequest(String refreshToken) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 6) String code,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    /** No portal web os tokens vao em cookies HttpOnly e sao omitidos do corpo. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SessionResponse(UserResponse user, String accessToken, String refreshToken,
                                  String tokenType, long expiresInSeconds) {
    }
}
