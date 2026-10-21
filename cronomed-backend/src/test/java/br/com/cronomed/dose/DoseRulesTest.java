package br.com.cronomed.dose;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DoseRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

    @Test
    void calibrateUsesElapsedTimeWhenProvided() {
        // O relogio do cliente esta errado, mas o tempo decorrido (monotonico) e confiavel.
        Instant wrongClientClock = Instant.parse("2020-01-01T00:00:00Z");
        Instant result = DoseService.calibrate(wrongClientClock, 90_000L, NOW);
        assertEquals(NOW.minusSeconds(90), result);
    }

    @Test
    void calibrateRejectsFutureTimestamps() {
        Instant future = NOW.plus(Duration.ofHours(3));
        assertEquals(NOW, DoseService.calibrate(future, null, NOW));
    }

    @Test
    void calibrateKeepsPlausiblePastTimestamp() {
        Instant past = NOW.minus(Duration.ofHours(5));
        assertEquals(past, DoseService.calibrate(past, null, NOW));
    }

    @Test
    void calibrateClampsVeryOldTimestamps() {
        Instant ancient = NOW.minus(Duration.ofDays(400));
        assertEquals(NOW.minus(Duration.ofDays(31)), DoseService.calibrate(ancient, null, NOW));
    }

    @Test
    void calibrateDefaultsToServerTime() {
        assertEquals(NOW, DoseService.calibrate(null, null, NOW));
    }

    @Test
    void doseIdIsDeterministicPerScheduleAndInstant() {
        UUID schedule = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Instant at = Instant.parse("2026-11-11T11:00:00Z");
        assertEquals(DoseGenerator.doseId(schedule, at), DoseGenerator.doseId(schedule, at));
        assertNotEquals(DoseGenerator.doseId(schedule, at), DoseGenerator.doseId(schedule, at.plusSeconds(60)));
        assertNotEquals(DoseGenerator.doseId(schedule, at), DoseGenerator.doseId(UUID.randomUUID(), at));
    }
}
