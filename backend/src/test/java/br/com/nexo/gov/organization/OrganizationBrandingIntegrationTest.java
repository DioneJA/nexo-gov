package br.com.nexo.gov.organization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Testcontainers
class OrganizationBrandingIntegrationTest {
    private static final String ADMIN = "branding-admin";
    private static final String SECTOR_ADMIN = "branding-sector-admin";
    private static final String PASSWORD = "branding-test-password-2026";

    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.4-alpine");

    @DynamicPropertySource static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("NEXOGOV_DEMO_USERNAME", () -> ADMIN);
        registry.add("NEXOGOV_DEMO_PASSWORD", () -> PASSWORD);
        registry.add("NEXOGOV_DEMO_MEMBER_USERNAME", () -> SECTOR_ADMIN);
        registry.add("NEXOGOV_DEMO_MEMBER_PASSWORD", () -> PASSWORD);
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    private UUID organizationId;
    private UUID otherOrganizationId;
    private UUID departmentId;
    private String slug;

    @BeforeEach void createOrganizations() {
        organizationId = UUID.randomUUID();
        otherOrganizationId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
        slug = "branding-test-" + organizationId;
        jdbc.update("INSERT INTO organizations(id,slug,name) VALUES(?,?,?)", organizationId, slug, "Município de origem");
        jdbc.update("INSERT INTO organizations(id,slug,name) VALUES(?,?,?)", otherOrganizationId,
                "branding-other-" + otherOrganizationId, "Município protegido");
        jdbc.update("INSERT INTO departments(id,organization_id,name) VALUES(?,?,?)", departmentId, organizationId, "Atendimento");
        jdbc.update("INSERT INTO user_accounts(username,active) VALUES(?,true) ON CONFLICT(username) DO NOTHING", ADMIN);
        jdbc.update("INSERT INTO user_accounts(username,active) VALUES(?,true) ON CONFLICT(username) DO NOTHING", SECTOR_ADMIN);
        jdbc.update("INSERT INTO organization_memberships(username,organization_id,role_code) VALUES(?,?,'ADMIN')", ADMIN, organizationId);
        jdbc.update("INSERT INTO staff_memberships(username,organization_id,department_id,role_code) VALUES(?,?,?,'ADMIN')",
                SECTOR_ADMIN, organizationId, departmentId);
    }

