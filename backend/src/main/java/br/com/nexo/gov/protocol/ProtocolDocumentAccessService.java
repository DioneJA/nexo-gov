package br.com.nexo.gov.protocol;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ProtocolDocumentAccessService {
    private final JdbcTemplate jdbc;

    public ProtocolDocumentAccessService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void recordRead(UUID organizationId, UUID protocolId, String documentType, UUID documentId,
                           String actorType, String actorReference) {
        jdbc.update("""
                        INSERT INTO protocol_document_access_events
                            (organization_id,protocol_id,document_type,document_id,actor_type,actor_reference,access_type,outcome)
                        VALUES(?,?,?,?,?,?,'READ','ALLOWED')
                        """, organizationId, protocolId, documentType, documentId, actorType, actorReference);
    }

    @Transactional(readOnly = true)
    public List<AccessHistoryEvent> history(UUID organizationId, UUID protocolId) {
        return jdbc.query("""
                        SELECT event_id,document_type,document_id,document_label,actor_type,actor_label,
                               access_type,outcome,accessed_at
                          FROM (
                            SELECT 'R:' || e.id::text AS event_id, e.document_type, e.document_id,
                                   CASE e.document_type
                                     WHEN 'PROTOCOL' THEN p.protocol_number
                                     WHEN 'ATTACHMENT' THEN a.original_filename
                                     WHEN 'INTERACTION' THEN 'Despacho ou mensagem'
                                   END AS document_label,
                                   e.actor_type,
                                   CASE e.actor_type
                                     WHEN 'STAFF' THEN e.actor_reference
                                     WHEN 'APPLICANT' THEN 'Requerente'
                                     WHEN 'PARTICIPANT' THEN 'Participante externo'
                                     ELSE 'Usuário não identificado'
                                   END AS actor_label,
                                   e.access_type, e.outcome, e.accessed_at
                              FROM protocol_document_access_events e
                              JOIN protocols p ON p.organization_id=e.organization_id AND p.id=e.protocol_id
                              LEFT JOIN protocol_attachments a
                                ON e.document_type='ATTACHMENT' AND a.organization_id=e.organization_id
                               AND a.protocol_id=e.protocol_id AND a.id=e.document_id
                             WHERE e.organization_id=? AND e.protocol_id=?
                            UNION ALL
                            SELECT 'D:' || d.id::text AS event_id, 'ATTACHMENT' AS document_type, d.attachment_id AS document_id,
                                   a.original_filename AS document_label,
                                   CASE WHEN d.audience='STAFF' THEN 'STAFF'
                                        WHEN d.actor='APPLICANT' THEN 'APPLICANT' ELSE 'PARTICIPANT' END AS actor_type,
                                   CASE WHEN d.audience='STAFF' THEN d.actor
                                        WHEN d.actor='APPLICANT' THEN 'Requerente' ELSE 'Participante externo' END AS actor_label,
                                   'DOWNLOAD' AS access_type, 'ALLOWED' AS outcome, d.downloaded_at AS accessed_at
                              FROM protocol_attachment_downloads d
                              JOIN protocol_attachments a
                                ON a.organization_id=d.organization_id AND a.protocol_id=d.protocol_id AND a.id=d.attachment_id
                             WHERE d.organization_id=? AND d.protocol_id=?
                          ) history
                         ORDER BY accessed_at DESC, event_id DESC
                        """, (rs, row) -> new AccessHistoryEvent(rs.getString("event_id"), rs.getString("document_type"),
                        rs.getObject("document_id", UUID.class), rs.getString("document_label"),
                        rs.getString("actor_type"), rs.getString("actor_label"), rs.getString("access_type"),
                        rs.getString("outcome"), rs.getObject("accessed_at", OffsetDateTime.class)),
                organizationId, protocolId, organizationId, protocolId);
    }

    public record AccessHistoryEvent(String id, String documentType, UUID documentId, String documentLabel,
                                     String actorType, String actorLabel, String accessType, String outcome,
                                     OffsetDateTime accessedAt) {}
}
