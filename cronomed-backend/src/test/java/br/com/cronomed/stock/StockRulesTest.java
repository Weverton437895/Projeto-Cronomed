package br.com.cronomed.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import br.com.cronomed.medication.Medication;
import br.com.cronomed.medication.Schedule;
import br.com.cronomed.stock.StockService.StockStatus;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;

class StockRulesTest {

    private final StockService service = new StockService(null, null, null, null, Clock.systemUTC());

    private Medication med(int stock, int doseQty, int minDays) {
        Medication m = new Medication();
        m.setStockCurrent(stock);
        m.setDoseQuantity(doseQty);
        m.setMinDaysAlert(minDays);
        return m;
    }

    private Schedule everyDay() {
        Schedule s = new Schedule();
        s.setDaysMask(127);
        s.setActive(true);
        return s;
    }

    @Test
    void daysRemainingConsidersDosesPerDay() {
        Medication m = med(30, 1, 3);
        // 2 horarios por dia x 1 comprimido = 2/dia => 15 dias
        assertEquals(15, service.daysRemaining(m, List.of(everyDay(), everyDay())));
    }

    @Test
    void noScheduleMeansNoConsumption() {
        assertNull(service.daysRemaining(med(30, 1, 3), List.of()));
    }

    @Test
    void statusZeroLowOk() {
        assertEquals(StockStatus.ZERO, service.status(med(0, 1, 3), 0));
        assertEquals(StockStatus.LOW, service.status(med(5, 1, 3), 3));
        assertEquals(StockStatus.OK, service.status(med(30, 1, 3), 15));
    }
}
