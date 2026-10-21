package br.com.cronomed.device;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DeviceDtos {

    private DeviceDtos() {
    }

    public record RegisterDeviceRequest(@NotBlank @Size(max = 40) String macAddress,
                                        @NotBlank @Size(max = 80) String name) {
    }

    public record UpdateDeviceRequest(@NotBlank @Size(max = 80) String name) {
    }

    public record UpdateCompartmentRequest(
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Use o formato #RRGGBB") String colorHex,
            @Size(max = 30) String colorName,
            LabelPattern pattern,
            UUID patientId,
            Boolean clearPatient) {
    }

    public record CompartmentResponse(UUID id, int index, UUID patientId, String colorHex, String colorName,
                                      LabelPattern pattern, String tactileId) {
        public static CompartmentResponse from(Compartment c) {
            return new CompartmentResponse(c.getId(), c.getIndexNumber(), c.getPatientId(), c.getColorHex(),
                    c.getColorName(), c.getPattern(), c.getTactileId());
        }
    }

    public record DeviceResponse(UUID id, String macAddress, String name, DeviceStatus status, Instant lastSeenAt,
                                 Integer batteryPercent, boolean online, Instant createdAt,
                                 List<CompartmentResponse> compartments) {
    }

    /** O segredo do dispositivo e retornado uma unica vez, no cadastro (grave-o no firmware/NVS). */
    public record RegisteredDeviceResponse(DeviceResponse device, String deviceSecret) {
    }

    public record DeviceTokenRequest(@NotBlank String macAddress, @NotBlank String secret) {
    }

    public record DeviceTokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
    }

    // ---- gateway HTTP (alternativa ao MQTT) ----

    public record DeviceEventIn(
            @NotNull UUID id,
            @NotNull @Min(1) @Max(6) Integer compartment,
            UUID doseId,
            @NotBlank String type,
            Instant occurredAt,
            @PositiveOrZero Long elapsedMs,
            Long seq) {
    }

    public record DeviceEventsRequest(@NotNull @Size(max = 200) List<@jakarta.validation.Valid DeviceEventIn> events) {
    }

    public record HeartbeatRequest(@Min(0) @Max(100) Integer battery) {
    }

    public record RejectedEvent(UUID id, String reason) {
    }

    /** ackedIds: eventos recebidos (novos ou duplicados) que o ESP32 ja pode apagar da LittleFS. */
    public record IngestResult(List<UUID> ackedIds, List<RejectedEvent> rejected) {
    }
}
