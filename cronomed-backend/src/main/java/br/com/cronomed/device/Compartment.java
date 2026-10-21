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

/**
 * Compartimento fisico da caixa (indice 1..6): cor do rotulo (RF13), padrao grafico e identificador tatil
 * (RF16/RF17). Limite do hardware: ate 2 pacientes x 3 medicamentos (RNF10).
 */
@Entity
@Table(name = "compartments")
@Getter
@Setter
@NoArgsConstructor
public class Compartment {

    @Id
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "index_number", nullable = false)
    private int indexNumber;

    @Column(name = "patient_id")
    private UUID patientId;

    @Column(name = "color_hex", nullable = false, length = 7)
    private String colorHex;

    @Column(name = "color_name", nullable = false, length = 30)
    private String colorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LabelPattern pattern;

    @Column(name = "tactile_id", nullable = false, length = 40)
    private String tactileId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Cor como inteiro RGB 0xRRGGBB (dado numerico enviado ao ESP32). */
    public int rgb() {
        return Integer.parseInt(colorHex.substring(1), 16);
    }
}
