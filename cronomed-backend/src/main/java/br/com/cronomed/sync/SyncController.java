package br.com.cronomed.sync;

import br.com.cronomed.auth.CurrentUser;
import br.com.cronomed.sync.SyncDtos.PullResponse;
import br.com.cronomed.sync.SyncDtos.PushRequest;
import br.com.cronomed.sync.SyncDtos.PushResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final SyncService service;

    public SyncController(SyncService service) {
        this.service = service;
    }

    /** Descarrega a fila offline do app (RF31). O app so remove da fila itens APPLIED/DUPLICATE/STALE/REJECTED. */
    @PostMapping("/push")
    public PushResponse push(@Valid @RequestBody PushRequest request) {
        return service.push(CurrentUser.userId(), request);
    }

    /** Baixa alteracoes feitas no site ou por outros usuarios (RF32). */
    @GetMapping("/pull")
    public PullResponse pull(@RequestParam(required = false) Instant since) {
        return service.pull(CurrentUser.userId(), since);
    }
}
