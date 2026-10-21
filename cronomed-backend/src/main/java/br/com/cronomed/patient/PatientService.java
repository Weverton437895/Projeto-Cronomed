package br.com.cronomed.patient;

import br.com.cronomed.audit.AuditService;
import br.com.cronomed.common.ApiException;
import br.com.cronomed.config.AppProperties;
import br.com.cronomed.device.CompartmentRepository;
import br.com.cronomed.device.PlanChangedEvent;
import br.com.cronomed.dose.DoseGenerator;
import br.com.cronomed.patient.PatientDtos.GrantRequest;
import br.com.cronomed.patient.PatientDtos.GrantResponse;
import br.com.cronomed.patient.PatientDtos.PatientRequest;
import br.com.cronomed.patient.PatientDtos.PatientResponse;
import br.com.cronomed.user.User;
import br.com.cronomed.user.UserRepository;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pacientes do titular e compartilhamento com terceiros (RF39-RF41), com auditoria imutavel (LGPD Art. 6, X). */
@Service
public class PatientService {

    private final PatientRepository patients;
    private final AccessGrantRepository grants;
    private final UserRepository users;
    private final CompartmentRepository compartments;
    private final AccessService access;
    private final AuditService audit;
    private final AppProperties props;
    private final Clock clock;
    private final ApplicationEventPublisher publisher;
    private final DoseGenerator generator;

    public PatientService(PatientRepository patients, AccessGrantRepository grants, UserRepository users,
                          CompartmentRepository compartments, AccessService access, AuditService audit,
                          AppProperties props, Clock clock, ApplicationEventPublisher publisher,
                          DoseGenerator generator) {
        this.patients = patients;
        this.grants = grants;
        this.users = users;
        this.compartments = compartments;
        this.access = access;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
        this.publisher = publisher;
        this.generator = generator;
    }

    // ------------------------------------------------------------------ pacientes

    @Transactional
    public PatientResponse create(UUID userId, PatientRequest req) {
        if (patients.countByOwnerId(userId) >= props.getDevice().getMaxPatientsPerOwner()) {
            throw ApiException.conflict("PATIENT_LIMIT", "Limite de " + props.getDevice().getMaxPatientsPerOwner()
                    + " pacientes por titular atingido");
        }
        Instant now = clock.instant();
        Patient p = new Patient();
        p.setId(UUID.randomUUID());
        p.setOwnerId(userId);
        p.setName(req.name().trim());
        p.setTimezone(validZone(req.timezone()));
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        patients.save(p);
        audit.record(userId, "PATIENT_CREATED", "PATIENT", p.getId(), null);
        return PatientResponse.from(p, AccessLevel.OWNER);
    }

    @Transactional(readOnly = true)
    public List<PatientResponse> list(UUID userId) {
        return patients.findAccessible(userId).stream()
                .map(p -> PatientResponse.from(p, access.levelOf(userId, p))).toList();
    }

    @Transactional(readOnly = true)
    public PatientResponse get(UUID userId, UUID patientId) {
        Patient p = access.requireView(userId, patientId);
        return PatientResponse.from(p, access.levelOf(userId, p));
    }

    @Transactional
    public PatientResponse update(UUID userId, UUID patientId, PatientRequest req) {
        Patient p = access.requireOwner(userId, patientId);
        p.setName(req.name().trim());
        if (req.timezone() != null && !req.timezone().isBlank()) {
            boolean changed = !p.getTimezone().equals(req.timezone());
            p.setTimezone(validZone(req.timezone()));
            p.setUpdatedAt(clock.instant());
            patients.save(p);
            if (changed) {
                // O fuso muda o instante UTC dos horarios: as doses futuras sao regeradas e o plano redistribuido.
                generator.regenerateForPatient(p.getId());
                publisher.publishEvent(PlanChangedEvent.of(p.getId()));
            }
        }
        p.setUpdatedAt(clock.instant());
        patients.save(p);
        return PatientResponse.from(p, AccessLevel.OWNER);
    }

    /** Exclusao definitiva do paciente e de todos os seus dados (cascata no banco). */
    @Transactional
    public void delete(UUID userId, UUID patientId) {
        Patient p = access.requireOwner(userId, patientId);
        Set<UUID> deviceIds = new HashSet<>(compartments.findDeviceIdsByPatient(patientId));
        audit.record(userId, "PATIENT_DELETED", "PATIENT", patientId, p.getName());
        patients.delete(p);
        publisher.publishEvent(new PlanChangedEvent(patientId, deviceIds));
    }

