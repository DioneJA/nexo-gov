package br.com.nexo.gov.protocol;

import br.com.nexo.gov.protocol.ProtocolDtos.Created;
import br.com.nexo.gov.protocol.ProtocolDtos.InteractionView;
import br.com.nexo.gov.protocol.ProtocolDtos.ProtocolSummary;
import br.com.nexo.gov.protocol.ProtocolDtos.ProtocolReadReceipt;
import br.com.nexo.gov.protocol.ProtocolDtos.ProtocolReadStatus;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
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
    @Container static final GenericContainer<?> S3MOCK = new GenericContainer<>(DockerImageName.parse(
            "adobe/s3mock:5.2.3@sha256:ab01a6946750f451ca215a47e91030695b260e4003b8a5a6201d25029b8fca92"))
            .withExposedPorts(9090)
            .withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", "nexogov-test-private-documents");

    @DynamicPropertySource static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("nexogov.attachments.endpoint-uri", () -> "http://127.0.0.1:" + S3MOCK.getMappedPort(9090));
        registry.add("nexogov.attachments.bucket", () -> "nexogov-test-private-documents");
        registry.add("nexogov.attachments.access-key", () -> "test-access-key");
        registry.add("nexogov.attachments.secret-key", () -> "test-secret-key");
        registry.add("nexogov.attachments.maximum-file-size-bytes", () -> "1024");
        registry.add("spring.servlet.multipart.max-file-size", () -> "2KB");
        registry.add("NEXOGOV_DEMO_USERNAME", () -> USER);
        registry.add("NEXOGOV_DEMO_PASSWORD", () -> PASSWORD);
        registry.add("NEXOGOV_ALLOWED_ORIGIN", () -> "http://localhost:9000");
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ProtocolInteractionService interactionService;
    @Autowired ProtocolAttachmentService attachmentService;
    @Autowired ProtocolPublicAccessService publicAccess;

    @Test void protocolAttachmentsAreRequiredValidatedPrivateAndTenantScoped() throws Exception {
        String unique = UUID.randomUUID().toString();
        String subjectName = "Anexos por assunto " + unique;
        UUID subjectId = findOrCreateOnlineSubject("municipio-alfa", subjectName);
        UUID organizationId = LocalDemoData.ORG_ALPHA;
        UUID requirementId;
        ResponseEntity<Map> requirement = http.exchange("/api/v1/staff/organization/protocol-subjects/" + subjectId + "/attachment-requirements",
                HttpMethod.POST, staffHeaders(organizationId, Map.of("name", "Documento de identificação " + unique,
                        "required", true, "allowedExtensions", List.of(".pdf"))), Map.class);
        assertThat(requirement.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        requirementId = UUID.fromString(Objects.requireNonNull(requirement.getBody()).get("id").toString());
        ResponseEntity<String> duplicateRule = http.exchange("/api/v1/staff/organization/protocol-subjects/" + subjectId + "/attachment-requirements",
                HttpMethod.POST, staffHeaders(organizationId, Map.of("name", "Documento de identificação " + unique,
                        "required", false, "allowedExtensions", List.of("pdf"))), String.class);
        assertThat(duplicateRule.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        String slug = jdbc.queryForObject("SELECT slug FROM organizations WHERE id=?", String.class, organizationId);
        ResponseEntity<List<Map<String, Object>>> publicSubjects = http.exchange("/api/v1/public/organizations/" + slug + "/protocol-subjects",
                HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<>() {});
        assertThat(publicSubjects.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> publicSubject = Objects.requireNonNull(publicSubjects.getBody()).stream()
                .filter(item -> subjectId.toString().equals(item.get("id"))).findFirst().orElseThrow();
        assertThat(((Number) publicSubject.get("maximumAttachmentSizeBytes")).longValue()).isEqualTo(1024L);
        long counterBefore = counterNextValue(organizationId);
        ResponseEntity<String> missingRequiredFile = http.postForEntity("/api/v1/public/protocols",
                openRequest(slug, subjectId), String.class);
        assertThat(missingRequiredFile.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(counterNextValue(organizationId)).isEqualTo(counterBefore);

        ResponseEntity<Map> rejectedExtension = http.exchange("/api/v1/public/protocols", HttpMethod.POST,
                multipartRequest(openRequest(slug, subjectId), requirementId, "identidade.exe", "MZ".getBytes()), Map.class);
        assertThat(rejectedExtension.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(Objects.requireNonNull(rejectedExtension.getBody()).get("attachmentRequirementId")).isEqualTo(requirementId.toString());
        assertThat(counterNextValue(organizationId)).isEqualTo(counterBefore);

        byte[] oversizedPdf = new byte[1025];
        System.arraycopy("%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, oversizedPdf, 0, 5);
        ResponseEntity<Map> oversizedAttachment = http.exchange("/api/v1/public/protocols", HttpMethod.POST,
                multipartRequest(openRequest(slug, subjectId), requirementId, "identidade.pdf", oversizedPdf), Map.class);
        assertThat(oversizedAttachment.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(Objects.requireNonNull(oversizedAttachment.getBody()).get("attachmentRequirementId")).isEqualTo(requirementId.toString());
        assertThat(counterNextValue(organizationId)).isEqualTo(counterBefore);

        byte[] parserOversizedPdf = new byte[2049];
        System.arraycopy("%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, parserOversizedPdf, 0, 5);
        ResponseEntity<Map> multipartLimit = http.exchange("/api/v1/public/protocols", HttpMethod.POST,
                multipartRequest(openRequest(slug, subjectId), requirementId, "identidade.pdf", parserOversizedPdf), Map.class);
        assertThat(multipartLimit.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(Objects.requireNonNull(multipartLimit.getBody()).get("message").toString()).contains("limite total");
        assertThat(counterNextValue(organizationId)).isEqualTo(counterBefore);

        byte[] pdfBytes = "%PDF-1.4\nNexoGov attachment test\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        ResponseEntity<Created> createdResponse = http.exchange("/api/v1/public/protocols", HttpMethod.POST,
                multipartRequest(openRequest(slug, subjectId), requirementId, "identidade.pdf", pdfBytes), Created.class);
        assertThat(createdResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Created created = Objects.requireNonNull(createdResponse.getBody());
        assertThat(counterNextValue(organizationId)).isEqualTo(counterBefore + 1);

        ResponseEntity<TrackingView> tracked = http.exchange("/api/v1/public/protocols/tracking", HttpMethod.GET,
                bearer(created.trackingToken()), TrackingView.class);
        assertThat(tracked.getStatusCode()).isEqualTo(HttpStatus.OK);
        ProtocolAttachmentService.AttachmentView publicAttachment = Objects.requireNonNull(tracked.getBody()).attachments().getFirst();
        assertThat(publicAttachment.filename()).isEqualTo("identidade.pdf");
        assertThat(publicAttachment.sizeBytes()).isEqualTo(pdfBytes.length);

        HttpHeaders invalidTokenHeaders = new HttpHeaders();
        invalidTokenHeaders.setBearerAuth("invalid-token");
        ResponseEntity<byte[]> invalidToken = http.exchange("/api/v1/public/protocols/" + created.id() + "/attachments/" + publicAttachment.id() + "/download",
                HttpMethod.GET, new HttpEntity<>(invalidTokenHeaders), byte[].class);
        assertThat(invalidToken.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<byte[]> publicDownload = http.exchange("/api/v1/public/protocols/" + created.id() + "/attachments/" + publicAttachment.id() + "/download",
                HttpMethod.GET, bearer(created.trackingToken()), byte[].class);
        assertThat(publicDownload.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publicDownload.getBody()).containsExactly(pdfBytes);
        assertThat(publicDownload.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(publicDownload.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("private, no-store");
        assertThat(jdbc.queryForObject("SELECT download_count FROM protocol_attachments WHERE id=?", Long.class, publicAttachment.id())).isEqualTo(1L);
        assertThat(jdbc.queryForMap("SELECT actor,audience FROM protocol_attachment_downloads WHERE attachment_id=?", publicAttachment.id()))
                .containsEntry("actor", "APPLICANT").containsEntry("audience", "PUBLIC");

        ResponseEntity<List<Map<String, Object>>> staffAttachments = http.exchange("/api/v1/staff/protocols/" + created.id() + "/attachments",
                HttpMethod.GET, staffHeaders(organizationId), new ParameterizedTypeReference<>() {});
        assertThat(staffAttachments.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(staffAttachments.getBody()).hasSize(1);
        ResponseEntity<ProtocolReadReceipt> staffRead = http.exchange("/api/v1/staff/protocols/" + created.id() + "/read",
                HttpMethod.POST, staffHeaders(organizationId), ProtocolReadReceipt.class);
        assertThat(staffRead.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<byte[]> staffDownload = http.exchange("/api/v1/staff/protocols/" + created.id()
                        + "/attachments/" + publicAttachment.id() + "/download",
                HttpMethod.GET, staffHeaders(organizationId), byte[].class);
        assertThat(staffDownload.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT download_count FROM protocol_attachments WHERE id=?", Long.class,
                publicAttachment.id())).isEqualTo(2L);

        ResponseEntity<List<Map<String, Object>>> accessHistory = http.exchange(
                "/api/v1/staff/protocols/" + created.id() + "/access-history", HttpMethod.GET,
                staffHeaders(organizationId), new ParameterizedTypeReference<>() {});
        assertThat(accessHistory.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accessHistory.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(accessHistory.getBody()).isNotEmpty();
        assertThat(accessHistory.getBody()).extracting(event -> event.get("actorType"))
                .contains("APPLICANT", "STAFF");
        assertThat(accessHistory.getBody()).extracting(event -> event.get("accessType"))
                .contains("READ", "DOWNLOAD");
        assertThat(accessHistory.getBody().toString()).doesNotContain(created.trackingToken());
        assertThat(accessHistory.getBody().stream().map(event -> java.time.OffsetDateTime.parse(event.get("accessedAt").toString()))
                .toList()).isSortedAccordingTo(Comparator.reverseOrder());
        assertThatThrownBy(() -> jdbc.update("UPDATE protocol_document_access_events SET actor_reference='alterado' WHERE protocol_id=?",
                created.id())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM protocol_document_access_events WHERE protocol_id=?", created.id()))
                .isInstanceOf(DataAccessException.class);

        ResponseEntity<String> wrongTenant = http.exchange("/api/v1/staff/protocols/" + created.id() + "/attachments",
                HttpMethod.GET, staffHeaders(LocalDemoData.ORG_BETA), String.class);
        assertThat(wrongTenant.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<String> wrongTenantHistory = http.exchange("/api/v1/staff/protocols/" + created.id() + "/access-history",
                HttpMethod.GET, staffHeaders(LocalDemoData.ORG_BETA), String.class);
        assertThat(wrongTenantHistory.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<String> wrongTenantDownload = http.exchange("/api/v1/staff/protocols/" + created.id()
                        + "/attachments/" + publicAttachment.id() + "/download",
                HttpMethod.GET, staffHeaders(LocalDemoData.ORG_BETA), String.class);
        assertThat(wrongTenantDownload.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        UUID otherDepartment = jdbc.queryForObject("SELECT id FROM departments WHERE organization_id=? AND id<>? AND active=true ORDER BY id LIMIT 1",
                UUID.class, organizationId, jdbc.queryForObject("SELECT assigned_department_id FROM protocols WHERE id=?", UUID.class, created.id()));
        List<String> priorOrganizationRoles = jdbc.query(
                "SELECT role_code FROM organization_memberships WHERE username='servidor.teste' AND organization_id=?",
                (rs, row) -> rs.getString(1), organizationId);
        List<DepartmentMembership> priorDepartmentMemberships = jdbc.query(
                "SELECT department_id,role_code FROM staff_memberships WHERE username='servidor.teste' AND organization_id=?",
                (rs, row) -> new DepartmentMembership(rs.getObject("department_id", UUID.class), rs.getString("role_code")), organizationId);
        try {
            jdbc.update("DELETE FROM organization_memberships WHERE username='servidor.teste' AND organization_id=?", organizationId);
            jdbc.update("INSERT INTO organization_memberships(username,organization_id,role_code) VALUES('servidor.teste',?,'STAFF')", organizationId);
            jdbc.update("DELETE FROM staff_memberships WHERE username='servidor.teste' AND organization_id=?", organizationId);
            jdbc.update("INSERT INTO staff_memberships(username,organization_id,department_id,role_code) VALUES('servidor.teste',?,?, 'STAFF')",
                    organizationId, otherDepartment);
            ResponseEntity<String> wrongDepartment = http.exchange("/api/v1/staff/protocols/" + created.id() + "/attachments",
                    HttpMethod.GET, staffHeaders(organizationId, null, "servidor.teste"), String.class);
            assertThat(wrongDepartment.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            long downloadsBeforeDeniedRead = jdbc.queryForObject(
                    "SELECT count(*) FROM protocol_attachment_downloads WHERE attachment_id=?", Long.class, publicAttachment.id());
            ResponseEntity<String> wrongDepartmentDownload = http.exchange("/api/v1/staff/protocols/" + created.id()
                            + "/attachments/" + publicAttachment.id() + "/download",
                    HttpMethod.GET, staffHeaders(organizationId, null, "servidor.teste"), String.class);
            assertThat(wrongDepartmentDownload.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            ResponseEntity<String> wrongDepartmentHistory = http.exchange("/api/v1/staff/protocols/" + created.id()
                            + "/access-history", HttpMethod.GET,
                    staffHeaders(organizationId, null, "servidor.teste"), String.class);
            assertThat(wrongDepartmentHistory.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM protocol_attachment_downloads WHERE attachment_id=?",
                    Long.class, publicAttachment.id())).isEqualTo(downloadsBeforeDeniedRead);
        } finally {
            jdbc.update("DELETE FROM staff_memberships WHERE username='servidor.teste' AND organization_id=?", organizationId);
            for (DepartmentMembership membership : priorDepartmentMemberships)
                jdbc.update("INSERT INTO staff_memberships(username,organization_id,department_id,role_code) VALUES('servidor.teste',?,?,?)",
                        organizationId, membership.departmentId(), membership.roleCode());
            jdbc.update("DELETE FROM organization_memberships WHERE username='servidor.teste' AND organization_id=?", organizationId);
            for (String role : priorOrganizationRoles)
                jdbc.update("INSERT INTO organization_memberships(username,organization_id,role_code) VALUES('servidor.teste',?,?)",
                        organizationId, role);
        }
    }

    @Test void protocolDocumentAccessHistoryIsTenantScopedCredentialFreeAndAppendOnly() {
        UUID organizationId = LocalDemoData.ORG_ALPHA;
        Created created = create("municipio-alfa", "Histórico de acesso " + UUID.randomUUID(),
                "Demanda fictícia para validar a trilha de acesso.");

        ResponseEntity<TrackingView> publicRead = http.exchange("/api/v1/public/protocols/tracking", HttpMethod.GET,
                bearer(created.trackingToken()), TrackingView.class);
        assertThat(publicRead.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<ProtocolReadReceipt> staffRead = http.exchange("/api/v1/staff/protocols/" + created.id() + "/read",
                HttpMethod.POST, staffHeaders(organizationId), ProtocolReadReceipt.class);
        assertThat(staffRead.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<List<ProtocolDocumentAccessService.AccessHistoryEvent>> history = http.exchange(
                "/api/v1/staff/protocols/" + created.id() + "/access-history", HttpMethod.GET,
                staffHeaders(organizationId), new ParameterizedTypeReference<>() {});
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(history.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(history.getBody()).hasSize(2);
        assertThat(history.getBody()).extracting(ProtocolDocumentAccessService.AccessHistoryEvent::documentType)
                .containsOnly("PROTOCOL");
        assertThat(history.getBody()).extracting(ProtocolDocumentAccessService.AccessHistoryEvent::documentId)
                .containsOnly(created.id());
        assertThat(history.getBody()).extracting(ProtocolDocumentAccessService.AccessHistoryEvent::actorType)
                .contains("APPLICANT", "STAFF");
        assertThat(history.getBody()).extracting(ProtocolDocumentAccessService.AccessHistoryEvent::accessType)
                .containsOnly("READ");
        assertThat(history.getBody()).extracting(ProtocolDocumentAccessService.AccessHistoryEvent::outcome)
                .containsOnly("ALLOWED");
        assertThat(history.getBody()).extracting(ProtocolDocumentAccessService.AccessHistoryEvent::actorLabel)
                .contains("Requerente", USER);
        assertThat(history.getBody()).extracting(ProtocolDocumentAccessService.AccessHistoryEvent::accessedAt)
                .isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(history.getBody().toString()).doesNotContain(created.trackingToken());

        ResponseEntity<String> otherOrganization = http.exchange("/api/v1/staff/protocols/" + created.id()
                        + "/access-history", HttpMethod.GET, staffHeaders(LocalDemoData.ORG_BETA), String.class);
        assertThat(otherOrganization.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThatThrownBy(() -> jdbc.update("UPDATE protocol_document_access_events SET actor_reference='changed' WHERE protocol_id=?",
                created.id())).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM protocol_document_access_events WHERE protocol_id=?", created.id()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE protocol_document_access_events"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test void protocolInteractionsAreAppendOnlyPrivateAndReopenRequiresResponsibleSector() throws Exception {
        String unique = UUID.randomUUID().toString();
        UUID organizationId = LocalDemoData.ORG_ALPHA;
        UUID subjectId = findOrCreateOnlineSubject("municipio-alfa", "Interações " + unique);
        ResponseEntity<Map> ruleResponse = http.exchange(
                "/api/v1/staff/organization/protocol-subjects/" + subjectId + "/attachment-requirements",
                HttpMethod.POST, staffHeaders(organizationId, Map.of("name", "Anexos de interação " + unique,
                        "required", false, "allowedExtensions", List.of(".pdf"))), Map.class);
        assertThat(ruleResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID ruleId = UUID.fromString(Objects.requireNonNull(ruleResponse.getBody()).get("id").toString());
        jdbc.update("UPDATE protocol_subjects SET destination_department_id=? WHERE organization_id=? AND id=?",
                LocalDemoData.ALPHA_PROTOCOL, organizationId, subjectId);
        String slug = jdbc.queryForObject("SELECT slug FROM organizations WHERE id=?", String.class, organizationId);
        Created created = Objects.requireNonNull(http.postForEntity("/api/v1/public/protocols",
                openRequest(slug, subjectId), Created.class).getBody());

        ResponseEntity<ProtocolSummary[]> staffInbox = http.exchange("/api/v1/staff/protocols", HttpMethod.GET,
                staffHeaders(organizationId), ProtocolSummary[].class);
        assertThat(staffInbox.getBody()).isNotNull();
        assertThat(Arrays.stream(staffInbox.getBody()).filter(protocol -> protocol.id().equals(created.id())).findFirst())
                .get().extracting(ProtocolSummary::interactionAllowedExtensions).isEqualTo(List.of(".pdf"));

        ResponseEntity<TrackingView> before = http.exchange("/api/v1/public/protocols/tracking", HttpMethod.GET,
                bearer(created.trackingToken()), TrackingView.class);
        assertThat(before.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(before.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("private, no-store");
        assertThat(before.getBody()).isNotNull().satisfies(view -> {
            assertThat(view.id()).isEqualTo(created.id());
            assertThat(view.interactionAllowedExtensions()).containsExactly(".pdf");
            assertThat(view.interactions()).isEmpty();
        });

        byte[] pdf = "%PDF-1.4\ninteraction public\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        ResponseEntity<String> invalidSignature = http.exchange("/api/v1/public/protocols/" + created.id() + "/interactions",
                HttpMethod.POST, publicInteractionMultipart("Formato incorreto.", "nao-pdf.pdf", "corrompido".getBytes(),
                        created.trackingToken()), String.class);
        assertThat(invalidSignature.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM protocol_interactions WHERE protocol_id=?", Integer.class, created.id())).isZero();
        ResponseEntity<InteractionView> publicPost = http.exchange("/api/v1/public/protocols/" + created.id() + "/interactions",
                HttpMethod.POST, publicInteractionMultipart("Complemento enviado pelo requerente.", "complemento.pdf", pdf,
                        created.trackingToken()), InteractionView.class);
        assertThat(publicPost.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        InteractionView publicInteraction = Objects.requireNonNull(publicPost.getBody());
        assertThat(publicInteraction.author()).isEqualTo("Requerente");
        assertThat(publicInteraction.attachments()).singleElement().satisfies(file -> {
            assertThat(file.filename()).isEqualTo("complemento.pdf");
            assertThat(file.downloadPath()).contains("/public/protocols/" + created.id() + "/attachments/");
        });

        List<String> previousRoles = jdbc.query("SELECT role_code FROM organization_memberships WHERE username='servidor.teste' AND organization_id=?",
                (rs, row) -> rs.getString(1), organizationId);
        List<DepartmentMembership> previousDepartmentMemberships = jdbc.query(
                "SELECT department_id,role_code FROM staff_memberships WHERE username='servidor.teste' AND organization_id=?",
                (rs, row) -> new DepartmentMembership(rs.getObject("department_id", UUID.class), rs.getString("role_code")), organizationId);
        jdbc.update("DELETE FROM organization_memberships WHERE username='servidor.teste' AND organization_id=?", organizationId);
        jdbc.update("INSERT INTO organization_memberships(username,organization_id,role_code) VALUES('servidor.teste',?,'STAFF')",
                organizationId);
        try {
            ResponseEntity<InteractionView> internalPost = http.exchange("/api/v1/staff/protocols/" + created.id() + "/interactions",
                    HttpMethod.POST, staffInteractionMultipart("Análise reservada do setor.", "INTERNAL", "nota.pdf", pdf,
                            organizationId, "servidor.teste"), InteractionView.class);
            assertThat(internalPost.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            InteractionView internalInteraction = Objects.requireNonNull(internalPost.getBody());
            assertThat(internalInteraction.visibility()).isEqualTo("INTERNAL");
            assertThat(internalInteraction.department()).isNotBlank();
            assertThat(internalInteraction.attachments()).hasSize(1);

            ResponseEntity<TrackingView> publicTracking = http.exchange("/api/v1/public/protocols/tracking", HttpMethod.GET,
                    bearer(created.trackingToken()), TrackingView.class);
            assertThat(publicTracking.getBody()).isNotNull().satisfies(view -> {
                assertThat(view.id()).isEqualTo(created.id());
                assertThat(view.attachments()).isEmpty();
                assertThat(view.interactions()).extracting(InteractionView::message)
                        .containsExactly("Complemento enviado pelo requerente.");
                assertThat(view.interactions()).allSatisfy(interaction -> assertThat(interaction.visibility()).isEqualTo("PUBLIC"));
            });
            assertThat(publicTracking.getBody().toString()).doesNotContain("Análise reservada", "nota.pdf");

            ResponseEntity<List<InteractionView>> staffList = http.exchange("/api/v1/staff/protocols/" + created.id() + "/interactions",
                    HttpMethod.GET, staffHeaders(organizationId, null, "servidor.teste"), new ParameterizedTypeReference<>() {});
            assertThat(staffList.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(staffList.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("private, no-store");
            assertThat(staffList.getBody()).extracting(InteractionView::visibility).containsExactly("PUBLIC", "INTERNAL");
            InteractionView staffViewOfPublicMessage = Objects.requireNonNull(staffList.getBody()).getFirst();
            UUID publicInteractionAttachmentId = publicInteraction.attachments().getFirst().id();
            assertThat(staffViewOfPublicMessage.attachments()).singleElement().satisfies(file -> {
                assertThat(file.id()).isEqualTo(publicInteractionAttachmentId);
                assertThat(file.downloadPath()).contains("/staff/protocols/" + created.id() + "/attachments/");
            });
            ResponseEntity<byte[]> staffPublicDownload = http.exchange("/api/v1/staff/protocols/" + created.id()
                            + "/attachments/" + publicInteractionAttachmentId + "/download",
                    HttpMethod.GET, staffHeaders(organizationId, null, "servidor.teste"), byte[].class);
            assertThat(staffPublicDownload.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(staffPublicDownload.getBody()).containsExactly(pdf);
            assertThat(jdbc.queryForMap("SELECT actor,audience FROM protocol_attachment_downloads WHERE attachment_id=?",
                    publicInteractionAttachmentId)).containsEntry("actor", "servidor.teste").containsEntry("audience", "STAFF");
            ResponseEntity<List<Map<String, Object>>> staffOpeningAttachments = http.exchange(
                    "/api/v1/staff/protocols/" + created.id() + "/attachments", HttpMethod.GET,
                    staffHeaders(organizationId, null, "servidor.teste"), new ParameterizedTypeReference<>() {});
            assertThat(staffOpeningAttachments.getBody()).isEmpty();
            ResponseEntity<List<Map<String, Object>>> staffProtocols = http.exchange(
                    "/api/v1/staff/protocols", HttpMethod.GET,
                    staffHeaders(organizationId, Map.of(), "servidor.teste"), new ParameterizedTypeReference<>() {});
            Map<String, Object> currentSummary = Objects.requireNonNull(staffProtocols.getBody()).stream()
                    .filter(row -> created.id().toString().equals(row.get("id"))).findFirst().orElseThrow();
            assertThat(((Number) currentSummary.get("attachmentCount")).intValue()).isZero();

            UUID internalAttachmentId = internalInteraction.attachments().getFirst().id();
            ResponseEntity<String> publicInternalDownload = http.exchange("/api/v1/public/protocols/" + created.id()
                            + "/attachments/" + internalAttachmentId + "/download",
                    HttpMethod.GET, bearer(created.trackingToken()), String.class);
            assertThat(publicInternalDownload.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            String originalSha = jdbc.queryForObject("SELECT sha256 FROM protocol_attachments WHERE id=?", String.class,
                    internalAttachmentId);
            jdbc.update("UPDATE protocol_attachments SET sha256=? WHERE id=?", "0".repeat(64), internalAttachmentId);
            ResponseEntity<String> corruptObject = http.exchange("/api/v1/staff/protocols/" + created.id()
                            + "/attachments/" + internalAttachmentId + "/download",
                    HttpMethod.GET, staffHeaders(organizationId, null, "servidor.teste"), String.class);
            assertThat(corruptObject.getStatusCode().is5xxServerError()).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM protocol_attachment_downloads WHERE attachment_id=?",
                    Long.class, internalAttachmentId)).isZero();
            jdbc.update("UPDATE protocol_attachments SET sha256=? WHERE id=?", originalSha, internalAttachmentId);
            ResponseEntity<byte[]> staffInternalDownload = http.exchange("/api/v1/staff/protocols/" + created.id()
                            + "/attachments/" + internalAttachmentId + "/download",
                    HttpMethod.GET, staffHeaders(organizationId, null, "servidor.teste"), byte[].class);
            assertThat(staffInternalDownload.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(staffInternalDownload.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("private, no-store");
            assertThat(staffInternalDownload.getBody()).containsExactly(pdf);
            assertThat(jdbc.queryForMap("SELECT actor,audience FROM protocol_attachment_downloads WHERE attachment_id=?",
                    internalAttachmentId)).containsEntry("actor", "servidor.teste").containsEntry("audience", "STAFF");

            UUID internalDepartmentId = jdbc.queryForObject(
                    "SELECT department_id FROM protocol_interactions WHERE id=?", UUID.class, internalInteraction.id());
            long downloadsBeforeInactiveDepartment = jdbc.queryForObject(
                    "SELECT count(*) FROM protocol_attachment_downloads WHERE attachment_id=?", Long.class, internalAttachmentId);
            jdbc.update("UPDATE departments SET active=false WHERE organization_id=? AND id=?", organizationId, internalDepartmentId);
            try {
                ResponseEntity<String> inactiveDepartmentDownload = http.exchange("/api/v1/staff/protocols/" + created.id()
                                + "/attachments/" + internalAttachmentId + "/download",
                        HttpMethod.GET, staffHeaders(organizationId, null, "servidor.teste"), String.class);
                assertThat(inactiveDepartmentDownload.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
                assertThatThrownBy(() -> attachmentService.staffDownload(
                        organizationId, created.id(), internalAttachmentId, "servidor.teste"))
                        .isInstanceOf(ProtocolExceptions.NotFound.class);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM protocol_attachment_downloads WHERE attachment_id=?",
                        Long.class, internalAttachmentId)).isEqualTo(downloadsBeforeInactiveDepartment);
            } finally {
                jdbc.update("UPDATE departments SET active=true WHERE organization_id=? AND id=?", organizationId, internalDepartmentId);
            }

            ResponseEntity<String> crossTenantRead = http.exchange("/api/v1/staff/protocols/" + created.id() + "/interactions",
                    HttpMethod.GET, staffHeaders(LocalDemoData.ORG_BETA), String.class);
            assertThat(crossTenantRead.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            ResponseEntity<String> crossTenantWrite = http.exchange("/api/v1/staff/protocols/" + created.id() + "/interactions",
                    HttpMethod.POST, staffHeaders(LocalDemoData.ORG_BETA, Map.of("message", "cross", "visibility", "PUBLIC")), String.class);
            assertThat(crossTenantWrite.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            long publicInteractionDownloadsBeforeCrossTenant = jdbc.queryForObject(
                    "SELECT count(*) FROM protocol_attachment_downloads WHERE attachment_id=?", Long.class,
                    publicInteractionAttachmentId);
            ResponseEntity<String> crossTenantInteractionDownload = http.exchange("/api/v1/staff/protocols/" + created.id()
                            + "/attachments/" + publicInteractionAttachmentId + "/download",
                    HttpMethod.GET, staffHeaders(LocalDemoData.ORG_BETA), String.class);
            assertThat(crossTenantInteractionDownload.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM protocol_attachment_downloads WHERE attachment_id=?",
                    Long.class, publicInteractionAttachmentId)).isEqualTo(publicInteractionDownloadsBeforeCrossTenant);
            long downloadsBeforeCrossTenant = jdbc.queryForObject("SELECT count(*) FROM protocol_attachment_downloads WHERE attachment_id=?",
                    Long.class, internalAttachmentId);
            long downloadCountBeforeCrossTenant = jdbc.queryForObject("SELECT download_count FROM protocol_attachments WHERE id=?",
                    Long.class, internalAttachmentId);
            ResponseEntity<String> crossTenantDownload = http.exchange("/api/v1/staff/protocols/" + created.id()
                            + "/attachments/" + internalAttachmentId + "/download",
                    HttpMethod.GET, staffHeaders(LocalDemoData.ORG_BETA), String.class);
            assertThat(crossTenantDownload.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM protocol_attachment_downloads WHERE attachment_id=?",
                    Long.class, internalAttachmentId)).isEqualTo(downloadsBeforeCrossTenant);
            assertThat(jdbc.queryForObject("SELECT download_count FROM protocol_attachments WHERE id=?",
                    Long.class, internalAttachmentId)).isEqualTo(downloadCountBeforeCrossTenant);

            UUID otherDepartment = jdbc.queryForObject("SELECT id FROM departments WHERE organization_id=? AND id<>? ORDER BY id LIMIT 1",
                    UUID.class, organizationId, LocalDemoData.ALPHA_PROTOCOL);
            jdbc.update("DELETE FROM staff_memberships WHERE username='servidor.teste' AND organization_id=?", organizationId);
            jdbc.update("INSERT INTO staff_memberships(username,organization_id,department_id,role_code) VALUES('servidor.teste',?,?, 'STAFF')",
                    organizationId, otherDepartment);
            ResponseEntity<String> otherSectorRead = http.exchange("/api/v1/staff/protocols/" + created.id() + "/interactions",
                    HttpMethod.GET, staffHeaders(organizationId, null, "servidor.teste"), String.class);
            assertThat(otherSectorRead.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            ResponseEntity<String> otherSectorDownload = http.exchange("/api/v1/staff/protocols/" + created.id()
                            + "/attachments/" + internalAttachmentId + "/download",
                    HttpMethod.GET, staffHeaders(organizationId, null, "servidor.teste"), String.class);
            assertThat(otherSectorDownload.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM protocol_attachment_downloads WHERE attachment_id=?",
                    Long.class, internalAttachmentId)).isEqualTo(1L);

            jdbc.update("DELETE FROM staff_memberships WHERE username='servidor.teste' AND organization_id=?", organizationId);
            jdbc.update("INSERT INTO staff_memberships(username,organization_id,department_id,role_code) VALUES('servidor.teste',?,?, 'STAFF')",
                    organizationId, LocalDemoData.ALPHA_PROTOCOL);

            String prefix = "/api/v1/staff/protocols/" + created.id();
            assertThat(staffPost(prefix + "/forward", organizationId,
                    Map.of("destinationDepartmentId", LocalDemoData.ALPHA_PROTOCOL), USER).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(staffPost(prefix + "/receive", organizationId, Map.of(), USER).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(staffPost(prefix + "/complete", organizationId, Map.of(), USER).getStatusCode()).isEqualTo(HttpStatus.OK);
            ResponseEntity<String> applicantAfterCompletion = http.exchange("/api/v1/public/protocols/" + created.id() + "/interactions",
                    HttpMethod.POST, publicInteractionMultipart("Após conclusão", null, null, created.trackingToken()), String.class);
            assertThat(applicantAfterCompletion.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            ResponseEntity<String> staffAfterCompletion = staffPost(prefix + "/interactions", organizationId,
                    Map.of("message", "Mensagem após conclusão", "visibility", "PUBLIC"), USER);
            assertThat(staffAfterCompletion.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM protocol_interactions WHERE protocol_id=?",
                    Integer.class, created.id())).isEqualTo(2);

            jdbc.update("DELETE FROM staff_memberships WHERE username='servidor.teste' AND organization_id=?", organizationId);
            jdbc.update("INSERT INTO staff_memberships(username,organization_id,department_id,role_code) VALUES('servidor.teste',?,?, 'STAFF')",
                    organizationId, otherDepartment);
            ResponseEntity<String> wrongSectorReopen = http.exchange(prefix + "/reopen", HttpMethod.POST,
                    staffHeaders(organizationId, null, "servidor.teste"), String.class);
            assertThat(wrongSectorReopen.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(jdbc.queryForObject("SELECT status FROM protocols WHERE id=?", String.class, created.id())).isEqualTo("COMPLETED");
            jdbc.update("DELETE FROM staff_memberships WHERE username='servidor.teste' AND organization_id=?", organizationId);
            jdbc.update("INSERT INTO staff_memberships(username,organization_id,department_id,role_code) VALUES('servidor.teste',?,?, 'STAFF')",
                    organizationId, LocalDemoData.ALPHA_PROTOCOL);
            ResponseEntity<String> responsibleSectorReopen = http.exchange(prefix + "/reopen", HttpMethod.POST,
                    staffHeaders(organizationId, null, "servidor.teste"), String.class);
            assertThat(responsibleSectorReopen.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(jdbc.queryForObject("SELECT status FROM protocols WHERE id=?", String.class, created.id())).isEqualTo("RECEIVED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM protocol_events WHERE protocol_id=? AND action='REOPENED' AND visibility='PUBLIC'",
                    Integer.class, created.id())).isEqualTo(1);
            ResponseEntity<InteractionView> reopenedPost = http.exchange("/api/v1/public/protocols/" + created.id() + "/interactions",
                    HttpMethod.POST, publicInteractionMultipart("Complemento após reabertura.", null, null,
                            created.trackingToken()), InteractionView.class);
            assertThat(reopenedPost.getStatusCode()).isEqualTo(HttpStatus.CREATED);

            assertThatThrownBy(() -> jdbc.update("UPDATE protocol_interactions SET message='alterada' WHERE id=?", publicInteraction.id()))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("DELETE FROM protocol_interactions WHERE id=?", publicInteraction.id()))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.execute("TRUNCATE protocol_interactions CASCADE"))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.execute("UPDATE protocol_attachment_downloads SET actor='alterado' WHERE attachment_id='"
                    + internalAttachmentId + "'" )).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.execute("DELETE FROM protocol_attachment_downloads WHERE attachment_id='"
                    + internalAttachmentId + "'" )).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.execute("TRUNCATE protocol_attachment_downloads"))
                    .isInstanceOf(DataAccessException.class);

            ResponseEntity<String> deactivate = http.exchange("/api/v1/staff/organization/protocol-subjects/" + subjectId
                            + "/attachment-requirements/" + ruleId,
                    HttpMethod.DELETE, staffHeaders(organizationId), String.class);
            assertThat(deactivate.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            ResponseEntity<TrackingView> noFormats = http.exchange("/api/v1/public/protocols/tracking", HttpMethod.GET,
                    bearer(created.trackingToken()), TrackingView.class);
            assertThat(noFormats.getBody()).isNotNull().extracting(TrackingView::interactionAllowedExtensions).isEqualTo(List.of());
            ResponseEntity<String> staleFormat = http.exchange("/api/v1/public/protocols/" + created.id() + "/interactions",
                    HttpMethod.POST, publicInteractionMultipart("Formato desativado.", "desativado.pdf", pdf,
                            created.trackingToken()), String.class);
            assertThat(staleFormat.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        } finally {
            jdbc.update("DELETE FROM staff_memberships WHERE username='servidor.teste' AND organization_id=?", organizationId);
            for (DepartmentMembership membership : previousDepartmentMemberships)
                jdbc.update("INSERT INTO staff_memberships(username,organization_id,department_id,role_code) VALUES('servidor.teste',?,?,?)",
                        organizationId, membership.departmentId(), membership.roleCode());
            jdbc.update("DELETE FROM organization_memberships WHERE username='servidor.teste' AND organization_id=?", organizationId);
            for (String role : previousRoles) jdbc.update("INSERT INTO organization_memberships(username,organization_id,role_code) VALUES('servidor.teste',?,?)",
                    organizationId, role);
        }
    }

    private HttpEntity<MultiValueMap<String, Object>> multipartRequest(Map<String, Object> request, UUID requirementId,
                                                                         String filename, byte[] bytes) throws Exception {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        parts.add("request", new HttpEntity<>(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request), requestHeaders));
        ByteArrayResource file = new ByteArrayResource(bytes) { @Override public String getFilename() { return filename; } };
        parts.add("files", file);
        parts.add("attachmentRequirementIds", requirementId.toString());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(parts, headers);
    }

    @Test void protocolSubjectCatalogIsTenantScopedOnlineOnlyAndKeepsSnapshots() throws Exception {
        String unique = UUID.randomUUID().toString();
        UUID catalogOrganizationId = UUID.randomUUID();
        String catalogSlug = "catalogo-assuntos-" + unique.substring(0, 12);
        jdbc.update("INSERT INTO organizations(id,slug,name) VALUES(?,?,?)", catalogOrganizationId, catalogSlug, "Catálogo de teste " + unique);
        jdbc.update("INSERT INTO organization_memberships(username,organization_id,role_code) VALUES(?,?,'ADMIN')",
                USER, catalogOrganizationId);
        jdbc.update("INSERT INTO organization_memberships(username,organization_id,role_code) VALUES(?,?,'STAFF')",
                "servidor.teste", catalogOrganizationId);
        UUID catalogDepartmentId = UUID.randomUUID();
        jdbc.update("INSERT INTO departments(id,organization_id,name) VALUES(?,?,?)",
                catalogDepartmentId, catalogOrganizationId, "Setor destinatário " + unique);
        String onlineName = "Solicitação de manutenção " + unique;
        String offlineName = "Atendimento presencial " + unique;
        UUID betaSubjectId = jdbc.queryForObject("SELECT id FROM protocol_subjects WHERE organization_id=? AND online_available=true ORDER BY id LIMIT 1",
                UUID.class, LocalDemoData.ORG_BETA);

        ResponseEntity<Map> onlineResponse = http.exchange("/api/v1/staff/organization/protocol-subjects", HttpMethod.POST,
                staffHeaders(catalogOrganizationId, Map.of("name", onlineName, "onlineAvailable", true,
                        "destinationDepartmentId", catalogDepartmentId)), Map.class);
        assertThat(onlineResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID onlineId = UUID.fromString(Objects.requireNonNull(onlineResponse.getBody()).get("id").toString());

        ResponseEntity<Map> offlineResponse = http.exchange("/api/v1/staff/organization/protocol-subjects", HttpMethod.POST,
                staffHeaders(catalogOrganizationId, Map.of("name", offlineName, "onlineAvailable", false)), Map.class);
        assertThat(offlineResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID offlineId = UUID.fromString(Objects.requireNonNull(offlineResponse.getBody()).get("id").toString());

        ResponseEntity<String> nonAdminWrite = http.exchange("/api/v1/staff/organization/protocol-subjects", HttpMethod.POST,
                staffHeaders(catalogOrganizationId, Map.of("name", "Sem permissão " + unique, "onlineAvailable", true,
                        "destinationDepartmentId", catalogDepartmentId), "servidor.teste"), String.class);
        assertThat(nonAdminWrite.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<List<Map<String, Object>>> publicSubjects = http.exchange(
                "/api/v1/public/organizations/" + catalogSlug + "/protocol-subjects", HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {});
        assertThat(publicSubjects.getBody()).isNotNull()
                .anySatisfy(row -> assertThat(row).containsEntry("id", onlineId.toString()).containsEntry("name", onlineName).containsEntry("onlineAvailable", true))
                .noneSatisfy(row -> assertThat(row.get("id")).isEqualTo(offlineId.toString()));

        long counterBeforeRejectedRequests = counterNextValue(catalogOrganizationId);
        ResponseEntity<String> balconyRejected = http.postForEntity("/api/v1/public/protocols",
                openRequest(catalogSlug, offlineId), String.class);
        assertThat(balconyRejected.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<String> crossTenantRejected = http.postForEntity("/api/v1/public/protocols",
                openRequest(catalogSlug, betaSubjectId), String.class);
        assertThat(crossTenantRejected.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<String> unknownSubjectRejected = http.postForEntity("/api/v1/public/protocols",
                openRequest(catalogSlug, UUID.randomUUID()), String.class);
        assertThat(unknownSubjectRejected.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        HttpHeaders invalidHeaders = new HttpHeaders();
        invalidHeaders.setBasicAuth(USER, PASSWORD);
        invalidHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> missingSubjectRejected = http.postForEntity("/api/v1/public/protocols",
                new HttpEntity<>(Map.of("organizationSlug", catalogSlug, "applicantName", "Pessoa de Teste",
                        "applicantEmail", "cidadao@example.test", "initialMessage", "Solicitação sem assunto."), invalidHeaders), String.class);
        assertThat(missingSubjectRejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(counterNextValue(catalogOrganizationId)).isEqualTo(counterBeforeRejectedRequests);

        UUID legacyId = UUID.randomUUID();
        String legacySubject = "Assunto de protocolo legado";
        String legacyToken = "legacy-token-" + unique;
        String legacyNumber = "OLD-" + unique.substring(0, 12);
        jdbc.update("""
                        INSERT INTO protocols(id,organization_id,protocol_number,subject,applicant_name,applicant_email,
                                              initial_message,status,tracking_token_hash)
                        VALUES(?,?,?,?,?,?,?,'OPEN',?)
                        """, legacyId, catalogOrganizationId, legacyNumber, legacySubject, "Pessoa Legada",
                "legado@example.test", "Registro importado antes do catálogo.", TrackingTokens.hash(legacyToken));

        ResponseEntity<Created> createdResponse = http.postForEntity("/api/v1/public/protocols",
                openRequest(catalogSlug, onlineId), Created.class);
        assertThat(createdResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Created created = Objects.requireNonNull(createdResponse.getBody());
        assertThat(created.protocolNumber()).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT protocol_subject_id FROM protocols WHERE id=?", UUID.class, created.id())).isEqualTo(onlineId);
        assertThat(jdbc.queryForObject("SELECT subject FROM protocols WHERE id=?", String.class, created.id())).isEqualTo(onlineName);
        assertThat(counterNextValue(catalogOrganizationId)).isEqualTo(counterBeforeRejectedRequests + 1);

        ResponseEntity<Map> renamed = http.exchange("/api/v1/staff/organization/protocol-subjects/" + onlineId, HttpMethod.PUT,
                staffHeaders(catalogOrganizationId, Map.of("name", onlineName + " atualizado", "onlineAvailable", false)), Map.class);
        assertThat(renamed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(renamed.getBody()).containsEntry("destinationDepartmentId", catalogDepartmentId.toString());
        assertThat(jdbc.queryForObject("SELECT subject FROM protocols WHERE id=?", String.class, created.id())).isEqualTo(onlineName);
        assertThat(jdbc.queryForObject("SELECT protocol_subject_id FROM protocols WHERE id=?", UUID.class, created.id())).isEqualTo(onlineId);
        TrackingView tracked = http.exchange("/api/v1/public/protocols/tracking", HttpMethod.GET,
                bearer(created.trackingToken()), TrackingView.class).getBody();
        assertThat(tracked).isNotNull().extracting(TrackingView::subject).isEqualTo(onlineName);

        assertThat(jdbc.queryForObject("SELECT subject FROM protocols WHERE id=?", String.class, legacyId)).isEqualTo(legacySubject);
        assertThat(jdbc.queryForObject("SELECT protocol_subject_id FROM protocols WHERE id=?", UUID.class, legacyId)).isNull();
        assertThat(jdbc.queryForObject("SELECT initial_assigned_department_name FROM protocols WHERE id=?", String.class, legacyId)).isNull();
        assertThat(receiptText(legacyId, legacyToken)).doesNotContain("Setor destinatário:");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_audit_events WHERE organization_id=? AND entity_type='PROTOCOL_SUBJECT' AND entity_id IN (?,?)",
                Integer.class, catalogOrganizationId, onlineId, offlineId)).isEqualTo(3);
    }

    @Test void departmentAdministrationIsTenantScopedAdminOnlyAndAudited() {
        String name = "Núcleo de Atendimento " + UUID.randomUUID();
        ResponseEntity<Map> created = http.exchange("/api/v1/staff/departments", HttpMethod.POST,
                staffHeaders(LocalDemoData.ORG_ALPHA, Map.of("name", name)), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID alphaDepartmentId = UUID.fromString(Objects.requireNonNull(created.getBody()).get("id").toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM staff_memberships WHERE username=? AND organization_id=? AND department_id=?",
                Integer.class, USER, LocalDemoData.ORG_ALPHA, alphaDepartmentId)).isZero();

        ResponseEntity<List<Map<String, Object>>> alphaDepartments = http.exchange("/api/v1/staff/departments",
                HttpMethod.GET, staffHeaders(LocalDemoData.ORG_ALPHA), new ParameterizedTypeReference<>() {});
        assertThat(alphaDepartments.getBody()).isNotNull().anyMatch(row -> alphaDepartmentId.toString().equals(row.get("id")));
        ResponseEntity<List<Map<String, Object>>> betaDepartments = http.exchange("/api/v1/staff/departments",
                HttpMethod.GET, staffHeaders(LocalDemoData.ORG_BETA), new ParameterizedTypeReference<>() {});
        assertThat(betaDepartments.getBody()).isNotNull().noneMatch(row -> alphaDepartmentId.toString().equals(row.get("id")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_audit_events WHERE organization_id=? AND entity_id=? AND action='DEPARTMENT_CREATED'",
                Integer.class, LocalDemoData.ORG_ALPHA, alphaDepartmentId)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("UPDATE organization_audit_events SET summary='alterado' WHERE entity_id=?", alphaDepartmentId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM organization_audit_events WHERE entity_id=?", alphaDepartmentId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE organization_audit_events"))
                .isInstanceOf(DataAccessException.class);

        ResponseEntity<String> duplicate = http.exchange("/api/v1/staff/departments", HttpMethod.POST,
                staffHeaders(LocalDemoData.ORG_ALPHA, Map.of("name", name.toLowerCase(Locale.ROOT))), String.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        try {
            jdbc.update("UPDATE staff_memberships SET role_code='ADMIN' WHERE username=? AND organization_id=? AND department_id=?",
                    USER, LocalDemoData.ORG_ALPHA, LocalDemoData.ALPHA_PROTOCOL);
            jdbc.update("DELETE FROM organization_memberships WHERE username=? AND organization_id=?", USER, LocalDemoData.ORG_ALPHA);
            ResponseEntity<Map> capabilities = http.exchange("/api/v1/staff/organization/department-management",
                    HttpMethod.GET, staffHeaders(LocalDemoData.ORG_ALPHA), Map.class);
            assertThat(Objects.requireNonNull(capabilities.getBody()).get("canManageDepartments")).isEqualTo(false);
            int departmentAuditCountBeforeForbidden = jdbc.queryForObject(
                    "SELECT count(*) FROM organization_audit_events WHERE organization_id=? AND action='DEPARTMENT_CREATED'",
                    Integer.class, LocalDemoData.ORG_ALPHA);
            ResponseEntity<String> forbidden = http.exchange("/api/v1/staff/departments", HttpMethod.POST,
                    staffHeaders(LocalDemoData.ORG_ALPHA, Map.of("name", "Não autorizado")), String.class);
            assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM organization_audit_events WHERE organization_id=? AND action='DEPARTMENT_CREATED'",
                    Integer.class, LocalDemoData.ORG_ALPHA)).isEqualTo(departmentAuditCountBeforeForbidden);
        } finally {
            jdbc.update("UPDATE staff_memberships SET role_code='STAFF' WHERE username=? AND organization_id=? AND department_id=?",
                    USER, LocalDemoData.ORG_ALPHA, LocalDemoData.ALPHA_PROTOCOL);
            jdbc.update("INSERT INTO organization_memberships(username,organization_id,role_code) VALUES(?,?,'ADMIN') ON CONFLICT DO NOTHING",
                    USER, LocalDemoData.ORG_ALPHA);
        }

        try {
            jdbc.update("INSERT INTO organization_memberships(username,organization_id,role_code) VALUES(?,?,'ADMIN') ON CONFLICT DO NOTHING",
                    USER, LocalDemoData.ORG_BETA);
            ResponseEntity<Map> sameNameInOtherOrganization = http.exchange("/api/v1/staff/departments", HttpMethod.POST,
                    staffHeaders(LocalDemoData.ORG_BETA, Map.of("name", name)), Map.class);
            assertThat(sameNameInOtherOrganization.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            UUID betaDepartmentId = UUID.fromString(Objects.requireNonNull(sameNameInOtherOrganization.getBody()).get("id").toString());
            assertThat(betaDepartmentId).isNotEqualTo(alphaDepartmentId);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_audit_events WHERE organization_id=? AND action='DEPARTMENT_CREATED'",
                    Integer.class, LocalDemoData.ORG_BETA)).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM organization_memberships WHERE username=? AND organization_id=?", USER, LocalDemoData.ORG_BETA);
        }
    }

    @Test void organizationMembershipWithUnscopedFutureRoleDoesNotGrantTenantAccess() {
        UUID organizationId = UUID.randomUUID();
        String slug = "future-role-" + UUID.randomUUID();
        jdbc.update("INSERT INTO organizations(id,slug,name) VALUES(?,?,?)", organizationId, slug, "Role sem escopo definido");
        jdbc.update("INSERT INTO organization_memberships(username,organization_id,role_code) VALUES(?,?,'FUTURE_SCOPE_UNDEFINED')",
                USER, organizationId);

        try {
            ResponseEntity<String> response = http.exchange("/api/v1/staff/protocols", HttpMethod.GET,
                    staffHeaders(organizationId), String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        } finally {
            jdbc.update("DELETE FROM organization_memberships WHERE username=? AND organization_id=?", USER, organizationId);
            jdbc.update("DELETE FROM organizations WHERE id=?", organizationId);
        }
    }

    @Test void departmentHierarchyIsTenantScopedAndHasNoConfiguredDepthLimit() {
        UUID organizationId = UUID.randomUUID();
        String slug = "hierarchy-" + UUID.randomUUID();
        jdbc.update("INSERT INTO organizations(id,slug,name) VALUES(?,?,?)", organizationId, slug, "Organização hierárquica de teste");
        jdbc.update("INSERT INTO organization_memberships(username,organization_id,role_code) VALUES(?,?,'ADMIN')", USER, organizationId);

        Map<String, Object> root = createDepartment(organizationId, "Raiz administrativa", null);
        UUID parentId = UUID.fromString(root.get("id").toString());
        jdbc.update("INSERT INTO staff_memberships(username,organization_id,department_id,role_code) VALUES(?,?,?,'ADMIN')",
                USER, organizationId, parentId);
        Map<String, Object> firstChild = createDepartment(organizationId, "Subsetor 1", parentId);
        parentId = UUID.fromString(firstChild.get("id").toString());
        int depth = 32;
        for (int level = 2; level <= depth; level++) {
            Map<String, Object> child = createDepartment(organizationId, "Subsetor " + level, parentId);
            parentId = UUID.fromString(child.get("id").toString());
        }

        ResponseEntity<String> crossTenantParent = http.exchange("/api/v1/staff/departments", HttpMethod.POST,
                staffHeaders(organizationId, Map.of("name", "Pai externo", "parentDepartmentId", LocalDemoData.ALPHA_PROTOCOL)), String.class);
        assertThat(crossTenantParent.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<List<Map<String, Object>>> treeResponse = http.exchange("/api/v1/staff/organization/organogram",
                HttpMethod.GET, staffHeaders(organizationId), new ParameterizedTypeReference<>() {});
        assertThat(treeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(treeResponse.getBody()).isNotNull().hasSize(1);
        Map<String, Object> node = treeResponse.getBody().get(0);
        assertThat(node.get("id")).isEqualTo(root.get("id"));
        List<Map<String, Object>> assignedStaff = (List<Map<String, Object>>) node.get("staff");
        assertThat(assignedStaff).anySatisfy(member -> {
            assertThat(member.get("username")).isEqualTo(USER);
            assertThat(member.get("roleCode")).isEqualTo("ADMIN");
        });
        for (int level = 1; level <= depth; level++) {
            List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
            assertThat(children).as("depth %s must remain represented", level).hasSize(1);
            node = children.get(0);
            assertThat(node.get("name")).isEqualTo("Subsetor " + level);
        }
        assertThat((List<?>) node.get("children")).isEmpty();

        ResponseEntity<List<Map<String, Object>>> otherOrganizationTree = http.exchange("/api/v1/staff/organization/organogram",
                HttpMethod.GET, staffHeaders(LocalDemoData.ORG_ALPHA), new ParameterizedTypeReference<>() {});
        assertThat(otherOrganizationTree.getBody()).isNotNull().noneMatch(item -> root.get("id").equals(item.get("id")));
    }

    @Test void staffProfilesAndPresenceAreTenantScopedAndAudited() {
        jdbc.update("INSERT INTO staff_memberships(username,organization_id,department_id,role_code) VALUES(?,?,?,'STAFF')",
                "offline-user", LocalDemoData.ORG_ALPHA, LocalDemoData.ALPHA_PROTOCOL);
        jdbc.update("INSERT INTO staff_profiles(organization_id,username,registration_date_source,last_online_at) " +
                        "VALUES(?,?,'UNKNOWN',now()-interval '5 minutes')",
                LocalDemoData.ORG_ALPHA, "offline-user");
        jdbc.update("INSERT INTO staff_memberships(username,organization_id,department_id,role_code) VALUES(?,?,?,'STAFF')",
                "never-seen-user", LocalDemoData.ORG_ALPHA, LocalDemoData.ALPHA_PROTOCOL);

        ResponseEntity<Map> heartbeat = http.exchange("/api/v1/staff/session/heartbeat", HttpMethod.POST,
                staffHeaders(LocalDemoData.ORG_ALPHA, Map.of()), Map.class);
        assertThat(heartbeat.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(heartbeat.getBody()).containsEntry("presenceStatus", "ONLINE");

        Map<String, Object> profileRequest = Map.of("fullName", "Joana Servidora", "positionTitle", "Analista de Protocolo",
                "registrationDate", "2022-05-19");
        ResponseEntity<Map> updatedProfile = http.exchange("/api/v1/staff/organization/staff/servidor", HttpMethod.PUT,
                staffHeaders(LocalDemoData.ORG_ALPHA, profileRequest), Map.class);
        assertThat(updatedProfile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updatedProfile.getBody()).containsEntry("fullName", "Joana Servidora")
                .containsEntry("positionTitle", "Analista de Protocolo")
                .containsEntry("registrationDate", "2022-05-19")
                .containsEntry("registrationDateSource", "IMPORTED");

        ResponseEntity<List<Map<String, Object>>> alphaTree = http.exchange("/api/v1/staff/organization/organogram",
                HttpMethod.GET, staffHeaders(LocalDemoData.ORG_ALPHA), new ParameterizedTypeReference<>() {});
        Map<String, Object> onlineMember = findStaff(alphaTree.getBody(), "servidor");
        assertThat(onlineMember).containsEntry("fullName", "Joana Servidora")
                .containsEntry("positionTitle", "Analista de Protocolo")
                .containsEntry("registrationDate", "2022-05-19")
                .containsEntry("registrationDateSource", "IMPORTED")
                .containsEntry("presenceStatus", "ONLINE");
        assertThat(onlineMember.get("lastOnlineAt")).isNotNull();
        assertThat(findStaff(alphaTree.getBody(), "offline-user").get("presenceStatus")).isEqualTo("OFFLINE");
        assertThat(findStaff(alphaTree.getBody(), "never-seen-user").get("presenceStatus")).isEqualTo("NOT_OBSERVED");
        assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM organization_audit_events ae
                        JOIN staff_profiles sp ON sp.id=ae.entity_id
                         WHERE ae.organization_id=? AND sp.username='servidor'
                           AND ae.entity_type='STAFF_PROFILE' AND ae.action='STAFF_PROFILE_UPDATED'
                        """, Integer.class, LocalDemoData.ORG_ALPHA)).isEqualTo(1);

        UUID anotherOrganization = UUID.randomUUID();
        UUID anotherDepartment = UUID.randomUUID();
        String slug = "profile-scope-" + UUID.randomUUID();
        jdbc.update("INSERT INTO organizations(id,slug,name) VALUES(?,?,?)", anotherOrganization, slug, "Outra organização");
        jdbc.update("INSERT INTO departments(id,organization_id,name) VALUES(?,?,?)", anotherDepartment, anotherOrganization, "Setor externo");
        jdbc.update("INSERT INTO organization_memberships(username,organization_id,role_code) VALUES(?,?,'ADMIN')", USER, anotherOrganization);
        ResponseEntity<String> crossTenantProfile = http.exchange("/api/v1/staff/organization/staff/offline-user", HttpMethod.PUT,
                staffHeaders(anotherOrganization, profileRequest), String.class);
        assertThat(crossTenantProfile.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        UUID staffOnlyOrganization = UUID.randomUUID();
        UUID staffOnlyDepartment = UUID.randomUUID();
        String staffOnlySlug = "profile-forbidden-" + UUID.randomUUID();
        jdbc.update("INSERT INTO organizations(id,slug,name) VALUES(?,?,?)", staffOnlyOrganization, staffOnlySlug, "Organização sem administrador");
        jdbc.update("INSERT INTO departments(id,organization_id,name) VALUES(?,?,?)", staffOnlyDepartment, staffOnlyOrganization, "Setor comum");
        jdbc.update("INSERT INTO staff_memberships(username,organization_id,department_id,role_code) VALUES(?,?,?,'STAFF')",
                USER, staffOnlyOrganization, staffOnlyDepartment);
        ResponseEntity<String> staffProfileWrite = http.exchange("/api/v1/staff/organization/staff/servidor", HttpMethod.PUT,
                staffHeaders(staffOnlyOrganization, profileRequest), String.class);
        assertThat(staffProfileWrite.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        jdbc.update("DELETE FROM organization_memberships WHERE username=? AND organization_id=?", USER, anotherOrganization);
        jdbc.update("DELETE FROM staff_profiles WHERE organization_id=?", anotherOrganization);
        jdbc.update("DELETE FROM departments WHERE organization_id=?", anotherOrganization);
        jdbc.update("DELETE FROM organizations WHERE id=?", anotherOrganization);
        jdbc.update("DELETE FROM staff_memberships WHERE organization_id=?", staffOnlyOrganization);
        jdbc.update("DELETE FROM staff_profiles WHERE organization_id=?", staffOnlyOrganization);
        jdbc.update("DELETE FROM departments WHERE organization_id=?", staffOnlyOrganization);
        jdbc.update("DELETE FROM organizations WHERE id=?", staffOnlyOrganization);
        jdbc.update("DELETE FROM staff_memberships WHERE organization_id=? AND username IN ('offline-user','never-seen-user')",
                LocalDemoData.ORG_ALPHA);
        jdbc.update("DELETE FROM staff_profiles WHERE organization_id=? AND username IN ('offline-user','never-seen-user')",
                LocalDemoData.ORG_ALPHA);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findStaff(List<Map<String, Object>> roots, String username) {
        Deque<Map<String, Object>> pending = new ArrayDeque<>(roots == null ? List.of() : roots);
        while (!pending.isEmpty()) {
            Map<String, Object> node = pending.pop();
            List<Map<String, Object>> members = (List<Map<String, Object>>) node.get("staff");
            if (members != null) {
                Optional<Map<String, Object>> match = members.stream()
                        .filter(member -> username.equals(member.get("username"))).findFirst();
                if (match.isPresent()) return match.get();
            }
            List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
            if (children != null) pending.addAll(children);
        }
        throw new AssertionError("Staff member not found in the scoped organization tree: " + username);
    }

    private Map<String, Object> createDepartment(UUID organizationId, String name, UUID parentId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("parentDepartmentId", parentId);
        ResponseEntity<Map> response = http.exchange("/api/v1/staff/departments", HttpMethod.POST,
                staffHeaders(organizationId, body), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return Objects.requireNonNull(response.getBody());
    }

    @Test void staffProtocolInboxIsLimitedToActiveDepartmentsUnlessOrganizationAdmin() {
        UUID organizationId = UUID.randomUUID();
        UUID memberDepartmentId = UUID.randomUUID();
        UUID otherDepartmentId = UUID.randomUUID();
        String slug = "scope-" + UUID.randomUUID();
        jdbc.update("INSERT INTO organizations(id,slug,name) VALUES(?,?,?)", organizationId, slug, "Organização de teste de escopo");
        jdbc.update("INSERT INTO departments(id,organization_id,name,active) VALUES(?,?,?,true)", memberDepartmentId, organizationId, "Setor autorizado");
        jdbc.update("INSERT INTO departments(id,organization_id,name,active) VALUES(?,?,?,true)", otherDepartmentId, organizationId, "Setor restrito");
        jdbc.update("INSERT INTO staff_memberships(username,organization_id,department_id,role_code,active) VALUES(?,?,?,'STAFF',true)",
                USER, organizationId, memberDepartmentId);
        assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM staff_memberships sm
                        JOIN departments d ON d.organization_id=sm.organization_id AND d.id=sm.department_id
                         WHERE sm.username=? AND sm.organization_id=? AND sm.active=true AND d.active=true
                        """, Integer.class, USER, organizationId)).isEqualTo(1);

        Created created = create(slug, "Demanda de outro setor", "Conteúdo protegido por setor.");
        jdbc.update("UPDATE protocols SET assigned_department_id=? WHERE id=? AND organization_id=?",
                otherDepartmentId, created.id(), organizationId);

        var withoutSectorAccess = http.exchange("/api/v1/staff/protocols", HttpMethod.GET,
                staffHeaders(organizationId), new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        assertThat(withoutSectorAccess.getBody()).isNotNull()
                .noneMatch(row -> created.id().toString().equals(row.get("id")));

        jdbc.update("INSERT INTO staff_memberships(username,organization_id,department_id,role_code) VALUES(?,?,?,'STAFF')",
                USER, organizationId, otherDepartmentId);
        var withSectorAccess = http.exchange("/api/v1/staff/protocols", HttpMethod.GET,
                staffHeaders(organizationId), new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        assertThat(withSectorAccess.getBody()).isNotNull()
                .anyMatch(row -> created.id().toString().equals(row.get("id")));

        jdbc.update("DELETE FROM staff_memberships WHERE username=? AND organization_id=? AND department_id=?",
                USER, organizationId, otherDepartmentId);
        jdbc.update("INSERT INTO organization_memberships(username,organization_id,role_code) VALUES(?,?,'ADMIN')",
                USER, organizationId);
        var asOrganizationAdmin = http.exchange("/api/v1/staff/protocols", HttpMethod.GET,
                staffHeaders(organizationId), new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        assertThat(asOrganizationAdmin.getBody()).isNotNull()
                .anyMatch(row -> created.id().toString().equals(row.get("id")));
    }

    @Test void protocolInboxReadReceiptIsIdempotentAndTenantAndDepartmentScoped() {
        UUID organizationId = LocalDemoData.ORG_ALPHA;
        Created created = create("municipio-alfa", "Recibo de leitura " + UUID.randomUUID(),
                "Protocolo fictício para verificar o indicador de leitura.");
        UUID assignedDepartment = jdbc.queryForObject(
                "SELECT assigned_department_id FROM protocols WHERE organization_id=? AND id=?",
                UUID.class, organizationId, created.id());
        assertThat(assignedDepartment).isEqualTo(LocalDemoData.ALPHA_PROTOCOL);
        String endpoint = "/api/v1/staff/protocols/" + created.id() + "/read";

        ResponseEntity<ProtocolSummary[]> initialInbox = http.exchange("/api/v1/staff/protocols", HttpMethod.GET,
                staffHeaders(organizationId), ProtocolSummary[].class);
        assertThat(initialInbox.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("private, no-store");
        ProtocolSummary initial = Arrays.stream(Objects.requireNonNull(initialInbox.getBody()))
                .filter(row -> row.id().equals(created.id())).findFirst().orElseThrow();
        assertThat(initial.readStatus()).isEqualTo(ProtocolReadStatus.UNREAD_BY_DEPARTMENT);
        assertThat(readReceiptCount(organizationId, created.id())).isZero();
        int initialEventCount = jdbc.queryForObject("SELECT count(*) FROM protocol_events WHERE organization_id=? AND protocol_id=?",
                Integer.class, organizationId, created.id());

        // An organization administrator without a membership in the currently assigned sector
        // can still use the existing broad inbox, but cannot create a sector read receipt.
        jdbc.update("DELETE FROM staff_memberships WHERE username=? AND organization_id=? AND department_id=?",
                USER, organizationId, assignedDepartment);
        try {
            ResponseEntity<String> adminWithoutDepartment = http.exchange(endpoint, HttpMethod.POST,
                    staffHeaders(organizationId), String.class);
            assertThat(adminWithoutDepartment.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(readReceiptCount(organizationId, created.id())).isZero();
            assertThat(protocolEventCount(organizationId, created.id())).isEqualTo(initialEventCount);
        } finally {
            jdbc.update("INSERT INTO staff_memberships(username,organization_id,department_id,role_code,active) " +
                            "VALUES(?,?,?,'STAFF',true) ON CONFLICT(username,organization_id,department_id) DO NOTHING",
                    USER, organizationId, assignedDepartment);
        }

        // A user with a different active sector membership in this organization is also denied.
        jdbc.update("UPDATE protocols SET assigned_department_id=? WHERE organization_id=? AND id=?",
                LocalDemoData.ALPHA_WORKS, organizationId, created.id());
        try {
            ResponseEntity<String> otherDepartment = http.exchange(endpoint, HttpMethod.POST,
                    staffHeaders(organizationId, null, "servidor.teste"), String.class);
            assertThat(otherDepartment.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(readReceiptCount(organizationId, created.id())).isZero();
            assertThat(protocolEventCount(organizationId, created.id())).isEqualTo(initialEventCount);
        } finally {
            jdbc.update("UPDATE protocols SET assigned_department_id=? WHERE organization_id=? AND id=?",
                    assignedDepartment, organizationId, created.id());
        }

        ResponseEntity<ProtocolReadReceipt> first = http.exchange(endpoint, HttpMethod.POST,
                staffHeaders(organizationId), ProtocolReadReceipt.class);
        ResponseEntity<ProtocolReadReceipt> retry = http.exchange(endpoint, HttpMethod.POST,
                staffHeaders(organizationId), ProtocolReadReceipt.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("private, no-store");
        assertThat(first.getBody()).isNotNull().satisfies(receipt -> {
            assertThat(receipt.protocolId()).isEqualTo(created.id());
            assertThat(receipt.departmentId()).isEqualTo(assignedDepartment);
            assertThat(receipt.readStatus()).isEqualTo(ProtocolReadStatus.READ_BY_ME);
        });
        assertThat(retry.getBody()).isNotNull().satisfies(receipt ->
                assertThat(receipt.readAt()).isEqualTo(Objects.requireNonNull(first.getBody()).readAt()));
        assertThat(readReceiptCount(organizationId, created.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT username FROM protocol_read_receipts WHERE organization_id=? AND protocol_id=?",
                String.class, organizationId, created.id())).isEqualTo(USER);

        // Listing remains a read-only projection for both people in the same department.
        ResponseEntity<ProtocolSummary[]> otherInbox = http.exchange("/api/v1/staff/protocols", HttpMethod.GET,
                staffHeaders(organizationId, null, "servidor.teste"), ProtocolSummary[].class);
        ProtocolSummary observedByOther = Arrays.stream(Objects.requireNonNull(otherInbox.getBody()))
                .filter(row -> row.id().equals(created.id())).findFirst().orElseThrow();
        assertThat(observedByOther.readStatus()).isEqualTo(ProtocolReadStatus.READ_BY_OTHER);
        assertThat(readReceiptCount(organizationId, created.id())).isEqualTo(1);

        ResponseEntity<ProtocolReadReceipt> secondReader = http.exchange(endpoint, HttpMethod.POST,
                staffHeaders(organizationId, null, "servidor.teste"), ProtocolReadReceipt.class);
        assertThat(secondReader.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readReceiptCount(organizationId, created.id())).isEqualTo(2);
        ResponseEntity<ProtocolSummary[]> currentInbox = http.exchange("/api/v1/staff/protocols", HttpMethod.GET,
                staffHeaders(organizationId), ProtocolSummary[].class);
        ProtocolSummary nowReadByOther = Arrays.stream(Objects.requireNonNull(currentInbox.getBody()))
                .filter(row -> row.id().equals(created.id())).findFirst().orElseThrow();
        assertThat(nowReadByOther.readStatus()).isEqualTo(ProtocolReadStatus.READ_BY_ME);
        ResponseEntity<ProtocolSummary[]> memberInbox = http.exchange("/api/v1/staff/protocols", HttpMethod.GET,
                staffHeaders(organizationId, null, "servidor.teste"), ProtocolSummary[].class);
        ProtocolSummary readByMember = Arrays.stream(Objects.requireNonNull(memberInbox.getBody()))
                .filter(row -> row.id().equals(created.id())).findFirst().orElseThrow();
        assertThat(readByMember.readStatus()).isEqualTo(ProtocolReadStatus.READ_BY_ME);
        assertThat(readReceiptCount(organizationId, created.id())).isEqualTo(2);

        // Reassignment changes the sector whose receipts are relevant to the inbox indicator.
        jdbc.update("UPDATE protocols SET assigned_department_id=? WHERE organization_id=? AND id=?",
                LocalDemoData.ALPHA_WORKS, organizationId, created.id());
        ResponseEntity<ProtocolSummary[]> reassignedInbox = http.exchange("/api/v1/staff/protocols", HttpMethod.GET,
                staffHeaders(organizationId), ProtocolSummary[].class);
        ProtocolSummary unreadInNewDepartment = Arrays.stream(Objects.requireNonNull(reassignedInbox.getBody()))
                .filter(row -> row.id().equals(created.id())).findFirst().orElseThrow();
        assertThat(unreadInNewDepartment.readStatus()).isEqualTo(ProtocolReadStatus.UNREAD_BY_DEPARTMENT);
        assertThat(readReceiptCount(organizationId, created.id())).isEqualTo(2);
        ResponseEntity<ProtocolReadReceipt> newDepartmentRead = http.exchange(endpoint, HttpMethod.POST,
                staffHeaders(organizationId), ProtocolReadReceipt.class);
        assertThat(newDepartmentRead.getBody()).isNotNull().satisfies(receipt ->
                assertThat(receipt.departmentId()).isEqualTo(LocalDemoData.ALPHA_WORKS));
        assertThat(readReceiptCount(organizationId, created.id())).isEqualTo(3);
        jdbc.update("UPDATE protocols SET assigned_department_id=? WHERE organization_id=? AND id=?",
                assignedDepartment, organizationId, created.id());

        ResponseEntity<String> crossTenant = http.exchange(endpoint, HttpMethod.POST,
                staffHeaders(LocalDemoData.ORG_BETA), String.class);
        assertThat(crossTenant.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(readReceiptCount(organizationId, created.id())).isEqualTo(3);
        assertThat(protocolEventCount(organizationId, created.id())).isEqualTo(initialEventCount);
    }

    private int readReceiptCount(UUID organizationId, UUID protocolId) {
        return jdbc.queryForObject("SELECT count(*) FROM protocol_read_receipts WHERE organization_id=? AND protocol_id=?",
                Integer.class, organizationId, protocolId);
    }

    private int protocolEventCount(UUID organizationId, UUID protocolId) {
        return jdbc.queryForObject("SELECT count(*) FROM protocol_events WHERE organization_id=? AND protocol_id=?",
                Integer.class, organizationId, protocolId);
    }

    @Test void departmentNameQueryAndIndexIgnoreLegacySurroundingWhitespace() {
        String legacyStoredName = "  Legado Espacado " + UUID.randomUUID() + "  ";
        UUID legacyId = UUID.randomUUID();
        jdbc.update("INSERT INTO departments(id,organization_id,name) VALUES(?,?,?)",
                legacyId, LocalDemoData.ORG_ALPHA, legacyStoredName);
        try {
            ResponseEntity<String> queryConflict = http.exchange("/api/v1/staff/departments", HttpMethod.POST,
                    staffHeaders(LocalDemoData.ORG_ALPHA, Map.of("name", legacyStoredName.trim().toUpperCase(Locale.ROOT))), String.class);
            assertThat(queryConflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThatThrownBy(() -> jdbc.update("INSERT INTO departments(id,organization_id,name) VALUES(?,?,?)",
                    UUID.randomUUID(), LocalDemoData.ORG_ALPHA, "  " + legacyStoredName.trim().toLowerCase(Locale.ROOT) + "  "))
                    .isInstanceOf(DataAccessException.class);
            assertThat(jdbc.queryForObject("SELECT name FROM departments WHERE id=?", String.class, legacyId))
                    .isEqualTo(legacyStoredName);
        } finally {
            jdbc.update("DELETE FROM departments WHERE id=? AND organization_id=?", legacyId, LocalDemoData.ORG_ALPHA);
        }
    }

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
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE protocol_events"))
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

    @Test void protocolSubjectParticipantFieldsAreTenantScopedAndAdminOnly() {
        UUID organizationId = LocalDemoData.ORG_ALPHA;
        UUID subjectId = jdbc.queryForObject("""
                SELECT id FROM protocol_subjects
                 WHERE organization_id=? AND lower(btrim(name))=lower('Solicitação geral')
                """, UUID.class, organizationId);
        UUID fieldId = UUID.randomUUID();
        UUID foreignFieldId = UUID.randomUUID();
        UUID foreignSubjectId = jdbc.queryForObject(
                "SELECT id FROM protocol_subjects WHERE organization_id=? ORDER BY id LIMIT 1",
                UUID.class, LocalDemoData.ORG_BETA);
        String subjectPath = "/api/v1/staff/organization/protocol-subjects/" + subjectId;
        String collectionPath = subjectPath + "/participant-fields";
        Map<String, Object> original = Map.of("key", "contact_email", "label", "E-mail de contato",
                "inputType", "EMAIL", "required", true, "order", 0);
        Map<String, Object> updated = Map.of("label", "Telefone", "inputType", "PHONE", "required", false, "order", 2);
        record OrganizationRoleState(String roleCode, boolean active) {}
        List<OrganizationRoleState> previousOrganizationRoles = jdbc.query("""
                        SELECT role_code, active FROM organization_memberships
                         WHERE organization_id=? AND username=? ORDER BY role_code
                        """, (rs, row) -> new OrganizationRoleState(rs.getString("role_code"), rs.getBoolean("active")),
                organizationId, "servidor.teste");
        record StaffRoleState(UUID departmentId, String roleCode, boolean active) {}
        List<StaffRoleState> previousDepartmentRoles = jdbc.query("""
                        SELECT department_id, role_code, active FROM staff_memberships
                         WHERE organization_id=? AND username=? ORDER BY department_id, role_code
                        """, (rs, row) -> new StaffRoleState(rs.getObject("department_id", UUID.class),
                        rs.getString("role_code"), rs.getBoolean("active")), organizationId, "servidor.teste");

        try {
            ResponseEntity<List<Map<String, Object>>> initial = http.exchange(collectionPath, HttpMethod.GET,
                    staffHeaders(organizationId), new ParameterizedTypeReference<>() {});
            assertThat(initial.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(initial.getBody()).isEmpty();

            ResponseEntity<Map> created = http.exchange(collectionPath, HttpMethod.POST,
                    staffHeaders(organizationId, original), Map.class);
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            fieldId = UUID.fromString(Objects.requireNonNull(created.getBody()).get("id").toString());
            assertThat(created.getBody()).containsEntry("subjectId", subjectId.toString())
                    .containsEntry("key", "contact_email").containsEntry("inputType", "EMAIL")
                    .containsEntry("required", true).containsEntry("order", 0);

            ResponseEntity<List<Map<String, Object>>> listed = http.exchange(collectionPath, HttpMethod.GET,
                    staffHeaders(organizationId), new ParameterizedTypeReference<>() {});
            assertThat(listed.getBody()).hasSize(1);
            assertThat(Objects.requireNonNull(listed.getBody()).getFirst())
                    .containsEntry("id", fieldId.toString()).containsEntry("label", "E-mail de contato");

            ResponseEntity<String> duplicateKey = http.exchange(collectionPath, HttpMethod.POST,
                    staffHeaders(organizationId, original), String.class);
            assertThat(duplicateKey.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

            ResponseEntity<String> invalidKey = http.exchange(collectionPath, HttpMethod.POST,
                    staffHeaders(organizationId, Map.of("key", "Invalid-Key", "label", "Rótulo",
                            "inputType", "TEXT", "required", false, "order", 0)), String.class);
            assertThat(invalidKey.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ResponseEntity<String> invalidLabel = http.exchange(collectionPath, HttpMethod.POST,
                    staffHeaders(organizationId, Map.of("key", "long_label", "label", "x".repeat(121),
                            "inputType", "TEXT", "required", false, "order", 0)), String.class);
            assertThat(invalidLabel.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ResponseEntity<String> invalidOrder = http.exchange(collectionPath, HttpMethod.POST,
                    staffHeaders(organizationId, Map.of("key", "large_order", "label", "Ordem fora do limite",
                            "inputType", "TEXT", "required", false, "order", 1001)), String.class);
            assertThat(invalidOrder.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            jdbc.update("""
                    INSERT INTO protocol_subject_participant_fields
                        (id, organization_id, subject_id, field_key, label, input_type, required,
                         display_order, created_by, updated_by)
                    VALUES(?,?,?,'foreign_field','Campo estrangeiro','TEXT',false,0,?,?)
                    """, foreignFieldId, LocalDemoData.ORG_BETA, foreignSubjectId, USER, USER);
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO protocol_subject_participant_fields
                        (id, organization_id, subject_id, field_key, label, input_type, required,
                         display_order, created_by, updated_by)
                    VALUES(?,?,?,'invalid_order','Ordem inválida','TEXT',false,1001,?,?)
                    """, UUID.randomUUID(), organizationId, subjectId, USER, USER))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO protocol_subject_participant_fields
                        (id, organization_id, subject_id, field_key, label, input_type, required,
                         display_order, created_by, updated_by)
                    VALUES(?,?,?,'invalid_type','Tipo inválido','BIRTHDATE',false,0,?,?)
                    """, UUID.randomUUID(), organizationId, subjectId, USER, USER))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO protocol_subject_participant_fields
                        (id, organization_id, subject_id, field_key, label, input_type, required,
                         display_order, created_by, updated_by)
                    VALUES(?,?,?,'invalid_fk','FK inválida','TEXT',false,0,?,?)
                    """, UUID.randomUUID(), organizationId, foreignSubjectId, USER, USER))
                    .isInstanceOf(DataAccessException.class);

            jdbc.update("DELETE FROM organization_memberships WHERE organization_id=? AND username=?",
                    organizationId, "servidor.teste");
            jdbc.update("DELETE FROM staff_memberships WHERE organization_id=? AND username=?",
                    organizationId, "servidor.teste");
            jdbc.update("INSERT INTO staff_memberships(username,organization_id,department_id,role_code) VALUES(?,?,?,'ADMIN')",
                    "servidor.teste", organizationId, LocalDemoData.ALPHA_PROTOCOL);
            long auditBeforeDeniedWrites = jdbc.queryForObject("""
                    SELECT count(*) FROM organization_audit_events
                     WHERE organization_id=? AND entity_type='PROTOCOL_SUBJECT_PARTICIPANT_FIELD'
                    """, Long.class, organizationId);
            ResponseEntity<String> sectorAdminList = http.exchange(collectionPath, HttpMethod.GET,
                    staffHeaders(organizationId, null, "servidor.teste"), String.class);
            assertThat(sectorAdminList.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            ResponseEntity<String> sectorAdminCreate = http.exchange(collectionPath, HttpMethod.POST,
                    staffHeaders(organizationId, Map.of("key", "denied_create", "label", "Negado",
                            "inputType", "TEXT", "required", false, "order", 3), "servidor.teste"), String.class);
            assertThat(sectorAdminCreate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            ResponseEntity<String> sectorAdminUpdate = http.exchange(collectionPath + "/" + fieldId, HttpMethod.PUT,
                    staffHeaders(organizationId, updated, "servidor.teste"), String.class);
            assertThat(sectorAdminUpdate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            ResponseEntity<String> sectorAdminDelete = http.exchange(collectionPath + "/" + fieldId, HttpMethod.DELETE,
                    staffHeaders(organizationId, null, "servidor.teste"), String.class);
            assertThat(sectorAdminDelete.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

            ResponseEntity<String> foreignSubjectWrite = http.exchange(
                    "/api/v1/staff/organization/protocol-subjects/" + foreignSubjectId + "/participant-fields",
                    HttpMethod.POST, staffHeaders(organizationId, original), String.class);
            assertThat(foreignSubjectWrite.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            ResponseEntity<String> foreignSubjectRead = http.exchange(
                    "/api/v1/staff/organization/protocol-subjects/" + foreignSubjectId + "/participant-fields",
                    HttpMethod.GET, staffHeaders(organizationId), String.class);
            assertThat(foreignSubjectRead.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            ResponseEntity<String> foreignFieldUpdate = http.exchange(collectionPath + "/" + foreignFieldId,
                    HttpMethod.PUT, staffHeaders(organizationId, updated), String.class);
            assertThat(foreignFieldUpdate.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            ResponseEntity<String> foreignFieldDelete = http.exchange(collectionPath + "/" + foreignFieldId,
                    HttpMethod.DELETE, staffHeaders(organizationId, null), String.class);
            assertThat(foreignFieldDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM protocol_subject_participant_fields
                     WHERE organization_id=? AND subject_id=?
                    """, Integer.class, organizationId, subjectId)).isEqualTo(1);
            assertThat(jdbc.queryForMap("""
                    SELECT field_key, label, input_type, required, display_order
                      FROM protocol_subject_participant_fields WHERE organization_id=? AND subject_id=? AND id=?
                    """, organizationId, subjectId, fieldId))
                    .containsEntry("field_key", "contact_email").containsEntry("label", "E-mail de contato")
                    .containsEntry("input_type", "EMAIL").containsEntry("required", true).containsEntry("display_order", 0);
            assertThat(jdbc.queryForObject("SELECT field_key FROM protocol_subject_participant_fields WHERE id=?",
                    String.class, foreignFieldId)).isEqualTo("foreign_field");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM organization_audit_events
                     WHERE organization_id=? AND entity_type='PROTOCOL_SUBJECT_PARTICIPANT_FIELD'
                    """, Long.class, organizationId)).isEqualTo(auditBeforeDeniedWrites);

            HttpHeaders deletePreflightHeaders = new HttpHeaders();
            deletePreflightHeaders.setOrigin("http://localhost:9000");
            deletePreflightHeaders.setAccessControlRequestMethod(HttpMethod.DELETE);
            deletePreflightHeaders.setAccessControlRequestHeaders(List.of("authorization", "x-organization-id"));
            ResponseEntity<Void> deletePreflight = http.exchange(collectionPath + "/" + fieldId, HttpMethod.OPTIONS,
                    new HttpEntity<>(deletePreflightHeaders), Void.class);
            assertThat(deletePreflight.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(deletePreflight.getHeaders().getAccessControlAllowMethods()).contains(HttpMethod.DELETE);

            ResponseEntity<Map> updatedResponse = http.exchange(collectionPath + "/" + fieldId, HttpMethod.PUT,
                    staffHeaders(organizationId, updated), Map.class);
            assertThat(updatedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(updatedResponse.getBody()).containsEntry("key", "contact_email")
                    .containsEntry("label", "Telefone").containsEntry("inputType", "PHONE")
                    .containsEntry("required", false).containsEntry("order", 2);

            ResponseEntity<Void> deleted = http.exchange(collectionPath + "/" + fieldId, HttpMethod.DELETE,
                    staffHeaders(organizationId), Void.class);
            assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(http.exchange(collectionPath, HttpMethod.GET, staffHeaders(organizationId),
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}).getBody()).isEmpty();
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM organization_audit_events
                     WHERE organization_id=? AND entity_type='PROTOCOL_SUBJECT_PARTICIPANT_FIELD' AND entity_id=?
                    """, Integer.class, organizationId, fieldId)).isEqualTo(3);
        } finally {
            // Audit rows are append-only; keep them as evidence and remove only temporary field definitions.
            jdbc.update("DELETE FROM protocol_subject_participant_fields WHERE organization_id=? AND subject_id=? AND id=?",
                    organizationId, subjectId, fieldId);
            jdbc.update("DELETE FROM protocol_subject_participant_fields WHERE id=?", foreignFieldId);
            jdbc.update("DELETE FROM organization_memberships WHERE organization_id=? AND username=?",
                    organizationId, "servidor.teste");
            for (OrganizationRoleState role : previousOrganizationRoles) {
                jdbc.update("INSERT INTO organization_memberships(username,organization_id,role_code,active) VALUES(?,?,?,?)",
                        "servidor.teste", organizationId, role.roleCode(), role.active());
            }
            jdbc.update("DELETE FROM staff_memberships WHERE organization_id=? AND username=?",
                    organizationId, "servidor.teste");
            for (StaffRoleState role : previousDepartmentRoles) {
                jdbc.update("""
                        INSERT INTO staff_memberships(username,organization_id,department_id,role_code,active)
                        VALUES(?,?,?,?,?)
                        """, "servidor.teste", organizationId, role.departmentId(), role.roleCode(), role.active());
            }
        }
    }

    @Test void protocolParticipantsHaveIndividualScopedAccessAndRevocation() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        UUID subjectId = findOrCreateOnlineSubject("municipio-alfa", "Participantes " + suffix);
        UUID organizationId = LocalDemoData.ORG_ALPHA;
        String slug = jdbc.queryForObject("SELECT slug FROM organizations WHERE id=?", String.class, organizationId);
        String fieldsPath = "/api/v1/staff/organization/protocol-subjects/" + subjectId + "/participant-fields";
        ResponseEntity<Map> field = http.exchange(fieldsPath, HttpMethod.POST,
                staffHeaders(organizationId, Map.of("key", "contact_email", "label", "E-mail de contato",
                        "inputType", "EMAIL", "required", true, "order", 0)), Map.class);
        assertThat(field.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> invalidRequest = new LinkedHashMap<>(openRequest(slug, subjectId));
        invalidRequest.put("participants", List.of(Map.of("name", "Pessoa inválida",
                "identityValues", Map.of("unexpected", "nao-deve-persistir@example.test"))));
        long counterBeforeInvalid = counterNextValue(organizationId);
        ResponseEntity<String> invalid = http.postForEntity("/api/v1/public/protocols", invalidRequest, String.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(counterNextValue(organizationId)).isEqualTo(counterBeforeInvalid);

        Map<String, Object> request = new LinkedHashMap<>(openRequest(slug, subjectId));
        request.put("participants", List.of(
                Map.of("name", "Marina de Teste", "identityValues", Map.of("contact_email", "marina@example.test")),
                Map.of("name", "Rafael de Teste", "identityValues", Map.of("contact_email", "rafael@example.test"))));
        Created created = Objects.requireNonNull(http.postForEntity("/api/v1/public/protocols", request, Created.class).getBody());
        assertThat(created.participantInvitations()).hasSize(2);
        assertThat(counterNextValue(organizationId)).isEqualTo(counterBeforeInvalid + 1);
        for (var invitation : created.participantInvitations()) {
            String storedHash = jdbc.queryForObject("SELECT token_hash FROM protocol_participants WHERE id=?",
                    String.class, invitation.id());
            assertThat(storedHash).isEqualTo(TrackingTokens.hash(invitation.token()));
            assertThat(storedHash).isNotEqualTo(invitation.token());
        }

        TrackingView applicantView = Objects.requireNonNull(http.exchange("/api/v1/public/protocols/tracking",
                HttpMethod.GET, bearer(created.trackingToken()), TrackingView.class).getBody());
        assertThat(applicantView.applicantView()).isTrue();
        assertThat(applicantView.participants()).extracting("name")
                .containsExactlyInAnyOrder("Marina de Teste", "Rafael de Teste");
        assertThat(applicantView.participantFields()).isEmpty();

        var marina = created.participantInvitations().getFirst();
        var rafael = created.participantInvitations().getLast();
        TrackingView marinaView = Objects.requireNonNull(http.exchange("/api/v1/public/protocols/tracking",
                HttpMethod.GET, bearer(marina.token()), TrackingView.class).getBody());
        assertThat(marinaView.applicantView()).isFalse();
        assertThat(marinaView.participantName()).isEqualTo("Marina de Teste");
        assertThat(marinaView.participants()).isEmpty();
        assertThat(marinaView.participantFields()).singleElement().satisfies(value -> {
            assertThat(value.key()).isEqualTo("contact_email");
            assertThat(value.value()).isEqualTo("marina@example.test");
        });

        ResponseEntity<InteractionView> participantMessage = http.exchange(
                "/api/v1/public/protocols/" + created.id() + "/interactions", HttpMethod.POST,
                publicInteractionMultipart("Complemento enviado por Marina.", null, null, marina.token()),
                InteractionView.class);
        assertThat(participantMessage.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(participantMessage.getBody().author()).isEqualTo("Você");
        assertThat(jdbc.queryForMap("SELECT actor_type,participant_id,visibility FROM protocol_interactions WHERE id=?",
                participantMessage.getBody().id()))
                .containsEntry("actor_type", "PARTICIPANT")
                .containsEntry("participant_id", marina.id())
                .containsEntry("visibility", "PUBLIC");

        TrackingView rafaelView = Objects.requireNonNull(http.exchange("/api/v1/public/protocols/tracking",
                HttpMethod.GET, bearer(rafael.token()), TrackingView.class).getBody());
        assertThat(rafaelView.participants()).isEmpty();
        assertThat(rafaelView.participantFields()).singleElement()
                .extracting("value").isEqualTo("rafael@example.test");
        assertThat(rafaelView.interactions()).filteredOn(i -> i.id().equals(participantMessage.getBody().id()))
                .singleElement().extracting("author").isEqualTo("Pessoa envolvida");
        assertThat(marinaView.interactions()).isEmpty();
        TrackingView refreshedMarina = Objects.requireNonNull(http.exchange("/api/v1/public/protocols/tracking",
                HttpMethod.GET, bearer(marina.token()), TrackingView.class).getBody());
        assertThat(refreshedMarina.interactions()).filteredOn(i -> i.id().equals(participantMessage.getBody().id()))
                .singleElement().extracting("author").isEqualTo("Você");

        Created anotherProtocol = create("municipio-alfa", "Protocolo sem participante", "Conteúdo de outro protocolo.");
        long interactionsBeforeCrossProtocol = jdbc.queryForObject(
                "SELECT count(*) FROM protocol_interactions WHERE organization_id=? AND protocol_id=?",
                Long.class, organizationId, anotherProtocol.id());
        long eventsBeforeCrossProtocol = jdbc.queryForObject(
                "SELECT count(*) FROM protocol_events WHERE organization_id=? AND protocol_id=?",
                Long.class, organizationId, anotherProtocol.id());
        ResponseEntity<String> crossProtocol = http.exchange("/api/v1/public/protocols/" + anotherProtocol.id()
                        + "/interactions", HttpMethod.POST,
                publicInteractionMultipart("Não deve atravessar o protocolo.", null, null, marina.token()), String.class);
        assertThat(crossProtocol.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM protocol_interactions WHERE organization_id=? AND protocol_id=?",
                Long.class, organizationId, anotherProtocol.id())).isEqualTo(interactionsBeforeCrossProtocol);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM protocol_events WHERE organization_id=? AND protocol_id=?",
                Long.class, organizationId, anotherProtocol.id())).isEqualTo(eventsBeforeCrossProtocol);
        Created otherOrganizationProtocol = create("municipio-beta", "Protocolo de outro órgão",
                "Conteúdo privado de outro órgão.");
        assertThat(otherOrganizationProtocol.id()).isNotNull();
        long interactionsBeforeCrossOrganization = jdbc.queryForObject(
                "SELECT count(*) FROM protocol_interactions WHERE organization_id=? AND protocol_id=?",
                Long.class, LocalDemoData.ORG_BETA, otherOrganizationProtocol.id());
        long eventsBeforeCrossOrganization = jdbc.queryForObject(
                "SELECT count(*) FROM protocol_events WHERE organization_id=? AND protocol_id=?",
                Long.class, LocalDemoData.ORG_BETA, otherOrganizationProtocol.id());
        ResponseEntity<String> crossOrganization = http.exchange("/api/v1/public/protocols/"
                        + otherOrganizationProtocol.id() + "/interactions", HttpMethod.POST,
                publicInteractionMultipart("Não deve atravessar a organização.", null, null, marina.token()), String.class);
        assertThat(crossOrganization.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM protocol_interactions WHERE organization_id=? AND protocol_id=?",
                Long.class, LocalDemoData.ORG_BETA, otherOrganizationProtocol.id())).isEqualTo(interactionsBeforeCrossOrganization);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM protocol_events WHERE organization_id=? AND protocol_id=?",
                Long.class, LocalDemoData.ORG_BETA, otherOrganizationProtocol.id())).isEqualTo(eventsBeforeCrossOrganization);

        ResponseEntity<Void> revoked = http.exchange("/api/v1/public/protocols/" + created.id() + "/participants/"
                        + marina.id() + "/revoke", HttpMethod.POST,
                bearer(created.trackingToken()), Void.class);
        assertThat(revoked.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(http.exchange("/api/v1/public/protocols/tracking", HttpMethod.GET,
                bearer(marina.token()), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.exchange("/api/v1/public/protocols/" + created.id() + "/interactions", HttpMethod.POST,
                publicInteractionMultipart("Acesso revogado.", null, null, marina.token()), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.exchange("/api/v1/public/protocols/tracking", HttpMethod.GET,
                bearer(rafael.token()), TrackingView.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test void subjectDeadlineIsConfiguredSnapshottedAndPrintedOnReceipt() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        String name = "Prazo por assunto " + suffix;
        ResponseEntity<Map> configured = http.exchange("/api/v1/staff/organization/protocol-subjects", HttpMethod.POST,
                staffHeaders(LocalDemoData.ORG_ALPHA, Map.of("name", name, "onlineAvailable", true,
                        "destinationDepartmentId", LocalDemoData.ALPHA_PROTOCOL, "automaticDeadlineDays", 10)), Map.class);
        assertThat(configured.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID subjectId = UUID.fromString(Objects.requireNonNull(configured.getBody()).get("id").toString());
        assertThat(configured.getBody()).containsEntry("automaticDeadlineDays", 10);

        String subjectPath = "/api/v1/staff/organization/protocol-subjects/" + subjectId;
        List<String> priorBetaRoles = jdbc.query("SELECT role_code FROM organization_memberships WHERE username=? AND organization_id=?",
                (rs, row) -> rs.getString(1), USER, LocalDemoData.ORG_BETA);
        jdbc.update("DELETE FROM organization_memberships WHERE username=? AND organization_id=?", USER, LocalDemoData.ORG_BETA);
        jdbc.update("INSERT INTO organization_memberships(username,organization_id,role_code) VALUES(?,?,'ADMIN')",
                USER, LocalDemoData.ORG_BETA);
        try {
            ResponseEntity<String> crossTenantEdit = http.exchange(subjectPath, HttpMethod.PUT,
                    staffHeaders(LocalDemoData.ORG_BETA, Map.of("name", name, "onlineAvailable", true,
                            "automaticDeadlineDays", 99)), String.class);
            assertThat(crossTenantEdit.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(jdbc.queryForObject("SELECT automatic_deadline_days FROM protocol_subjects WHERE organization_id=? AND id=?",
                    Integer.class, LocalDemoData.ORG_ALPHA, subjectId)).isEqualTo(10);
        } finally {
            jdbc.update("DELETE FROM organization_memberships WHERE username=? AND organization_id=?", USER, LocalDemoData.ORG_BETA);
            for (String role : priorBetaRoles)
                jdbc.update("INSERT INTO organization_memberships(username,organization_id,role_code) VALUES(?,?,?)",
                        USER, LocalDemoData.ORG_BETA, role);
        }

        String slug = jdbc.queryForObject("SELECT slug FROM organizations WHERE id=?", String.class, LocalDemoData.ORG_ALPHA);
        Created first = Objects.requireNonNull(http.postForEntity("/api/v1/public/protocols",
                openRequest(slug, subjectId), Created.class).getBody());
        assertThat(jdbc.queryForObject("SELECT automatic_deadline_days FROM protocols WHERE id=?", Integer.class, first.id()))
                .isEqualTo(10);

        ResponseEntity<Map> updated = http.exchange(subjectPath, HttpMethod.PUT,
                staffHeaders(LocalDemoData.ORG_ALPHA, Map.of("name", name, "onlineAvailable", true,
                        "destinationDepartmentId", LocalDemoData.ALPHA_PROTOCOL, "automaticDeadlineDays", 20)), Map.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).containsEntry("automaticDeadlineDays", 20);
        assertThat(jdbc.queryForObject("SELECT automatic_deadline_days FROM protocols WHERE id=?", Integer.class, first.id()))
                .isEqualTo(10);

        ResponseEntity<Map> omittedDeadline = http.exchange(subjectPath, HttpMethod.PUT,
                staffHeaders(LocalDemoData.ORG_ALPHA, Map.of("name", name, "onlineAvailable", true,
                        "destinationDepartmentId", LocalDemoData.ALPHA_PROTOCOL)), Map.class);
        assertThat(omittedDeadline.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(omittedDeadline.getBody()).containsEntry("automaticDeadlineDays", 20);
        assertThat(jdbc.queryForObject("SELECT automatic_deadline_days FROM protocol_subjects WHERE organization_id=? AND id=?",
                Integer.class, LocalDemoData.ORG_ALPHA, subjectId)).isEqualTo(20);

        Created second = Objects.requireNonNull(http.postForEntity("/api/v1/public/protocols",
                openRequest(slug, subjectId), Created.class).getBody());
        assertThat(jdbc.queryForObject("SELECT automatic_deadline_days FROM protocols WHERE id=?", Integer.class, second.id()))
                .isEqualTo(20);
        assertThat(receiptText(first)).contains("Prazo configurado: 10 dias").doesNotContain("Prazo configurado: 20 dias");
        assertThat(receiptText(second)).contains("Prazo configurado: 20 dias").doesNotContain("Prazo configurado: 10 dias");

        String clearDeadline = """
                {"name":"%s","onlineAvailable":true,"destinationDepartmentId":"%s","automaticDeadlineDays":null}
                """.formatted(name, LocalDemoData.ALPHA_PROTOCOL);
        HttpHeaders clearDeadlineHeaders = new HttpHeaders();
        clearDeadlineHeaders.putAll(staffHeaders(LocalDemoData.ORG_ALPHA).getHeaders());
        clearDeadlineHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> cleared = http.exchange(subjectPath, HttpMethod.PUT,
                new HttpEntity<>(clearDeadline, clearDeadlineHeaders), Map.class);
        assertThat(cleared.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cleared.getBody()).doesNotContainKey("automaticDeadlineDays");
        Created third = Objects.requireNonNull(http.postForEntity("/api/v1/public/protocols",
                openRequest(slug, subjectId), Created.class).getBody());
        assertThat(jdbc.queryForObject("SELECT automatic_deadline_days FROM protocols WHERE id=?", Integer.class, third.id()))
                .isNull();
        assertThat(receiptText(third)).doesNotContain("Prazo configurado:");

        Map<String, Object> invalidDeadline = Map.of("name", name, "onlineAvailable", true,
                "destinationDepartmentId", LocalDemoData.ALPHA_PROTOCOL, "automaticDeadlineDays", 0);
        ResponseEntity<String> invalid = http.exchange(subjectPath, HttpMethod.PUT,
                staffHeaders(LocalDemoData.ORG_ALPHA, invalidDeadline), String.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("SELECT automatic_deadline_days FROM protocol_subjects WHERE organization_id=? AND id=?",
                Integer.class, LocalDemoData.ORG_ALPHA, subjectId)).isNull();
    }

    @Test void configuredSubjectRoutesProtocolAndReceiptWithoutCrossTenantLeakOrCounterConsumption() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        UUID alphaOrganization = UUID.randomUUID();
        UUID betaOrganization = UUID.randomUUID();
        UUID alphaDepartment = UUID.randomUUID();
        UUID betaDepartment = UUID.randomUUID();
        String alphaSlug = "roteamento-alfa-" + suffix;
        String betaSlug = "roteamento-beta-" + suffix;
        jdbc.update("INSERT INTO organizations(id,slug,name) VALUES(?,?,?)", alphaOrganization, alphaSlug, "Organização de roteamento Alfa");
        jdbc.update("INSERT INTO organizations(id,slug,name) VALUES(?,?,?)", betaOrganization, betaSlug, "Organização de roteamento Beta");
        jdbc.update("INSERT INTO organization_memberships(username,organization_id,role_code) VALUES(?,?,'ADMIN')",
                USER, alphaOrganization);
        jdbc.update("INSERT INTO organization_memberships(username,organization_id,role_code) VALUES(?,?,'ADMIN')",
                USER, betaOrganization);
        jdbc.update("INSERT INTO organization_memberships(username,organization_id,role_code) VALUES(?,?,'STAFF')",
                "servidor.teste", alphaOrganization);
        jdbc.update("INSERT INTO departments(id,organization_id,name) VALUES(?,?,?)",
                alphaDepartment, alphaOrganization, "Setor Alfa " + suffix);
        jdbc.update("INSERT INTO departments(id,organization_id,name) VALUES(?,?,?)",
                betaDepartment, betaOrganization, "Setor Beta " + suffix);

        ResponseEntity<Map> alphaSubjectResponse = http.exchange("/api/v1/staff/organization/protocol-subjects", HttpMethod.POST,
                staffHeaders(alphaOrganization, Map.of("name", "Assunto roteado Alfa " + suffix,
                        "onlineAvailable", true)), Map.class);
        assertThat(alphaSubjectResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> alphaSubjectBody = Objects.requireNonNull(alphaSubjectResponse.getBody());
        UUID alphaSubjectId = UUID.fromString(alphaSubjectBody.get("id").toString());

        String subjectPath = "/api/v1/staff/organization/protocol-subjects/" + alphaSubjectId;
        ResponseEntity<String> crossTenantDestination = http.exchange(subjectPath, HttpMethod.PUT,
                staffHeaders(alphaOrganization, Map.of("name", "Assunto roteado Alfa " + suffix,
                        "onlineAvailable", true, "destinationDepartmentId", betaDepartment)), String.class);
        assertThat(crossTenantDestination.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT destination_department_id FROM protocol_subjects WHERE organization_id=? AND id=?",
                UUID.class, alphaOrganization, alphaSubjectId)).isNull();

        ResponseEntity<String> nonAdminRouting = http.exchange(subjectPath, HttpMethod.PUT,
                staffHeaders(alphaOrganization, Map.of("name", "Assunto roteado Alfa " + suffix,
                        "onlineAvailable", true, "destinationDepartmentId", alphaDepartment), "servidor.teste"), String.class);
        assertThat(nonAdminRouting.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_audit_events WHERE organization_id=? AND entity_id=?",
                Integer.class, alphaOrganization, alphaSubjectId)).isEqualTo(1);

        ResponseEntity<Map> configuredSubject = http.exchange(subjectPath, HttpMethod.PUT,
                staffHeaders(alphaOrganization, Map.of("name", "Assunto roteado Alfa " + suffix,
                        "onlineAvailable", true, "destinationDepartmentId", alphaDepartment)), Map.class);
        assertThat(configuredSubject.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(configuredSubject.getBody()).containsEntry("destinationDepartmentId", alphaDepartment.toString())
                .containsEntry("destinationDepartmentName", "Setor Alfa " + suffix);

        ResponseEntity<Map> unconfiguredResponse = http.exchange("/api/v1/staff/organization/protocol-subjects", HttpMethod.POST,
                staffHeaders(alphaOrganization, Map.of("name", "Assunto sem destino " + suffix, "onlineAvailable", true)), Map.class);
        assertThat(unconfiguredResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID unconfiguredSubjectId = UUID.fromString(Objects.requireNonNull(unconfiguredResponse.getBody()).get("id").toString());
        long counterBeforeUnconfiguredOpen = counterNextValue(alphaOrganization);
        ResponseEntity<String> unconfiguredOpen = http.postForEntity("/api/v1/public/protocols",
                openRequest(alphaSlug, unconfiguredSubjectId), String.class);
        assertThat(unconfiguredOpen.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(counterNextValue(alphaOrganization)).isEqualTo(counterBeforeUnconfiguredOpen);

        ResponseEntity<List<Map<String, Object>>> publicSubjects = http.exchange(
                "/api/v1/public/organizations/" + alphaSlug + "/protocol-subjects", HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<>() {});
        assertThat(publicSubjects.getBody()).isNotNull().anySatisfy(row ->
                assertThat(row).containsEntry("id", alphaSubjectId.toString()).doesNotContainKey("destinationDepartmentId"))
                .noneMatch(row -> unconfiguredSubjectId.toString().equals(row.get("id")));

        ResponseEntity<Created> alphaCreatedResponse = http.postForEntity("/api/v1/public/protocols",
                openRequest(alphaSlug, alphaSubjectId), Created.class);
        assertThat(alphaCreatedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Created alpha = Objects.requireNonNull(alphaCreatedResponse.getBody());
        assertThat(alpha.assignedDepartmentName()).isEqualTo("Setor Alfa " + suffix);
        assertThat(jdbc.queryForObject("SELECT assigned_department_id FROM protocols WHERE id=?", UUID.class, alpha.id()))
                .isEqualTo(alphaDepartment);
        assertThat(jdbc.queryForObject("SELECT department_id FROM protocol_events WHERE protocol_id=? AND action='OPENED'",
                UUID.class, alpha.id())).isEqualTo(alphaDepartment);
        assertThat(counterNextValue(alphaOrganization)).isEqualTo(counterBeforeUnconfiguredOpen + 1);

        ResponseEntity<byte[]> receipt = http.exchange("/api/v1/public/protocols/" + alpha.id() + "/receipt",
                HttpMethod.GET, bearer(alpha.trackingToken()), byte[].class);
        assertThat(receipt.getStatusCode()).isEqualTo(HttpStatus.OK);
        try (PDDocument pdf = Loader.loadPDF(Objects.requireNonNull(receipt.getBody()))) {
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains("Setor destinatário: Setor Alfa " + suffix);
        }

        ResponseEntity<Map> betaSubjectResponse = http.exchange("/api/v1/staff/organization/protocol-subjects", HttpMethod.POST,
                staffHeaders(betaOrganization, Map.of("name", "Assunto roteado Beta " + suffix,
                        "onlineAvailable", true, "destinationDepartmentId", betaDepartment)), Map.class);
        UUID betaSubjectId = UUID.fromString(Objects.requireNonNull(betaSubjectResponse.getBody()).get("id").toString());
        Created beta = Objects.requireNonNull(http.postForEntity("/api/v1/public/protocols",
                openRequest(betaSlug, betaSubjectId), Created.class).getBody());
        ResponseEntity<String> crossOrganizationReceipt = http.exchange(
                "/api/v1/public/protocols/" + beta.id() + "/receipt", HttpMethod.GET,
                bearer(alpha.trackingToken()), String.class);
        assertThat(crossOrganizationReceipt.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test void receiptKeepsTheOpeningDepartmentAfterForwardingAndDispatch() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        Created created = create("municipio-alfa", "Snapshot do recibo " + suffix, "Teste fictício do setor inicial.");
        UUID initialDepartmentId = jdbc.queryForObject("SELECT assigned_department_id FROM protocols WHERE id=?",
                UUID.class, created.id());
        String initialDepartmentName = jdbc.queryForObject("SELECT name FROM departments WHERE id=?",
                String.class, initialDepartmentId);
        String initialReceipt = receiptText(created);
        String normalizedInitialDepartmentName = initialDepartmentName.replaceAll("[\\p{Cntrl}]", " ")
                .replaceAll("\\s+", " ").trim();
        assertThat(initialReceipt).contains("Setor destinatário: " + normalizedInitialDepartmentName);

        UUID forwardedDepartmentId = LocalDemoData.ALPHA_WORKS;
        String forwardedDepartmentName = jdbc.queryForObject("SELECT name FROM departments WHERE id=?",
                String.class, forwardedDepartmentId);
        UUID dispatchedDepartmentId = UUID.randomUUID();
        String dispatchedDepartmentName = "Setor de destino " + suffix;
        jdbc.update("INSERT INTO departments(id,organization_id,name) VALUES(?,?,?)",
                dispatchedDepartmentId, LocalDemoData.ORG_ALPHA, dispatchedDepartmentName);
        assertThat(forwardedDepartmentId).isNotEqualTo(initialDepartmentId);
        assertThat(dispatchedDepartmentId).isNotEqualTo(initialDepartmentId);

        String prefix = "/api/v1/staff/protocols/" + created.id();
        assertThat(staffPost(prefix + "/forward", LocalDemoData.ORG_ALPHA,
                Map.of("destinationDepartmentId", forwardedDepartmentId)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT assigned_department_id FROM protocols WHERE id=?",
                UUID.class, created.id())).isEqualTo(forwardedDepartmentId);
        assertThat(receiptText(created))
                .contains("Setor destinatário: " + initialDepartmentName)
                .doesNotContain("Setor destinatário: " + forwardedDepartmentName);

        assertThat(staffPost(prefix + "/receive", LocalDemoData.ORG_ALPHA, Map.of()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(staffPost(prefix + "/dispatch", LocalDemoData.ORG_ALPHA,
                Map.of("destinationDepartmentId", dispatchedDepartmentId)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT assigned_department_id FROM protocols WHERE id=?",
                UUID.class, created.id())).isEqualTo(dispatchedDepartmentId);
        assertThat(receiptText(created))
                .contains("Setor destinatário: " + initialDepartmentName)
                .doesNotContain("Setor destinatário: " + dispatchedDepartmentName);
    }

    @Test void forwardingRequiresBodyAndDestinationDepartment() {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        Created created = create("municipio-alfa", "Validação de encaminhamento " + suffix, "O destino é obrigatório.");
        String path = "/api/v1/staff/protocols/" + created.id() + "/forward";
        Map<String, Object> initialState = jdbc.queryForMap(
                "SELECT status, assigned_department_id FROM protocols WHERE id=?", created.id());
        int initialEvents = jdbc.queryForObject("SELECT count(*) FROM protocol_events WHERE protocol_id=?",
                Integer.class, created.id());

        ResponseEntity<String> missingBody = http.exchange(path, HttpMethod.POST,
                staffHeadersWithoutBody(LocalDemoData.ORG_ALPHA), String.class);
        assertThat(missingBody.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<String> missingDestination = staffPost(path, LocalDemoData.ORG_ALPHA, Map.of());
        assertThat(missingDestination.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForMap("SELECT status, assigned_department_id FROM protocols WHERE id=?", created.id()))
                .isEqualTo(initialState);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM protocol_events WHERE protocol_id=?",
                Integer.class, created.id())).isEqualTo(initialEvents);
    }

    @Test void otherTransitionBodiesAreOptionalAndDoNotRequireDestination() {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        Created created = create("municipio-alfa", "Comandos opcionais " + suffix, "Apenas encaminhamento pede destino.");
        UUID initialDepartment = jdbc.queryForObject("SELECT assigned_department_id FROM protocols WHERE id=?",
                UUID.class, created.id());
        String path = "/api/v1/staff/protocols/" + created.id();

        assertThat(staffPost(path + "/forward", LocalDemoData.ORG_ALPHA,
                Map.of("destinationDepartmentId", initialDepartment)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(staffPostWithoutBody(path + "/receive", LocalDemoData.ORG_ALPHA).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(staffPostWithoutBody(path + "/dispatch", LocalDemoData.ORG_ALPHA).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT assigned_department_id FROM protocols WHERE id=?", UUID.class, created.id()))
                .isEqualTo(initialDepartment);
        assertThat(staffPostWithoutBody(path + "/receive", LocalDemoData.ORG_ALPHA).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(staffPostWithoutBody(path + "/complete", LocalDemoData.ORG_ALPHA).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT status FROM protocols WHERE id=?", String.class, created.id()))
                .isEqualTo("COMPLETED");
    }

    @Test void staffTransitionsRejectProtocolsFromOtherOrganizationsWithoutMutation() {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        Created created = create("municipio-alfa", "Isolamento de transições " + suffix, "Protocolo fictício em Alfa.");
        String prefix = "/api/v1/staff/protocols/" + created.id();
        Map<String, Object> initialState = jdbc.queryForMap(
                "SELECT status, assigned_department_id FROM protocols WHERE id=?", created.id());
        int initialEvents = jdbc.queryForObject("SELECT count(*) FROM protocol_events WHERE protocol_id=?",
                Integer.class, created.id());

        Map<String, Object> forward = Map.of("destinationDepartmentId", LocalDemoData.BETA_PROTOCOL);
        Map<String, Object> receive = Map.of();
        Map<String, Object> dispatch = Map.of("destinationDepartmentId", LocalDemoData.BETA_WORKS);
        Map<String, Object> complete = Map.of();
        for (var attempt : List.of(
                Map.entry("forward", forward),
                Map.entry("receive", receive),
                Map.entry("dispatch", dispatch),
                Map.entry("complete", complete))) {
            ResponseEntity<String> response = staffPost(prefix + "/" + attempt.getKey(),
                    LocalDemoData.ORG_BETA, attempt.getValue());
            assertThat(response.getStatusCode()).as("%s cross-tenant transition", attempt.getKey())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        assertThat(jdbc.queryForMap("SELECT status, assigned_department_id FROM protocols WHERE id=?", created.id()))
                .isEqualTo(initialState);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM protocol_events WHERE protocol_id=?",
                Integer.class, created.id())).isEqualTo(initialEvents);
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

    @Test void concurrentCompletionRecordsOnlyOneProtocolTransition() throws Exception {
        Created created = create("municipio-alfa", "Conclusão concorrente", "Apenas uma conclusão pode ser registrada.");
        String prefix = "/api/v1/staff/protocols/" + created.id();
        assertThat(staffPost(prefix + "/forward", LocalDemoData.ORG_ALPHA,
                Map.of("destinationDepartmentId", LocalDemoData.ALPHA_PROTOCOL)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(staffPost(prefix + "/receive", LocalDemoData.ORG_ALPHA, Map.of()).getStatusCode()).isEqualTo(HttpStatus.OK);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Callable<HttpStatusCode>> tasks = List.of(
                    () -> completeAfterBarrier(prefix, ready, start),
                    () -> completeAfterBarrier(prefix, ready, start));
            List<Future<HttpStatusCode>> futures = tasks.stream().map(pool::submit).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<HttpStatusCode> statuses = futures.stream().map(future -> {
                try { return future.get(15, TimeUnit.SECONDS); }
                catch (Exception failure) { throw new AssertionError(failure); }
            }).toList();

            assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM protocol_events WHERE protocol_id=? AND action='COMPLETED'",
                    Integer.class, created.id())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM protocols WHERE id=?", String.class, created.id())).isEqualTo("COMPLETED");
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test void applicantInteractionSerializesWithProtocolCompletion() throws Exception {
        Created created = create("municipio-alfa", "Interação contra conclusão", "Teste de serialização de escrita.");
        String prefix = "/api/v1/staff/protocols/" + created.id();
        assertThat(staffPost(prefix + "/forward", LocalDemoData.ORG_ALPHA,
                Map.of("destinationDepartmentId", LocalDemoData.ALPHA_PROTOCOL)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(staffPost(prefix + "/receive", LocalDemoData.ORG_ALPHA, Map.of()).getStatusCode()).isEqualTo(HttpStatus.OK);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<HttpStatusCode> completion = pool.submit(() -> completeAfterBarrier(prefix, ready, start));
            Future<HttpStatusCode> applicantPost = pool.submit(() -> applicantInteractionAfterBarrier(created, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            HttpStatusCode completionStatus = completion.get(15, TimeUnit.SECONDS);
            HttpStatusCode interactionStatus = applicantPost.get(15, TimeUnit.SECONDS);
            assertThat(completionStatus).isEqualTo(HttpStatus.OK);
            assertThat(interactionStatus).isIn(HttpStatus.CREATED, HttpStatus.CONFLICT);
            assertThat(jdbc.queryForObject("SELECT status FROM protocols WHERE id=?", String.class, created.id())).isEqualTo("COMPLETED");
            int interactionCount = jdbc.queryForObject("SELECT count(*) FROM protocol_interactions WHERE protocol_id=?",
                    Integer.class, created.id());
            assertThat(interactionCount).isEqualTo(interactionStatus == HttpStatus.CREATED ? 1 : 0);
            if (interactionCount == 1) {
                assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM protocol_interactions i JOIN protocol_events e
                          ON e.organization_id=i.organization_id AND e.protocol_id=i.protocol_id AND e.action='COMPLETED'
                         WHERE i.protocol_id=? AND i.created_at<=e.created_at
                        """, Integer.class, created.id())).isEqualTo(1);
            }
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test void interactionHistoryUsesWriteOrderWhenAnOlderTransactionWaitsForProtocolLock() throws Exception {
        Created created = create("municipio-alfa", "Ordem de gravação", "Teste de ordenação concorrente.");
        CountDownLatch olderTransactionStarted = new CountDownLatch(1);
        CountDownLatch allowOlderTransactionToWrite = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<?> olderTransaction = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(jdbc.queryForObject("SELECT transaction_timestamp()", java.time.OffsetDateTime.class)).isNotNull();
            olderTransactionStarted.countDown();
            try {
                if (!allowOlderTransactionToWrite.await(10, TimeUnit.SECONDS))
                    throw new IllegalStateException("A transação anterior não foi liberada para gravar.");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
            interactionService.createPublic(created.id(), created.trackingToken(),
                    "A iniciou primeiro, mas gravou depois.", List.of());
        }));
        try {
            assertThat(olderTransactionStarted.await(5, TimeUnit.SECONDS)).isTrue();
            ResponseEntity<InteractionView> laterTransaction = http.exchange("/api/v1/public/protocols/"
                            + created.id() + "/interactions", HttpMethod.POST,
                    publicInteractionMultipart("B iniciou depois e gravou primeiro.", null, null, created.trackingToken()),
                    InteractionView.class);
            assertThat(laterTransaction.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        } finally {
            allowOlderTransactionToWrite.countDown();
        }
        try {
            olderTransaction.get(10, TimeUnit.SECONDS);
            assertThat(interactionService.publicList(LocalDemoData.ORG_ALPHA, created.id(),
                    publicAccess.resolveForProtocol(created.id(), created.trackingToken())))
                    .extracting(InteractionView::message)
                    .containsExactly("B iniciou depois e gravou primeiro.", "A iniciou primeiro, mas gravou depois.");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test void attachmentPolicyChangesWaitForProtocolSubjectReadLock() throws Exception {
        UUID organizationId = LocalDemoData.ORG_ALPHA;
        UUID subjectId = findOrCreateOnlineSubject("municipio-alfa", "Política serializada " + UUID.randomUUID());
        ResponseEntity<Map> createdRule = http.exchange("/api/v1/staff/organization/protocol-subjects/" + subjectId
                        + "/attachment-requirements", HttpMethod.POST,
                staffHeaders(organizationId, Map.of("name", "Documento opcional", "required", false,
                        "allowedExtensions", List.of(".pdf"))), Map.class);
        assertThat(createdRule.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID requirementId = UUID.fromString(Objects.requireNonNull(createdRule.getBody()).get("id").toString());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            String path = "/api/v1/staff/organization/protocol-subjects/" + subjectId
                    + "/attachment-requirements/" + requirementId;
            assertPolicyMutationWaitsForSubjectReadLock(pool, subjectId,
                    () -> http.exchange(path, HttpMethod.PUT,
                            staffHeaders(organizationId, Map.of("name", "Documento opcional atualizado",
                                    "required", false, "allowedExtensions", List.of(".pdf"))), String.class),
                    HttpStatus.OK);
            assertPolicyMutationWaitsForSubjectReadLock(pool, subjectId,
                    () -> http.exchange(path, HttpMethod.DELETE, staffHeaders(organizationId), String.class),
                    HttpStatus.NO_CONTENT);
        } finally {
            pool.shutdownNow();
        }
    }

    private void assertPolicyMutationWaitsForSubjectReadLock(ExecutorService pool, UUID subjectId,
            Callable<ResponseEntity<String>> mutation, HttpStatus expectedStatus) throws Exception {
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        Future<?> holder = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.query("SELECT id FROM protocol_subjects WHERE id=? FOR SHARE", (rs, row) -> rs.getObject(1, UUID.class), subjectId);
            lockHeld.countDown();
            try {
                if (!releaseLock.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Subject read lock timed out.");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        }));
        Future<ResponseEntity<String>> response = null;
        try {
            assertThat(lockHeld.await(5, TimeUnit.SECONDS)).isTrue();
            response = pool.submit(mutation);
            Thread.sleep(250);
            assertThat(response.isDone()).as("policy mutation must wait while protocol opening holds FOR SHARE").isFalse();
        } finally {
            releaseLock.countDown();
        }
        holder.get(10, TimeUnit.SECONDS);
        assertThat(Objects.requireNonNull(response).get(10, TimeUnit.SECONDS).getStatusCode()).isEqualTo(expectedStatus);
    }

    private HttpStatusCode completeAfterBarrier(String protocolPath, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Início concorrente não foi liberado.");
        return staffPost(protocolPath + "/complete", LocalDemoData.ORG_ALPHA, Map.of()).getStatusCode();
    }

    private HttpStatusCode applicantInteractionAfterBarrier(Created created, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Início concorrente não foi liberado.");
        return http.exchange("/api/v1/public/protocols/" + created.id() + "/interactions", HttpMethod.POST,
                publicInteractionMultipart("Envio concorrente com a conclusão.", null, null,
                        created.trackingToken()), String.class).getStatusCode();
    }

    private Created create(String organization, String subject, String message) {
        Map<String, Object> request = Map.of("organizationSlug", organization, "applicantName", "Pessoa de Teste",
                "applicantEmail", "cidadao@example.test", "subjectId", findOrCreateOnlineSubject(organization, subject),
                "initialMessage", message);
        return Objects.requireNonNull(http.postForEntity("/api/v1/public/protocols", request, Created.class).getBody());
    }

    private Map<String, Object> openRequest(String organization, UUID subjectId) {
        return Map.of("organizationSlug", organization, "applicantName", "Pessoa de Teste",
                "applicantEmail", "cidadao@example.test", "subjectId", subjectId,
                "initialMessage", "Solicitação de teste do catálogo de assuntos.");
    }

    private long counterNextValue(UUID organizationId) {
        return jdbc.queryForList("SELECT next_value FROM protocol_counters WHERE organization_id=? AND counter_year=?",
                        Long.class, organizationId, java.time.Year.now().getValue())
                .stream().findFirst().orElse(0L);
    }

    private UUID findOrCreateOnlineSubject(String organizationSlug, String name) {
        UUID organizationId = jdbc.queryForObject("SELECT id FROM organizations WHERE slug=?", UUID.class, organizationSlug);
        UUID targetDepartmentId = jdbc.query("""
                        SELECT d.id FROM departments d
                        JOIN staff_memberships membership
                          ON membership.organization_id=d.organization_id AND membership.department_id=d.id
                        WHERE d.organization_id=? AND d.active=true AND membership.username=?
                        ORDER BY d.id LIMIT 1
                        """, (rs, row) -> rs.getObject("id", UUID.class), organizationId, USER)
                .stream().findFirst().orElseThrow(() -> new IllegalStateException(
                        "Organização sem setor ativo vinculado ao servidor de teste: " + organizationSlug));
        return jdbc.query("SELECT id,destination_department_id FROM protocol_subjects WHERE organization_id=? AND lower(btrim(name))=lower(btrim(?)) AND online_available=true",
                        (rs, row) -> new SubjectTestRecord(rs.getObject("id", UUID.class), rs.getObject("destination_department_id", UUID.class)), organizationId, name)
                .stream().findFirst().map(existing -> {
                    jdbc.update("UPDATE protocol_subjects SET destination_department_id=COALESCE(destination_department_id,?) WHERE organization_id=? AND id=?",
                            targetDepartmentId, organizationId, existing.id);
                    return existing.id;
                }).orElseGet(() -> {
                    UUID id = UUID.randomUUID();
                    jdbc.update("""
                                    INSERT INTO protocol_subjects(id, organization_id, name, online_available,
                                                                  destination_department_id, created_by, updated_by)
                                    VALUES(?,?,?,true,?,?,?)
                                    """, id, organizationId, name, targetDepartmentId, USER, USER);
                    return id;
                });
    }

    private record SubjectTestRecord(UUID id, UUID destinationDepartmentId) {}
    private record DepartmentMembership(UUID departmentId, String roleCode) {}

    private ResponseEntity<String> staffPost(String path, UUID organizationId, Object body) {
        return http.exchange(path, HttpMethod.POST, staffHeaders(organizationId, body), String.class);
    }

    private ResponseEntity<String> staffPost(String path, UUID organizationId, Object body, String username) {
        return http.exchange(path, HttpMethod.POST, staffHeaders(organizationId, body, username), String.class);
    }

    private HttpEntity<MultiValueMap<String, Object>> publicInteractionMultipart(String message, String filename,
                                                                                 byte[] bytes, String token) throws Exception {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        parts.add("request", new HttpEntity<>(new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(Map.of("message", message)), requestHeaders));
        addMultipartFile(parts, filename, bytes);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(parts, headers);
    }

    private HttpEntity<MultiValueMap<String, Object>> staffInteractionMultipart(String message, String visibility,
                                                                                String filename, byte[] bytes,
                                                                                UUID organizationId, String username) throws Exception {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        parts.add("request", new HttpEntity<>(new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(Map.of("message", message, "visibility", visibility)), requestHeaders));
        addMultipartFile(parts, filename, bytes);
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(staffHeaders(organizationId, null, username).getHeaders());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(parts, headers);
    }

    private void addMultipartFile(MultiValueMap<String, Object> parts, String filename, byte[] bytes) {
        if (filename == null || bytes == null) return;
        ByteArrayResource file = new ByteArrayResource(bytes) { @Override public String getFilename() { return filename; } };
        parts.add("files", file);
    }

    private ResponseEntity<String> staffPostWithoutBody(String path, UUID organizationId) {
        return http.exchange(path, HttpMethod.POST, staffHeadersWithoutBody(organizationId), String.class);
    }

    private HttpEntity<Object> staffHeadersWithoutBody(UUID organizationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(USER, PASSWORD);
        headers.set("X-Organization-Id", organizationId.toString());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(null, headers);
    }

    private HttpEntity<Void> bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private String receiptText(Created created) throws Exception {
        return receiptText(created.id(), created.trackingToken());
    }

    private String receiptText(UUID protocolId, String trackingToken) throws Exception {
        ResponseEntity<byte[]> receipt = http.exchange("/api/v1/public/protocols/" + protocolId + "/receipt",
                HttpMethod.GET, bearer(trackingToken), byte[].class);
        assertThat(receipt.getStatusCode()).isEqualTo(HttpStatus.OK);
        try (PDDocument document = Loader.loadPDF(Objects.requireNonNull(receipt.getBody()))) {
            return new PDFTextStripper().getText(document);
        }
    }

    private HttpEntity<Object> staffHeaders(UUID organizationId) { return staffHeaders(organizationId, null); }

    private HttpEntity<Object> staffHeaders(UUID organizationId, Object body) {
        return staffHeaders(organizationId, body, USER);
    }

    private HttpEntity<Object> staffHeaders(UUID organizationId, Object body, String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, PASSWORD);
        headers.set("X-Organization-Id", organizationId.toString());
        if (body != null) headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
