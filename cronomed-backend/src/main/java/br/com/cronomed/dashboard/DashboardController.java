package br.com.cronomed.dashboard;

import br.com.cronomed.auth.CurrentUser;
import br.com.cronomed.common.PageResponse;
import br.com.cronomed.dashboard.DashboardDtos.AlertView;
import br.com.cronomed.dashboard.DashboardDtos.DashboardResponse;
import br.com.cronomed.dose.DoseDtos.DoseView;
import br.com.cronomed.dose.DoseDtos.EventView;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/patients/{patientId}")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@PathVariable UUID patientId,
                                       @RequestParam(required = false) Instant from,
                                       @RequestParam(required = false) Instant to) {
        return service.dashboard(CurrentUser.userId(), patientId, from, to);
    }

    @GetMapping("/doses")
    public PageResponse<DoseView> doses(@PathVariable UUID patientId,
                                        @RequestParam(required = false) Instant from,
                                        @RequestParam(required = false) Instant to,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return service.doseHistory(CurrentUser.userId(), patientId, from, to, page, size);
    }

    @GetMapping("/events")
    public PageResponse<EventView> events(@PathVariable UUID patientId,
                                          @RequestParam(required = false) Instant from,
                                          @RequestParam(required = false) Instant to,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return service.eventHistory(CurrentUser.userId(), patientId, from, to, page, size);
    }

    @GetMapping("/alerts")
    public PageResponse<AlertView> alerts(@PathVariable UUID patientId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return service.alertHistory(CurrentUser.userId(), patientId, page, size);
    }

    @GetMapping(value = "/report.csv", produces = "text/csv")
    public ResponseEntity<byte[]> report(@PathVariable UUID patientId,
                                         @RequestParam(required = false) Instant from,
                                         @RequestParam(required = false) Instant to) {
        String csv = service.reportCsv(CurrentUser.userId(), patientId, from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"relatorio-adesao.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
