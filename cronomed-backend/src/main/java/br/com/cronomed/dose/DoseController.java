package br.com.cronomed.dose;

import br.com.cronomed.auth.CurrentUser;
import br.com.cronomed.common.RequestInfo;
import br.com.cronomed.dose.DoseDtos.DoseEventRequest;
import br.com.cronomed.dose.DoseDtos.DoseEventResponse;
import br.com.cronomed.dose.DoseDtos.DoseView;
import br.com.cronomed.dose.DoseService.RecordResult;
import br.com.cronomed.dose.DoseService.UserEventInput;
import br.com.cronomed.patient.AccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Confirmacao de dose (RF26/RF27) e proximas doses. */
@RestController
@RequestMapping("/api/v1")
public class DoseController {

    private final DoseService doseService;
    private final ScheduledDoseRepository doses;
    private final DoseViewMapper mapper;
    private final AccessService access;
    private final Clock clock;

    public DoseController(DoseService doseService, ScheduledDoseRepository doses, DoseViewMapper mapper,
                          AccessService access, Clock clock) {
        this.doseService = doseService;
        this.doses = doses;
        this.mapper = mapper;
        this.access = access;
        this.clock = clock;
    }

    /**
     * Registra a resposta do usuario a uma dose: CONFIRMED_YES (baixa o estoque), CONFIRMED_NO ou POSTPONED
     * (com justificativa). Idempotente por eventId.
     */
    @PostMapping("/doses/{doseId}/events")
    public DoseEventResponse recordEvent(@PathVariable UUID doseId, @Valid @RequestBody DoseEventRequest req,
                                         HttpServletRequest http) {
        UUID eventId = req.eventId() != null ? req.eventId() : UUID.randomUUID();
        RecordResult result = doseService.recordUserEvent(CurrentUser.userId(), RequestInfo.clientSource(http),
                new UserEventInput(eventId, doseId, req.type(), req.occurredAt(), req.elapsedMs(), null,
                        req.justification()));
        return new DoseEventResponse(eventId, result);
    }

    /** Proximas doses pendentes (tela "Inicio"): das ultimas tolerancias ate as proximas N horas. */
    @GetMapping("/patients/{patientId}/doses/upcoming")
    public List<DoseView> upcoming(@PathVariable UUID patientId,
                                   @RequestParam(defaultValue = "24") int hours) {
        access.requireView(CurrentUser.userId(), patientId);
        int h = Math.max(1, Math.min(hours, 24 * 7));
        Instant now = clock.instant();
        return mapper.doseViews(doses.findUpcoming(patientId, now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(h))));
    }
}
