package br.com.nexo.gov.organization;

import br.com.nexo.core.tenant.TenantContext;
import br.com.nexo.gov.protocol.ProtocolExceptions.ForbiddenAction;
import br.com.nexo.gov.protocol.ProtocolExceptions.NotFound;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.sql.Array;
import java.sql.SQLException;

import static br.com.nexo.gov.organization.DepartmentAdminDtos.*;

@Service
public class OrganizationAdminService {
    private final JdbcTemplate jdbc;

    public OrganizationAdminService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<OrganizationMemberView> organizationMembers(String actor) {
        UUID organizationId = TenantContext.requireTenantId();
        requireOrganizationAdmin(actor, organizationId);
        return jdbc.query("""
                        WITH member_roles AS (
                            SELECT username, role_code, active, 'ORGANIZATION' AS scope
                              FROM organization_memberships WHERE organization_id=?
                            UNION ALL
                            SELECT username, role_code, active, 'DEPARTMENT' AS scope
                              FROM staff_memberships WHERE organization_id=?
                        ), member_summary AS (
                            SELECT username,
                                   COALESCE(array_agg(DISTINCT role_code ORDER BY role_code)
                                       FILTER (WHERE scope='ORGANIZATION' AND active=true), ARRAY[]::varchar[]) AS organization_roles,
                                   COALESCE(array_agg(DISTINCT role_code ORDER BY role_code)
                                       FILTER (WHERE scope='DEPARTMENT' AND active=true), ARRAY[]::varchar[]) AS department_roles,
                                   bool_or(active) AS has_active_membership
                              FROM member_roles GROUP BY username
                        )
                        SELECT m.username, p.full_name, p.position_title,
                               CASE WHEN access_status.access_active=false THEN 'SUSPENDED'
                                    WHEN m.has_active_membership THEN 'ACTIVE' ELSE 'INACTIVE' END AS access_status,
                               m.organization_roles, m.department_roles
                          FROM member_summary m
                          LEFT JOIN staff_profiles p ON p.organization_id=? AND p.username=m.username
                          LEFT JOIN organization_member_access_status access_status
                                 ON access_status.organization_id=? AND access_status.username=m.username
                         ORDER BY lower(COALESCE(p.full_name,m.username)), m.username
                        """, (rs, row) -> new OrganizationMemberView(
                        rs.getString("username"), rs.getString("full_name"), rs.getString("position_title"),
                        rs.getString("access_status"), stringArray(rs.getArray("organization_roles")),
                        stringArray(rs.getArray("department_roles"))),
                organizationId, organizationId, organizationId, organizationId);
    }

    @Transactional
    public OrganizationMemberAccessView setOrganizationMemberAccess(String actor, String username, boolean active) {
        UUID organizationId = TenantContext.requireTenantId();
        String target = username == null ? "" : username.trim();

        // Serialize all access changes in an organization so concurrent admin
        // suspensions cannot each observe the same last two administrators.
        List<UUID> lockedOrganization = jdbc.query(
                "SELECT id FROM organizations WHERE id=? AND active=true FOR UPDATE",
                (rs, row) -> rs.getObject("id", UUID.class), organizationId);
        if (lockedOrganization.isEmpty()) throw new NotFound();
        requireOrganizationAdmin(actor, organizationId);
        if (!isMember(target, organizationId)) throw new NotFound();

        MemberAccessState current = jdbc.query("""
                        SELECT id, access_active FROM organization_member_access_status
                         WHERE organization_id=? AND username=? FOR UPDATE
                        """, (rs, row) -> new MemberAccessState(rs.getObject("id", UUID.class), rs.getBoolean("access_active")),
                organizationId, target).stream().findFirst().orElse(null);
        boolean currentlyActive = current == null || current.active;
        if (currentlyActive == active) return new OrganizationMemberAccessView(target, memberAccessStatus(target, organizationId), false);

        if (!active && hasActiveOrganizationAdminRole(target, organizationId)
                && countOtherActiveOrganizationAdmins(target, organizationId) == 0) {
            throw new LastActiveOrganizationAdministrator();
        }

        UUID accessStatusId = jdbc.queryForObject("""
                        INSERT INTO organization_member_access_status
                            (organization_id,username,access_active,created_by,updated_by)
                        VALUES(?,?,?,?,?)
                        ON CONFLICT (organization_id,username) DO UPDATE SET
                            access_active=EXCLUDED.access_active, updated_by=EXCLUDED.updated_by, updated_at=now()
                        RETURNING id
                        """, UUID.class, organizationId, target, active, actor, actor);
        String action = active ? "ORGANIZATION_MEMBER_ACCESS_REACTIVATED" : "ORGANIZATION_MEMBER_ACCESS_SUSPENDED";
        String summary = active ? "Acesso à organização reativado para " + target : "Acesso à organização suspenso para " + target;
        jdbc.update("""
                        INSERT INTO organization_audit_events(organization_id,actor_username,action,entity_type,entity_id,summary)
                        VALUES(?,?,?,'ORGANIZATION_MEMBER_ACCESS',?,?)
                        """, organizationId, actor, action, accessStatusId, summary);
        return new OrganizationMemberAccessView(target, memberAccessStatus(target, organizationId), true);
    }

