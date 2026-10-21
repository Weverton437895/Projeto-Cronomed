package br.com.cronomed.stock;

import br.com.cronomed.alert.AlertService;
import br.com.cronomed.alert.AlertType;
import br.com.cronomed.medication.Medication;
import br.com.cronomed.medication.Schedule;
import br.com.cronomed.medication.ScheduleRepository;
import br.com.cronomed.patient.PatientRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Controle de estoque: baixa por dose confirmada, reabastecimento e alertas de termino (RF10, RF23, RF24, RF29). */
@Service
public class StockService {

    public enum StockStatus {
        OK, LOW, ZERO
    }

    private final StockMovementRepository movements;
    private final ScheduleRepository schedules;
    private final PatientRepository patients;
    private final AlertService alerts;
    private final Clock clock;

    public StockService(StockMovementRepository movements, ScheduleRepository schedules,
                        PatientRepository patients, AlertService alerts, Clock clock) {
        this.movements = movements;
        this.schedules = schedules;
        this.patients = patients;
        this.alerts = alerts;
        this.clock = clock;
    }

    /** Dias restantes de medicamento no consumo atual; null quando nao ha consumo programado. */
    public Integer daysRemaining(Medication m, List<Schedule> activeSchedules) {
        int weekly = 0;
        for (Schedule s : activeSchedules) {
            if (s.isActive()) {
                weekly += Integer.bitCount(s.getDaysMask()) * m.getDoseQuantity();
            }
        }
        if (weekly == 0) {
            return null;
        }
        return (int) Math.floor((m.getStockCurrent() * 7.0) / weekly);
    }

    public StockStatus status(Medication m, Integer daysRemaining) {
        if (m.getStockCurrent() <= 0) {
            return StockStatus.ZERO;
        }
        if (daysRemaining != null && daysRemaining <= m.getMinDaysAlert()) {
            return StockStatus.LOW;
        }
        return StockStatus.OK;
    }

    @Transactional
    public void recordInitial(Medication m) {
        if (m.getStockCurrent() > 0) {
            addMovement(m, m.getStockCurrent(), StockReason.INITIAL, null);
        }
    }

    /** Baixa definitiva (apos confirmacao do usuario). Nunca deixa o saldo negativo. */
    @Transactional
    public void deduct(Medication m, int quantity, UUID refId) {
        int newBalance = Math.max(0, m.getStockCurrent() - quantity);
        int delta = newBalance - m.getStockCurrent();
        m.setStockCurrent(newBalance);
        m.setUpdatedAt(clock.instant());
        addMovement(m, delta, StockReason.DOSE, refId);
        evaluate(m);
    }

    @Transactional
    public void refill(Medication m, int quantity) {
        m.setStockCurrent(m.getStockCurrent() + quantity);
        m.setStockAcquired(m.getStockAcquired() + quantity);
        m.setUpdatedAt(clock.instant());
        addMovement(m, quantity, StockReason.REFILL, null);
        alerts.resolve(m.getId(), List.of(AlertType.STOCK_LOW, AlertType.STOCK_ZERO));
        evaluate(m);
    }

    /** Gera alerta de estoque baixo (RF23) ou zerado (RF24), uma vez por situacao, ate o proximo reabastecimento. */
    @Transactional
    public void evaluate(Medication m) {
        if (!m.isActive() || m.isDeleted()) {
            return;
        }
        List<Schedule> sch = schedules.findByMedicationIdAndActiveTrue(m.getId());
        Integer days = daysRemaining(m, sch);
        StockStatus st = status(m, days);
        String patientName = patients.findById(m.getPatientId()).map(p -> p.getName()).orElse("Paciente");
        if (st == StockStatus.ZERO) {
            alerts.raiseOnce(m.getPatientId(), AlertType.STOCK_ZERO, m.getId(), "Medicamento acabou",
                    "O estoque de " + m.getName() + " de " + patientName + " chegou a zero.");
        } else if (st == StockStatus.LOW) {
            alerts.raiseOnce(m.getPatientId(), AlertType.STOCK_LOW, m.getId(), "Medicamento acabando",
                    "Restam cerca de " + days + " dia(s) de " + m.getName() + " para " + patientName + ".");
        }
    }

    private void addMovement(Medication m, int delta, StockReason reason, UUID refId) {
        StockMovement mv = new StockMovement();
        mv.setId(UUID.randomUUID());
        mv.setMedicationId(m.getId());
        mv.setDelta(delta);
        mv.setBalanceAfter(m.getStockCurrent());
        mv.setReason(reason);
        mv.setRefId(refId);
        mv.setCreatedAt(clock.instant());
        movements.save(mv);
    }
}
