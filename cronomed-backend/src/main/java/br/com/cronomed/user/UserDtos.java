package br.com.cronomed.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserResponse(UUID id, String name, String email, ProfileType profileType, Instant createdAt) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getProfileType(), u.getCreatedAt());
        }
    }

    public record UpdateProfileRequest(@NotBlank @Size(max = 120) String name, ProfileType profileType) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record PushTokenRequest(@NotBlank @Size(max = 512) String token,
                                   @NotBlank @Size(max = 20) String platform) {
    }

    public record DeleteAccountRequest(@NotBlank String password) {
    }
}
