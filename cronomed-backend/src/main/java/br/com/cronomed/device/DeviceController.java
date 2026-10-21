package br.com.cronomed.device;

import br.com.cronomed.auth.CurrentUser;
import br.com.cronomed.device.DeviceDtos.CompartmentResponse;
import br.com.cronomed.device.DeviceDtos.DeviceResponse;
import br.com.cronomed.device.DeviceDtos.RegisterDeviceRequest;
import br.com.cronomed.device.DeviceDtos.RegisteredDeviceResponse;
import br.com.cronomed.device.DeviceDtos.UpdateCompartmentRequest;
import br.com.cronomed.device.DeviceDtos.UpdateDeviceRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Gerenciamento das caixas IoT pelo titular (app/site). */
@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService service;
    private final PlanService planService;

    public DeviceController(DeviceService service, PlanService planService) {
        this.service = service;
        this.planService = planService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RegisteredDeviceResponse register(@Valid @RequestBody RegisterDeviceRequest req) {
        return service.register(CurrentUser.userId(), req.macAddress(), req.name());
    }

    @GetMapping
    public List<DeviceResponse> list() {
        return service.list(CurrentUser.userId());
    }

    @GetMapping("/{id}")
    public DeviceResponse get(@PathVariable UUID id) {
        return service.get(CurrentUser.userId(), id);
    }

    @PutMapping("/{id}")
    public DeviceResponse rename(@PathVariable UUID id, @Valid @RequestBody UpdateDeviceRequest req) {
        return service.rename(CurrentUser.userId(), id, req.name());
    }

    @PostMapping("/{id}/revoke")
    public DeviceResponse revoke(@PathVariable UUID id) {
        return service.revoke(CurrentUser.userId(), id);
    }

    @PutMapping("/{id}/compartments/{compartmentId}")
    public CompartmentResponse updateCompartment(@PathVariable UUID id, @PathVariable UUID compartmentId,
                                                 @Valid @RequestBody UpdateCompartmentRequest req) {
        return service.updateCompartment(CurrentUser.userId(), id, compartmentId, req);
    }

    /** Pre-visualizacao do plano numerico que a caixa recebe (util para depuracao do firmware). */
    @GetMapping("/{id}/plan")
    public PlanService.DevicePlan plan(@PathVariable UUID id) {
        service.requireOwned(CurrentUser.userId(), id);
        return planService.buildForDevice(id);
    }
}
