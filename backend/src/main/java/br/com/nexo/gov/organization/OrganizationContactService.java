package br.com.nexo.gov.organization;

import br.com.nexo.core.tenant.TenantContext;
import br.com.nexo.gov.protocol.ProtocolExceptions.NotFound;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static br.com.nexo.gov.organization.OrganizationContactDtos.*;

@Service
public class OrganizationContactService {
    private final JdbcTemplate jdbc;
    private final OrganizationAdminService organizationAdmin;
    private final Validator validator;

    public OrganizationContactService(JdbcTemplate jdbc, OrganizationAdminService organizationAdmin, Validator validator) {
        this.jdbc = jdbc;
        this.organizationAdmin = organizationAdmin;
        this.validator = validator;
    }

    public ContactManagementCapabilities capabilities(String actor) {
        UUID organizationId = TenantContext.requireTenantId();
        return new ContactManagementCapabilities(organizationAdmin.isOrganizationAdmin(actor, organizationId));
    }

    public ContactPage list(String actor, String query, boolean includeArchived, int page, int size) {
        UUID organizationId = TenantContext.requireTenantId();
        organizationAdmin.requireOrganizationAdmin(actor, organizationId);
        String search = query == null ? "" : query.trim();
        long offset = (long) page * size;
        String activeClause = includeArchived ? "" : " AND active=true";
        String searchClause = search.isEmpty() ? "" : " AND (position(lower(?) in lower(name))>0 OR position(lower(?) in lower(coalesce(email,'')))>0 OR position(lower(?) in lower(coalesce(phone,'')))>0)";
        Object[] searchParameters = search.isEmpty() ? new Object[0] : new Object[]{search, search, search};

        long total = jdbc.queryForObject("SELECT count(*) FROM organization_contacts WHERE organization_id=?" + activeClause + searchClause,
                Long.class, concat(new Object[]{organizationId}, searchParameters));
        List<ContactView> contacts = jdbc.query("""
                        SELECT id, contact_type, name, email, phone, active
                          FROM organization_contacts
                         WHERE organization_id=?
                        """ + activeClause + searchClause + " ORDER BY lower(name),id LIMIT ? OFFSET ?",
                (rs, row) -> new ContactView(rs.getObject("id", UUID.class), ContactType.valueOf(rs.getString("contact_type")),
                        rs.getString("name"), rs.getString("email"), rs.getString("phone"), rs.getBoolean("active")),
                concat(new Object[]{organizationId}, searchParameters, new Object[]{size, offset}));
        return new ContactPage(contacts, total, page, size);
    }