    public DepartmentManagementCapabilities departmentManagementCapabilities(String actor) {
        UUID organizationId = TenantContext.requireTenantId();
        return new DepartmentManagementCapabilities(isOrganizationAdmin(actor, organizationId));
    }

    public List<OrganogramNode> organogram() {
        UUID organizationId = TenantContext.requireTenantId();
        Map<UUID, MutableNode> nodes = new LinkedHashMap<>();
        jdbc.query("""
                        WITH RECURSIVE department_tree AS (
                            SELECT d.id, d.parent_department_id, d.name, ARRAY[d.id] AS path
                              FROM departments d
                             WHERE d.organization_id=? AND d.active=true
                               AND (d.parent_department_id IS NULL OR NOT EXISTS (
                                   SELECT 1 FROM departments parent
                                    WHERE parent.organization_id=d.organization_id
                                      AND parent.id=d.parent_department_id AND parent.active=true))
                            UNION ALL
                            SELECT child.id, child.parent_department_id, child.name, tree.path || child.id
                              FROM departments child
                              JOIN department_tree tree ON tree.id=child.parent_department_id
                             WHERE child.organization_id=? AND child.active=true
                               AND NOT child.id=ANY(tree.path)
                        )
                        SELECT id, parent_department_id, name FROM department_tree ORDER BY name, id
                        """,
                rs -> {
                    UUID id = rs.getObject("id", UUID.class);
                    UUID parentId = rs.getObject("parent_department_id", UUID.class);
                    nodes.put(id, new MutableNode(id, rs.getString("name"), parentId));
                }, organizationId, organizationId);

        jdbc.query("""
                        SELECT sm.department_id, sm.username, sm.role_code,
                               sp.full_name, sp.position_title, sp.registration_date,
                               sp.registration_date_source, sp.last_online_at,
                               CASE WHEN sp.last_online_at IS NULL THEN 'NOT_OBSERVED'
                                    WHEN sp.last_online_at >= now() - interval '2 minutes' THEN 'ONLINE'
                                    ELSE 'OFFLINE' END AS presence_status
                          FROM staff_memberships sm
                          LEFT JOIN staff_profiles sp
                            ON sp.organization_id=sm.organization_id AND sp.username=sm.username
                         WHERE sm.organization_id=? AND sm.active=true
                         ORDER BY sm.username,sm.role_code
                        """,
                rs -> {
                    MutableNode node = nodes.get(rs.getObject("department_id", UUID.class));
                    if (node != null) {
                        Timestamp lastOnline = rs.getTimestamp("last_online_at");
                        java.sql.Date registrationDate = rs.getDate("registration_date");
                        node.staff.add(new StaffAssignmentView(rs.getString("username"), rs.getString("full_name"),
                                rs.getString("position_title"), registrationDate == null ? null : registrationDate.toLocalDate(),
                                rs.getString("registration_date_source"), lastOnline == null ? null : lastOnline.toInstant(),
                                rs.getString("presence_status"), rs.getString("role_code")));
                    }
                }, organizationId);

        List<MutableNode> roots = new ArrayList<>();
        for (MutableNode node : nodes.values()) {
            MutableNode parent = node.parentId == null ? null : nodes.get(node.parentId);
            if (parent == null) roots.add(node);
            else parent.children.add(node);
        }
        Map<UUID, OrganogramNode> views = new HashMap<>();
        Deque<BuildFrame> pending = new ArrayDeque<>();
        for (MutableNode root : roots) pending.push(new BuildFrame(root, false));
        while (!pending.isEmpty()) {
            BuildFrame frame = pending.pop();
            if (!frame.expanded()) {
                pending.push(new BuildFrame(frame.node(), true));
                for (int index = frame.node().children.size() - 1; index >= 0; index--) {
                    pending.push(new BuildFrame(frame.node().children.get(index), false));
                }
            } else {
                List<OrganogramNode> childViews = frame.node().children.stream()
                        .map(child -> views.get(child.id))
                        .toList();
                views.put(frame.node().id, new OrganogramNode(frame.node().id, frame.node().name,
                        frame.node().parentId, List.copyOf(frame.node().staff), childViews));
            }
        }
        return roots.stream().map(root -> views.get(root.id)).toList();
    }

