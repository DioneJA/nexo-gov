package br.com.nexo.gov.protocol;

import br.com.nexo.gov.protocol.ProtocolDtos.ParticipantIdentityInput;
import br.com.nexo.gov.protocol.ProtocolDtos.ParticipantIdentityValue;
import br.com.nexo.gov.protocol.ProtocolDtos.ParticipantInvitation;
import br.com.nexo.gov.protocol.ProtocolDtos.ProtocolParticipantView;
import br.com.nexo.gov.protocol.ProtocolDtos.PublicParticipantField;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Persists protocol-scoped participant identities and their one-time invitation credentials. */
@Service
public class ProtocolParticipantService {
    private static final int MAX_VALUE_LENGTH = 500;
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE = Pattern.compile("^[+0-9() .-]{5,32}$");
    private static final Pattern DOCUMENT = Pattern.compile("^[A-Za-z0-9./ -]{3,64}$");

    private final JdbcTemplate jdbc;

    public ProtocolParticipantService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PreparedRegistration validate(UUID organizationId, UUID subjectId, List<ParticipantIdentityInput> inputs) {
        List<FieldDefinition> definitions = jdbc.query("""
                        SELECT field_key, label, input_type, required, display_order
                          FROM protocol_subject_participant_fields
                         WHERE organization_id=? AND subject_id=?
                         ORDER BY display_order, field_key
                        """, (rs, row) -> new FieldDefinition(rs.getString("field_key"), rs.getString("label"),
                        rs.getString("input_type"), rs.getBoolean("required"), rs.getInt("display_order")),
                organizationId, subjectId);

        List<PreparedParticipant> prepared = new ArrayList<>();
        for (ParticipantIdentityInput input : inputs == null ? List.<ParticipantIdentityInput>of() : inputs) {
            if (input == null || input.name() == null || input.name().isBlank() || input.name().trim().length() > 180)
                throw rejected("Informe um nome válido para cada pessoa envolvida.");
            Map<String, String> submitted = input.identityValues() == null ? Map.of() : input.identityValues();
            if (submitted.size() > definitions.size())
                throw rejected("Foram enviados campos que não estão configurados para este assunto.");
            Map<String, FieldDefinition> allowed = new HashMap<>();
            definitions.forEach(definition -> allowed.put(definition.key, definition));
            for (String key : submitted.keySet()) {
                if (!allowed.containsKey(key))
                    throw rejected("Foram enviados campos que não estão configurados para este assunto.");
            }

            Map<String, String> answers = new LinkedHashMap<>();
            for (FieldDefinition definition : definitions) {
                String value = submitted.get(definition.key);
                if (value == null || value.isBlank()) {
                    if (definition.required)
                        throw rejected("Preencha os campos obrigatórios das pessoas envolvidas.");
                    continue;
                }
                String normalized = value.trim();
                if (normalized.length() > MAX_VALUE_LENGTH || containsUnsafeControl(normalized)
                        || !validType(definition.inputType, normalized))
                    throw rejected("Confira os campos de identificação das pessoas envolvidas.");
                answers.put(definition.key, normalized);
            }
            prepared.add(new PreparedParticipant(input.name().trim(), Map.copyOf(answers)));
        }
        return new PreparedRegistration(List.copyOf(definitions), List.copyOf(prepared));
    }

    public List<PublicParticipantField> publicFields(UUID organizationId, UUID subjectId) {
        return jdbc.query("""
                        SELECT field_key, label, input_type, required, display_order
                          FROM protocol_subject_participant_fields
                         WHERE organization_id=? AND subject_id=?
                         ORDER BY display_order, field_key
                        """, (rs, row) -> new PublicParticipantField(rs.getString("field_key"),
                        rs.getString("label"), rs.getString("input_type"), rs.getBoolean("required"),
                        rs.getInt("display_order")), organizationId, subjectId);
    }