    @Transactional
    public ContactView create(String actor, ContactInput input) {
        UUID organizationId = TenantContext.requireTenantId();
        organizationAdmin.requireOrganizationAdmin(actor, organizationId);
        ContactInput normalized = normalize(input);
        validate(normalized);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO organization_contacts(id,organization_id,contact_type,name,email,phone,created_by,updated_by)
                        VALUES(?,?,?,?,?,?,?,?)
                        """, id, organizationId, normalized.type().name(), normalized.name(), normalized.email(),
                normalized.phone(), actor, actor);
        audit(organizationId, actor, "CONTACT_CREATED", id);
        return new ContactView(id, normalized.type(), normalized.name(), normalized.email(), normalized.phone(), true);
    }

    @Transactional
    public ContactView update(String actor, UUID id, ContactInput input) {
        UUID organizationId = TenantContext.requireTenantId();
        organizationAdmin.requireOrganizationAdmin(actor, organizationId);
        ContactInput normalized = normalize(input);
        validate(normalized);
        int changed = jdbc.update("""
                        UPDATE organization_contacts
                           SET contact_type=?,name=?,email=?,phone=?,updated_by=?,updated_at=now()
                         WHERE organization_id=? AND id=?
                        """, normalized.type().name(), normalized.name(), normalized.email(), normalized.phone(),
                actor, organizationId, id);
        if (changed == 0) throw new NotFound();
        audit(organizationId, actor, "CONTACT_UPDATED", id);
        return loadById(organizationId, id);
    }

    @Transactional
    public ContactActiveResult setActive(String actor, UUID id, boolean active) {
        UUID organizationId = TenantContext.requireTenantId();
        organizationAdmin.requireOrganizationAdmin(actor, organizationId);
        int changed = jdbc.update("""
                UPDATE organization_contacts
                   SET active=?,updated_by=?,updated_at=now()
                 WHERE organization_id=? AND id=? AND active<>?
                """, active, actor, organizationId, id, active);
        if (changed == 1) {
            audit(organizationId, actor, active ? "CONTACT_RESTORED" : "CONTACT_ARCHIVED", id);
            return new ContactActiveResult(id, active, true);
        }

        List<Boolean> current = jdbc.query("SELECT active FROM organization_contacts WHERE organization_id=? AND id=?",
                (rs, row) -> rs.getBoolean(1), organizationId, id);
        if (current.isEmpty()) throw new NotFound();
        return new ContactActiveResult(id, current.getFirst(), false);
    }

    public byte[] exportCsv(String actor, boolean includeArchived) {
        UUID organizationId = TenantContext.requireTenantId();
        organizationAdmin.requireOrganizationAdmin(actor, organizationId);
        List<ContactView> contacts = jdbc.query("""
                        SELECT id, contact_type, name, email, phone, active
                          FROM organization_contacts
                         WHERE organization_id=?
                        """ + (includeArchived ? "" : " AND active=true") + " ORDER BY lower(name),id",
                (rs, row) -> new ContactView(rs.getObject("id", UUID.class), ContactType.valueOf(rs.getString("contact_type")),
                        rs.getString("name"), rs.getString("email"), rs.getString("phone"), rs.getBoolean("active")),
                organizationId);
        return OrganizationContactCsv.export(contacts);
    }

    @Transactional
    public ContactImportResult importCsv(String actor, byte[] body) {
        UUID organizationId = TenantContext.requireTenantId();
        organizationAdmin.requireOrganizationAdmin(actor, organizationId);
        List<OrganizationContactCsv.ImportRow> rows = OrganizationContactCsv.parse(body);
        for (OrganizationContactCsv.ImportRow row : rows) {
            ContactInput input = new ContactInput(row.type(), row.name(), row.email(), row.phone());
            validate(input, row.rowNumber());
            if (row.id() != null && !exists(organizationId, row.id())) throw new NotFound();
        }

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        for (OrganizationContactCsv.ImportRow row : rows) {
            ContactInput input = new ContactInput(row.type(), row.name(), row.email(), row.phone());
            boolean active = row.active() == null ? row.id() == null || active(organizationId, row.id()) : row.active();
            if (row.id() == null) {
                UUID id = UUID.randomUUID();
                jdbc.update("""
                                INSERT INTO organization_contacts(id,organization_id,contact_type,name,email,phone,active,created_by,updated_by)
                                VALUES(?,?,?,?,?,?,?,?,?)
                                """, id, organizationId, input.type().name(), input.name(), input.email(), input.phone(), active, actor, actor);
                audit(organizationId, actor, "CONTACT_CREATED", id);
                created++;
            } else {
                ContactView old = loadById(organizationId, row.id());
                boolean differs = old.type() != input.type() || !old.name().equals(input.name())
                        || !equal(old.email(), input.email()) || !equal(old.phone(), input.phone()) || old.active() != active;
                if (!differs) {
                    unchanged++;
                    continue;
                }
                jdbc.update("""
                                UPDATE organization_contacts
                                   SET contact_type=?,name=?,email=?,phone=?,active=?,updated_by=?,updated_at=now()
                                 WHERE organization_id=? AND id=?
                                """, input.type().name(), input.name(), input.email(), input.phone(), active, actor, organizationId, row.id());
                String action = old.active() != active ? (active ? "CONTACT_RESTORED" : "CONTACT_ARCHIVED") : "CONTACT_UPDATED";
                audit(organizationId, actor, action, row.id());
                updated++;
            }
        }
        return new ContactImportResult(created, updated, unchanged, created + updated + unchanged);
    }

    private ContactInput normalize(ContactInput input) {
        return new ContactInput(input.type(), input.name().trim(), trimToNull(input.email()), trimToNull(input.phone()));
    }

    private void validate(ContactInput input) {
        validate(input, null);
    }

    private void validate(ContactInput input, Integer row) {
        Set<ConstraintViolation<ContactInput>> violations = validator.validate(input);
        if (!violations.isEmpty()) {
            String details = violations.stream().map(ConstraintViolation::getMessage).sorted().collect(Collectors.joining("; "));
            throw new OrganizationContactCsv.CsvException(row == null ? details : "Linha " + row + ": " + details);
        }
    }

    private ContactView loadById(UUID organizationId, UUID id) {
        List<ContactView> contacts = jdbc.query("""
                        SELECT id,contact_type,name,email,phone,active FROM organization_contacts
                         WHERE organization_id=? AND id=?
                        """, (rs, row) -> new ContactView(rs.getObject("id", UUID.class), ContactType.valueOf(rs.getString("contact_type")),
                rs.getString("name"), rs.getString("email"), rs.getString("phone"), rs.getBoolean("active")), organizationId, id);
        if (contacts.isEmpty()) throw new NotFound();
        return contacts.getFirst();
    }

    private boolean exists(UUID organizationId, UUID id) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM organization_contacts WHERE organization_id=? AND id=?",
                Integer.class, organizationId, id);
        return count != null && count > 0;
    }

    private boolean active(UUID organizationId, UUID id) {
        return jdbc.queryForObject("SELECT active FROM organization_contacts WHERE organization_id=? AND id=?",
                Boolean.class, organizationId, id);
    }

    private void audit(UUID organizationId, String actor, String action, UUID id) {
        String summary = switch (action) {
            case "CONTACT_CREATED" -> "Contato da organização criado";
            case "CONTACT_UPDATED" -> "Contato da organização atualizado";
            case "CONTACT_ARCHIVED" -> "Contato da organização arquivado";
            case "CONTACT_RESTORED" -> "Contato da organização reativado";
            default -> "Contato da organização alterado";
        };
        jdbc.update("""
                        INSERT INTO organization_audit_events(organization_id,actor_username,action,entity_type,entity_id,summary)
                        VALUES(?,? ,?,'ORGANIZATION_CONTACT',?,?)
                        """, organizationId, actor, action, id, summary);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean equal(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static Object[] concat(Object[]... arrays) {
        int size = 0;
        for (Object[] array : arrays) size += array.length;
        Object[] result = new Object[size];
        int offset = 0;
        for (Object[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }
}
