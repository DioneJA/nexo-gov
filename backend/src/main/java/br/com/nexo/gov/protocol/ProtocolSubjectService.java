package br.com.nexo.gov.protocol;

import br.com.nexo.core.tenant.TenantContext;
import br.com.nexo.gov.protocol.ProtocolExceptions.ForbiddenAction;
import br.com.nexo.gov.protocol.ProtocolExceptions.NotFound;
import br.com.nexo.gov.protocol.ProtocolSubjectDtos.PublicSubjectView;
import br.com.nexo.gov.protocol.ProtocolSubjectDtos.SubjectCreateRequest;
import br.com.nexo.gov.protocol.ProtocolSubjectDtos.SubjectUpdateRequest;
import br.com.nexo.gov.protocol.ProtocolSubjectDtos.SubjectView;
import br.com.nexo.gov.protocol.ProtocolSubjectDtos.AttachmentRequirementView;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Service
public class ProtocolSubjectService {
    private final JdbcTemplate jdbc;
    private final ProtocolAttachmentPolicyService attachmentPolicies;
    private final ProtocolParticipantService participants;

    public ProtocolSubjectService(JdbcTemplate jdbc, ProtocolAttachmentPolicyService attachmentPolicies,
                                  ProtocolParticipantService participants) {
        this.jdbc = jdbc;
        this.attachmentPolicies = attachmentPolicies;
        this.participants = participants;
    }

    public List<SubjectView> listOrganizationSubjects(String actor) {
        UUID organizationId = TenantContext.requireTenantId();
        requireOrganizationAdmin(actor, organizationId);
        return jdbc.query("""
                        SELECT ps.id, ps.organization_id, ps.name, ps.online_available, ps.destination_department_id,
                               ps.automatic_deadline_days,
                               d.name AS destination_department_name
                          FROM protocol_subjects ps
                          LEFT JOIN departments d
                            ON d.organization_id=ps.organization_id AND d.id=ps.destination_department_id
                         WHERE ps.organization_id=?
                         ORDER BY lower(ps.name), ps.id
                        """, (rs, row) -> subjectView(rs, organizationId), organizationId);
    }

    public List<PublicSubjectView> listPublicOnlineSubjects(String organizationSlug) {
        return jdbc.query("""
                        SELECT ps.id, ps.organization_id, ps.name, ps.online_available
                          FROM protocol_subjects ps
                          JOIN organizations o ON o.id=ps.organization_id
                          JOIN departments d ON d.organization_id=ps.organization_id
                                            AND d.id=ps.destination_department_id AND d.active=true
                         WHERE o.slug=? AND o.active=true AND ps.online_available=true
                         ORDER BY lower(ps.name), ps.id
                        """, (rs, row) -> {
                            UUID id = rs.getObject("id", UUID.class);
                            UUID organizationId = rs.getObject("organization_id", UUID.class);
                            return new PublicSubjectView(id, rs.getString("name"), rs.getBoolean("online_available"),
                                    attachmentPolicies.maximumFileSizeBytes(),
                                    attachmentPolicies.forSubject(organizationId, id, true),
                                    participants.publicFields(organizationId, id));
                        }, organizationSlug);
    }

    @Transactional
    public SubjectView create(String actor, SubjectCreateRequest request) {
        UUID organizationId = TenantContext.requireTenantId();
        requireOrganizationAdmin(actor, organizationId);
        UUID id = UUID.randomUUID();
        String name = request.name().trim();
        DepartmentDestination destination = validateDestination(organizationId, request.destinationDepartmentId());
        try {
            jdbc.update("""
                            INSERT INTO protocol_subjects(id, organization_id, name, online_available,
                                                          destination_department_id, automatic_deadline_days,
                                                          created_by, updated_by)
                            VALUES(?,?,?,?,?,?,?,?)
                            """, id, organizationId, name, request.onlineAvailable(), destination.id,
                    request.automaticDeadlineDays(), actor, actor);
        } catch (DataIntegrityViolationException duplicate) {
            throw new SubjectNameConflict();
        }
        recordAudit(organizationId, actor, id, "PROTOCOL_SUBJECT_CREATED",
                auditSubjectSummary("Assunto de protocolo criado: " + name, destination.name, request.automaticDeadlineDays()));
        return new SubjectView(id, name, request.onlineAvailable(), destination.id, destination.name,
                request.automaticDeadlineDays(),
                attachmentPolicies.forSubject(organizationId, id, true));
    }

