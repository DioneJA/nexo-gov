package br.com.nexo.gov.organization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Testcontainers
class OrganizationPrivacyNoticeIntegrationTest {
    private static final String ADMIN = "privacy-notice-admin";
    private static final String SECTOR_ADMIN = "privacy-notice-sector-admin";
    private static final String PASSWORD = "privacy-notice-test-password-2026";

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
    private String otherSlug;

    @BeforeEach void createOrganizations() {
        organizationId = UUID.randomUUID();
        otherOrganizationId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
        slug = "privacy-notice-" + organizationId;
        otherSlug = "privacy-notice-other-" + otherOrganizationId;
        jdbc.update("INSERT INTO organizations(id,slug,name) VALUES(?,?,?)", organizationId, slug, "Município Alfa");
        jdbc.update("INSERT INTO organizations(id,slug,name) VALUES(?,?,?)", otherOrganizationId, otherSlug, "Município Beta");
        jdbc.update("INSERT INTO departments(id,organization_id,name) VALUES(?,?,?)", departmentId, organizationId, "Atendimento");
        jdbc.update("INSERT INTO user_accounts(username,active) VALUES(?,true) ON CONFLICT(username) DO NOTHING", ADMIN);
        jdbc.update("INSERT INTO user_accounts(username,active) VALUES(?,true) ON CONFLICT(username) DO NOTHING", SECTOR_ADMIN);
        jdbc.update("INSERT INTO organization_memberships(username,organization_id,role_code) VALUES(?,?,'ADMIN')", ADMIN, organizationId);
        jdbc.update("INSERT INTO staff_memberships(username,organization_id,department_id,role_code) VALUES(?,?,?,'ADMIN')",
                SECTOR_ADMIN, organizationId, departmentId);
    }

