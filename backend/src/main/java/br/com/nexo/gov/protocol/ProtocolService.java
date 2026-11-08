package br.com.nexo.gov.protocol;

import br.com.nexo.core.tenant.TenantContext;
import br.com.nexo.gov.protocol.ProtocolDtos.*;
import br.com.nexo.gov.protocol.ProtocolExceptions.ForbiddenAction;
import br.com.nexo.gov.protocol.ProtocolExceptions.InvalidTransition;
import br.com.nexo.gov.protocol.ProtocolExceptions.NotFound;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class ProtocolService {
    private static final String READ_STATUS_PROJECTION = "CASE " +
            "WHEN EXISTS (SELECT 1 FROM protocol_read_receipts rr WHERE rr.organization_id=p.organization_id " +
            "AND rr.protocol_id=p.id AND rr.department_id=p.assigned_department_id AND rr.username=?) THEN 'READ_BY_ME' " +
            "WHEN EXISTS (SELECT 1 FROM protocol_read_receipts rr WHERE rr.organization_id=p.organization_id " +
            "AND rr.protocol_id=p.id AND rr.department_id=p.assigned_department_id) THEN 'READ_BY_OTHER' " +
            "ELSE 'UNREAD_BY_DEPARTMENT' END AS read_status";
    private static final Logger log = LoggerFactory.getLogger(ProtocolService.class);
    private final JdbcTemplate jdbc;
    private final ReceiptPdfService receiptPdf;
    private final ProtocolAttachmentService attachments;
    private final ProtocolInteractionService interactions;
    private final ProtocolParticipantService participants;
    private final ProtocolPublicAccessService publicAccess;

    public ProtocolService(JdbcTemplate jdbc, ReceiptPdfService receiptPdf, ProtocolAttachmentService attachments,
                           ProtocolInteractionService interactions, ProtocolParticipantService participants,
                           ProtocolPublicAccessService publicAccess) {
        this.jdbc = jdbc;
        this.receiptPdf = receiptPdf;
        this.attachments = attachments;
        this.interactions = interactions;
        this.participants = participants;
        this.publicAccess = publicAccess;
    }

    @Transactional
    public Created create(CreateRequest request) {
        return create(request, List.of(), List.of());
    }

    @Transactional
    public Created create(CreateRequest request, List<MultipartFile> files, List<UUID> requirementIds) {
        List<ProtocolAttachmentService.Upload> uploads = attachments.prepare(files, requirementIds);
        UUID organizationId = jdbc.query("SELECT id FROM organizations WHERE slug=? AND active=true", (rs, n) -> rs.getObject(1, UUID.class), request.organizationSlug())
                .stream().findFirst().orElseThrow(NotFound::new);
        ProtocolSubjectDestination destination = jdbc.query("""
                        SELECT ps.name, ps.destination_department_id, ps.automatic_deadline_days
                          FROM protocol_subjects ps
                          JOIN organizations o ON o.id=ps.organization_id
                         WHERE o.id=? AND o.active=true AND ps.id=? AND ps.online_available=true
                         FOR SHARE OF ps
                        """, (rs, n) -> new ProtocolSubjectDestination(rs.getString("name"),
                        rs.getObject("destination_department_id", UUID.class), null,
                        (Integer) rs.getObject("automatic_deadline_days")),
                organizationId, request.subjectId())
                .stream().findFirst().orElseThrow(NotFound::new);
        ProtocolParticipantService.PreparedRegistration participantRegistration = participants.validate(
                organizationId, request.subjectId(), request.participants());
        if (destination.departmentId == null)
            throw new ProtocolSubjectService.SubjectRoutingUnavailable();
        String departmentName = jdbc.query("""
                        SELECT name FROM departments
                         WHERE organization_id=? AND id=? AND active=true
                         FOR SHARE
                        """, (rs, n) -> rs.getString("name"), organizationId, destination.departmentId)
                .stream().findFirst().orElseThrow(ProtocolSubjectService.SubjectRoutingUnavailable::new);
        destination = new ProtocolSubjectDestination(destination.subjectName, destination.departmentId, departmentName,
                destination.automaticDeadlineDays);
        attachments.validatePolicy(organizationId, request.subjectId(), uploads);
        UUID id = UUID.randomUUID();
        List<String> stagedObjectKeys = new ArrayList<>();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        for (String key : stagedObjectKeys) {
                            try { attachments.deleteStagedObject(key); }
                            catch (RuntimeException cleanupFailure) {
                                log.warn("Falha ao limpar objeto de anexo após rollback; reconciliação operacional necessária.");
                            }
                        }
                    }
                }
            });
        }
        long sequence = jdbc.queryForObject(
                "INSERT INTO protocol_counters(organization_id,counter_year,next_value) VALUES(?,?,1) " +
                        "ON CONFLICT(organization_id,counter_year) DO UPDATE SET next_value=protocol_counters.next_value+1 RETURNING next_value",
                Long.class, organizationId, Year.now().getValue());
        String number = "%d-%06d".formatted(Year.now().getValue(), sequence);
        String token = TrackingTokens.generate();
        OffsetDateTime createdAt = jdbc.queryForObject(
                "INSERT INTO protocols(id,organization_id,protocol_number,protocol_subject_id,assigned_department_id,initial_assigned_department_name,automatic_deadline_days,subject,applicant_name,applicant_email,initial_message,status,tracking_token_hash) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?, 'OPEN',?) RETURNING created_at",
                (rs, n) -> rs.getObject(1, OffsetDateTime.class), id, organizationId, number, request.subjectId(),
                destination.departmentId, destination.departmentName, destination.automaticDeadlineDays,
                destination.subjectName, request.applicantName().trim(), request.applicantEmail().trim(),
                request.initialMessage().trim(), TrackingTokens.hash(token));
        List<ParticipantInvitation> participantInvitations = participants.persist(
                organizationId, id, participantRegistration);
        // The protocol row must exist before attachment metadata can satisfy its FK.
        // Policy and content validation already ran before allocating the number; any
        // storage failure rolls back this transaction, including the counter update.
        List<ProtocolAttachmentService.AttachmentView> storedAttachments = attachments.storeAll(
                organizationId, id, request.subjectId(), "PORTAL_EXTERNO", uploads, stagedObjectKeys);
        addEvent(organizationId, id, "PORTAL_EXTERNO", "OPENED", null, "OPEN", destination.departmentId,
                "Solicitação recebida pelo setor " + destination.departmentName + ".", "PUBLIC");
        if (!storedAttachments.isEmpty()) {
            addEvent(organizationId, id, "PORTAL_EXTERNO", "ATTACHMENTS_ADDED", "OPEN", "OPEN", destination.departmentId,
                    storedAttachments.size() + " anexo(s) enviado(s) pelo requerente.", "PUBLIC");
        }
        if (!participantInvitations.isEmpty()) {
            addEvent(organizationId, id, "PORTAL_EXTERNO", "PARTICIPANTS_ADDED", "OPEN", "OPEN",
                    destination.departmentId, "Pessoas adicionais foram vinculadas ao protocolo.", "PUBLIC");
        }
        return new Created(id, number, "OPEN", createdAt, token,
                "/api/v1/public/protocols/%s/receipt".formatted(id), destination.departmentName,
                participantInvitations);
    }

    public List<OrganizationOption> organizations() {
        return jdbc.query("SELECT id,slug,name FROM organizations WHERE active=true ORDER BY name", (rs, n) -> new OrganizationOption(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("name")));
    }

    public List<DepartmentOption> departments() {
        UUID organizationId = TenantContext.requireTenantId();
        return jdbc.query("SELECT id,name FROM departments WHERE organization_id=? AND active=true ORDER BY name",
                (rs, n) -> new DepartmentOption(rs.getObject("id", UUID.class), rs.getString("name")), organizationId);
    }

    public List<ProtocolSummary> list(String status, String actor) {
        UUID organizationId = TenantContext.requireTenantId();
        String select = "SELECT p.id,p.protocol_number,p.subject,p.applicant_name,p.status,p.assigned_department_id,d.name AS department_name,p.created_at,p.updated_at, " +
                "(SELECT count(*) FROM protocol_attachments a WHERE a.organization_id=p.organization_id AND a.protocol_id=p.id AND a.interaction_id IS NULL) AS attachment_count " +
                ",(SELECT COALESCE(array_agg(DISTINCT extension ORDER BY extension), ARRAY[]::text[]) FROM protocol_subject_attachment_requirements r CROSS JOIN LATERAL unnest(r.allowed_extensions) AS allowed(extension) " +
                "WHERE r.organization_id=p.organization_id AND r.subject_id=p.protocol_subject_id AND r.active=true) AS interaction_allowed_extensions, " +
                READ_STATUS_PROJECTION + " " +
                "FROM protocols p LEFT JOIN departments d ON d.id=p.assigned_department_id AND d.organization_id=p.organization_id " +
                "WHERE p.organization_id=? ";
        List<Object> parameters = new ArrayList<>();
        parameters.add(actor);
        parameters.add(organizationId);
        if (!isOrganizationAdmin(actor, organizationId)) {
            select += "AND EXISTS (SELECT 1 FROM staff_memberships sm WHERE sm.username=? AND sm.organization_id=p.organization_id " +
                    "AND sm.department_id=p.assigned_department_id AND sm.active=true) ";
            parameters.add(actor);
        }
        if (status != null && !status.isBlank()) {
            select += "AND p.status=? ";
            parameters.add(status);
        }
        select += "ORDER BY p.created_at DESC";
        return jdbc.query(select, summaryMapper(), parameters.toArray());
    }

    @Transactional
    public ProtocolReadReceipt recordRead(UUID id, String actor) {
        ProtocolRow protocol = requireProtocol(id);
        UUID departmentId = protocol.assignedDepartmentId;
        if (departmentId == null) throw new ForbiddenAction();
        requireActiveCurrentDepartmentMembership(protocol.organizationId, departmentId, actor);
        jdbc.update("""
                        INSERT INTO protocol_read_receipts(organization_id, protocol_id, department_id, username)
                        VALUES(?,?,?,?)
                        ON CONFLICT (organization_id, protocol_id, department_id, username) DO NOTHING
                        """, protocol.organizationId, protocol.id, departmentId, actor);
        OffsetDateTime readAt = jdbc.queryForObject("""
                        SELECT read_at FROM protocol_read_receipts
                         WHERE organization_id=? AND protocol_id=? AND department_id=? AND username=?
                        """, OffsetDateTime.class, protocol.organizationId, protocol.id, departmentId, actor);
        return new ProtocolReadReceipt(protocol.id, departmentId, readAt, ProtocolReadStatus.READ_BY_ME);
    }

    private void requireActiveCurrentDepartmentMembership(UUID organizationId, UUID departmentId, String actor) {
        List<UUID> activeMemberships = jdbc.query("""
                        SELECT sm.department_id
                          FROM staff_memberships sm
                          JOIN departments d ON d.organization_id=sm.organization_id AND d.id=sm.department_id
                         WHERE sm.username=? AND sm.organization_id=? AND sm.department_id=?
                           AND sm.active=true AND d.active=true
                         FOR UPDATE OF sm
                         FOR SHARE OF d
                        """, (rs, row) -> rs.getObject("department_id", UUID.class),
                actor, organizationId, departmentId);
        if (activeMemberships.isEmpty()) throw new ForbiddenAction();
    }

    private boolean isOrganizationAdmin(String actor, UUID organizationId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM organization_memberships WHERE username=? AND organization_id=? AND role_code='ADMIN' AND active=true",
                Integer.class, actor, organizationId);
        return count != null && count > 0;
    }

    @Transactional
    public TrackingView track(String rawToken) {
        ProtocolPublicAccessService.Principal resolved = publicAccess.resolve(rawToken);
        ProtocolPublicAccessService.Principal protocol = publicAccess.lockForProtocol(resolved.protocolId(), rawToken);
        List<EventView> events = jdbc.query("SELECT action,summary,created_at FROM protocol_events WHERE protocol_id=? AND visibility='PUBLIC' ORDER BY created_at,id",
                (rs, n) -> new EventView(rs.getString("action"), rs.getString("summary"), rs.getObject("created_at", OffsetDateTime.class)), protocol.protocolId());
        return new TrackingView(protocol.protocolId(), protocol.protocolNumber(), protocol.subject(), protocol.status(), protocol.createdAt(), events,
                attachments.publicList(protocol.organizationId(), protocol.protocolId()),
                interactions.publicList(protocol.organizationId(), protocol.protocolId(), protocol),
                interactions.interactionAllowedExtensions(protocol.organizationId(), protocol.subjectId()),
                protocol.applicant(), protocol.participantName(),
                protocol.applicant() ? participants.listForApplicant(protocol.organizationId(), protocol.protocolId()) : List.of(),
                participants.identityValues(protocol.organizationId(), protocol.protocolId(), protocol.participantId()));
    }

    @Transactional
    public void revokeParticipant(UUID protocolId, UUID participantId, String rawToken) {
        ProtocolPublicAccessService.Principal applicant = publicAccess.lockApplicantProtocol(protocolId, rawToken);
        participants.revoke(applicant.organizationId(), protocolId, participantId);
        addEvent(applicant.organizationId(), protocolId, "PORTAL_EXTERNO", "PARTICIPANT_ACCESS_REVOKED",
                applicant.status(), applicant.status(), applicant.departmentId(),
                "O acesso de uma pessoa vinculada foi revogado pelo requerente.", "PUBLIC");
    }

    public List<ProtocolAttachmentService.AttachmentView> staffAttachments(UUID id, String actor) {
        ProtocolRow protocol = requireProtocol(id);
        authorizeForAttachmentRead(protocol, actor);
        return attachments.staffList(protocol.organizationId, id);
    }

    public ProtocolAttachmentService.Downloaded downloadStaffAttachment(UUID id, UUID attachmentId, String actor) {
        ProtocolRow protocol = requireProtocol(id);
        authorizeForAttachmentRead(protocol, actor);
        return attachments.staffDownload(protocol.organizationId, id, attachmentId, actor);
    }

    public ProtocolAttachmentService.Downloaded downloadPublicAttachment(UUID id, UUID attachmentId, String rawToken) {
        return attachments.publicDownload(id, attachmentId, rawToken);
    }

    public byte[] receipt(UUID id, String rawToken) {
        ReceiptData protocol = jdbc.query(
                "SELECT p.protocol_number,p.subject,p.created_at,p.initial_assigned_department_name AS department_name, " +
                        "p.automatic_deadline_days " +
                        "FROM protocols p " +
                        "WHERE p.id=? AND p.tracking_token_hash=?",
                (rs, n) -> new ReceiptData(rs.getString("protocol_number"), rs.getString("subject"),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getString("department_name"),
                        (Integer) rs.getObject("automatic_deadline_days")),
                id, TrackingTokens.hash(rawToken)).stream().findFirst().orElseThrow(NotFound::new);
        return receiptPdf.create(protocol.number, protocol.subject, protocol.createdAt, protocol.departmentName,
                protocol.automaticDeadlineDays, rawToken);
    }

    @Transactional public ProtocolSummary forward(UUID id, String actor, ForwardRequest request) {
        ProtocolRow protocol = requireProtocol(id);
        if (!List.of("OPEN", "RECEIVED").contains(protocol.status)) throw new InvalidTransition("Somente protocolos abertos ou recebidos podem ser encaminhados.");
        authorizeForCurrentDepartment(protocol, actor);
        UUID target = request.destinationDepartmentId();
        if (target == null || jdbc.queryForObject("SELECT count(*) FROM departments WHERE id=? AND organization_id=? AND active=true", Integer.class, target, protocol.organizationId) == 0)
            throw new NotFound();
        updateStatus(protocol, "FORWARDED", target);
        eventForTransition(protocol, actor, "FORWARDED", "FORWARDED", target, "Protocolo encaminhado.",
                new TransitionRequest(target, request.note(), request.visibleToApplicant()));
        return findSummary(id, actor);
    }

    @Transactional public ProtocolSummary receive(UUID id, String actor, TransitionRequest request) {
        ProtocolRow protocol = requireProtocol(id);
        if (!List.of("FORWARDED", "DISPATCHED").contains(protocol.status)) throw new InvalidTransition("O protocolo precisa estar encaminhado para ser recebido.");
        requireDepartmentMembership(protocol.organizationId, protocol.assignedDepartmentId, actor);
        updateStatus(protocol, "RECEIVED", protocol.assignedDepartmentId);
        eventForTransition(protocol, actor, "RECEIVED", "RECEIVED", protocol.assignedDepartmentId, "Protocolo recebido pelo setor.", request);
        return findSummary(id, actor);
    }

    @Transactional public ProtocolSummary dispatch(UUID id, String actor, TransitionRequest request) {
        ProtocolRow protocol = requireProtocol(id);
        if (!"RECEIVED".equals(protocol.status)) throw new InvalidTransition("O protocolo precisa estar recebido para ser despachado.");
        requireDepartmentMembership(protocol.organizationId, protocol.assignedDepartmentId, actor);
        UUID requestedDestination = request == null ? null : request.destinationDepartmentId();
        UUID target = requestedDestination == null ? protocol.assignedDepartmentId : requestedDestination;
        ensureDepartmentBelongs(target, protocol.organizationId);
        updateStatus(protocol, "DISPATCHED", target);
        eventForTransition(protocol, actor, "DISPATCHED", "DISPATCHED", target, "Despacho registrado.", request);
        return findSummary(id, actor);
    }

    @Transactional public ProtocolSummary complete(UUID id, String actor, TransitionRequest request) {
        ProtocolRow protocol = requireProtocol(id);
        if (!"RECEIVED".equals(protocol.status)) throw new InvalidTransition("O protocolo deve estar recebido antes da conclusão.");
        requireDepartmentMembership(protocol.organizationId, protocol.assignedDepartmentId, actor);
        updateStatus(protocol, "COMPLETED", protocol.assignedDepartmentId);
        eventForTransition(protocol, actor, "COMPLETED", "COMPLETED", protocol.assignedDepartmentId, "Protocolo concluído.", request);
        return findSummary(id, actor);
    }

    @Transactional public ProtocolSummary reopen(UUID id, String actor) {
        ProtocolRow protocol = requireProtocol(id);
        if (!"COMPLETED".equals(protocol.status))
            throw new InvalidTransition("Somente um protocolo concluído pode ser reaberto.");
        if (protocol.assignedDepartmentId == null)
            throw new ForbiddenAction();
        requireDepartmentMembership(protocol.organizationId, protocol.assignedDepartmentId, actor);
        updateStatus(protocol, "RECEIVED", protocol.assignedDepartmentId);
        addEvent(protocol.organizationId, protocol.id, actor, "REOPENED", "COMPLETED", "RECEIVED",
                protocol.assignedDepartmentId, "Protocolo reaberto pelo setor responsável.", "PUBLIC");
        return findSummary(id, actor);
    }

    private void authorizeForCurrentDepartment(ProtocolRow protocol, String actor) {
        if (protocol.assignedDepartmentId == null) {
            Integer admin = jdbc.queryForObject("SELECT count(*) FROM organization_memberships WHERE username=? AND organization_id=? AND role_code='ADMIN' AND active=true", Integer.class, actor, protocol.organizationId);
            if (admin == null || admin == 0) throw new ForbiddenAction();
        } else requireDepartmentMembership(protocol.organizationId, protocol.assignedDepartmentId, actor);
    }

    private void authorizeForAttachmentRead(ProtocolRow protocol, String actor) {
        if (isOrganizationAdmin(actor, protocol.organizationId)) return;
        Integer count = jdbc.queryForObject("""
                        SELECT count(*) FROM staff_memberships sm
                          JOIN departments d ON d.organization_id=sm.organization_id AND d.id=sm.department_id AND d.active=true
                         WHERE sm.username=? AND sm.organization_id=? AND sm.active=true
                           AND (sm.department_id=? OR EXISTS (
                               SELECT 1 FROM protocol_events e WHERE e.organization_id=sm.organization_id
                                 AND e.protocol_id=? AND e.department_id=sm.department_id))
                        """, Integer.class, actor, protocol.organizationId,
                protocol.assignedDepartmentId, protocol.id);
        if (count == null || count == 0) throw new ForbiddenAction();
    }

    private void requireDepartmentMembership(UUID organizationId, UUID departmentId, String actor) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM staff_memberships WHERE username=? AND organization_id=? AND department_id=? AND active=true", Integer.class, actor, organizationId, departmentId);
        if (count == null || count == 0) throw new ForbiddenAction();
    }

    private void ensureDepartmentBelongs(UUID departmentId, UUID organizationId) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM departments WHERE id=? AND organization_id=? AND active=true", Integer.class, departmentId, organizationId);
        if (count == null || count == 0) throw new NotFound();
    }

    private ProtocolRow requireProtocol(UUID id) {
        UUID organizationId = TenantContext.requireTenantId();
        return jdbc.query("SELECT id,organization_id,status,assigned_department_id FROM protocols WHERE id=? AND organization_id=? FOR UPDATE",
                (rs, n) -> new ProtocolRow(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class), rs.getString("status"), rs.getObject("assigned_department_id", UUID.class)), id, organizationId)
                .stream().findFirst().orElseThrow(NotFound::new);
    }

    private void updateStatus(ProtocolRow protocol, String next, UUID departmentId) {
        jdbc.update("UPDATE protocols SET status=?,assigned_department_id=?,updated_at=now() WHERE id=? AND organization_id=?",
                next, departmentId, protocol.id, protocol.organizationId);
    }

    private void eventForTransition(ProtocolRow protocol, String actor, String action, String next, UUID department, String defaultSummary, TransitionRequest request) {
        String note = request == null || request.note() == null ? "" : request.note().trim();
        boolean visible = request != null && Boolean.TRUE.equals(request.visibleToApplicant());
        String summary = note.isEmpty() ? defaultSummary : defaultSummary + " " + note;
        addEvent(protocol.organizationId, protocol.id, actor, action, protocol.status, next, department, summary, visible ? "PUBLIC" : "INTERNAL");
    }

    private void addEvent(UUID organizationId, UUID protocolId, String actor, String action, String from, String to, UUID department, String summary, String visibility) {
        jdbc.update("INSERT INTO protocol_events(organization_id,protocol_id,actor,action,from_status,to_status,department_id,summary,visibility) VALUES(?,?,?,?,?,?,?,?,?)",
                organizationId, protocolId, actor, action, from, to, department, summary, visibility);
    }

    private ProtocolSummary findSummary(UUID id, String actor) {
        UUID organizationId = TenantContext.requireTenantId();
        return jdbc.query("SELECT p.id,p.protocol_number,p.subject,p.applicant_name,p.status,p.assigned_department_id,d.name AS department_name,p.created_at,p.updated_at, " +
                        "(SELECT count(*) FROM protocol_attachments a WHERE a.organization_id=p.organization_id AND a.protocol_id=p.id AND a.interaction_id IS NULL) AS attachment_count " +
                        ",(SELECT COALESCE(array_agg(DISTINCT extension ORDER BY extension), ARRAY[]::text[]) FROM protocol_subject_attachment_requirements r CROSS JOIN LATERAL unnest(r.allowed_extensions) AS allowed(extension) " +
                        "WHERE r.organization_id=p.organization_id AND r.subject_id=p.protocol_subject_id AND r.active=true) AS interaction_allowed_extensions, " +
                        READ_STATUS_PROJECTION + " " +
                        "FROM protocols p LEFT JOIN departments d ON d.id=p.assigned_department_id AND d.organization_id=p.organization_id WHERE p.id=? AND p.organization_id=?",
                summaryMapper(), actor, id, organizationId).stream().findFirst().orElseThrow(NotFound::new);
    }

    private RowMapper<ProtocolSummary> summaryMapper() {
        return (rs, n) -> new ProtocolSummary(rs.getObject("id", UUID.class), rs.getString("protocol_number"), rs.getString("subject"),
                rs.getString("applicant_name"), rs.getString("status"), rs.getObject("assigned_department_id", UUID.class),
                rs.getString("department_name"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class), rs.getInt("attachment_count"),
                Arrays.asList((String[]) rs.getArray("interaction_allowed_extensions").getArray()),
                ProtocolReadStatus.valueOf(rs.getString("read_status")));
    }

    private record ProtocolRow(UUID id, UUID organizationId, String status, UUID assignedDepartmentId) {}
    private record ProtocolSummaryAndId(UUID id, UUID organizationId, String number, UUID subjectId, String subject,
                                        String status, OffsetDateTime createdAt) {}
    private record ReceiptData(String number, String subject, OffsetDateTime createdAt, String departmentName,
                               Integer automaticDeadlineDays) {}
    private record ProtocolSubjectDestination(String subjectName, UUID departmentId, String departmentName,
                                               Integer automaticDeadlineDays) {}
}