    @Transactional
    public List<ParticipantInvitation> persist(UUID organizationId, UUID protocolId,
                                                PreparedRegistration registration) {
        for (FieldDefinition definition : registration.definitions) {
            jdbc.update("""
                            INSERT INTO protocol_participant_field_snapshots
                                (organization_id, protocol_id, field_key, label, input_type, required, display_order)
                            VALUES(?,?,?,?,?,?,?)
                            """, organizationId, protocolId, definition.key, definition.label,
                    definition.inputType, definition.required, definition.order);
        }
        List<ParticipantInvitation> invitations = new ArrayList<>();
        for (PreparedParticipant prepared : registration.participants) {
            UUID participantId = UUID.randomUUID();
            String token = TrackingTokens.generate();
            jdbc.update("""
                            INSERT INTO protocol_participants(id, organization_id, protocol_id, name, token_hash)
                            VALUES(?,?,?,?,?)
                            """, participantId, organizationId, protocolId, prepared.name, TrackingTokens.hash(token));
            for (Map.Entry<String, String> answer : prepared.answers.entrySet()) {
                FieldDefinition definition = registration.definitions.stream()
                        .filter(field -> field.key.equals(answer.getKey())).findFirst().orElseThrow();
                jdbc.update("""
                                INSERT INTO protocol_participant_answers
                                    (organization_id, protocol_id, participant_id, field_key, label, input_type, field_value)
                                VALUES(?,?,?,?,?,?,?)
                                """, organizationId, protocolId, participantId, definition.key, definition.label,
                        definition.inputType, answer.getValue());
            }
            invitations.add(new ParticipantInvitation(participantId, prepared.name, token));
        }
        return List.copyOf(invitations);
    }

    public List<ProtocolParticipantView> listForApplicant(UUID organizationId, UUID protocolId) {
        return jdbc.query("""
                        SELECT id, name, revoked_at, created_at FROM protocol_participants
                         WHERE organization_id=? AND protocol_id=? ORDER BY created_at, id
                        """, (rs, row) -> new ProtocolParticipantView(rs.getObject("id", UUID.class),
                        rs.getString("name"), rs.getObject("revoked_at", OffsetDateTime.class) == null,
                        rs.getObject("created_at", OffsetDateTime.class)), organizationId, protocolId);
    }

    public List<ParticipantIdentityValue> identityValues(UUID organizationId, UUID protocolId, UUID participantId) {
        if (participantId == null) return List.of();
        return jdbc.query("""
                        SELECT field_key, label, field_value
                          FROM protocol_participant_answers
                         WHERE organization_id=? AND protocol_id=? AND participant_id=?
                         ORDER BY field_key
                        """, (rs, row) -> new ParticipantIdentityValue(rs.getString("field_key"),
                        rs.getString("label"), rs.getString("field_value")), organizationId, protocolId, participantId);
    }

    @Transactional
    public void revoke(UUID organizationId, UUID protocolId, UUID participantId) {
        int updated = jdbc.update("""
                        UPDATE protocol_participants SET revoked_at=clock_timestamp()
                         WHERE organization_id=? AND protocol_id=? AND id=? AND revoked_at IS NULL
                        """, organizationId, protocolId, participantId);
        if (updated == 0) throw new ProtocolExceptions.NotFound();
    }

    private static boolean validType(String inputType, String value) {
        return switch (inputType) {
            case "EMAIL" -> value.length() <= 254 && EMAIL.matcher(value).matches();
            case "PHONE" -> PHONE.matcher(value).matches();
            case "DOCUMENT" -> DOCUMENT.matcher(value).matches();
            case "TEXT" -> true;
            default -> false;
        };
    }

    private static boolean containsUnsafeControl(String value) {
        return value.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint)
                && codePoint != '\n' && codePoint != '\r' && codePoint != '\t');
    }

    private static ProtocolAttachmentService.AttachmentRejected rejected(String message) {
        return new ProtocolAttachmentService.AttachmentRejected(message);
    }

    public record PreparedRegistration(List<FieldDefinition> definitions, List<PreparedParticipant> participants) {}
    public record PreparedParticipant(String name, Map<String, String> answers) {}
    public record FieldDefinition(String key, String label, String inputType, boolean required, int order) {}
}
