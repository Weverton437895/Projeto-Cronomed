package br.com.cronomed.patient;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class PatientDtos {

    private PatientDtos() {
    }

    public record PatientRequest(@NotBlank @Size(max = 120) String name, @Size(max = 60) String timezone) {
    }

    public record PatientResponse(UUID id, String name, String timezone, UUID ownerId, UUID linkedUserId,
                                  AccessLevel myAccess, Instant createdAt) {
        public static PatientResponse from(Patient p, AccessLevel level) {
            return new PatientResponse(p.getId(), p.getName(), p.getTimezone(), p.getOwnerId(), p.getLinkedUserId(),
                    level, p.getCreatedAt());
        }
    }

    public record GrantRequest(
            @NotBlank @Email String granteeEmail,
            @NotNull GrantLevel level,
            @NotNull Relationship relationship,
            /** Consentimento explicito e revogavel do titular (LGPD). */
            @AssertTrue(message = "O consentimento explicito do titular e obrigatorio para compartilhar dados")
            boolean consentAccepted) {
    }

    public record GrantUpdateRequest(@NotNull GrantLevel level) {
    }

    public record GrantResponse(UUID id, UUID patientId, UUID granteeUserId, String granteeName, String granteeEmail,
                                GrantLevel level, Relationship relationship, Instant consentAt, Instant createdAt) {
    }
}
