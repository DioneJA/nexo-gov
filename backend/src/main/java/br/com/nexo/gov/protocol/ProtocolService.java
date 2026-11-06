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

import java.time.OffsetDateTime;
import java.time.Year;
import java.util.List;
import java.util.UUID;

@Service
public class ProtocolService {
    private final JdbcTemplate jdbc;
    private final ReceiptPdfService receiptPdf;

    public ProtocolService(JdbcTemplate jdbc, ReceiptPdfService receiptPdf) { this.jdbc = jdbc; this.receiptPdf = receiptPdf; }

    @Transactional
    public Created create(CreateRequest request) {
        UUID organizationId = jdbc.query("SELECT id FROM organizations WHERE slug=? AND active=true", (rs, n) -> rs.getObject(1, UUID.class), request.organizationSlug())
                .stream().findFirst().orElseThrow(NotFound::new);
        long sequence = jdbc.queryForObject(
                "INSERT INTO protocol_counters(organization_id,counter_year,next_value) VALUES(?,?,1) " +
                        "ON CONFLICT(organization_id,counter_year) DO UPDATE SET next_value=protocol_counters.next_value+1 RETURNING next_value",
                Long.class, organizationId, Year.now().getValue());
        String number = "%d-%06d".formatted(Year.now().getValue(), sequence);
        UUID id = UUID.randomUUID();
        String token = TrackingTokens.generate();
        OffsetDateTime createdAt = jdbc.queryForObject(
                "INSERT INTO protocols(id,organization_id,protocol_number,subject,applicant_name,applicant_email,initial_message,status,tracking_token_hash) " +
                        "VALUES(?,?,?,?,?,?,?,'OPEN',?) RETURNING created_at",
                (rs, n) -> rs.getObject(1, OffsetDateTime.class), id, organizationId, number, request.subject().trim(),
                request.applicantName().trim(), request.applicantEmail().trim(), request.initialMessage().trim(), TrackingTokens.hash(token));
        addEvent(organizationId, id, "PORTAL_EXTERNO", "OPENED", null, "OPEN", null, "Solicitação recebida pelo portal.", "PUBLIC");
        return new Created(id, number, "OPEN", createdAt, token, "/api/v1/public/protocols/%s/receipt".formatted(id));
    }

    public List<OrganizationOption> organizations() {
        return jdbc.query("SELECT id,slug,name FROM organizations WHERE active=true ORDER BY name", (rs, n) -> new OrganizationOption(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("name")));
    }

    public List<DepartmentOption> departments() {
        UUID organizationId = TenantContext.requireTenantId();
        return jdbc.query("SELECT id,name FROM departments WHERE organization_id=? AND active=true ORDER BY name",
                (rs, n) -> new DepartmentOption(rs.getObject("id", UUID.class), rs.getString("name")), organizationId);
    }

    public List<ProtocolSummary> list(String status) {
        UUID organizationId = TenantContext.requireTenantId();
        String select = "SELECT p.id,p.protocol_number,p.subject,p.applicant_name,p.status,p.assigned_department_id,d.name AS department_name,p.created_at,p.updated_at " +
                "FROM protocols p LEFT JOIN departments d ON d.id=p.assigned_department_id AND d.organization_id=p.organization_id " +
                "WHERE p.organization_id=? ";
        if (status == null || status.isBlank()) return jdbc.query(select + "ORDER BY p.created_at DESC", summaryMapper(), organizationId);
        return jdbc.query(select + "AND p.status=? ORDER BY p.created_at DESC", summaryMapper(), organizationId, status);
    }

