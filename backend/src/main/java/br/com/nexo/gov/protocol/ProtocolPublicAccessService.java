package br.com.nexo.gov.protocol;

import br.com.nexo.gov.protocol.ProtocolExceptions.NotFound;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Resolves public bearer links to one protocol-scoped actor. */
@Service
public class ProtocolPublicAccessService {
    private final JdbcTemplate jdbc;

    public ProtocolPublicAccessService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Principal resolve(String rawToken) {
        String hash = hash(rawToken);
        return jdbc.query("""
                        SELECT id, organization_id, protocol_number, protocol_subject_id, subject, status,
                               created_at, assigned_department_id
                          FROM protocols WHERE tracking_token_hash=?
                        """, (rs, row) -> applicant(rs), hash).stream().findFirst()
                .or(() -> resolveParticipant(null, null, hash, false)).orElseThrow(NotFound::new);
    }

    @Transactional(readOnly = true)
    public Principal resolveForProtocol(UUID protocolId, String rawToken) {
        String hash = hash(rawToken);
        Principal protocol = jdbc.query("""
                        SELECT id, organization_id, protocol_number, protocol_subject_id, subject, status,
                               created_at, assigned_department_id, tracking_token_hash
                          FROM protocols WHERE id=?
                        """, (rs, row) -> {
                    Principal base = applicant(rs);
                    return rs.getString("tracking_token_hash").equals(hash) ? base : null;
                }, protocolId).stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (protocol != null) return protocol;
        return resolveParticipant(protocolId, null, hash, false).orElseThrow(NotFound::new);
    }

    @Transactional
    public Principal lockForProtocol(UUID protocolId, String rawToken) {
        String hash = hash(rawToken);
        PrincipalAndHash protocol = jdbc.query("""
                        SELECT id, organization_id, protocol_number, protocol_subject_id, subject, status,
                               created_at, assigned_department_id, tracking_token_hash
                          FROM protocols WHERE id=? FOR UPDATE
                        """, (rs, row) -> new PrincipalAndHash(applicant(rs), rs.getString("tracking_token_hash")),
                protocolId).stream().findFirst().orElseThrow(NotFound::new);
        if (protocol.tokenHash.equals(hash)) return protocol.principal;
        return resolveParticipant(protocolId, protocol.principal.organizationId, hash, true).orElseThrow(NotFound::new);
    }

    @Transactional
    public Principal lockApplicantProtocol(UUID protocolId, String rawToken) {
        Principal principal = lockForProtocol(protocolId, rawToken);
        if (!principal.applicant()) throw new NotFound();
        return principal;
    }

    @Transactional(readOnly = true)
    public Principal resolveApplicantProtocol(UUID protocolId, String rawToken) {
        Principal principal = resolveForProtocol(protocolId, rawToken);
        if (!principal.applicant()) throw new NotFound();
        return principal;
    }

    private java.util.Optional<Principal> resolveParticipant(UUID protocolId, UUID organizationId, String hash,
                                                             boolean lockParticipant) {
        String sql = """
                SELECT p.id, p.organization_id, p.protocol_number, p.protocol_subject_id, p.subject, p.status,
                       p.created_at, p.assigned_department_id, pp.id AS participant_id, pp.name AS participant_name
                  FROM protocol_participants pp
                  JOIN protocols p ON p.organization_id=pp.organization_id AND p.id=pp.protocol_id
                 WHERE pp.token_hash=? AND pp.revoked_at IS NULL
                """;
        java.util.ArrayList<Object> parameters = new java.util.ArrayList<>();
        parameters.add(hash);
        if (protocolId != null) { sql += " AND p.id=?"; parameters.add(protocolId); }
        if (organizationId != null) { sql += " AND p.organization_id=?"; parameters.add(organizationId); }
        sql += lockParticipant ? " LIMIT 1 FOR SHARE OF pp" : " LIMIT 1";
        return jdbc.query(sql, (rs, row) -> new Principal(rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class), rs.getString("protocol_number"),
                rs.getObject("protocol_subject_id", UUID.class), rs.getString("subject"), rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("assigned_department_id", UUID.class),
                rs.getObject("participant_id", UUID.class), rs.getString("participant_name")), parameters.toArray())
                .stream().findFirst();
    }

    private static Principal applicant(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Principal(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getString("protocol_number"), rs.getObject("protocol_subject_id", UUID.class),
                rs.getString("subject"), rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("assigned_department_id", UUID.class), null, null);
    }

    private static String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw new NotFound();
        return TrackingTokens.hash(rawToken);
    }

    public record Principal(UUID protocolId, UUID organizationId, String protocolNumber, UUID subjectId,
                             String subject, String status, OffsetDateTime createdAt, UUID departmentId,
                             UUID participantId, String participantName) {
        public boolean applicant() { return participantId == null; }
    }

    private record PrincipalAndHash(Principal principal, String tokenHash) {}
}
