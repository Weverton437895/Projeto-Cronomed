package br.com.cronomed.medication;

import br.com.cronomed.auth.CurrentUser;
import br.com.cronomed.common.RequestInfo;
import br.com.cronomed.medication.MedicationDtos.MedicationRequest;
import br.com.cronomed.medication.MedicationDtos.MedicationResponse;
import br.com.cronomed.medication.MedicationDtos.RefillRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MedicationController {

    private final MedicationService service;

    public MedicationController(MedicationService service) {
        this.service = service;
    }

    @GetMapping("/patients/{patientId}/medications")
    public List<MedicationResponse> list(@PathVariable UUID patientId) {
        return service.list(CurrentUser.userId(), patientId);
    }

    @PostMapping("/patients/{patientId}/medications")
    @ResponseStatus(HttpStatus.CREATED)
    public MedicationResponse create(@PathVariable UUID patientId, @Valid @RequestBody MedicationRequest req,
                                     HttpServletRequest http) {
        return service.create(CurrentUser.userId(), patientId, req, RequestInfo.clientSource(http));
    }

    @GetMapping("/medications/{id}")
    public MedicationResponse get(@PathVariable UUID id) {
        return service.get(CurrentUser.userId(), id);
    }

    @PutMapping("/medications/{id}")
    public MedicationResponse update(@PathVariable UUID id, @Valid @RequestBody MedicationRequest req,
                                     HttpServletRequest http) {
        return service.update(CurrentUser.userId(), id, req, RequestInfo.clientSource(http));
    }

    @DeleteMapping("/medications/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest http) {
        service.delete(CurrentUser.userId(), id, RequestInfo.clientSource(http));
        return ResponseEntity.noContent().build();
    }

    /** Reabastecimento manual (RF29). */
    @PostMapping("/medications/{id}/refill")
    public MedicationResponse refill(@PathVariable UUID id, @Valid @RequestBody RefillRequest req) {
        return service.refill(CurrentUser.userId(), id, req.quantity());
    }
}