    public TrackingView track(String rawToken) {
        String hash = TrackingTokens.hash(rawToken);
        ProtocolSummaryAndId protocol = jdbc.query(
                "SELECT id,protocol_number,subject,status,created_at FROM protocols WHERE tracking_token_hash=?",
                (rs, n) -> new ProtocolSummaryAndId(rs.getObject("id", UUID.class), rs.getString("protocol_number"), rs.getString("subject"), rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class)), hash)
                .stream().findFirst().orElseThrow(NotFound::new);
        List<EventView> events = jdbc.query("SELECT action,summary,created_at FROM protocol_events WHERE protocol_id=? AND visibility='PUBLIC' ORDER BY created_at,id",
                (rs, n) -> new EventView(rs.getString("action"), rs.getString("summary"), rs.getObject("created_at", OffsetDateTime.class)), protocol.id);
        return new TrackingView(protocol.number, protocol.subject, protocol.status, protocol.createdAt, events);
    }

    public byte[] receipt(UUID id, String rawToken) {
        ProtocolSummaryAndId protocol = jdbc.query(
                "SELECT id,protocol_number,subject,status,created_at FROM protocols WHERE id=? AND tracking_token_hash=?",
                (rs, n) -> new ProtocolSummaryAndId(rs.getObject("id", UUID.class), rs.getString("protocol_number"), rs.getString("subject"), rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class)),
                id, TrackingTokens.hash(rawToken)).stream().findFirst().orElseThrow(NotFound::new);
        return receiptPdf.create(protocol.number, protocol.subject, protocol.createdAt, rawToken);
    }

    @Transactional public ProtocolSummary forward(UUID id, String actor, TransitionRequest request) {
        ProtocolRow protocol = requireProtocol(id);
        if (!List.of("OPEN", "RECEIVED").contains(protocol.status)) throw new InvalidTransition("Somente protocolos abertos ou recebidos podem ser encaminhados.");
        authorizeForCurrentDepartment(protocol, actor);
        UUID target = request.destinationDepartmentId();
        if (target == null || jdbc.queryForObject("SELECT count(*) FROM departments WHERE id=? AND organization_id=? AND active=true", Integer.class, target, protocol.organizationId) == 0)
            throw new NotFound();
        updateStatus(protocol, "FORWARDED", target);
        eventForTransition(protocol, actor, "FORWARDED", "FORWARDED", target, "Protocolo encaminhado.", request);
        return findSummary(id);
    }

    @Transactional public ProtocolSummary receive(UUID id, String actor, TransitionRequest request) {
        ProtocolRow protocol = requireProtocol(id);
        if (!List.of("FORWARDED", "DISPATCHED").contains(protocol.status)) throw new InvalidTransition("O protocolo precisa estar encaminhado para ser recebido.");
        requireDepartmentMembership(protocol.organizationId, protocol.assignedDepartmentId, actor);
        updateStatus(protocol, "RECEIVED", protocol.assignedDepartmentId);
        eventForTransition(protocol, actor, "RECEIVED", "RECEIVED", protocol.assignedDepartmentId, "Protocolo recebido pelo setor.", request);
        return findSummary(id);
    }

    @Transactional public ProtocolSummary dispatch(UUID id, String actor, TransitionRequest request) {
        ProtocolRow protocol = requireProtocol(id);
        if (!"RECEIVED".equals(protocol.status)) throw new InvalidTransition("O protocolo precisa estar recebido para ser despachado.");
        requireDepartmentMembership(protocol.organizationId, protocol.assignedDepartmentId, actor);
        UUID target = request.destinationDepartmentId() == null ? protocol.assignedDepartmentId : request.destinationDepartmentId();
        ensureDepartmentBelongs(target, protocol.organizationId);
        updateStatus(protocol, "DISPATCHED", target);
        eventForTransition(protocol, actor, "DISPATCHED", "DISPATCHED", target, "Despacho registrado.", request);
        return findSummary(id);
    }

    @Transactional public ProtocolSummary complete(UUID id, String actor, TransitionRequest request) {
        ProtocolRow protocol = requireProtocol(id);
        if (!"RECEIVED".equals(protocol.status)) throw new InvalidTransition("O protocolo deve estar recebido antes da conclusão.");
        requireDepartmentMembership(protocol.organizationId, protocol.assignedDepartmentId, actor);
        updateStatus(protocol, "COMPLETED", protocol.assignedDepartmentId);
        eventForTransition(protocol, actor, "COMPLETED", "COMPLETED", protocol.assignedDepartmentId, "Protocolo concluído.", request);
        return findSummary(id);
    }

    private void authorizeForCurrentDepartment(ProtocolRow protocol, String actor) {
        if (protocol.assignedDepartmentId == null) {
            Integer admin = jdbc.queryForObject("SELECT count(*) FROM staff_memberships WHERE username=? AND organization_id=? AND role_code='ADMIN' AND active=true", Integer.class, actor, protocol.organizationId);
            if (admin == null || admin == 0) throw new ForbiddenAction();
        } else requireDepartmentMembership(protocol.organizationId, protocol.assignedDepartmentId, actor);
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
        return jdbc.query("SELECT id,organization_id,status,assigned_department_id FROM protocols WHERE id=? AND organization_id=?",
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

    private ProtocolSummary findSummary(UUID id) {
        UUID organizationId = TenantContext.requireTenantId();
        return jdbc.query("SELECT p.id,p.protocol_number,p.subject,p.applicant_name,p.status,p.assigned_department_id,d.name AS department_name,p.created_at,p.updated_at " +
                        "FROM protocols p LEFT JOIN departments d ON d.id=p.assigned_department_id AND d.organization_id=p.organization_id WHERE p.id=? AND p.organization_id=?",
                summaryMapper(), id, organizationId).stream().findFirst().orElseThrow(NotFound::new);
    }

    private static RowMapper<ProtocolSummary> summaryMapper() {
        return (rs, n) -> new ProtocolSummary(rs.getObject("id", UUID.class), rs.getString("protocol_number"), rs.getString("subject"),
                rs.getString("applicant_name"), rs.getString("status"), rs.getObject("assigned_department_id", UUID.class),
                rs.getString("department_name"), rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
    }

    private record ProtocolRow(UUID id, UUID organizationId, String status, UUID assignedDepartmentId) {}
    private record ProtocolSummaryAndId(UUID id, String number, String subject, String status, OffsetDateTime createdAt) {}
}