    // ------------------------------------------------------------------ compartilhamento

    @Transactional
    public GrantResponse grant(UUID ownerId, UUID patientId, GrantRequest req) {
        Patient p = access.requireOwner(ownerId, patientId);
        User grantee = users.findByEmail(br.com.cronomed.auth.AuthService.normalizeEmail(req.granteeEmail()))
                .orElseThrow(() -> ApiException.badRequest("GRANTEE_NOT_FOUND",
                        "Nao existe conta com este e-mail. Peca para a pessoa criar uma conta no CronoMed."));
        if (grantee.getId().equals(p.getOwnerId())) {
            throw ApiException.badRequest("INVALID_GRANTEE", "O titular ja tem acesso total");
        }
        if (grants.findByPatientIdAndGranteeUserIdAndRevokedAtIsNull(patientId, grantee.getId()).isPresent()) {
            throw ApiException.conflict("GRANT_EXISTS", "Esta pessoa ja tem acesso. Altere o nivel de permissao.");
        }
        Instant now = clock.instant();
        AccessGrant g = new AccessGrant();
        g.setId(UUID.randomUUID());
        g.setPatientId(patientId);
        g.setGranteeUserId(grantee.getId());
        g.setGrantedBy(ownerId);
        g.setAccessLevel(req.level());
        g.setRelationship(req.relationship());
        g.setConsentAt(now);
        g.setCreatedAt(now);
        grants.save(g);
        audit.record(ownerId, "GRANT_CREATED", "PATIENT", patientId,
                "grantee=" + grantee.getId() + " level=" + req.level() + " rel=" + req.relationship());
        return toResponse(g, grantee);
    }

    @Transactional(readOnly = true)
    public List<GrantResponse> listGrants(UUID ownerId, UUID patientId) {
        access.requireOwner(ownerId, patientId);
        return grants.findByPatientIdAndRevokedAtIsNull(patientId).stream()
                .map(g -> toResponse(g, users.findById(g.getGranteeUserId()).orElse(null))).toList();
    }

    @Transactional
    public GrantResponse changeLevel(UUID ownerId, UUID patientId, UUID grantId, GrantLevel level) {
        access.requireOwner(ownerId, patientId);
        AccessGrant g = grants.findByIdAndPatientId(grantId, patientId)
                .filter(x -> x.getRevokedAt() == null).orElseThrow(() -> ApiException.notFound("Permissao"));
        GrantLevel before = g.getAccessLevel();
        g.setAccessLevel(level);
        grants.save(g);
        audit.record(ownerId, "GRANT_LEVEL_CHANGED", "PATIENT", patientId,
                "grantee=" + g.getGranteeUserId() + " " + before + "->" + level);
        return toResponse(g, users.findById(g.getGranteeUserId()).orElse(null));
    }

    /** Revogacao a qualquer momento (RF40): como cada requisicao consulta as permissoes vigentes, o efeito e imediato. */
    @Transactional
    public void revoke(UUID ownerId, UUID patientId, UUID grantId) {
        access.requireOwner(ownerId, patientId);
        AccessGrant g = grants.findByIdAndPatientId(grantId, patientId)
                .filter(x -> x.getRevokedAt() == null).orElseThrow(() -> ApiException.notFound("Permissao"));
        g.setRevokedAt(clock.instant());
        grants.save(g);
        audit.record(ownerId, "GRANT_REVOKED", "PATIENT", patientId, "grantee=" + g.getGranteeUserId());
    }

    private GrantResponse toResponse(AccessGrant g, User grantee) {
        return new GrantResponse(g.getId(), g.getPatientId(), g.getGranteeUserId(),
                grantee == null ? null : grantee.getName(), grantee == null ? null : grantee.getEmail(),
                g.getAccessLevel(), g.getRelationship(), g.getConsentAt(), g.getCreatedAt());
    }

    private static String validZone(String tz) {
        if (tz == null || tz.isBlank()) {
            return Patient.DEFAULT_TIMEZONE;
        }
        try {
            return ZoneId.of(tz.trim()).getId();
        } catch (DateTimeException e) {
            throw ApiException.badRequest("INVALID_TIMEZONE", "Fuso horario invalido");
        }
    }
}
