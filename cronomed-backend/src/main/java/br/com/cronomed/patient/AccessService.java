package br.com.cronomed.patient;

import br.com.cronomed.common.ApiException;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RBAC por paciente (RF40/RF41). Toda leitura/escrita de dados de medicacao passa por aqui, a cada
 * requisicao, consultando as permissoes vigentes no banco: a revogacao pelo titular tem efeito imediato.
 */
@Service
public class AccessService {

    private final PatientRepository patients;
    private final AccessGrantRepository grants;

    public AccessService(PatientRepository patients, AccessGrantRepository grants) {
        this.patients = patients;
        this.grants = grants;
    }

    @Transactional(readOnly = true)
    public AccessLevel levelOf(UUID userId, Patient patient) {
        if (patient.getOwnerId().equals(userId)) {
            return AccessLevel.OWNER;
        }
        Optional<AccessGrant> grant = grants.findByPatientIdAndGranteeUserIdAndRevokedAtIsNull(patient.getId(), userId);
        AccessLevel level = AccessLevel.NONE;
        if (grant.isPresent()) {
            level = grant.get().getAccessLevel() == GrantLevel.MANAGE ? AccessLevel.MANAGE : AccessLevel.VIEW;
        }
        if (userId.equals(patient.getLinkedUserId()) && !level.atLeast(AccessLevel.CONFIRM)) {
            level = AccessLevel.CONFIRM; // o proprio paciente confirma suas doses
        }
        return level;
    }

    @Transactional(readOnly = true)
    public Patient require(UUID userId, UUID patientId, AccessLevel needed) {
        Patient patient = patients.findById(patientId).orElseThrow(() -> ApiException.notFound("Paciente"));
        AccessLevel level = levelOf(userId, patient);
        if (level == AccessLevel.NONE) {
            throw ApiException.notFound("Paciente"); // nao revela a existencia de pacientes alheios
        }
        if (!level.atLeast(needed)) {
            throw ApiException.forbidden("Permissao insuficiente para esta operacao");
        }
        return patient;
    }

    public Patient requireView(UUID userId, UUID patientId) {
        return require(userId, patientId, AccessLevel.VIEW);
    }

    public Patient requireConfirm(UUID userId, UUID patientId) {
        return require(userId, patientId, AccessLevel.CONFIRM);
    }

    public Patient requireManage(UUID userId, UUID patientId) {
        return require(userId, patientId, AccessLevel.MANAGE);
    }

    public Patient requireOwner(UUID userId, UUID patientId) {
        return require(userId, patientId, AccessLevel.OWNER);
    }

    /** Usuarios que devem receber alertas deste paciente: titular, o proprio paciente e terceiros autorizados. */
    @Transactional(readOnly = true)
    public Set<UUID> recipients(UUID patientId) {
        Set<UUID> result = new LinkedHashSet<>();
        patients.findById(patientId).ifPresent(p -> {
            result.add(p.getOwnerId());
            if (p.getLinkedUserId() != null) {
                result.add(p.getLinkedUserId());
            }
            grants.findByPatientIdAndRevokedAtIsNull(patientId).forEach(g -> result.add(g.getGranteeUserId()));
        });
        return result;
    }

    /** Usuarios que podem agir em nome do paciente (para o push de "hora da dose"). */
    @Transactional(readOnly = true)
    public Set<UUID> doseRecipients(UUID patientId) {
        Set<UUID> result = new LinkedHashSet<>();
        patients.findById(patientId).ifPresent(p -> {
            result.add(p.getOwnerId());
            if (p.getLinkedUserId() != null) {
                result.add(p.getLinkedUserId());
            }
            grants.findByPatientIdAndRevokedAtIsNull(patientId).stream()
                    .filter(g -> g.getAccessLevel() == GrantLevel.MANAGE)
                    .forEach(g -> result.add(g.getGranteeUserId()));
        });
        return result;
    }
}
