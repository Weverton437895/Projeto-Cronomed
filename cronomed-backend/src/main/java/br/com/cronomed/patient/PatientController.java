package br.com.cronomed.patient;

import br.com.cronomed.auth.CurrentUser;
import br.com.cronomed.patient.PatientDtos.GrantRequest;
import br.com.cronomed.patient.PatientDtos.GrantResponse;
import br.com.cronomed.patient.PatientDtos.GrantUpdateRequest;
import br.com.cronomed.patient.PatientDtos.PatientRequest;
import br.com.cronomed.patient.PatientDtos.PatientResponse;
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
@RequestMapping("/api/v1/patients")
public class PatientController {

    private final PatientService service;

    public PatientController(PatientService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PatientResponse create(@Valid @RequestBody PatientRequest req) {
        return service.create(CurrentUser.userId(), req);
    }

    @GetMapping
    public List<PatientResponse> list() {
        return service.list(CurrentUser.userId());
    }

    @GetMapping("/{id}")
    public PatientResponse get(@PathVariable UUID id) {
        return service.get(CurrentUser.userId(), id);
    }

    @PutMapping("/{id}")
    public PatientResponse update(@PathVariable UUID id, @Valid @RequestBody PatientRequest req) {
        return service.update(CurrentUser.userId(), id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(CurrentUser.userId(), id);
        return ResponseEntity.noContent().build();
    }

    // ---- compartilhamento (RF39, RF40, RF41) ----

    @PostMapping("/{id}/grants")
    @ResponseStatus(HttpStatus.CREATED)
    public GrantResponse grant(@PathVariable UUID id, @Valid @RequestBody GrantRequest req) {
        return service.grant(CurrentUser.userId(), id, req);
    }

    @GetMapping("/{id}/grants")
    public List<GrantResponse> grants(@PathVariable UUID id) {
        return service.listGrants(CurrentUser.userId(), id);
    }

    @PutMapping("/{id}/grants/{grantId}")
    public GrantResponse changeLevel(@PathVariable UUID id, @PathVariable UUID grantId,
                                     @Valid @RequestBody GrantUpdateRequest req) {
        return service.changeLevel(CurrentUser.userId(), id, grantId, req.level());
    }

    @DeleteMapping("/{id}/grants/{grantId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id, @PathVariable UUID grantId) {
        service.revoke(CurrentUser.userId(), id, grantId);
        return ResponseEntity.noContent().build();
    }
}