    @Transactional
    public SubjectView update(String actor, UUID id, SubjectUpdateRequest request) {
        UUID organizationId = TenantContext.requireTenantId();
        requireOrganizationAdmin(actor, organizationId);
        SubjectState current = jdbc.query("""
                        SELECT ps.name, ps.online_available, ps.destination_department_id,
                               ps.automatic_deadline_days,
                               d.name AS destination_department_name
                          FROM protocol_subjects ps
                          LEFT JOIN departments d
                            ON d.organization_id=ps.organization_id AND d.id=ps.destination_department_id
                         WHERE ps.organization_id=? AND ps.id=?
                         FOR UPDATE OF ps
                        """, (rs, row) -> new SubjectState(rs.getString("name"), rs.getBoolean("online_available"),
                        rs.getObject("destination_department_id", UUID.class), rs.getString("destination_department_name"),
                        (Integer) rs.getObject("automatic_deadline_days")),
                organizationId, id).stream().findFirst().orElseThrow(NotFound::new);
        String name = request.name().trim();
        boolean onlineAvailable = request.onlineAvailable();
        Integer automaticDeadlineDays = request.automaticDeadlineDaysPresent()
                ? request.automaticDeadlineDays() : current.automaticDeadlineDays;
        UUID configuredDestination = request.destinationDepartmentId() == null
                ? current.destinationDepartmentId : request.destinationDepartmentId();
        DepartmentDestination destination = validateDestination(organizationId, configuredDestination);
        if (current.name.equals(name) && current.onlineAvailable == onlineAvailable
                && java.util.Objects.equals(current.destinationDepartmentId, destination.id)
                && java.util.Objects.equals(current.automaticDeadlineDays, automaticDeadlineDays)) {
            return new SubjectView(id, current.name, current.onlineAvailable,
                    current.destinationDepartmentId, current.destinationDepartmentName,
                    current.automaticDeadlineDays,
                    attachmentPolicies.forSubject(organizationId, id, true));
        }
        try {
            jdbc.update("""
                            UPDATE protocol_subjects
                               SET name=?, online_available=?, destination_department_id=?, automatic_deadline_days=?,
                                   updated_by=?, updated_at=now()
                             WHERE organization_id=? AND id=?
                            """, name, onlineAvailable, destination.id, automaticDeadlineDays, actor,
                    organizationId, id);
        } catch (DataIntegrityViolationException duplicate) {
            throw new SubjectNameConflict();
        }
        String audience = onlineAvailable ? "Central de Atendimento" : "somente balcão interno";
        recordAudit(organizationId, actor, id, "PROTOCOL_SUBJECT_UPDATED",
                auditSubjectSummary("Configuração do assunto atualizada: " + name + " (" + audience + ")", destination.name,
                        automaticDeadlineDays));
        return new SubjectView(id, name, onlineAvailable, destination.id, destination.name,
                automaticDeadlineDays,
                attachmentPolicies.forSubject(organizationId, id, true));
    }

    private DepartmentDestination validateDestination(UUID organizationId, UUID departmentId) {
        if (departmentId == null) return new DepartmentDestination(null, null);
        return jdbc.query("""
                        SELECT id, name FROM departments
                         WHERE organization_id=? AND id=? AND active=true
                         FOR SHARE
                        """, (rs, row) -> new DepartmentDestination(rs.getObject("id", UUID.class), rs.getString("name")),
                organizationId, departmentId).stream().findFirst().orElseThrow(NotFound::new);
    }

    private void requireOrganizationAdmin(String actor, UUID organizationId) {
        Integer count = jdbc.queryForObject("""
                        SELECT count(*)
                          FROM organization_memberships om
                         WHERE om.username=? AND om.organization_id=? AND om.role_code='ADMIN' AND om.active=true
                           AND NOT EXISTS (
                               SELECT 1 FROM organization_member_access_status s
                                WHERE s.organization_id=om.organization_id
                                  AND s.username=om.username AND s.access_active=false
                           )
                        """, Integer.class, actor, organizationId);
        if (count == null || count == 0) throw new ForbiddenAction();
    }

    private void recordAudit(UUID organizationId, String actor, UUID subjectId, String action, String summary) {
        jdbc.update("""
                        INSERT INTO organization_audit_events(organization_id,actor_username,action,entity_type,entity_id,summary)
                        VALUES(?,?,?,'PROTOCOL_SUBJECT',?,?)
                        """, organizationId, actor, action, subjectId, summary);
    }

    private static String auditSubjectSummary(String prefix, String destinationName, Integer automaticDeadlineDays) {
        String destination = destinationName == null ? " (sem setor destinatário configurado)" : " (destino: " + destinationName + ")";
        String deadline = automaticDeadlineDays == null ? " (sem prazo configurado)" : " (prazo: " + automaticDeadlineDays + " dias)";
        return prefix + destination + deadline;
    }

    private SubjectView subjectView(ResultSet rs, UUID organizationId) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        return new SubjectView(id, rs.getString("name"), rs.getBoolean("online_available"),
                rs.getObject("destination_department_id", UUID.class), rs.getString("destination_department_name"),
                (Integer) rs.getObject("automatic_deadline_days"),
                attachmentPolicies.forSubject(organizationId, id, true));
    }

    private record SubjectState(String name, boolean onlineAvailable, UUID destinationDepartmentId,
                                String destinationDepartmentName, Integer automaticDeadlineDays) {}
    private record DepartmentDestination(UUID id, String name) {}

    public static class SubjectNameConflict extends RuntimeException {
        public SubjectNameConflict() { super("Já existe um assunto com esse nome nesta organização."); }
    }

    public static class SubjectRoutingUnavailable extends RuntimeException {
        public SubjectRoutingUnavailable() { super("O assunto está sem um setor destinatário ativo configurado."); }
    }
}