    @Test void publishesPlainTextNoticeAndSecureLinkForOneOrganizationWithScopedAudit() {
        ResponseEntity<Map> emptyStaff = http.exchange(staffPath(), HttpMethod.GET,
                new HttpEntity<>(headers(ADMIN, organizationId)), Map.class);
        assertThat(emptyStaff.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertNoStore(emptyStaff.getHeaders());
        assertThat(emptyStaff.getBody()).containsEntry("explanatoryText", null)
                .containsEntry("privacyPolicyUrl", null).containsEntry("canManagePrivacyNotice", true);

        ResponseEntity<Map> publicDefault = http.getForEntity(publicPath(slug), Map.class);
        assertThat(publicDefault.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertNoStore(publicDefault.getHeaders());
        assertThat(publicDefault.getBody()).containsEntry("explanatoryText", null).containsEntry("privacyPolicyUrl", null)
                .doesNotContainKeys("organizationId", "createdBy", "updatedBy", "canManagePrivacyNotice");

        String text = "Os dados deste pedido serão usados para prestar o atendimento e exercer os direitos previstos em lei.\nAcompanhe a resposta pelo protocolo.";
        String url = "https://www.exemplo.gov.br/privacidade";
        ResponseEntity<Map> forbidden = publish(SECTOR_ADMIN, organizationId, text, url);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(noticeCount(organizationId)).isZero();

        ResponseEntity<Map> published = publish(ADMIN, organizationId, text, url);
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertNoStore(published.getHeaders());
        assertThat(published.getBody()).containsEntry("explanatoryText", text)
                .containsEntry("privacyPolicyUrl", url).containsEntry("canManagePrivacyNotice", true);
        assertThat(auditCount(organizationId)).isEqualTo(1);
        assertThat(auditSummary(organizationId)).doesNotContain(text, url);

        ResponseEntity<Map> publicPublished = http.getForEntity(publicPath(slug), Map.class);
        assertNoStore(publicPublished.getHeaders());
        assertThat(publicPublished.getBody()).containsEntry("explanatoryText", text).containsEntry("privacyPolicyUrl", url)
                .doesNotContainKeys("canManagePrivacyNotice", "organizationId", "createdBy", "updatedBy");
        assertThat(http.getForEntity(publicPath(otherSlug), Map.class).getBody())
                .containsEntry("explanatoryText", null).containsEntry("privacyPolicyUrl", null);

        ResponseEntity<Map> unchanged = publish(ADMIN, organizationId, text, url);
        assertThat(unchanged.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(auditCount(organizationId)).isEqualTo(1);

        Map<String, Object> clear = new HashMap<>();
        clear.put("explanatoryText", "  ");
        clear.put("privacyPolicyUrl", "");
        ResponseEntity<Map> cleared = http.exchange(staffPath(), HttpMethod.PUT,
                new HttpEntity<>(clear, headers(ADMIN, organizationId)), Map.class);
        assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cleared.getBody()).containsEntry("explanatoryText", null).containsEntry("privacyPolicyUrl", null);
        assertThat(noticeCount(organizationId)).isZero();
        assertThat(auditCount(organizationId)).isEqualTo(2);

        ResponseEntity<String> explicitNullClear = putRaw(ADMIN, organizationId,
                "{\"explanatoryText\":null,\"privacyPolicyUrl\":null}");
        assertThat(explicitNullClear.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(auditCount(organizationId)).isEqualTo(2);
    }

    @Test void rejectsHalfConfiguredUnsafeUrlsAndCrossTenantWritesWithoutMutation() {
        ResponseEntity<String> missingFields = putRaw(ADMIN, organizationId, "{}");
        assertThat(missingFields.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> halfConfigured = putRaw(ADMIN, organizationId,
                "{\"explanatoryText\":\"Aviso\",\"privacyPolicyUrl\":null}");
        assertThat(halfConfigured.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> blankUrlHalfConfigured = putRaw(ADMIN, organizationId,
                "{\"explanatoryText\":\"Aviso\",\"privacyPolicyUrl\":\"\"}");
        assertThat(blankUrlHalfConfigured.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> httpUrl = putRaw(ADMIN, organizationId,
                "{\"explanatoryText\":\"Aviso\",\"privacyPolicyUrl\":\"http://example.gov.br\"}");
        assertThat(httpUrl.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> credentialUrl = putRaw(ADMIN, organizationId,
                "{\"explanatoryText\":\"Aviso\",\"privacyPolicyUrl\":\"https://user:pass@example.gov.br/politica\"}");
        assertThat(credentialUrl.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> scriptUrl = putRaw(ADMIN, organizationId,
                "{\"explanatoryText\":\"Aviso\",\"privacyPolicyUrl\":\"javascript:alert(1)\"}");
        assertThat(scriptUrl.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> crossTenantWrite = putRaw(ADMIN, otherOrganizationId,
                "{\"explanatoryText\":\"Aviso\",\"privacyPolicyUrl\":\"https://example.gov.br/politica\"}");
        assertThat(crossTenantWrite.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(noticeCount(organizationId)).isZero();
        assertThat(noticeCount(otherOrganizationId)).isZero();
        assertThat(auditCount(organizationId)).isZero();
        assertThat(auditCount(otherOrganizationId)).isZero();
    }

    @Test void inactiveOrganizationCannotReadOrPublishAndItsSavedNoticeIsPreserved() {
        assertThat(publish(ADMIN, organizationId, "Aviso vigente", "https://www.exemplo.gov.br/privacidade")
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        jdbc.update("UPDATE organizations SET active=false WHERE id=?", organizationId);

        ResponseEntity<String> staffRead = http.exchange(staffPath(), HttpMethod.GET,
                new HttpEntity<>(headers(ADMIN, organizationId)), String.class);
        ResponseEntity<String> staffWrite = putRaw(ADMIN, organizationId,
                "{\"explanatoryText\":\"Novo aviso\",\"privacyPolicyUrl\":\"https://www.exemplo.gov.br/nova\"}");
        ResponseEntity<String> publicRead = http.getForEntity(publicPath(slug), String.class);

        assertThat(staffRead.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(staffWrite.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(publicRead.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(noticeCount(organizationId)).isEqualTo(1);
        assertThat(auditCount(organizationId)).isEqualTo(1);
    }

    private ResponseEntity<Map> publish(String actor, UUID organization, String text, String url) {
        Map<String, Object> body = new HashMap<>();
        body.put("explanatoryText", text);
        body.put("privacyPolicyUrl", url);
        return http.exchange(staffPath(), HttpMethod.PUT,
                new HttpEntity<>(body, headers(actor, organization)), Map.class);
    }

    private ResponseEntity<String> putRaw(String actor, UUID organization, String body) {
        return http.exchange(staffPath(), HttpMethod.PUT,
                new HttpEntity<>(body, headers(actor, organization)), String.class);
    }

    private String staffPath() { return "/api/v1/staff/organization/privacy-notice"; }
    private String publicPath(String organizationSlug) {
        return "/api/v1/public/organizations/" + organizationSlug + "/privacy-notice";
    }

    private HttpHeaders headers(String actor, UUID organization) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(actor, PASSWORD);
        headers.set("X-Organization-Id", organization.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private int noticeCount(UUID organization) {
        return jdbc.queryForObject("SELECT count(*) FROM organization_privacy_notice WHERE organization_id=?", Integer.class, organization);
    }

    private int auditCount(UUID organization) {
        return jdbc.queryForObject("SELECT count(*) FROM organization_audit_events WHERE organization_id=? AND entity_type='ORGANIZATION_PRIVACY_NOTICE'",
                Integer.class, organization);
    }

    private String auditSummary(UUID organization) {
        return jdbc.queryForObject("SELECT summary FROM organization_audit_events WHERE organization_id=? AND entity_type='ORGANIZATION_PRIVACY_NOTICE'",
                String.class, organization);
    }

    private static void assertNoStore(HttpHeaders headers) {
        assertThat(headers.getCacheControl()).contains("no-store");
    }
}
