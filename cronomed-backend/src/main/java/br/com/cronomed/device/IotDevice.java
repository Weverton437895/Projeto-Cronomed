package br.com.cronomed.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Caixa de medicamentos (ESP32). Cada caixa tem identidade e segredo proprios (MAC + chave exclusiva). */
@Entity
@Table(name = "iot_devices")
@Getter
@Setter
@NoArgsConstructor
public class IotDevice {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    /** Formato AA:BB:CC:DD:EE:FF (maiusculo). */
    @Column(name = "mac_address", nullable = false, unique = true, length = 17)
    private String macAddress;

    @Column(nullable = false, length = 80)
    private String name;

    /** BCrypt do segredo do dispositivo (o segredo em claro so e mostrado uma vez, no cadastro). */
    @Column(name = "secret_hash", nullable = false, length = 100)
    private String secretHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DeviceStatus status;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "battery_percent")
    private Integer batteryPercent;

    @Column(name = "battery_low_notified", nullable = false)
    private boolean batteryLowNotified;

    @Column(name = "offline_notified", nullable = false)
    private boolean offlineNotified;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Identificador usado nos topicos MQTT: MAC maiusculo sem separadores. */
    public String topicId() {
        return macAddress.replace(":", "");
    }
}
