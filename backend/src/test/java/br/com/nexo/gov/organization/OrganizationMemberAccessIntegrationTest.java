package br.com.nexo.gov.organization;

import br.com.nexo.gov.security.LocalDemoData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Testcontainers
class OrganizationMemberAccessIntegrationTest {
    private static final String USER = "servidor";
    private static final String PASSWORD = "test-only-password-2026";
    private static final String SECTOR_ADMIN = "sector-admin";

    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.4-alpine");

    @DynamicPropertySource static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("NEXOGOV_DEMO_USERNAME", () -> USER);
        registry.add("NEXOGOV_DEMO_PASSWORD", () -> PASSWORD);
        registry.add("NEXOGOV_DEMO_MEMBER_USERNAME", () -> SECTOR_ADMIN);
        registry.add("NEXOGOV_DEMO_MEMBER_PASSWORD", () -> PASSWORD);
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired @Qualifier("tenantMembershipFilterSecurityChainOnly")
    FilterRegistrationBean<?> tenantMembershipFilterRegistration;

    private UUID organizationId;
    private UUID departmentId;

    @BeforeEach void createOrganization() {
        organizationId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
        jdbc.update("INSERT INTO organizations(id,slug,name) VALUES(?,?,?)", organizationId,
                "member-access-" + organizationId, "Organização de acesso de teste");
        jdbc.update("INSERT INTO departments(id,organization_id,name) VALUES(?,?,?)", departmentId,
                organizationId, "Atendimento de teste");
        addDepartmentMembership(USER, "STAFF");
        addOrganizationRole(USER, "ADMIN");
        jdbc.update("INSERT INTO user_accounts(username,active) VALUES(?,true) ON CONFLICT(username) DO NOTHING", USER);
        addProfile(USER, "Servidora administradora");
    }

