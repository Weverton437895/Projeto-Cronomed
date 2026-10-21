package br.com.cronomed.dose;

import br.com.cronomed.config.AppProperties;
import br.com.cronomed.medication.MedicationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rotinas agendadas. Com varias instancias da API, o ShedLock garante que apenas uma execute cada rotina
 * por intervalo (sem notificacoes duplicadas).
 */
@Component
@ConditionalOnProperty(prefix = "cronomed.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DoseJobs {

    private static final Logger log = LoggerFactory.getLogger(DoseJobs.class);

    private final ScheduledDoseRepository doses;
    private final MedicationRepository medications;
    private final DoseMaintenanceService maintenance;
    private final DoseGenerator generator;
    private final AppProperties props;
    private final Clock clock;

    public DoseJobs(ScheduledDoseRepository doses, MedicationRepository medications,
                    DoseMaintenanceService maintenance, DoseGenerator generator, AppProperties props, Clock clock) {
        this.doses = doses;
        this.medications = medications;
        this.maintenance = maintenance;
        this.generator = generator;
        this.props = props;
        this.clock = clock;
    }

    /** Push inicial no horario da dose: o job roda a cada 10 s, dentro do limite de 20 s do RNF04. */
    @Scheduled(fixedDelay = 10_000, initialDelay = 15_000)
    @SchedulerLock(name = "doseDueNotifier", lockAtMostFor = "PT30S", lockAtLeastFor = "PT5S")
    public void notifyDueDoses() {
        Instant now = clock.instant();
        Instant since = now.minus(Duration.ofMinutes(props.getDose().getToleranceMinutes()));
        List<UUID> ids = doses.findDueForNotification(now, since).stream().map(ScheduledDose::getId).toList();
        for (UUID id : ids) {
            try {
                maintenance.notifyDue(id);
            } catch (RuntimeException ex) {
                log.error("Falha ao notificar dose {}", id, ex);
            }
        }
    }

    /** Fecha janelas de tolerancia esgotadas (dose perdida / aberta sem confirmacao). */
    @Scheduled(fixedDelay = 30_000, initialDelay = 20_000)
    @SchedulerLock(name = "doseReconciler", lockAtMostFor = "PT2M", lockAtLeastFor = "PT10S")
    public void closeExpiredDoses() {
        int minutes = props.getDose().getToleranceMinutes() + props.getDose().getReconcileGraceMinutes();
        Instant cutoff = clock.instant().minus(Duration.ofMinutes(minutes));
        List<UUID> ids = doses.findExpiredPending(cutoff).stream().map(ScheduledDose::getId).toList();
        for (UUID id : ids) {
            try {
                maintenance.closeExpired(id);
            } catch (RuntimeException ex) {
                log.error("Falha ao encerrar dose expirada {}", id, ex);
            }
        }
    }

    /** Mantem o horizonte de doses (ex.: 7 dias) sempre preenchido para todos os pacientes ativos. */
    @Scheduled(cron = "0 5 * * * *")
    @SchedulerLock(name = "doseHorizonGenerator", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void generateHorizon() {
        for (UUID patientId : medications.findPatientIdsWithActiveMedication()) {
            try {
                generator.generateForPatient(patientId);
            } catch (RuntimeException ex) {
                log.error("Falha ao gerar doses do paciente {}", patientId, ex);
            }
        }
    }
}
