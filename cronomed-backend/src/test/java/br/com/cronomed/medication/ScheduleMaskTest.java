package br.com.cronomed.medication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.cronomed.common.ApiException;
import java.time.DayOfWeek;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScheduleMaskTest {

    @Test
    void emptyMeansEveryDay() {
        assertEquals(127, MedicationService.toMask(null));
        assertEquals(127, MedicationService.toMask(List.of()));
    }

    @Test
    void roundTrip() {
        int mask = MedicationService.toMask(List.of(1, 3, 7));
        assertEquals(List.of(1, 3, 7), MedicationService.fromMask(mask));
    }

    @Test
    void invalidDayIsRejected() {
        assertThrows(ApiException.class, () -> MedicationService.toMask(List.of(8)));
        assertThrows(ApiException.class, () -> MedicationService.toMask(List.of(0)));
    }

    @Test
    void scheduleRunsOnConfiguredDays() {
        Schedule s = new Schedule();
        s.setDaysMask(MedicationService.toMask(List.of(1, 5)));
        assertTrue(s.runsOn(DayOfWeek.MONDAY));
        assertTrue(s.runsOn(DayOfWeek.FRIDAY));
        assertEquals(false, s.runsOn(DayOfWeek.TUESDAY));
    }
}