    @Transactional
    public DepartmentView createDepartment(String actor, DepartmentCreateRequest request) {
        UUID organizationId = TenantContext.requireTenantId();
        if (!isOrganizationAdmin(actor, organizationId)) throw new ForbiddenAction();

        String name = request.name().trim();
        if (existsByName(organizationId, name)) throw new DepartmentNameConflict();
        UUID parentId = request.parentDepartmentId();
        String parentName = null;
        if (parentId != null) {
            List<String> parents = jdbc.query("SELECT name FROM departments WHERE organization_id=? AND id=? AND active=true",
                    (rs, row) -> rs.getString("name"), organizationId, parentId);
            if (parents.isEmpty()) throw new NotFound();
            parentName = parents.get(0);
        }

        UUID departmentId = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO departments(id,organization_id,name,parent_department_id) VALUES(?,?,?,?)",
                    departmentId, organizationId, name, parentId);
        } catch (DataIntegrityViolationException duplicate) {
            throw new DepartmentNameConflict();
        }

        jdbc.update("INSERT INTO organization_audit_events(organization_id,actor_username,action,entity_type,entity_id,summary) " +
                        "VALUES(?,?,'DEPARTMENT_CREATED','DEPARTMENT',?,?)",
                organizationId, actor, departmentId, parentName == null ? "Setor criado: " + name :
                        "Subsetor criado: " + name + " em " + parentName);
        return new DepartmentView(departmentId, name);
    }

    @Transactional
    public StaffProfileView updateStaffProfile(String actor, String username, StaffProfileUpdateRequest request) {
        UUID organizationId = TenantContext.requireTenantId();
        if (!isOrganizationAdmin(actor, organizationId)) throw new ForbiddenAction();

        String targetUsername = username == null ? "" : username.trim();
        Integer memberships = jdbc.queryForObject("""
                        SELECT count(*) FROM staff_memberships
                         WHERE organization_id=? AND username=? AND active=true
                        """, Integer.class, organizationId, targetUsername);
        if (memberships == null || memberships == 0) throw new NotFound();

        String fullName = request.fullName().trim();
        String position = request.positionTitle() == null || request.positionTitle().isBlank()
                ? null : request.positionTitle().trim();
        LocalDate registrationDate = request.registrationDate();
        String registrationDateSource = registrationDate == null ? "UNKNOWN" : "IMPORTED";
        jdbc.update("""
                        INSERT INTO staff_profiles(organization_id,username,full_name,position_title,registration_date,registration_date_source)
                        VALUES(?,?,?,?,?,?)
                        ON CONFLICT (organization_id,username) DO UPDATE SET
                            full_name=EXCLUDED.full_name,
                            position_title=EXCLUDED.position_title,
                            registration_date=EXCLUDED.registration_date,
                            registration_date_source=EXCLUDED.registration_date_source,
                            updated_at=now()
                        """, organizationId, targetUsername, fullName, position, registrationDate, registrationDateSource);

        UUID profileId = jdbc.queryForObject("SELECT id FROM staff_profiles WHERE organization_id=? AND username=?",
                UUID.class, organizationId, targetUsername);
        jdbc.update("""
                        INSERT INTO organization_audit_events(organization_id,actor_username,action,entity_type,entity_id,summary)
                        VALUES(?,?,'STAFF_PROFILE_UPDATED','STAFF_PROFILE',?,'Perfil funcional de servidor atualizado')
                        """, organizationId, actor, profileId);
        return new StaffProfileView(targetUsername, fullName, position, registrationDate, registrationDateSource);
    }

    public PresenceHeartbeatView heartbeat() {
        return new PresenceHeartbeatView("ONLINE");
    }

    boolean isOrganizationAdmin(String actor, UUID organizationId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM organization_memberships om WHERE username=? AND organization_id=? AND role_code='ADMIN' AND active=true " +
                        "AND NOT EXISTS (SELECT 1 FROM organization_member_access_status s WHERE s.organization_id=om.organization_id " +
                        "AND s.username=om.username AND s.access_active=false)",
                Integer.class, actor, organizationId);
        return count != null && count > 0;
    }

    void requireOrganizationAdmin(String actor, UUID organizationId) {
        if (!isOrganizationAdmin(actor, organizationId)) throw new ForbiddenAction();
    }

    private boolean isMember(String username, UUID organizationId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT username FROM organization_memberships WHERE organization_id=? AND username=?
                    UNION
                    SELECT username FROM staff_memberships WHERE organization_id=? AND username=?
                ) member_records
                """, Integer.class, organizationId, username, organizationId, username);
        return count != null && count > 0;
    }

    private boolean hasActiveOrganizationAdminRole(String username, UUID organizationId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM organization_memberships
                 WHERE organization_id=? AND username=? AND role_code='ADMIN' AND active=true
                """, Integer.class, organizationId, username);
        return count != null && count > 0;
    }

    private int countOtherActiveOrganizationAdmins(String username, UUID organizationId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM organization_memberships om
                 WHERE om.organization_id=? AND om.role_code='ADMIN' AND om.active=true AND om.username<>?
                   AND NOT EXISTS (SELECT 1 FROM organization_member_access_status s
                                    WHERE s.organization_id=om.organization_id AND s.username=om.username
                                      AND s.access_active=false)
                """, Integer.class, organizationId, username);
        return count == null ? 0 : count;
    }

    private String memberAccessStatus(String username, UUID organizationId) {
        return jdbc.queryForObject("""
                SELECT CASE
                           WHEN access_status.access_active=false THEN 'SUSPENDED'
                           WHEN EXISTS (SELECT 1 FROM organization_memberships om WHERE om.organization_id=? AND om.username=? AND om.active=true)
                             OR EXISTS (SELECT 1 FROM staff_memberships sm WHERE sm.organization_id=? AND sm.username=? AND sm.active=true)
                           THEN 'ACTIVE' ELSE 'INACTIVE'
                       END
                  FROM (SELECT 1) seed
                  LEFT JOIN organization_member_access_status access_status
                    ON access_status.organization_id=? AND access_status.username=?
                """, String.class, organizationId, username, organizationId, username, organizationId, username);
    }

    private static List<String> stringArray(Array sqlArray) {
        if (sqlArray == null) return List.of();
        try {
            Object value = sqlArray.getArray();
            if (value instanceof String[] strings) return List.of(strings);
            Object[] values = (Object[]) value;
            List<String> result = new ArrayList<>(values.length);
            for (Object item : values) if (item != null) result.add(item.toString());
            return List.copyOf(result);
        } catch (SQLException failure) {
            throw new IllegalStateException("Não foi possível ler os papéis do servidor.", failure);
        } finally {
            try { sqlArray.free(); } catch (SQLException ignored) { }
        }
    }

    private boolean existsByName(UUID organizationId, String name) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM departments WHERE organization_id=? AND lower(btrim(name))=lower(btrim(?))",
                Integer.class, organizationId, name);
        return count != null && count > 0;
    }

    public static class DepartmentNameConflict extends RuntimeException {
        public DepartmentNameConflict() {
            super("Já existe um setor com esse nome nesta organização.");
        }
    }

    public static class LastActiveOrganizationAdministrator extends RuntimeException {
        public LastActiveOrganizationAdministrator() {
            super("A suspensão deixaria a organização sem administrador ativo.");
        }
    }

    private record MemberAccessState(UUID id, boolean active) {}

    private static final class MutableNode {
        private final UUID id;
        private final String name;
        private final UUID parentId;
        private final List<StaffAssignmentView> staff = new ArrayList<>();
        private final List<MutableNode> children = new ArrayList<>();

        private MutableNode(UUID id, String name, UUID parentId) {
            this.id = id;
            this.name = name;
            this.parentId = parentId;
        }

    }

    private record BuildFrame(MutableNode node, boolean expanded) {}
}
