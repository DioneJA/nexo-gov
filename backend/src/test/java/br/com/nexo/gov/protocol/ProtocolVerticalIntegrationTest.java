package br.com.nexo.gov.protocol;

import br.com.nexo.gov.protocol.ProtocolDtos.Created;
import br.com.nexo.gov.protocol.ProtocolDtos.TrackingView;
import br.com.nexo.gov.security.LocalDemoData;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.qrcode.QRCodeReader;
import com.google.zxing.common.HybridBinarizer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Testcontainers
class ProtocolVerticalIntegrationTest {
    private static final String USER = "servidor";
    private static final String PASSWORD = "test-only-password-2026";

    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.4-alpine");

    @DynamicPropertySource static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("NEXOGOV_DEMO_USERNAME", () -> USER);
        registry.add("NEXOGOV_DEMO_PASSWORD", () -> PASSWORD);
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @Test void opensRoutesTracksAndClosesProtocolWithinItsOrganization() throws Exception {
        Created created = create("municipio-alfa", "Solicitação de teste", "Solicito análise da via.");
        assertThat(created.protocolNumber()).matches("\\d{4}-\\d{6}");
        assertThat(created.trackingToken()).hasSizeGreaterThan(40);

        ResponseEntity<byte[]> receipt = http.exchange("/api/v1/public/protocols/" + created.id() + "/receipt",
                HttpMethod.GET, bearer(created.trackingToken()), byte[].class);
        assertThat(receipt.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(receipt.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(new String(Objects.requireNonNull(receipt.getBody()), 0, 5)).isEqualTo("%PDF-");
        try (PDDocument pdf = Loader.loadPDF(receipt.getBody())) {
            assertThat(new PDFTextStripper().getText(pdf)).contains(created.protocolNumber());
            var xobjects = pdf.getPage(0).getResources().getXObjectNames();
            PDImageXObject qr = (PDImageXObject) pdf.getPage(0).getResources().getXObject(xobjects.iterator().next());
            String qrValue = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(
                    new BufferedImageLuminanceSource(qr.getImage())))).getText();
            assertThat(qrValue).isEqualTo("http://localhost:9000/#/acompanhar?token=" + created.trackingToken());
        }

        ResponseEntity<String> invalidTracking = http.exchange("/api/v1/public/protocols/tracking", HttpMethod.GET,
                bearer("token-invalido"), String.class);
        assertThat(invalidTracking.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        UUID protocolDepartment = UUID.fromString("20000000-0000-4000-8000-000000000001");
        UUID worksDepartment = UUID.fromString("20000000-0000-4000-8000-000000000002");
        UUID betaDepartment = UUID.fromString("20000000-0000-4000-8000-000000000004");
        String prefix = "/api/v1/staff/protocols/" + created.id();

        ResponseEntity<String> crossTenantDepartment = staffPost(prefix + "/forward", LocalDemoData.ORG_ALPHA,
                Map.of("destinationDepartmentId", betaDepartment));
        assertThat(crossTenantDepartment.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(staffPost(prefix + "/forward", LocalDemoData.ORG_ALPHA, Map.of("destinationDepartmentId", protocolDepartment)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(staffPost(prefix + "/receive", LocalDemoData.ORG_ALPHA, Map.of()).getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> dispatch = new HashMap<>();
        dispatch.put("destinationDepartmentId", worksDepartment);
        dispatch.put("note", "Encaminhado para análise técnica.");
        dispatch.put("visibleToApplicant", true);
        assertThat(staffPost(prefix + "/dispatch", LocalDemoData.ORG_ALPHA, dispatch).getStatusCode()).isEqualTo(HttpStatus.OK);
        jdbc.update("DELETE FROM staff_memberships WHERE username=? AND organization_id=? AND department_id=?",
                USER, LocalDemoData.ORG_ALPHA, worksDepartment);
        assertThat(staffPost(prefix + "/receive", LocalDemoData.ORG_ALPHA, Map.of()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        jdbc.update("INSERT INTO staff_memberships(username,organization_id,department_id,role_code) VALUES(?,?,?,'ADMIN') ON CONFLICT DO NOTHING",
                USER, LocalDemoData.ORG_ALPHA, worksDepartment);
        assertThat(staffPost(prefix + "/receive", LocalDemoData.ORG_ALPHA, Map.of()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(staffPost(prefix + "/complete", LocalDemoData.ORG_ALPHA, Map.of()).getStatusCode()).isEqualTo(HttpStatus.OK);

        TrackingView tracked = http.exchange("/api/v1/public/protocols/tracking", HttpMethod.GET,
                bearer(created.trackingToken()), TrackingView.class).getBody();
        assertThat(tracked).isNotNull();
        assertThat(tracked.status()).isEqualTo("COMPLETED");
        assertThat(tracked.events()).extracting("summary").anyMatch(summary -> summary.toString().contains("análise técnica"));
        assertThat(tracked.events()).extracting("action").contains("OPENED", "DISPATCHED");

        ResponseEntity<List<Map<String, Object>>> inOtherOrganization = http.exchange("/api/v1/staff/protocols",
                HttpMethod.GET, staffHeaders(LocalDemoData.ORG_BETA), new ParameterizedTypeReference<>() {});
        assertThat(inOtherOrganization.getBody()).isNotNull()
                .noneMatch(row -> created.id().toString().equals(row.get("id")));

        assertThatThrownBy(() -> jdbc.update("UPDATE protocol_events SET summary='alterado' WHERE protocol_id=?", created.id()))
                .isInstanceOf(DataAccessException.class);

        HttpHeaders randomOrganization = staffHeaders(UUID.randomUUID()).getHeaders();
        assertThat(http.exchange("/api/v1/staff/protocols", HttpMethod.GET, new HttpEntity<>(randomOrganization), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        HttpHeaders noOrganization = new HttpHeaders(); noOrganization.setBasicAuth(USER, PASSWORD);
        assertThat(http.exchange("/api/v1/staff/protocols", HttpMethod.GET, new HttpEntity<>(noOrganization), String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        HttpHeaders noAuthentication = new HttpHeaders(); noAuthentication.set("X-Organization-Id", LocalDemoData.ORG_ALPHA.toString());
        assertThat(http.exchange("/api/v1/staff/protocols", HttpMethod.GET, new HttpEntity<>(noAuthentication), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void concurrentRequestsReceiveDistinctProtocolNumbers() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                int number = i;
                tasks.add(() -> create("municipio-beta", "Concorrência " + number, "Teste de numeração concorrente.").protocolNumber());
            }
            Set<String> numbers = new HashSet<>();
            for (Future<String> future : pool.invokeAll(tasks)) numbers.add(future.get(30, TimeUnit.SECONDS));
            assertThat(numbers).hasSize(24);
        } finally { pool.shutdownNow(); }
    }

    private Created create(String organization, String subject, String message) {
        Map<String, Object> request = Map.of("organizationSlug", organization, "applicantName", "Pessoa de Teste",
                "applicantEmail", "cidadao@example.test", "subject", subject, "initialMessage", message);
        return Objects.requireNonNull(http.postForEntity("/api/v1/public/protocols", request, Created.class).getBody());
    }

    private ResponseEntity<String> staffPost(String path, UUID organizationId, Object body) {
        return http.exchange(path, HttpMethod.POST, staffHeaders(organizationId, body), String.class);
    }

    private HttpEntity<Void> bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Object> staffHeaders(UUID organizationId) { return staffHeaders(organizationId, null); }

    private HttpEntity<Object> staffHeaders(UUID organizationId, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(USER, PASSWORD);
        headers.set("X-Organization-Id", organizationId.toString());
        if (body != null) headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
