package br.com.cronomed.device;

import br.com.cronomed.audit.AuditService;
import br.com.cronomed.common.AfterCommit;
import br.com.cronomed.common.ApiException;
import br.com.cronomed.config.AppProperties;
import br.com.cronomed.device.DeviceDtos.CompartmentResponse;
import br.com.cronomed.device.DeviceDtos.DeviceResponse;
import br.com.cronomed.device.DeviceDtos.RegisteredDeviceResponse;
import br.com.cronomed.device.DeviceDtos.UpdateCompartmentRequest;
import br.com.cronomed.device.mqtt.DeviceMessagePublisher;
import br.com.cronomed.medication.MedicationRepository;
import br.com.cronomed.patient.AccessService;
import br.com.cronomed.patient.Patient;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro, configuracao e revogacao de caixas IoT (secao 3.3-d). So o titular configura a caixa. */
@Service
public class DeviceService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern MAC_COLONS = Pattern.compile("^([0-9A-F]{2}:){5}[0-9A-F]{2}$");
    private static final Pattern MAC_PLAIN = Pattern.compile("^[0-9A-F]{12}$");

    /** Paleta padrao (Okabe-Ito, segura para daltonismo) + padrao grafico + identificador tatil por compartimento. */
    private static final String[][] DEFAULT_COLORS = {
            {"#0072B2", "azul"}, {"#E69F00", "laranja"}, {"#009E73", "verde"},
            {"#D55E00", "vermelho"}, {"#CC79A7", "roxo"}, {"#F0E442", "amarelo"}};
    private static final LabelPattern[] DEFAULT_PATTERNS = LabelPattern.values();

    private final DeviceRepository devices;
    private final CompartmentRepository compartments;
    private final MedicationRepository medications;
    private final AccessService access;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final AppProperties props;
    private final Clock clock;
    private final ApplicationEventPublisher publisher;
    private final DeviceMessagePublisher devicePublisher;

    public DeviceService(DeviceRepository devices, CompartmentRepository compartments,
                         MedicationRepository medications, AccessService access, PasswordEncoder encoder,
                         AuditService audit, AppProperties props, Clock clock, ApplicationEventPublisher publisher,
                         DeviceMessagePublisher devicePublisher) {
        this.devices = devices;
        this.compartments = compartments;
        this.medications = medications;
        this.access = access;
        this.encoder = encoder;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
        this.publisher = publisher;
        this.devicePublisher = devicePublisher;
    }

    public static String normalizeMac(String raw) {
        String mac = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replace('-', ':');
        if (MAC_PLAIN.matcher(mac).matches()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12; i += 2) {
                if (i > 0) {
                    sb.append(':');
                }
                sb.append(mac, i, i + 2);
            }
            mac = sb.toString();
        }
        if (!MAC_COLONS.matcher(mac).matches()) {
            throw ApiException.badRequest("INVALID_MAC", "MAC address invalido (use AA:BB:CC:DD:EE:FF)");
        }
        return mac;
    }

    // ------------------------------------------------------------------ cadastro

    @Transactional
    public RegisteredDeviceResponse register(UUID ownerId, String macRaw, String name) {
        String mac = normalizeMac(macRaw);
        if (devices.countByOwnerId(ownerId) >= props.getDevice().getMaxPerOwner()) {
            throw ApiException.conflict("DEVICE_LIMIT", "Limite de " + props.getDevice().getMaxPerOwner()
                    + " caixas por titular atingido");
        }
        if (devices.findByMacAddress(mac).isPresent()) {
            throw ApiException.conflict("DEVICE_EXISTS", "Esta caixa ja esta cadastrada");
        }
        Instant now = clock.instant();
        byte[] secretBytes = new byte[32];
        RANDOM.nextBytes(secretBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);

        IotDevice d = new IotDevice();
        d.setId(UUID.randomUUID());
        d.setOwnerId(ownerId);
        d.setMacAddress(mac);
        d.setName(name.trim());
        d.setSecretHash(encoder.encode(secret));
        d.setStatus(DeviceStatus.ACTIVE);
        d.setCreatedAt(now);
        devices.save(d);

        for (int i = 1; i <= 6; i++) {
            Compartment c = new Compartment();
            c.setId(UUID.randomUUID());
            c.setDeviceId(d.getId());
            c.setIndexNumber(i);
            c.setColorHex(DEFAULT_COLORS[i - 1][0]);
            c.setColorName(DEFAULT_COLORS[i - 1][1]);
            c.setPattern(DEFAULT_PATTERNS[i - 1]);
            c.setTactileId("RELEVO-" + DEFAULT_PATTERNS[i - 1].name() + "-" + i);
            c.setUpdatedAt(now);
            compartments.save(c);
        }
        audit.record(ownerId, "DEVICE_REGISTERED", "DEVICE", d.getId(), mac);
        return new RegisteredDeviceResponse(toResponse(d), secret);
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> list(UUID ownerId) {
        return devices.findByOwnerIdOrderByCreatedAt(ownerId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public DeviceResponse get(UUID ownerId, UUID deviceId) {
        return toResponse(requireOwned(ownerId, deviceId));
    }

    @Transactional
    public DeviceResponse rename(UUID ownerId, UUID deviceId, String name) {
        IotDevice d = requireOwned(ownerId, deviceId);
        d.setName(name.trim());
        devices.save(d);
        return toResponse(d);
    }

    /**
     * Revoga a caixa (perdida/roubada/desativada): o servidor passa a rejeitar tokens, eventos, telemetria e
     * envio de cronogramas desse MAC. O acesso do broker MQTT tambem deve ser removido (ver docs/ARQUITETURA.md).
     */
    @Transactional
    public DeviceResponse revoke(UUID ownerId, UUID deviceId) {
        IotDevice d = requireOwned(ownerId, deviceId);
        if (d.getStatus() == DeviceStatus.REVOKED) {
            return toResponse(d);
        }
        d.setStatus(DeviceStatus.REVOKED);
        d.setRevokedAt(clock.instant());
        devices.save(d);
        audit.record(ownerId, "DEVICE_REVOKED", "DEVICE", d.getId(), d.getMacAddress());
        IotDevice snapshot = d;
        AfterCommit.run(() -> devicePublisher.clearPlan(snapshot));
        return toResponse(d);
    }

    // ------------------------------------------------------------------ compartimentos (RF13, RF16, RF17)

    @Transactional
    public CompartmentResponse updateCompartment(UUID ownerId, UUID deviceId, UUID compartmentId,
                                                 UpdateCompartmentRequest req) {
        IotDevice device = requireOwned(ownerId, deviceId);
        Compartment c = compartments.findById(compartmentId)
                .filter(x -> x.getDeviceId().equals(deviceId))
                .orElseThrow(() -> ApiException.notFound("Compartimento"));

        if (req.colorHex() != null) {
            String hex = req.colorHex().toUpperCase(Locale.ROOT);
            boolean taken = compartments.findByDeviceIdOrderByIndexNumber(deviceId).stream()
                    .anyMatch(o -> !o.getId().equals(c.getId()) && o.getColorHex().equalsIgnoreCase(hex));
            if (taken) {
                throw ApiException.conflict("COLOR_IN_USE", "Ja existe um compartimento com esta cor nesta caixa");
            }
            c.setColorHex(hex);
        }
        if (req.colorName() != null && !req.colorName().isBlank()) {
            c.setColorName(req.colorName().trim());
        }
        if (req.pattern() != null) {
            c.setPattern(req.pattern());
            c.setTactileId("RELEVO-" + req.pattern().name() + "-" + c.getIndexNumber());
        }
        Set<UUID> affectedPatients = new HashSet<>();
        if (c.getPatientId() != null) {
            affectedPatients.add(c.getPatientId());
        }
        if (Boolean.TRUE.equals(req.clearPatient())) {
            ensureNoActiveMedication(c);
            c.setPatientId(null);
        } else if (req.patientId() != null && !req.patientId().equals(c.getPatientId())) {
            ensureNoActiveMedication(c);
            Patient p = access.requireOwner(ownerId, req.patientId());
            assignPatient(c, p, device);
            affectedPatients.add(p.getId());
        }
        c.setUpdatedAt(clock.instant());
        compartments.save(c);
        for (UUID pid : affectedPatients) {
            publisher.publishEvent(new PlanChangedEvent(pid, Set.of(deviceId)));
        }
        return CompartmentResponse.from(c);
    }

    /**
     * Associa um paciente a um compartimento respeitando o limite do hardware (RNF10):
     * ate 2 pacientes por caixa e ate 3 medicamentos (compartimentos) por paciente.
     */
    @Transactional
    public void assignPatient(Compartment c, Patient patient, IotDevice device) {
        if (device.getStatus() != DeviceStatus.ACTIVE) {
            throw ApiException.conflict("DEVICE_REVOKED", "Caixa revogada");
        }
        if (!patient.getOwnerId().equals(device.getOwnerId())) {
            throw ApiException.forbidden("A caixa e o paciente devem pertencer ao mesmo titular");
        }
        if (patient.getId().equals(c.getPatientId())) {
            return;
        }
        List<Compartment> all = compartments.findByDeviceIdOrderByIndexNumber(device.getId());
        Set<UUID> patientsOnDevice = new HashSet<>();
        int forThisPatient = 0;
        for (Compartment o : all) {
            if (o.getId().equals(c.getId()) || o.getPatientId() == null) {
                continue;
            }
            patientsOnDevice.add(o.getPatientId());
            if (o.getPatientId().equals(patient.getId())) {
                forThisPatient++;
            }
        }
        if (!patientsOnDevice.contains(patient.getId()) && patientsOnDevice.size() >= 2) {
            throw ApiException.conflict("DEVICE_PATIENT_LIMIT", "Cada caixa suporta no maximo 2 pacientes");
        }
        if (forThisPatient >= 3) {
            throw ApiException.conflict("PATIENT_COMPARTMENT_LIMIT",
                    "Cada paciente pode ter no maximo 3 medicamentos por caixa");
        }
        c.setPatientId(patient.getId());
    }

    private void ensureNoActiveMedication(Compartment c) {
        boolean inUse = medications.findByCompartmentId(c.getId()).filter(m -> !m.isDeleted()).isPresent();
        if (inUse) {
            throw ApiException.conflict("COMPARTMENT_IN_USE",
                    "Remova ou mova o medicamento deste compartimento antes de altera-lo");
        }
    }

    // ------------------------------------------------------------------ utilitarios

    public IotDevice requireOwned(UUID ownerId, UUID deviceId) {
        IotDevice d = devices.findById(deviceId).orElseThrow(() -> ApiException.notFound("Caixa"));
        if (!d.getOwnerId().equals(ownerId)) {
            throw ApiException.notFound("Caixa");
        }
        return d;
    }

    public boolean isOnline(IotDevice d) {
        return d.getStatus() == DeviceStatus.ACTIVE && d.getLastSeenAt() != null
                && d.getLastSeenAt().isAfter(clock.instant().minus(Duration.ofMinutes(props.getDevice().getOfflineAfterMinutes())));
    }

    private DeviceResponse toResponse(IotDevice d) {
        List<CompartmentResponse> comps = compartments.findByDeviceIdOrderByIndexNumber(d.getId()).stream()
                .map(CompartmentResponse::from).toList();
        return new DeviceResponse(d.getId(), d.getMacAddress(), d.getName(), d.getStatus(), d.getLastSeenAt(),
                d.getBatteryPercent(), isOnline(d), d.getCreatedAt(), comps);
    }
}
