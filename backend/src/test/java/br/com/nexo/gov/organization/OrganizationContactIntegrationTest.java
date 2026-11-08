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

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Testcontainers
class OrganizationContactIntegrationTest {
    private static final String ADMIN = "contact-admin";
    private static final String PASSWORD = "contact-test-password-2026";
    private static final String SECTOR_ADMIN = "contact-sector-admin";

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
    private UUID departmentId;

    @BeforeEach void createOrganization() {
        organizationId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
        jdbc.update("INSERT INTO organizations(id,slug,name) VALUES(?,?,?)", organizationId,
                "contact-test-" + organizationId, "Organização de contatos de teste");
        jdbc.update("INSERT INTO departments(id,organization_id,name) VALUES(?,?,?)", departmentId,
                organizationId, "Atendimento de teste");
        jdbc.update("INSERT INTO user_accounts(username,active) VALUES(?,true) ON CONFLICT(username) DO NOTHING", ADMIN);
        jdbc.update("INSERT INTO user_accounts(username,active) VALUES(?,true) ON CONFLICT(username) DO NOTHING", SECTOR_ADMIN);
        jdbc.update("INSERT INTO organization_memberships(username,organization_id,role_code) VALUES(?,?,'ADMIN')", ADMIN, organizationId);
        jdbc.update("INSERT INTO staff_memberships(username,organization_id,department_id,role_code) VALUES(?,?,?,'STAFF')",
                ADMIN, organizationId, departmentId);
        jdbc.update("INSERT INTO staff_memberships(username,organization_id,department_id,role_code) VALUES(?,?,?,'ADMIN')",
                SECTOR_ADMIN, organizationId, departmentId);
    }

