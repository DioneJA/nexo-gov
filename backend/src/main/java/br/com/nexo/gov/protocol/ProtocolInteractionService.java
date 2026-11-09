package br.com.nexo.gov.protocol;

import br.com.nexo.core.tenant.TenantContext;
import br.com.nexo.gov.protocol.ProtocolDtos.InteractionView;
import br.com.nexo.gov.protocol.ProtocolDtos.StaffInteractionRequest;
import br.com.nexo.gov.protocol.ProtocolExceptions.ForbiddenAction;
import br.com.nexo.gov.protocol.ProtocolExceptions.InvalidTransition;
import br.com.nexo.gov.protocol.ProtocolExceptions.NotFound;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ProtocolInteractionService {
    private static final Logger log = LoggerFactory.getLogger(ProtocolInteractionService.class);

    private final JdbcTemplate jdbc;
    private final ProtocolAttachmentService attachments;
    private final ProtocolAttachmentPolicyService policies;
    private final ProtocolPublicAccessService publicAccess;
    private final ProtocolDocumentAccessService documentAccess;

    public ProtocolInteractionService(JdbcTemplate jdbc, ProtocolAttachmentService attachments,
                                      ProtocolAttachmentPolicyService policies,
                                      ProtocolPublicAccessService publicAccess,
                                      ProtocolDocumentAccessService documentAccess) {
        this.jdbc = jdbc;
        this.attachments = attachments;
        this.policies = policies;
        this.publicAccess = publicAccess;
        this.documentAccess = documentAccess;
    }

    @Transactional
    public InteractionView createPublic(UUID protocolId, String rawToken, String message, List<MultipartFile> files) {
        ProtocolPublicAccessService.Principal principal = publicAccess.lockForProtocol(protocolId, rawToken);
        ProtocolRow protocol = new ProtocolRow(principal.protocolId(), principal.organizationId(), principal.status(),
                principal.subjectId(), principal.departmentId());
        if ("COMPLETED".equals(protocol.status))
            throw new InvalidTransition("O protocolo foi concluído e não aceita novas mensagens externas.");
        String normalizedMessage = normalizeMessage(message);
        List<ProtocolAttachmentService.Upload> uploads = policies.validateInteraction(protocol.organizationId,
                protocol.subjectId, attachments.prepareInteraction(files));
        UUID interactionId = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO protocol_interactions(id,organization_id,protocol_id,actor,actor_type,participant_id,department_id,visibility,message)
                        VALUES(?,?,?,?,?, ?,NULL,'PUBLIC',?)
                        """, interactionId, protocol.organizationId, protocol.id,
                principal.applicant() ? "APPLICANT" : "PARTICIPANT",
                principal.applicant() ? "APPLICANT" : "PARTICIPANT", principal.participantId(), normalizedMessage);
        String actor = principal.applicant() ? "APPLICANT" : "PARTICIPANT";
        store(protocol, interactionId, actor, "PUBLIC", uploads);
        return view(interactionId, principal.applicant() ? "Requerente" : "Você", null,
                normalizedMessage, "PUBLIC", OffsetDateTime.now(),
                interactionAttachments(protocol, interactionId, false, null));
    }

    @Transactional
    public InteractionView createStaff(UUID protocolId, String actor, StaffInteractionRequest request,
                                      List<MultipartFile> files) {
        UUID organizationId = TenantContext.requireTenantId();
        ProtocolRow protocol = lockStaffProtocol(organizationId, protocolId);
        authorizeInvolvedStaff(protocol, actor);
        if ("COMPLETED".equals(protocol.status))
            throw new InvalidTransition("O protocolo foi concluído; reabra-o antes de registrar uma nova mensagem.");
        String normalizedMessage = normalizeMessage(request.message());
        String visibility = request.visibility();
        List<ProtocolAttachmentService.Upload> uploads = policies.validateInteraction(protocol.organizationId,
                protocol.subjectId, attachments.prepareInteraction(files));
        UUID departmentId = actorDepartment(protocol, actor, visibility);
        UUID interactionId = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO protocol_interactions(id,organization_id,protocol_id,actor,actor_type,department_id,visibility,message)
                        VALUES(?,?,?,?,'STAFF',?,?,?)
                        """, interactionId, protocol.organizationId, protocol.id, actor, departmentId, visibility,
                normalizedMessage);
        store(protocol, interactionId, actor, visibility, uploads);
        return view(interactionId, "Servidor do setor", departmentName(protocol.organizationId, departmentId),
                normalizedMessage, visibility, OffsetDateTime.now(), interactionAttachments(protocol, interactionId, true, actor));
    }

    @Transactional
    public List<InteractionView> publicList(UUID organizationId, UUID protocolId,
                                            ProtocolPublicAccessService.Principal principal) {
        List<InteractionRow> rows = jdbc.query("""
                        SELECT i.id,i.actor_type,i.participant_id,i.department_id,d.name AS department_name,i.message,i.visibility,i.created_at
                          FROM protocol_interactions i
                          LEFT JOIN departments d ON d.organization_id=i.organization_id AND d.id=i.department_id
                         WHERE i.organization_id=? AND i.protocol_id=? AND i.visibility='PUBLIC'
                         ORDER BY i.created_at,i.id
                        """, this::mapRow, organizationId, protocolId);
        List<InteractionView> result = rows.stream().map(row -> toView(organizationId, protocolId, row, false, null, principal)).toList();
        String actorType = principal.applicant() ? "APPLICANT" : "PARTICIPANT";
        String actorReference = principal.applicant() ? "APPLICANT" : principal.participantId().toString();
        for (InteractionView interaction : result) {
            documentAccess.recordRead(organizationId, protocolId, "INTERACTION", interaction.id(), actorType, actorReference);
            interaction.attachments().forEach(attachment -> documentAccess.recordRead(organizationId, protocolId,
                    "ATTACHMENT", attachment.id(), actorType, actorReference));
        }
        return result;
    }

    @Transactional
    public List<InteractionView> staffList(UUID protocolId, String actor) {
        UUID organizationId = TenantContext.requireTenantId();
        ProtocolRow protocol = findStaffProtocol(organizationId, protocolId);
        boolean admin = isOrganizationAdmin(actor, organizationId);
        authorizeInvolvedStaff(protocol, actor);
        List<InteractionRow> rows = jdbc.query("""
                        SELECT i.id,i.actor_type,i.participant_id,i.department_id,d.name AS department_name,i.message,i.visibility,i.created_at
                          FROM protocol_interactions i
                          LEFT JOIN departments d ON d.organization_id=i.organization_id AND d.id=i.department_id
                         WHERE i.organization_id=? AND i.protocol_id=?
                           AND (i.visibility='PUBLIC' OR ?=true OR EXISTS (
                               SELECT 1 FROM staff_memberships sm WHERE sm.username=? AND sm.organization_id=i.organization_id
                                 AND sm.department_id=i.department_id AND sm.active=true))
                         ORDER BY i.created_at,i.id
                        """, this::mapRow, organizationId, protocolId, admin, actor);
        List<InteractionView> result = rows.stream().map(row -> toView(organizationId, protocolId, row, true, actor, null)).toList();
        for (InteractionView interaction : result) {
            documentAccess.recordRead(organizationId, protocolId, "INTERACTION", interaction.id(), "STAFF", actor);
            interaction.attachments().forEach(attachment -> documentAccess.recordRead(organizationId, protocolId,
                    "ATTACHMENT", attachment.id(), "STAFF", actor));
        }
        return result;
    }

    public List<String> interactionAllowedExtensions(UUID organizationId, UUID subjectId) {
        return policies.interactionAllowedExtensions(organizationId, subjectId);
    }

    private InteractionView toView(UUID organizationId, UUID protocolId, InteractionRow row,
                                   boolean staff, String actor,
                                   ProtocolPublicAccessService.Principal principal) {
        String author = switch (row.actorType) {
            case "APPLICANT" -> "Requerente";
            case "STAFF" -> "Servidor do setor";
            case "PARTICIPANT" -> principal != null && row.participantId != null
                    && row.participantId.equals(principal.participantId()) ? "Você" : "Pessoa envolvida";
            default -> "";
        };
        return view(row.id, author,
                row.departmentName, row.message, row.visibility, row.createdAt,
                interactionAttachments(new ProtocolRow(protocolId, organizationId, null, null, null), row.id,
                        staff, actor));
    }

    private List<ProtocolDtos.InteractionAttachmentView> interactionAttachments(ProtocolRow protocol,
                                                                                UUID interactionId,
                                                                                boolean staff, String actor) {
        return attachments.interactionList(protocol.organizationId, protocol.id, interactionId, staff, actor);
    }

    private InteractionView view(UUID id, String author, String department, String message, String visibility,
                                 OffsetDateTime createdAt, List<ProtocolDtos.InteractionAttachmentView> files) {
        return new InteractionView(id, author, department, message, visibility, createdAt, files);
    }

    private List<ProtocolAttachmentService.AttachmentView> store(ProtocolRow protocol, UUID interactionId,
                                                                  String actor, String visibility,
                                                                  List<ProtocolAttachmentService.Upload> uploads) {
        List<String> stagedObjectKeys = new ArrayList<>();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        for (String key : stagedObjectKeys) {
                            try { attachments.deleteStagedObject(key); }
                            catch (RuntimeException cleanupFailure) {
                                log.warn("Falha ao limpar objeto de interação após rollback; reconciliação operacional necessária.");
                            }
                        }
                    }
                }
            });
        }
        return attachments.storeAll(protocol.organizationId, protocol.id, protocol.subjectId, actor,
                interactionId, visibility, uploads, stagedObjectKeys);
    }

    private ProtocolRow lockStaffProtocol(UUID organizationId, UUID protocolId) {
        return jdbc.query("""
                        SELECT id,organization_id,status,protocol_subject_id,assigned_department_id
                          FROM protocols WHERE id=? AND organization_id=? FOR UPDATE
                        """, (rs, row) -> new ProtocolRow(rs.getObject("id", UUID.class),
                        rs.getObject("organization_id", UUID.class), rs.getString("status"),
                        rs.getObject("protocol_subject_id", UUID.class), rs.getObject("assigned_department_id", UUID.class)), protocolId, organizationId)
                .stream().findFirst().orElseThrow(NotFound::new);
    }

    private ProtocolRow findStaffProtocol(UUID organizationId, UUID protocolId) {
        return jdbc.query("""
                        SELECT id,organization_id,status,protocol_subject_id,assigned_department_id
                          FROM protocols WHERE id=? AND organization_id=?
                        """, (rs, row) -> new ProtocolRow(rs.getObject("id", UUID.class),
                        rs.getObject("organization_id", UUID.class), rs.getString("status"),
                        rs.getObject("protocol_subject_id", UUID.class), rs.getObject("assigned_department_id", UUID.class)), protocolId, organizationId)
                .stream().findFirst().orElseThrow(NotFound::new);
    }

    private void authorizeInvolvedStaff(ProtocolRow protocol, String actor) {
        if (isOrganizationAdmin(actor, protocol.organizationId)) return;
        Integer count = jdbc.queryForObject("""
                        SELECT count(*) FROM staff_memberships sm
                          JOIN departments d ON d.organization_id=sm.organization_id AND d.id=sm.department_id AND d.active=true
                         WHERE sm.username=? AND sm.organization_id=? AND sm.active=true
                           AND EXISTS (SELECT 1 FROM protocol_events e WHERE e.organization_id=sm.organization_id
                             AND e.protocol_id=? AND e.department_id=sm.department_id)
                        """, Integer.class, actor, protocol.organizationId, protocol.id);
        if (count == null || count == 0) throw new ForbiddenAction();
    }

    private UUID actorDepartment(ProtocolRow protocol, String actor, String visibility) {
        List<UUID> departments = jdbc.query("""
                        SELECT DISTINCT sm.department_id FROM staff_memberships sm
                          JOIN departments d ON d.organization_id=sm.organization_id AND d.id=sm.department_id AND d.active=true
                         WHERE sm.username=? AND sm.organization_id=? AND sm.active=true
                           AND EXISTS (SELECT 1 FROM protocol_events e WHERE e.organization_id=sm.organization_id
                             AND e.protocol_id=? AND e.department_id=sm.department_id)
                         ORDER BY sm.department_id
                        """, (rs, row) -> rs.getObject(1, UUID.class), actor, protocol.organizationId, protocol.id);
        if (departments.isEmpty()) {
            if (isOrganizationAdmin(actor, protocol.organizationId) && protocol.departmentId != null) return protocol.departmentId;
            throw new ForbiddenAction();
        }
        if (protocol.departmentId != null && departments.contains(protocol.departmentId)) return protocol.departmentId;
        return departments.getFirst();
    }

    private boolean isOrganizationAdmin(String actor, UUID organizationId) {
        Integer count = jdbc.queryForObject("""
                        SELECT count(*) FROM organization_memberships om
                         WHERE om.username=? AND om.organization_id=? AND om.role_code='ADMIN' AND om.active=true
                           AND NOT EXISTS (SELECT 1 FROM organization_member_access_status s
                             WHERE s.organization_id=om.organization_id AND s.username=om.username AND s.access_active=false)
                        """, Integer.class, actor, organizationId);
        return count != null && count > 0;
    }

    private String departmentName(UUID organizationId, UUID departmentId) {
        return jdbc.query("SELECT name FROM departments WHERE organization_id=? AND id=?",
                        (rs, row) -> rs.getString(1), organizationId, departmentId)
                .stream().findFirst().orElse(null);
    }

    private String normalizeMessage(String message) {
        if (message == null || message.isBlank())
            throw new ProtocolAttachmentService.AttachmentRejected("Escreva uma mensagem antes de enviar.");
        String normalized = message.trim();
        if (normalized.length() > 8000)
            throw new ProtocolAttachmentService.AttachmentRejected("A mensagem excede o limite de 8.000 caracteres.");
        return normalized;
    }

    private InteractionRow mapRow(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new InteractionRow(rs.getObject("id", UUID.class), rs.getString("actor_type"),
                rs.getObject("participant_id", UUID.class), rs.getObject("department_id", UUID.class),
                rs.getString("department_name"), rs.getString("message"),
                rs.getString("visibility"), rs.getObject("created_at", OffsetDateTime.class));
    }

    private record ProtocolRow(UUID id, UUID organizationId, String status, UUID subjectId, UUID departmentId) {}
    private record InteractionRow(UUID id, String actorType, UUID participantId, UUID departmentId, String departmentName,
                                  String message, String visibility, OffsetDateTime createdAt) {}
}