    @Test void listsMembersWithinValidatedOrganizationAndRequiresOrganizationAdminRole() {
        addDepartmentMembership("sector-admin", "ADMIN");
        addOrganizationRole("sector-admin", "ADMIN");
        jdbc.update("UPDATE organization_memberships SET active=false WHERE organization_id=? AND username=? AND role_code='ADMIN'",
                organizationId, "sector-admin");
        addProfile("sector-admin", "Administradora de setor");
        addOrganizationRole("org-admin", "ADMIN");
        addDepartmentMembership("org-admin", "STAFF");
        addProfile("org-admin", "Administrador da organização");

        UUID otherOrganization = createOtherOrganization();
        UUID otherDepartment = UUID.randomUUID();
        jdbc.update("INSERT INTO departments(id,organization_id,name) VALUES(?,?,?)", otherDepartment, otherOrganization, "Outro setor");
        jdbc.update("INSERT INTO staff_memberships(username,organization_id,department_id,role_code) VALUES(?,?,?,'STAFF')",
                "foreign-member", otherOrganization, otherDepartment);

        ResponseEntity<List<Map<String, Object>>> response = http.exchange("/api/v1/staff/organization/members",
                HttpMethod.GET, new HttpEntity<>(staffHeaders(organizationId)), new ParameterizedTypeReference<>() {});
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull().hasSize(3)
                .anySatisfy(member -> assertThat(member).containsEntry("username", "sector-admin")
                        .containsEntry("departmentRoles", List.of("ADMIN"))
                        .containsEntry("organizationRoles", List.of())
                        .containsEntry("accessStatus", "ACTIVE"))
                .anySatisfy(member -> assertThat(member).containsEntry("username", "org-admin")
                        .containsEntry("organizationRoles", List.of("ADMIN")));
        assertThat(response.getBody()).noneMatch(member -> "foreign-member".equals(member.get("username")));

        ResponseEntity<String> crossTenant = http.exchange("/api/v1/staff/organization/members", HttpMethod.GET,
                new HttpEntity<>(staffHeaders(otherOrganization)), String.class);
        assertThat(crossTenant.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // An ADMIN role in a department is not organization administration.
        jdbc.update("DELETE FROM organization_memberships WHERE username=? AND organization_id=?", USER, organizationId);
        ResponseEntity<String> sectorRoleOnly = http.exchange("/api/v1/staff/organization/members", HttpMethod.GET,
                new HttpEntity<>(staffHeaders(organizationId)), String.class);
        assertThat(sectorRoleOnly.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        addOrganizationRole(USER, "ADMIN");
    }

    @Test void suspendsAndReactivatesAccessWithoutChangingAccountsMembershipsOrHistory() {
        addOrganizationRole("target-admin", "ADMIN");
        addDepartmentMembership("target-admin", "ADMIN");
        addProfile("target-admin", "Administradora de outro setor");
        UUID protocolId = addAuthoredProtocolEvent(USER);

        List<String> actorDepartmentMembershipsBefore = jdbc.query("""
                SELECT department_id::text || ':' || role_code || ':' || active
                  FROM staff_memberships WHERE organization_id=? AND username=? ORDER BY department_id,role_code
                """, (rs, row) -> rs.getString(1), organizationId, USER);
        List<String> actorOrganizationMembershipsBefore = jdbc.query("""
                SELECT role_code || ':' || active FROM organization_memberships
                 WHERE organization_id=? AND username=? ORDER BY role_code
                """, (rs, row) -> rs.getString(1), organizationId, USER);
        boolean globalAccountActiveBefore = jdbc.queryForObject("SELECT active FROM user_accounts WHERE username=?", Boolean.class, USER);
        String profileNameBefore = jdbc.queryForObject("SELECT full_name FROM staff_profiles WHERE organization_id=? AND username=?",
                String.class, organizationId, USER);
        Map<String, Object> authoredEventBefore = jdbc.queryForMap("""
                SELECT actor,action,from_status,to_status,department_id,summary,visibility,created_at
                  FROM protocol_events WHERE organization_id=? AND protocol_id=? AND actor=?
                """, organizationId, protocolId, USER);

        ResponseEntity<Map> suspended = putAccess("target-admin", false,
                Map.of("active", false, "organizationId", UUID.randomUUID().toString()));
        assertThat(suspended.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(suspended.getBody()).containsEntry("username", "target-admin")
                .containsEntry("accessStatus", "SUSPENDED").containsEntry("changed", true);
        assertThat(jdbc.queryForObject("SELECT active FROM staff_memberships WHERE organization_id=? AND username=?",
                Boolean.class, organizationId, "target-admin")).isTrue();
        assertThat(jdbc.queryForObject("SELECT active FROM organization_memberships WHERE organization_id=? AND username=? AND role_code='ADMIN'",
                Boolean.class, organizationId, "target-admin")).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_member_access_status WHERE organization_id=? AND username=? AND access_active=false",
                Integer.class, organizationId, "target-admin")).isEqualTo(1);

        ResponseEntity<Map> repeatedSuspend = putAccess("target-admin", false, Map.of("active", false));
        assertThat(repeatedSuspend.getBody()).containsEntry("changed", false);
        assertThat(countAccessAudit("target-admin")).isEqualTo(1);

        ResponseEntity<Map> reactivated = putAccess("target-admin", true, Map.of("active", true));
        assertThat(reactivated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reactivated.getBody()).containsEntry("accessStatus", "ACTIVE").containsEntry("changed", true);
        assertThat(jdbc.queryForObject("SELECT access_active FROM organization_member_access_status WHERE organization_id=? AND username=?",
                Boolean.class, organizationId, "target-admin")).isTrue();
        assertThat(countAccessAudit("target-admin")).isEqualTo(2);

        // The same authenticated credentials are denied on the next request even though
        // the department membership remains active; this API uses stateless Basic in local.
        ResponseEntity<Map> selfSuspended = putAccess(USER, false, Map.of("active", false));
        assertThat(selfSuspended.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> nextRequest = http.exchange("/api/v1/staff/organization/members", HttpMethod.GET,
                new HttpEntity<>(staffHeaders(organizationId)), String.class);
        assertThat(nextRequest.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("SELECT active FROM staff_memberships WHERE organization_id=? AND username=?",
                Boolean.class, organizationId, USER)).isTrue();
        assertThat(jdbc.queryForObject("SELECT active FROM organization_memberships WHERE organization_id=? AND username=? AND role_code='ADMIN'",
                Boolean.class, organizationId, USER)).isTrue();

        // Restore fixture access as the other administrator would through the reactivation API.
        jdbc.update("UPDATE organization_member_access_status SET access_active=true,updated_by='test-fixture',updated_at=now() " +
                "WHERE organization_id=? AND username=?", organizationId, USER);
        ResponseEntity<String> afterReactivation = http.exchange("/api/v1/staff/organization/members", HttpMethod.GET,
                new HttpEntity<>(staffHeaders(organizationId)), String.class);
        assertThat(afterReactivation.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(jdbc.queryForObject("SELECT active FROM user_accounts WHERE username=?", Boolean.class, USER))
                .isEqualTo(globalAccountActiveBefore);
        assertThat(jdbc.query("""
                SELECT department_id::text || ':' || role_code || ':' || active
                  FROM staff_memberships WHERE organization_id=? AND username=? ORDER BY department_id,role_code
                """, (rs, row) -> rs.getString(1), organizationId, USER)).containsExactlyElementsOf(actorDepartmentMembershipsBefore);
        assertThat(jdbc.query("""
                SELECT role_code || ':' || active FROM organization_memberships
                 WHERE organization_id=? AND username=? ORDER BY role_code
                """, (rs, row) -> rs.getString(1), organizationId, USER)).containsExactlyElementsOf(actorOrganizationMembershipsBefore);
        assertThat(jdbc.queryForObject("SELECT full_name FROM staff_profiles WHERE organization_id=? AND username=?",
                String.class, organizationId, USER)).isEqualTo(profileNameBefore);
        assertThat(jdbc.queryForMap("""
                SELECT actor,action,from_status,to_status,department_id,summary,visibility,created_at
                  FROM protocol_events WHERE organization_id=? AND protocol_id=? AND actor=?
                """, organizationId, protocolId, USER)).isEqualTo(authoredEventBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_audit_events WHERE organization_id=? AND action='ORGANIZATION_MEMBER_ACCESS_SUSPENDED'",
                Integer.class, organizationId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_audit_events WHERE organization_id=? AND action='ORGANIZATION_MEMBER_ACCESS_REACTIVATED'",
                Integer.class, organizationId)).isEqualTo(1); // Fixture restoration changes only the access-state row.
    }

    @Test void preventsSuspendingTheLastActiveOrganizationAdministrator() {
        ResponseEntity<String> response = putAccess(USER, false, Map.of("active", false), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("sem administrador ativo");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_member_access_status WHERE organization_id=? AND username=?",
                Integer.class, organizationId, USER)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_audit_events WHERE organization_id=? AND entity_type='ORGANIZATION_MEMBER_ACCESS'",
                Integer.class, organizationId)).isZero();
    }