    @Test void managesContactsWithOrganizationAdminAuthorizationAndArchivedSearch() {
        ResponseEntity<Map> forbidden = create(SECTOR_ADMIN, "Setor sem admin organizacional", "MUNICIPAL_DEPARTMENT");
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> created = create(ADMIN, "Finanças, Obras e Serviços", "MUNICIPAL_DEPARTMENT");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = (String) created.getBody().get("id");
        assertThat(created.getBody()).containsEntry("type", "MUNICIPAL_DEPARTMENT")
                .containsEntry("name", "Finanças, Obras e Serviços").containsEntry("active", true);

        HttpHeaders headers = headers(ADMIN, organizationId);
        ResponseEntity<Map> updated = http.exchange(path("/" + id), HttpMethod.PUT,
                new HttpEntity<>(contact("LEGAL_ENTITY", "Prefeitura Municipal", "contato@municipio.test", null), headers), Map.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).containsEntry("type", "LEGAL_ENTITY").containsEntry("email", "contato@municipio.test");

        ResponseEntity<Map> archive = http.exchange(path("/" + id + "/active"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("active", false), headers), Map.class);
        assertThat(archive.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(archive.getBody()).containsEntry("active", false).containsEntry("changed", true);

        ResponseEntity<Map> activeOnly = http.exchange(path("?q=Prefeitura"), HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat(((List<?>) activeOnly.getBody().get("items"))).isEmpty();
        ResponseEntity<Map> includeArchived = http.exchange(path("?q=Prefeitura&includeArchived=true"), HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat((List<?>) includeArchived.getBody().get("items")).hasSize(1);
        assertThat(includeArchived.getBody()).containsEntry("total", 1);

        ResponseEntity<Map> restored = http.exchange(path("/" + id + "/active"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("active", true), headers), Map.class);
        assertThat(restored.getBody()).containsEntry("active", true);
        assertThat(auditCount(id)).isEqualTo(4);

        assertThat(create(ADMIN, "Outro contato", "PERSON").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<Map> firstPage = http.exchange(path("?page=0&size=1"), HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        ResponseEntity<Map> secondPage = http.exchange(path("?page=1&size=1"), HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat(((List<?>) firstPage.getBody().get("items"))).hasSize(1);
        assertThat(((List<?>) secondPage.getBody().get("items"))).hasSize(1);
        assertThat(firstPage.getBody()).containsEntry("total", 2);
    }

    @Test void scopesReadsMutationsAndCsvImportToValidatedOrganization() {
        ResponseEntity<Map> own = create(ADMIN, "Atendimento", "PERSON");
        assertThat(own.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID otherOrganization = UUID.randomUUID();
        UUID foreignContact = UUID.randomUUID();
        jdbc.update("INSERT INTO organizations(id,slug,name) VALUES(?,?,?)", otherOrganization,
                "foreign-contact-" + otherOrganization, "Outra organização");
        jdbc.update("INSERT INTO organization_contacts(id,organization_id,contact_type,name,created_by,updated_by) " +
                        "VALUES(?,?,'PERSON','Contato reservado','fixture','fixture')", foreignContact, otherOrganization);

        ResponseEntity<String> foreignList = http.exchange(path(""), HttpMethod.GET,
                new HttpEntity<>(headers(ADMIN, otherOrganization)), String.class);
        assertThat(foreignList.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<String> foreignUpdate = http.exchange(path("/" + foreignContact), HttpMethod.PUT,
                new HttpEntity<>(contact("PERSON", "Tentativa", null, null), headers(ADMIN, organizationId)), String.class);
        assertThat(foreignUpdate.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        String importCsv = "type,name\nPERSON,Novo contato\n" + foreignContact + ",PERSON,Alteração externa\n";
        ResponseEntity<String> importResponse = http.exchange(path("/import"), HttpMethod.POST,
                new HttpEntity<>(importCsv, csvHeaders(ADMIN, organizationId)), String.class);
        assertThat(importResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST); // extra ID column is rejected before writing
        assertThat(countContacts(organizationId)).isEqualTo(1);

        importCsv = "id,type,name\n," + "PERSON,Novo contato\n" + foreignContact + ",PERSON,Alteração externa\n";
        importResponse = http.exchange(path("/import"), HttpMethod.POST,
                new HttpEntity<>(importCsv, csvHeaders(ADMIN, organizationId)), String.class);
        assertThat(importResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(countContacts(organizationId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT name FROM organization_contacts WHERE organization_id=? AND id=?",
                String.class, otherOrganization, foreignContact)).isEqualTo("Contato reservado");
    }

    @Test void concurrentArchiveRequestsCreateOnlyOneTransitionAndAuditEvent() throws Exception {
        ResponseEntity<Map> created = create(ADMIN, "Contato concorrente", "PERSON");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = (String) created.getBody().get("id");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<Map>> first = executor.submit(() -> archiveWhenReleased(id, ready, start));
            Future<ResponseEntity<Map>> second = executor.submit(() -> archiveWhenReleased(id, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            ResponseEntity<Map> firstResult = first.get(10, TimeUnit.SECONDS);
            ResponseEntity<Map> secondResult = second.get(10, TimeUnit.SECONDS);
            assertThat(firstResult.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(secondResult.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(List.of(firstResult, secondResult).stream()
                    .map(response -> response.getBody().get("changed")))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(auditCount(id)).isEqualTo(2); // creation plus one archive transition
            ResponseEntity<Map> archived = http.exchange(path("?includeArchived=true"), HttpMethod.GET,
                    new HttpEntity<>(headers(ADMIN, organizationId)), Map.class);
            assertThat(((List<Map<String, Object>>) archived.getBody().get("items")).getFirst())
                    .containsEntry("id", id).containsEntry("active", false);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test void importsAndExportsCsvIdempotentlyAndRejectsInvalidOrOversizedFilesAtomically() {
        String csv = "type,name,email,phone\r\nPERSON,\"João, Silva\",joao@example.test,+5511999999999\r\n" +
                "MUNICIPAL_DEPARTMENT,Suporte,,\r\nLEGAL_ENTITY,Prefeitura,,\r\n";
        ResponseEntity<Map> imported = http.exchange(path("/import"), HttpMethod.POST,
                new HttpEntity<>(csv, csvHeaders(ADMIN, organizationId)), Map.class);
        assertThat(imported.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(imported.getBody()).containsEntry("created", 3).containsEntry("processed", 3);
        assertThat(countContacts(organizationId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT name FROM organization_contacts WHERE organization_id=? AND name='João, Silva'",
                String.class, organizationId)).isEqualTo("João, Silva");
        assertThat(create(ADMIN, "=1+1", "LEGAL_ENTITY").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<byte[]> exported = http.exchange(path("/export"), HttpMethod.GET,
                new HttpEntity<>(headers(ADMIN, organizationId)), byte[].class);
        assertThat(exported.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exported.getHeaders().getContentType().toString()).isEqualTo("text/csv;charset=UTF-8");
        String exportText = new String(exported.getBody(), StandardCharsets.UTF_8);
        assertThat(exportText).contains("\"João, Silva\"").contains("\"'=1+1\"").contains("\"'+5511999999999\"");
        int auditsBefore = auditCount();
        ResponseEntity<Map> repeated = http.exchange(path("/import"), HttpMethod.POST,
                new HttpEntity<>(new String(exported.getBody(), StandardCharsets.UTF_8), csvHeaders(ADMIN, organizationId)), Map.class);
        assertThat(repeated.getBody()).containsEntry("created", 0).containsEntry("updated", 0).containsEntry("unchanged", 4);
        assertThat(auditCount()).isEqualTo(auditsBefore);

        String invalid = "type,name\nPERSON,Não deve gravar\nUNKNOWN,Tipo inválido\n";
        ResponseEntity<String> rejected = http.exchange(path("/import"), HttpMethod.POST,
                new HttpEntity<>(invalid, csvHeaders(ADMIN, organizationId)), String.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(countContacts(organizationId)).isEqualTo(4);

        StringBuilder tooManyRows = new StringBuilder("type,name\n");
        for (int row = 0; row < 1_001; row++) tooManyRows.append("PERSON,Contato ").append(row).append('\n');
        ResponseEntity<String> rowLimitResponse = http.exchange(path("/import"), HttpMethod.POST,
                new HttpEntity<>(tooManyRows.toString(), csvHeaders(ADMIN, organizationId)), String.class);
        assertThat(rowLimitResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(countContacts(organizationId)).isEqualTo(4);

        HttpHeaders oversizeHeaders = csvHeaders(ADMIN, organizationId);
        String oversize = "type,name\nPERSON," + "x".repeat(1_048_576);
        ResponseEntity<String> oversizeResponse = http.exchange(path("/import"), HttpMethod.POST,
                new HttpEntity<>(oversize, oversizeHeaders), String.class);
        assertThat(oversizeResponse.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(countContacts(organizationId)).isEqualTo(4);
    }

    @Test void rejectsMalformedUtf8AndQuotedFieldsAndReportsManagementCapability() {
        ResponseEntity<Map> capability = http.exchange("/api/v1/staff/organization/contact-management", HttpMethod.GET,
                new HttpEntity<>(headers(ADMIN, organizationId)), Map.class);
        assertThat(capability.getBody()).containsEntry("canManageContacts", true);
        ResponseEntity<Map> sectorCapability = http.exchange("/api/v1/staff/organization/contact-management", HttpMethod.GET,
                new HttpEntity<>(headers(SECTOR_ADMIN, organizationId)), Map.class);
        assertThat(sectorCapability.getBody()).containsEntry("canManageContacts", false);

        byte[] invalidUtf8 = new byte[]{'t','y','p','e',',','n','a','m','e','\n',(byte) 0xC3,0x28};
        ResponseEntity<String> malformedEncoding = http.exchange(path("/import"), HttpMethod.POST,
                new HttpEntity<>(invalidUtf8, csvHeaders(ADMIN, organizationId)), String.class);
        assertThat(malformedEncoding.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> malformedQuotes = http.exchange(path("/import"), HttpMethod.POST,
                new HttpEntity<>("type,name\nPERSON,\"sem fechamento", csvHeaders(ADMIN, organizationId)), String.class);
        assertThat(malformedQuotes.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String duplicateIds = "id,type,name\n00000000-0000-0000-0000-000000000001,PERSON,Um\n" +
                "00000000-0000-0000-0000-000000000001,PERSON,Dois\n";
        ResponseEntity<String> duplicateIdsResponse = http.exchange(path("/import"), HttpMethod.POST,
                new HttpEntity<>(duplicateIds, csvHeaders(ADMIN, organizationId)), String.class);
        assertThat(duplicateIdsResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(countContacts(organizationId)).isZero();
    }

    private ResponseEntity<Map> create(String actor, String name, String type) {
        return http.postForEntity(path(""), new HttpEntity<>(contact(type, name, null, null), headers(actor, organizationId)), Map.class);
    }

    private ResponseEntity<Map> archiveWhenReleased(String id, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return http.exchange(path("/" + id + "/active"), HttpMethod.PUT,
                new HttpEntity<>(Map.of("active", false), headers(ADMIN, organizationId)), Map.class);
    }

    private Map<String, Object> contact(String type, String name, String email, String phone) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        body.put("name", name);
        body.put("email", email);
        body.put("phone", phone);
        return body;
    }

    private String path(String suffix) { return "/api/v1/staff/organization" + "/contacts" + suffix; }

    private HttpHeaders headers(String actor, UUID organization) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(actor, PASSWORD);
        headers.set("X-Organization-Id", organization.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders csvHeaders(String actor, UUID organization) {
        HttpHeaders headers = headers(actor, organization);
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        return headers;
    }

    private long countContacts(UUID organization) {
        return jdbc.queryForObject("SELECT count(*) FROM organization_contacts WHERE organization_id=?", Long.class, organization);
    }

    private int auditCount() {
        return jdbc.queryForObject("SELECT count(*) FROM organization_audit_events WHERE organization_id=? AND entity_type='ORGANIZATION_CONTACT'",
                Integer.class, organizationId);
    }

    private int auditCount(String id) {
        return jdbc.queryForObject("SELECT count(*) FROM organization_audit_events WHERE organization_id=? AND entity_type='ORGANIZATION_CONTACT' AND entity_id=?",
                Integer.class, organizationId, UUID.fromString(id));
    }
}
