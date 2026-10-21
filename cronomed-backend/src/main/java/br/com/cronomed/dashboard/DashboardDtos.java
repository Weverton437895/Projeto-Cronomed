package br.com.cronomed.dashboard;

import br.com.cronomed.alert.AlertType;
import br.com.cronomed.stock.StockService.StockStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record Summary(int taken, int missed, int declined, int openedUnconfirmed, int pending,
                          Double adherenceRate) {
    }

    public record MedicationSummary(UUID medicationId, String name, String dosage, int taken, int missed,
                                    int declined, int openedUnconfirmed, int pending, int stockCurrent,
                                    Integer daysRemaining, StockStatus stockStatus) {
    }

    public record AlertView(UUID id, AlertType type, String message, Instant createdAt, Instant resolvedAt) {
    }

    public record DashboardResponse(UUID patientId, String patientName, Instant from, Instant to, Summary summary,
                                    List<MedicationSummary> medications, List<AlertView> recentAlerts) {
    }
}
