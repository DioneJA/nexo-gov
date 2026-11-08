package br.com.nexo.gov.protocol;

import br.com.nexo.core.tenant.TenantContext;
import br.com.nexo.gov.protocol.ProtocolExceptions.ForbiddenAction;
import br.com.nexo.gov.protocol.ProtocolExceptions.NotFound;
import br.com.nexo.gov.protocol.ProtocolSubjectDtos.AttachmentRequirementRequest;
import br.com.nexo.gov.protocol.ProtocolSubjectDtos.AttachmentRequirementView;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;

import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ProtocolAttachmentPolicyService {
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".pdf", ".png", ".jpg", ".jpeg", ".txt", ".mp4");
    private final JdbcTemplate jdbc;
    private final int maximumFiles;
    private final long maximumFileSizeBytes;

    public ProtocolAttachmentPolicyService(JdbcTemplate jdbc,
            @Value("${nexogov.attachments.maximum-files:10}") int maximumFiles,
            @Value("${nexogov.attachments.maximum-file-size-bytes:10485760}") long maximumFileSizeBytes,
            @Value("${spring.servlet.multipart.max-file-size:10MB}") String multipartMaximumFileSize) {
        this.jdbc = jdbc;
        this.maximumFiles = maximumFiles;
        long multipartMaximumBytes = DataSize.parse(multipartMaximumFileSize).toBytes();
        this.maximumFileSizeBytes = Math.min(maximumFileSizeBytes, multipartMaximumBytes);
        if (maximumFiles < 1 || this.maximumFileSizeBytes < 1 || this.maximumFileSizeBytes >= Integer.MAX_VALUE)
            throw new IllegalStateException("Limites de anexo precisam ser positivos e caber no limite de leitura em memória.");
    }

    public long maximumFileSizeBytes() { return maximumFileSizeBytes; }

    @Transactional
    public AttachmentRequirementView create(String actor, UUID subjectId, AttachmentRequirementRequest request) {
        UUID organizationId = TenantContext.requireTenantId();
        requireOrganizationAdmin(actor, organizationId);
        requireSubject(organizationId, subjectId);
        String name = request.name().trim();
        List<String> extensions = normalizeExtensions(request.allowedExtensions());
        UUID id = UUID.randomUUID();
        try {
            jdbc.execute((ConnectionCallback<Integer>) connection -> {
                java.sql.Array sqlExtensions = connection.createArrayOf("text", extensions.toArray());
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO protocol_subject_attachment_requirements
                            (id, organization_id, subject_id, name, required, allowed_extensions, created_by, updated_by)
                        VALUES(?,?,?,?,?,?,?,?)
                        """)) {
                    statement.setObject(1, id);
                    statement.setObject(2, organizationId);
                    statement.setObject(3, subjectId);
                    statement.setString(4, name);
                    statement.setBoolean(5, request.required());
                    statement.setArray(6, sqlExtensions);
                    statement.setString(7, actor);
                    statement.setString(8, actor);
                    return statement.executeUpdate();
                } finally { sqlExtensions.free(); }
            });
        } catch (DataIntegrityViolationException duplicate) {
            throw new AttachmentRequirementNameConflict();
        }
        jdbc.update("""
                        INSERT INTO organization_audit_events(organization_id,actor_username,action,entity_type,entity_id,summary)
                        VALUES(?,?,'PROTOCOL_ATTACHMENT_REQUIREMENT_CREATED','PROTOCOL_SUBJECT_ATTACHMENT_REQUIREMENT',?,?)
                        """, organizationId, actor, id, "Regra de anexo criada para assunto: " + name);
        return new AttachmentRequirementView(id, name, request.required(), extensions, true);
    }

    @Transactional
    public AttachmentRequirementView update(String actor, UUID subjectId, UUID requirementId, AttachmentRequirementRequest request) {
        UUID organizationId = TenantContext.requireTenantId();
        requireOrganizationAdmin(actor, organizationId);
        requireSubject(organizationId, subjectId);
        String name = request.name().trim();
        List<String> extensions = normalizeExtensions(request.allowedExtensions());
        int updated;
        try {
            updated = jdbc.execute((ConnectionCallback<Integer>) connection -> {
                java.sql.Array sqlExtensions = connection.createArrayOf("text", extensions.toArray());
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE protocol_subject_attachment_requirements
                           SET name=?, required=?, allowed_extensions=?, updated_by=?, updated_at=now()
                         WHERE organization_id=? AND subject_id=? AND id=? AND active=true
                        """)) {
                    statement.setString(1, name);
                    statement.setBoolean(2, request.required());
                    statement.setArray(3, sqlExtensions);
                    statement.setString(4, actor);
                    statement.setObject(5, organizationId);
                    statement.setObject(6, subjectId);
                    statement.setObject(7, requirementId);
                    return statement.executeUpdate();
                } finally { sqlExtensions.free(); }
            });
        } catch (DataIntegrityViolationException duplicate) {
            throw new AttachmentRequirementNameConflict();
        }
        if (updated == 0) throw new NotFound();
        jdbc.update("""
                        INSERT INTO organization_audit_events(organization_id,actor_username,action,entity_type,entity_id,summary)
                        VALUES(?,?,'PROTOCOL_ATTACHMENT_REQUIREMENT_UPDATED','PROTOCOL_SUBJECT_ATTACHMENT_REQUIREMENT',?,?)
                        """, organizationId, actor, requirementId, "Regra de anexo atualizada para assunto: " + name);
        return new AttachmentRequirementView(requirementId, name, request.required(), extensions, true);
    }

    @Transactional
    public void deactivate(String actor, UUID subjectId, UUID requirementId) {
        UUID organizationId = TenantContext.requireTenantId();
        requireOrganizationAdmin(actor, organizationId);
        requireSubject(organizationId, subjectId);
        int updated = jdbc.update("""
                        UPDATE protocol_subject_attachment_requirements
                           SET active=false, updated_by=?, updated_at=now()
                         WHERE organization_id=? AND subject_id=? AND id=? AND active=true
                        """, actor, organizationId, subjectId, requirementId);
        if (updated == 0) throw new NotFound();
        jdbc.update("""
                        INSERT INTO organization_audit_events(organization_id,actor_username,action,entity_type,entity_id,summary)
                        VALUES(?,?,'PROTOCOL_ATTACHMENT_REQUIREMENT_DEACTIVATED','PROTOCOL_SUBJECT_ATTACHMENT_REQUIREMENT',?,?)
                        """, organizationId, actor, requirementId, "Regra de anexo desativada.");
    }

    List<AttachmentRequirementView> forSubject(UUID organizationId, UUID subjectId, boolean activeOnly) {
        return jdbc.query("""
                        SELECT id, name, required, allowed_extensions, active
                          FROM protocol_subject_attachment_requirements
                         WHERE organization_id=? AND subject_id=? AND (?=false OR active=true)
                         ORDER BY created_at, id
                        """, (rs, row) -> new AttachmentRequirementView(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getBoolean("required"), Arrays.asList((String[]) rs.getArray("allowed_extensions").getArray()), rs.getBoolean("active")),
                organizationId, subjectId, activeOnly);
    }

    List<AttachmentRequirementView> validateRequest(UUID organizationId, UUID subjectId, List<ProtocolAttachmentService.Upload> uploads) {
        List<AttachmentRequirementView> rules = forSubject(organizationId, subjectId, true);
        if (uploads.size() > maximumFiles) throw new ProtocolAttachmentService.AttachmentRejected("Envie no máximo " + maximumFiles + " arquivos por solicitação.");
        if (!uploads.isEmpty() && rules.isEmpty()) throw new ProtocolAttachmentService.AttachmentRejected("Este assunto ainda não aceita anexos.");
        for (ProtocolAttachmentService.Upload upload : uploads) {
            if (upload.content().length > maximumFileSizeBytes) throw new ProtocolAttachmentService.AttachmentRejected("O arquivo excede o limite permitido.", upload.requirementId());
            AttachmentRequirementView rule = rules.stream().filter(candidate -> candidate.id().equals(upload.requirementId())).findFirst()
                    .orElseThrow(() -> new ProtocolAttachmentService.AttachmentRejected("O tipo de anexo não está disponível para este assunto."));
            if (!rule.allowedExtensions().contains(upload.extension()))
                throw new ProtocolAttachmentService.AttachmentRejected("A extensão do arquivo não é aceita para " + rule.name() + ".", upload.requirementId());
        }
        for (AttachmentRequirementView rule : rules) {
            if (rule.required() && uploads.stream().noneMatch(upload -> upload.requirementId().equals(rule.id())))
                throw new ProtocolAttachmentService.AttachmentRejected("Anexe o documento obrigatório: " + rule.name() + ".", rule.id());
        }
        return rules;
    }

    List<ProtocolAttachmentService.Upload> validateInteraction(UUID organizationId, UUID subjectId,
                                                                 List<ProtocolAttachmentService.Upload> uploads) {
        if (uploads.isEmpty()) return List.of();
        if (subjectId == null || jdbc.queryForList(
                "SELECT id FROM protocol_subjects WHERE organization_id=? AND id=? FOR SHARE",
                UUID.class, organizationId, subjectId).isEmpty()) {
            throw new ProtocolAttachmentService.AttachmentRejected("Este protocolo não aceita anexos em interações.");
        }
        List<AttachmentRequirementView> rules = forSubject(organizationId, subjectId, true);
        if (rules.isEmpty()) throw new ProtocolAttachmentService.AttachmentRejected("Este assunto não aceita anexos em interações.");
        List<ProtocolAttachmentService.Upload> validated = new java.util.ArrayList<>();
        for (ProtocolAttachmentService.Upload upload : uploads) {
            AttachmentRequirementView rule = rules.stream()
                    .filter(candidate -> candidate.allowedExtensions().contains(upload.extension()))
                    .findFirst()
                    .orElseThrow(() -> new ProtocolAttachmentService.AttachmentRejected(
                            "O formato do anexo não está habilitado para este assunto."));
            validated.add(new ProtocolAttachmentService.Upload(rule.id(), upload.filename(), upload.extension(),
                    upload.mediaType(), upload.content(), upload.sha256()));
        }
        return List.copyOf(validated);
    }

    List<String> interactionAllowedExtensions(UUID organizationId, UUID subjectId) {
        if (subjectId == null) return List.of();
        java.util.Set<String> activeExtensions = forSubject(organizationId, subjectId, true).stream()
                .flatMap(rule -> rule.allowedExtensions().stream()).collect(java.util.stream.Collectors.toSet());
        return SUPPORTED_EXTENSIONS.stream().filter(activeExtensions::contains).toList();
    }

    private List<String> normalizeExtensions(List<String> extensions) {
        if (extensions == null || extensions.isEmpty()) throw new ProtocolAttachmentService.AttachmentRejected("Informe ao menos uma extensão permitida.");
        List<String> normalized = extensions.stream().map(value -> value == null ? "" : value.trim().toLowerCase(Locale.ROOT))
                .map(value -> value.startsWith(".") ? value : "." + value).distinct().toList();
        if (normalized.size() != extensions.size() || normalized.stream().anyMatch(extension -> !SUPPORTED_EXTENSIONS.contains(extension)))
            throw new ProtocolAttachmentService.AttachmentRejected("Extensões disponíveis: PDF, PNG, JPG, TXT e MP4.");
        return normalized;
    }

    private void requireSubject(UUID organizationId, UUID subjectId) {
        if (jdbc.queryForList("SELECT id FROM protocol_subjects WHERE organization_id=? AND id=? FOR UPDATE", UUID.class, organizationId, subjectId).isEmpty()) throw new NotFound();
    }

    private void requireOrganizationAdmin(String actor, UUID organizationId) {
        Integer count = jdbc.queryForObject("""
                        SELECT count(*) FROM organization_memberships om
                         WHERE om.username=? AND om.organization_id=? AND om.role_code='ADMIN' AND om.active=true
                           AND NOT EXISTS (SELECT 1 FROM organization_member_access_status s
                            WHERE s.organization_id=om.organization_id AND s.username=om.username AND s.access_active=false)
                        """, Integer.class, actor, organizationId);
        if (count == null || count == 0) throw new ForbiddenAction();
    }

    public static class AttachmentRequirementNameConflict extends RuntimeException {
        public AttachmentRequirementNameConflict() { super("Já existe um tipo de anexo com esse nome para este assunto."); }
    }
}
