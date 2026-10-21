package br.com.cronomed.device;

import br.com.cronomed.auth.CurrentUser;
import br.com.cronomed.auth.JwtService;
import br.com.cronomed.common.ApiException;
import br.com.cronomed.config.AppProperties;
import br.com.cronomed.device.DeviceDtos.DeviceEventsRequest;
import br.com.cronomed.device.DeviceDtos.DeviceTokenRequest;
import br.com.cronomed.device.DeviceDtos.DeviceTokenResponse;
import br.com.cronomed.device.DeviceDtos.HeartbeatRequest;
import br.com.cronomed.device.DeviceDtos.IngestResult;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Canal HTTPS da caixa (alternativa/contingencia ao MQTT): autenticacao individual (MAC + segredo exclusivo
 * -> JWT de dispositivo), download do plano e envio de eventos/heartbeat.
 */
@RestController
public class DeviceGatewayController {

    private final DeviceRepository devices;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final PlanService planService;
    private final DeviceIngestService ingest;
    private final AppProperties props;

    public DeviceGatewayController(DeviceRepository devices, PasswordEncoder encoder, JwtService jwt,
                                   PlanService planService, DeviceIngestService ingest, AppProperties props) {
        this.devices = devices;
        this.encoder = encoder;
        this.jwt = jwt;
        this.planService = planService;
        this.ingest = ingest;
        this.props = props;
    }

    @PostMapping("/api/v1/device-auth/token")
    public DeviceTokenResponse token(@Valid @RequestBody DeviceTokenRequest req) {
        IotDevice d = devices.findByMacAddress(DeviceService.normalizeMac(req.macAddress()))
                .orElseThrow(() -> ApiException.unauthorized("Credenciais de dispositivo invalidas"));
        if (!encoder.matches(req.secret(), d.getSecretHash())) {
            throw ApiException.unauthorized("Credenciais de dispositivo invalidas");
        }
        if (d.getStatus() != DeviceStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "DEVICE_REVOKED", "Dispositivo revogado");
        }
        return new DeviceTokenResponse(jwt.createDeviceToken(d), "Bearer",
                props.getJwt().getDeviceTtl().toSeconds());
    }

    @GetMapping("/api/v1/device-gateway/plan")
    public PlanService.DevicePlan plan() {
        return planService.buildForDevice(CurrentUser.deviceId());
    }

    @PostMapping("/api/v1/device-gateway/events")
    public IngestResult events(@Valid @RequestBody DeviceEventsRequest req) {
        UUID id = CurrentUser.deviceId();
        IotDevice device = devices.findById(id).orElseThrow(() -> ApiException.unauthorized("Dispositivo inexistente"));
        return ingest.ingestEvents(device, req.events());
    }

    @PostMapping("/api/v1/device-gateway/heartbeat")
    public ResponseEntity<Void> heartbeat(@Valid @RequestBody(required = false) HeartbeatRequest req) {
        ingest.heartbeat(CurrentUser.deviceId(), req == null ? null : req.battery());
        return ResponseEntity.noContent().build();
    }
}