    @Test void publishesTenantScopedBrandingWithPublicProjectionAndAuditOnlyOnChange() {
        ResponseEntity<Map> publicDefault = http.getForEntity(publicPath(), Map.class);
        assertThat(publicDefault.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertNoStore(publicDefault.getHeaders());
        assertThat(publicDefault.getBody()).containsEntry("organizationSlug", slug)
                .containsEntry("displayName", "Município de origem").containsEntry("accentToken", "ACTION_BLUE")
                .doesNotContainKeys("organizationId", "createdBy", "updatedBy", "canManageBranding");

        ResponseEntity<Map> forbidden = http.exchange(staffPath(), HttpMethod.PUT,
                new HttpEntity<>(Map.of("displayNameOverride", "Nome secreto", "accentToken", "SUCCESS_GREEN"),
                        headers(SECTOR_ADMIN, organizationId)), Map.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertNoStore(forbidden.getHeaders());

        ResponseEntity<Map> published = publish(ADMIN, organizationId, "Prefeitura de Exemplo", "SUCCESS_GREEN");
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertNoStore(published.getHeaders());
        assertThat(published.getBody()).containsEntry("displayName", "Prefeitura de Exemplo")
                .containsEntry("displayNameOverride", "Prefeitura de Exemplo")
                .containsEntry("accentToken", "SUCCESS_GREEN").containsEntry("canManageBranding", true);
        assertThat(auditCount()).isEqualTo(1);

        ResponseEntity<Map> unchanged = publish(ADMIN, organizationId, "Prefeitura de Exemplo", "SUCCESS_GREEN");
        assertThat(unchanged.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(auditCount()).isEqualTo(1);

        ResponseEntity<Map> publicUpdated = http.getForEntity(publicPath(), Map.class);
        assertNoStore(publicUpdated.getHeaders());
        assertThat(publicUpdated.getBody()).containsEntry("displayName", "Prefeitura de Exemplo")
                .containsEntry("accentToken", "SUCCESS_GREEN")
                .doesNotContainKeys("displayNameOverride", "canManageBranding");
    }

    @Test void rejectsInvalidTokensAndCrossTenantContextWithoutMutation() {
        ResponseEntity<String> invalid = http.exchange(staffPath(), HttpMethod.PUT,
                new HttpEntity<>("{\"displayNameOverride\":\"Nome\",\"accentToken\":\"#ff0000\"}", headers(ADMIN, organizationId)),
                String.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(countBranding(organizationId)).isZero();

        ResponseEntity<String> crossTenantRead = http.exchange(staffPath(), HttpMethod.GET,
                new HttpEntity<>(headers(ADMIN, otherOrganizationId)), String.class);
        assertThat(crossTenantRead.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> crossTenantWrite = http.exchange(staffPath(), HttpMethod.PUT,
                new HttpEntity<>("{\"displayNameOverride\":\"Não autorizado\",\"accentToken\":\"NEXO_NAVY\"}",
                        headers(ADMIN, otherOrganizationId)), String.class);
        assertThat(crossTenantWrite.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(countBranding(organizationId)).isZero();
        assertThat(countBranding(otherOrganizationId)).isZero();
        assertThat(auditCount()).isZero();
    }

    @Test void resettingPublishedValuesRestoresOrganizationNameAndDefaultAccent() {
        assertThat(publish(ADMIN, organizationId, "Nome temporário", "NEXO_NAVY").getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> reset = publish(ADMIN, organizationId, null, "ACTION_BLUE");
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reset.getBody()).containsEntry("displayName", "Município de origem")
                .containsEntry("displayNameOverride", null).containsEntry("accentToken", "ACTION_BLUE");
        assertThat(auditCount()).isEqualTo(2);
    }

    @Test void concurrentIdenticalPublishesCreateOneBrandingRowAndAuditEvent() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<ResponseEntity<Map>> first = workers.submit(() -> publishAfterBarrier(ready, start));
            Future<ResponseEntity<Map>> second = workers.submit(() -> publishAfterBarrier(ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(first.get(30, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(second.get(30, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(countBranding(organizationId)).isEqualTo(1);
            assertThat(auditCount()).isEqualTo(1);
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test void inactiveOrganizationIsRejectedByTenantFilterForStaffRoutes() {
        jdbc.update("UPDATE organizations SET active=false WHERE id=?", organizationId);

        ResponseEntity<String> staffRead = http.exchange(staffPath(), HttpMethod.GET,
                new HttpEntity<>(headers(ADMIN, organizationId)), String.class);
        ResponseEntity<String> staffWrite = http.exchange(staffPath(), HttpMethod.PUT,
                new HttpEntity<>("{\"displayNameOverride\":\"Não publicar\",\"accentToken\":\"NEXO_NAVY\"}",
                        headers(ADMIN, organizationId)), String.class);
        ResponseEntity<String> publicRead = http.getForEntity(publicPath(), String.class);

        assertThat(staffRead.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(staffWrite.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(publicRead.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(countBranding(organizationId)).isZero();
        assertThat(auditCount()).isZero();
    }

    private ResponseEntity<Map> publishAfterBarrier(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent publish barrier timed out");
        return publish(ADMIN, organizationId, "Publicação simultânea", "SUCCESS_GREEN");
    }

    private ResponseEntity<Map> publish(String actor, UUID organization, String displayName, String accent) {
        HttpHeaders headers = headers(actor, organization);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(staffPath(), HttpMethod.PUT,
                new HttpEntity<>(Map.of("accentToken", accent, "displayNameOverride", displayName == null ? "" : displayName), headers),
                Map.class);
    }

    private String publicPath() { return "/api/v1/public/organizations/" + slug + "/branding"; }
    private String staffPath() { return "/api/v1/staff/organization/branding"; }

    private HttpHeaders headers(String actor, UUID organization) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(actor, PASSWORD);
        headers.set("X-Organization-Id", organization.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private long countBranding(UUID organization) {
        return jdbc.queryForObject("SELECT count(*) FROM organization_branding WHERE organization_id=?", Long.class, organization);
    }

    private int auditCount() {
        return jdbc.queryForObject("SELECT count(*) FROM organization_audit_events WHERE organization_id=? AND entity_type='ORGANIZATION_BRANDING'",
                Integer.class, organizationId);
    }

    private static void assertNoStore(HttpHeaders headers) {
        assertThat(headers.getCacheControl()).contains("no-store");
    }
}