    @Test void departmentAdministratorCannotSuspendOrganizationMember() {
        addDepartmentMembership(SECTOR_ADMIN, "ADMIN");
        addDepartmentMembership("target-member", "STAFF");

        ResponseEntity<String> response = putAccessAs(SECTOR_ADMIN, "target-member", false,
                Map.of("active", false), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_member_access_status WHERE organization_id=? AND username=?",
                Integer.class, organizationId, "target-member")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_audit_events WHERE organization_id=? AND entity_type='ORGANIZATION_MEMBER_ACCESS'",
                Integer.class, organizationId)).isZero();
        assertThat(jdbc.queryForObject("SELECT active FROM staff_memberships WHERE organization_id=? AND username=? AND role_code='STAFF'",
                Boolean.class, organizationId, "target-member")).isTrue();
    }

    @Test void organizationAdministratorCannotSuspendMemberFromAnotherTenant() {
        UUID otherOrganization = createOtherOrganization();
        UUID otherDepartment = UUID.randomUUID();
        jdbc.update("INSERT INTO departments(id,organization_id,name) VALUES(?,?,?)", otherDepartment,
                otherOrganization, "Setor externo");
        jdbc.update("INSERT INTO staff_memberships(username,organization_id,department_id,role_code) VALUES(?,?,?,'STAFF')",
                "foreign-member", otherOrganization, otherDepartment);

        ResponseEntity<String> response = putAccess("foreign-member", false, Map.of("active", false), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_member_access_status WHERE username=?",
                Integer.class, "foreign-member")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_audit_events WHERE entity_type='ORGANIZATION_MEMBER_ACCESS' " +
                        "AND summary LIKE ?", Integer.class, "%foreign-member")).isZero();
        assertThat(jdbc.queryForObject("SELECT active FROM staff_memberships WHERE organization_id=? AND username=?",
                Boolean.class, otherOrganization, "foreign-member")).isTrue();
    }

    @Test void concurrentSuspensionsCannotRemoveBothActiveOrganizationAdministrators() throws Exception {
        addOrganizationRole("second-admin", "ADMIN");
        addDepartmentMembership("second-admin", "STAFF");

        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<ResponseEntity<String>> suspendActor = CompletableFuture.supplyAsync(() -> {
            await(start);
            return putAccess(USER, false, Map.of("active", false), String.class);
        });
        CompletableFuture<ResponseEntity<String>> suspendSecond = CompletableFuture.supplyAsync(() -> {
            await(start);
            return putAccess("second-admin", false, Map.of("active", false), String.class);
        });
        start.countDown();

        List<ResponseEntity<String>> responses = List.of(suspendActor.get(20, TimeUnit.SECONDS), suspendSecond.get(20, TimeUnit.SECONDS));
        long successfulChanges = responses.stream().filter(response -> response.getStatusCode() == HttpStatus.OK).count();
        assertThat(successfulChanges).isEqualTo(1);
        assertThat(responses.stream().filter(response -> response.getStatusCode() != HttpStatus.OK)
                .map(ResponseEntity::getStatusCode).toList())
                .hasSize(1).allSatisfy(status -> assertThat(status)
                        .isIn(HttpStatus.FORBIDDEN, HttpStatus.CONFLICT));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM organization_memberships om
                 WHERE om.organization_id=? AND om.role_code='ADMIN' AND om.active=true
                   AND NOT EXISTS (SELECT 1 FROM organization_member_access_status s
                                    WHERE s.organization_id=om.organization_id AND s.username=om.username
                                      AND s.access_active=false)
                """, Integer.class, organizationId)).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_audit_events WHERE organization_id=? AND action='ORGANIZATION_MEMBER_ACCESS_SUSPENDED'",
                Integer.class, organizationId)).isEqualTo(successfulChanges);
        assertThat(tenantMembershipFilterRegistration.isEnabled()).isFalse();
    }

    private void addDepartmentMembership(String username, String role) {
        jdbc.update("INSERT INTO staff_memberships(username,organization_id,department_id,role_code) VALUES(?,?,?,?)",
                username, organizationId, departmentId, role);
    }

    private void addOrganizationRole(String username, String role) {
        jdbc.update("INSERT INTO organization_memberships(username,organization_id,role_code) VALUES(?,?,?)",
                username, organizationId, role);
    }

    private void addProfile(String username, String fullName) {
        jdbc.update("INSERT INTO staff_profiles(organization_id,username,full_name,registration_date_source,last_online_at) " +
                        "VALUES(?,?,?,'UNKNOWN',now()) ON CONFLICT(organization_id,username) DO UPDATE SET full_name=EXCLUDED.full_name,last_online_at=now()",
                organizationId, username, fullName);
    }

    private UUID createOtherOrganization() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO organizations(id,slug,name) VALUES(?,?,?)", id, "foreign-" + id, "Outra organização");
        return id;
    }

    private UUID addAuthoredProtocolEvent(String actor) {
        UUID protocolId = UUID.randomUUID();
        String number = "TEST-" + protocolId.toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO protocols(id,organization_id,protocol_number,subject,applicant_name,applicant_email,
                                     initial_message,status,assigned_department_id,tracking_token_hash)
                VALUES(?,?,?,?,?,?,?,'OPEN',?,?)
                """, protocolId, organizationId, number, "Assunto fictício", "Requerente fictício", "test@example.test",
                "Mensagem fictícia", departmentId, UUID.randomUUID().toString().replace("-", "") + "0123456789abcdef");
        jdbc.update("""
                INSERT INTO protocol_events(organization_id,protocol_id,actor,action,from_status,to_status,
                                           department_id,summary,visibility)
                VALUES(?,?,?,'RECEIVED','FORWARDED','RECEIVED',?, 'Histórico de teste', 'INTERNAL')
                """, organizationId, protocolId, actor, departmentId);
        return protocolId;
    }

    private int countAccessAudit(String username) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM organization_audit_events
                 WHERE organization_id=? AND entity_type='ORGANIZATION_MEMBER_ACCESS'
                   AND summary LIKE ?
                """, Integer.class, organizationId, "%" + username);
    }

    private HttpHeaders staffHeaders(UUID tenant) {
        return staffHeaders(tenant, USER, PASSWORD);
    }

    private HttpHeaders staffHeaders(UUID tenant, String actor, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(actor, password);
        headers.set("X-Organization-Id", tenant.toString());
        return headers;
    }

    private ResponseEntity<Map> putAccess(String username, boolean active, Map<String, ?> body) {
        return putAccess(username, active, body, Map.class);
    }

    private <T> ResponseEntity<T> putAccess(String username, boolean active, Map<String, ?> body, Class<T> responseType) {
        return putAccessAs(USER, username, active, body, responseType);
    }

    private <T> ResponseEntity<T> putAccessAs(String actor, String username, boolean active, Map<String, ?> body,
                                              Class<T> responseType) {
        Map<String, Object> requestBody = new HashMap<>(body);
        requestBody.put("active", active);
        return http.exchange("/api/v1/staff/organization/members/{username}/access", HttpMethod.PUT,
                new HttpEntity<>(requestBody, staffHeaders(organizationId, actor, PASSWORD)), responseType, username);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concorrência de teste expirou.");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concorrência de teste interrompida.", interrupted);
        }
    }
}
