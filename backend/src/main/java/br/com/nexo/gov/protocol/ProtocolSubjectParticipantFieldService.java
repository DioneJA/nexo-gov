package br.com.nexo.gov.protocol;

import br.com.nexo.core.tenant.TenantContext;
import br.com.nexo.gov.protocol.ProtocolExceptions.ForbiddenAction;
import br.com.nexo.gov.protocol.ProtocolExceptions.NotFound;
import br.com.nexo.gov.protocol.ProtocolSubjectDtos.ParticipantIdentityFieldInput;
import br.com.nexo.gov.protocol.ProtocolSubjectDtos.ParticipantIdentityFieldType;
import br.com.nexo.gov.protocol.ProtocolSubjectDtos.ParticipantIdentityFieldUpdate;
import br.com.nexo.gov.protocol.ProtocolSubjectDtos.ParticipantIdentityFieldView;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProtocolSubjectParticipantFieldService {
    private final JdbcTemplate jdbc;

    public ProtocolSubjectParticipantFieldService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<ParticipantIdentityFieldView> list(String actor, UUID subjectId) {
        UUID organizationId = TenantContext.requireTenantId();
        requireOrganizationAdmin(actor, organizationId);
        requireSubject(organizationId, subjectId);
        return jdbc.query("""
                        SELECT id, subject_id, field_key, label, input_type, required, display_order
                          FROM protocol_subject_participant_fields
                         WHERE organization_id=? AND subject_id=?
                         ORDER BY display_order, field_key
                        """, (rs, row) -> new ParticipantIdentityFieldView(
                        rs.getObject("id", UUID.class), rs.getObject("subject_id", UUID.class),
                        rs.getString("field_key"), rs.getString("label"),
                        ParticipantIdentityFieldType.valueOf(rs.getString("input_type")),
                        rs.getBoolean("required"), rs.getInt("display_order")), organizationId, subjectId);
    }

    @Transactional
    public ParticipantIdentityFieldView create(String actor, UUID subjectId, ParticipantIdentityFieldInput request) {
        UUID organizationId = TenantContext.requireTenantId();
        requireOrganizationAdmin(actor, organizationId);
        requireSubjectForUpdate(organizationId, subjectId);
        UUID id = UUID.randomUUID();
        String key = request.key();
        String label = request.label().trim();
        try {
            jdbc.update("""
                            INSERT INTO protocol_subject_participant_fields
                                (id, organization_id, subject_id, field_key, label, input_type, required,
                                 display_order, created_by, updated_by)
                            VALUES(?,?,?,?,?,?,?,?,?,?)
                            """, id, organizationId, subjectId, key, label, request.inputType().name(),
                    request.required(), request.order(), actor, actor);
        } catch (DataIntegrityViolationException duplicateKey) {
            throw new ParticipantFieldConflict();
        }
        recordAudit(organizationId, actor, id, "PROTOCOL_PARTICIPANT_FIELD_CREATED",
                "Campo de identificação de participante criado: " + key + " (" + label + ")");
        return view(id, subjectId, key, label, request.inputType(), request.required(), request.order());
    }

    @Transactional
    public ParticipantIdentityFieldView update(String actor, UUID subjectId, UUID fieldId,
                                               ParticipantIdentityFieldUpdate request) {
        UUID organizationId = TenantContext.requireTenantId();
        requireOrganizationAdmin(actor, organizationId);
        requireSubjectForUpdate(organizationId, subjectId);
        String key = jdbc.query("""
                        SELECT field_key FROM protocol_subject_participant_fields
                         WHERE organization_id=? AND subject_id=? AND id=? FOR UPDATE
                        """, (rs, row) -> rs.getString("field_key"), organizationId, subjectId, fieldId)
                .stream().findFirst().orElseThrow(NotFound::new);
        String label = request.label().trim();
        int updated;
        try {
            updated = jdbc.update("""
                            UPDATE protocol_subject_participant_fields
                               SET label=?, input_type=?, required=?, display_order=?,
                                   updated_by=?, updated_at=now()
                             WHERE organization_id=? AND subject_id=? AND id=?
                            """, label, request.inputType().name(), request.required(), request.order(),
                    actor, organizationId, subjectId, fieldId);
        } catch (DataIntegrityViolationException duplicateKey) {
            throw new ParticipantFieldConflict();
        }
        if (updated == 0) throw new NotFound();
        recordAudit(organizationId, actor, fieldId, "PROTOCOL_PARTICIPANT_FIELD_UPDATED",
                "Campo de identificação de participante atualizado: " + key + " (" + label + ")");
        return view(fieldId, subjectId, key, label, request.inputType(), request.required(), request.order());
    }

    @Transactional
    public void delete(String actor, UUID subjectId, UUID fieldId) {
        UUID organizationId = TenantContext.requireTenantId();
        requireOrganizationAdmin(actor, organizationId);
        requireSubjectForUpdate(organizationId, subjectId);
        List<FieldSummary> fields = jdbc.query("""
                        SELECT field_key, label
                          FROM protocol_subject_participant_fields
                         WHERE organization_id=? AND subject_id=? AND id=?
                         FOR UPDATE
                        """, (rs, row) -> new FieldSummary(rs.getString("field_key"), rs.getString("label")),
                organizationId, subjectId, fieldId);
        FieldSummary field = fields.stream().findFirst().orElseThrow(NotFound::new);
        jdbc.update("DELETE FROM protocol_subject_participant_fields WHERE organization_id=? AND subject_id=? AND id=?",
                organizationId, subjectId, fieldId);
        recordAudit(organizationId, actor, fieldId, "PROTOCOL_PARTICIPANT_FIELD_DELETED",
                "Campo de identificação de participante removido: " + field.key + " (" + field.label + ")");
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

    private void requireSubject(UUID organizationId, UUID subjectId) {
        if (jdbc.queryForList("SELECT id FROM protocol_subjects WHERE organization_id=? AND id=?",
                UUID.class, organizationId, subjectId).isEmpty()) throw new NotFound();
    }

    private void requireSubjectForUpdate(UUID organizationId, UUID subjectId) {
        if (jdbc.queryForList("SELECT id FROM protocol_subjects WHERE organization_id=? AND id=? FOR UPDATE",
                UUID.class, organizationId, subjectId).isEmpty()) throw new NotFound();
    }

    private void recordAudit(UUID organizationId, String actor, UUID fieldId, String action, String summary) {
        jdbc.update("""
                        INSERT INTO organization_audit_events(organization_id, actor_username, action, entity_type, entity_id, summary)
                        VALUES(?,?,?,'PROTOCOL_SUBJECT_PARTICIPANT_FIELD',?,?)
                        """, organizationId, actor, action, fieldId, summary);
    }

    private static ParticipantIdentityFieldView view(UUID id, UUID subjectId, String key, String label,
                                                     ParticipantIdentityFieldType inputType, boolean required,
                                                     int order) {
        return new ParticipantIdentityFieldView(id, subjectId, key, label, inputType, required, order);
    }

    private record FieldSummary(String key, String label) {}

    public static class ParticipantFieldConflict extends RuntimeException {
        public ParticipantFieldConflict() {
            super("Já existe um campo com essa chave para o assunto.");
        }
    }
}
