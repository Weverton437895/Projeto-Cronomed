package br.com.cronomed.medication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Horario de alarme de um medicamento (RF08). timeOfDay e o horario local do paciente; as ocorrencias
 * concretas (ScheduledDose) sao geradas em UTC usando o fuso do paciente.
 */
@Entity
@Table(name = "schedules")
@Getter
@Setter
@NoArgsConstructor
public class Schedule {

    public static final int ALL_DAYS = 127;

    @Id
    private UUID id;

    @Column(name = "medication_id", nullable = false)
    private UUID medicationId;

    @Column(name = "time_of_day", nullable = false)
    private LocalTime timeOfDay;

    /** Bitmask de dias da semana: bit0 = segunda ... bit6 = domingo. 127 = todos os dias. */
    @Column(name = "days_mask", nullable = false)
    private int daysMask;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public boolean runsOn(java.time.DayOfWeek day) {
        return (daysMask & (1 << (day.getValue() - 1))) != 0;
    }
}
