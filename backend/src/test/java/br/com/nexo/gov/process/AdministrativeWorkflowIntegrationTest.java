package br.com.nexo.gov.process;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Testcontainers
@Import(AdministrativeWorkflowIntegrationTest.FixedNumberingClockConfiguration.class)
class AdministrativeWorkflowIntegrationTest {
    private static final String ADMIN = "workflow-admin";
    private static final String OTHER_STAGE_MEMBER = "workflow-other";
    private static final String PASSWORD = "workflow-test-password-2026";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.4-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("NEXOGOV_DEMO_USERNAME", () -> ADMIN);
        registry.add("NEXOGOV_DEMO_PASSWORD", () -> PASSWORD);
        registry.add("NEXOGOV_DEMO_MEMBER_USERNAME", () -> OTHER_STAGE_MEMBER);
        registry.add("NEXOGOV_DEMO_MEMBER_PASSWORD", () -> PASSWORD);
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    private UUID organizationId;
    private UUID otherOrganizationId;
    private UUID intakeDepartmentId;
    private UUID analysisDepartmentId;
    private UUID legalDepartmentId;
    private UUID decisionDepartmentId;
    private UUID otherOrganizationDepartmentId;

    @TestConfiguration
    static class FixedNumberingClockConfiguration {
        @Bean
        @Primary
        Clock fixedNumberingClock() {
            return Clock.fixed(Instant.parse("2027-01-01T02:30:00Z"), ZoneId.of("America/Sao_Paulo"));
        }
    }

    @BeforeEach
    void prepareOrganizationsAndMemberships() {
        organizationId = UUID.randomUUID();
        otherOrganizationId = UUID.randomUUID();
        intakeDepartmentId = UUID.randomUUID();
        analysisDepartmentId = UUID.randomUUID();
        legalDepartmentId = UUID.randomUUID();
        decisionDepartmentId = UUID.randomUUID();
        otherOrganizationDepartmentId = UUID.randomUUID();

        insertOrganization(organizationId, "Fluxo Alpha");
        insertOrganization(otherOrganizationId, "Fluxo Beta");
        insertDepartment(organizationId, intakeDepartmentId, "Recebimento");
        insertDepartment(organizationId, analysisDepartmentId, "Análise");
        insertDepartment(organizationId, legalDepartmentId, "Jurídico");
        insertDepartment(organizationId, decisionDepartmentId, "Decisão");
        insertDepartment(otherOrganizationId, otherOrganizationDepartmentId, "Beta geral");

        addOrganizationAdmin(organizationId, ADMIN);
        addOrganizationAdmin(otherOrganizationId, ADMIN);
        addDepartmentMember(organizationId, ADMIN, intakeDepartmentId);
        addDepartmentMember(organizationId, ADMIN, analysisDepartmentId);
        addDepartmentMember(organizationId, ADMIN, legalDepartmentId);
        addDepartmentMember(organizationId, ADMIN, decisionDepartmentId);
        addDepartmentMember(organizationId, OTHER_STAGE_MEMBER, decisionDepartmentId);

        // Membership in the second organization allows the request to reach the service;
        // the service must still scope every resource lookup to this validated tenant.
        addDepartmentMember(otherOrganizationId, ADMIN, otherOrganizationDepartmentId);
    }

    @Test
    void processListIsTenantScopedAuthorizedFilteredStableAndPaginated() {
        addDepartmentMember(organizationId, OTHER_STAGE_MEMBER, analysisDepartmentId);
        jdbc.update("UPDATE staff_memberships SET role_code='ADMIN' WHERE username=? AND organization_id=? AND department_id=?",
                OTHER_STAGE_MEMBER, organizationId, analysisDepartmentId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_memberships WHERE username=? AND organization_id=? AND role_code='ADMIN' AND active=true",
                Integer.class, OTHER_STAGE_MEMBER, organizationId)).isZero();

        String subjectKey = "process-listing";
        assertThat(createWorkflow(organizationId, ADMIN, workflowBody(subjectKey, "Licença urbana", List.of(
                step("Recebimento", intakeDepartmentId),
                step("Análise técnica", analysisDepartmentId),
                step("Decisão", decisionDepartmentId)))).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        List<UUID> alphaProcessIds = new ArrayList<>();
        UUID visibleToSectorAdmin = null;
        for (int index = 0; index < 27; index++) {
            UUID processId = createProcess(subjectKey, intakeDepartmentId, "Solicitação urbana %02d".formatted(index));
            alphaProcessIds.add(processId);
            if (index == 0) visibleToSectorAdmin = processId;
        }
        UUID literalSearchProcess = createProcess(subjectKey, intakeDepartmentId, "Special-%_Permit");
        alphaProcessIds.add(literalSearchProcess);
        OffsetDateTime tiedCreatedAt = OffsetDateTime.parse("2026-11-08T12:00:00Z");
        for (UUID processId : alphaProcessIds) {
            jdbc.update("UPDATE administrative_processes SET created_at=? WHERE organization_id=? AND id=?",
                    tiedCreatedAt, organizationId, processId);
        }
        assertThat(advance(organizationId, ADMIN, Objects.requireNonNull(visibleToSectorAdmin),
                2, analysisDepartmentId, Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        String betaSubjectKey = "process-listing-beta";
        assertThat(createWorkflow(otherOrganizationId, ADMIN, workflowBody(betaSubjectKey, "Licença Beta", List.of(
                step("Entrada Beta", otherOrganizationDepartmentId),
                step("Decisão Beta", otherOrganizationDepartmentId)))).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID betaProcessId = createProcess(otherOrganizationId, ADMIN, betaSubjectKey,
                otherOrganizationDepartmentId, "Solicitação Beta exclusiva");

        ResponseEntity<Map> firstPage = listProcesses(organizationId, ADMIN, null, null, null, Map.class);
        assertThat(firstPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstPage.getHeaders().getCacheControl()).isEqualTo("no-store");
        Map<?, ?> firstPageBody = Objects.requireNonNull(firstPage.getBody());
        assertThat(firstPageBody.get("page")).isEqualTo(0);
        assertThat(firstPageBody.get("size")).isEqualTo(25);
        assertThat(firstPageBody.get("hasNext")).isEqualTo(true);
        List<?> firstPageItems = (List<?>) firstPageBody.get("items");
        assertThat(firstPageItems).hasSize(25);
        assertThat(firstPageItems.getFirst()).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .doesNotContainKey("events").doesNotContainKey("steps");
        List<UUID> stableExpected = jdbc.query("""
                SELECT id FROM administrative_processes WHERE organization_id=?
                 ORDER BY created_at DESC, id DESC LIMIT 25
                """, (rs, row) -> rs.getObject("id", UUID.class), organizationId);
        assertThat(firstPageItems.stream().map(item -> UUID.fromString(((Map<?, ?>) item).get("id").toString())))
                .containsExactlyElementsOf(stableExpected);

        Map<?, ?> secondPage = Objects.requireNonNull(
                listProcesses(organizationId, ADMIN, 1, 25, null, Map.class).getBody());
        assertThat(secondPage.get("page")).isEqualTo(1);
        assertThat(secondPage.get("size")).isEqualTo(25);
        assertThat(secondPage.get("hasNext")).isEqualTo(false);
        List<?> secondPageItems = (List<?>) secondPage.get("items");
        assertThat(secondPageItems).hasSize(3);
        List<UUID> allReturned = new ArrayList<>(firstPageItems.stream()
                .map(item -> UUID.fromString(((Map<?, ?>) item).get("id").toString())).toList());
        allReturned.addAll(secondPageItems.stream()
                .map(item -> UUID.fromString(((Map<?, ?>) item).get("id").toString())).toList());
        assertThat(allReturned).doesNotContain(betaProcessId).hasSize(28);

        Map<?, ?> filtered = Objects.requireNonNull(
                listProcesses(organizationId, ADMIN, 0, 100, "Special-%_", Map.class).getBody());
        assertThat((List<?>) filtered.get("items")).hasSize(1);
        assertThat(((Map<?, ?>) ((List<?>) filtered.get("items")).getFirst()).get("id"))
                .isEqualTo(literalSearchProcess.toString());
        Map<?, ?> subjectFiltered = Objects.requireNonNull(
                listProcesses(organizationId, ADMIN, 0, 100, "Licença urbana", Map.class).getBody());
        assertThat((List<?>) subjectFiltered.get("items")).hasSize(28);
        Map<?, ?> injectionTextIsLiteral = Objects.requireNonNull(
                listProcesses(organizationId, ADMIN, 0, 100, "' OR 1=1 --", Map.class).getBody());
        assertThat((List<?>) injectionTextIsLiteral.get("items")).isEmpty();

        Map<?, ?> sectorAdminPage = Objects.requireNonNull(
                listProcesses(organizationId, OTHER_STAGE_MEMBER, 0, 100, null, Map.class).getBody());
        List<?> sectorItems = (List<?>) sectorAdminPage.get("items");
        assertThat(sectorItems).hasSize(1);
        Map<?, ?> sectorSummary = (Map<?, ?>) sectorItems.getFirst();
        assertThat(sectorSummary.get("id")).isEqualTo(visibleToSectorAdmin.toString());
        assertThat(sectorSummary.get("currentDepartmentId")).isEqualTo(analysisDepartmentId.toString());
        assertThat(getProcess(organizationId, OTHER_STAGE_MEMBER, visibleToSectorAdmin, Map.class).getBody())
                .containsEntry("id", visibleToSectorAdmin.toString());

        ProcessMutationSnapshot beforeDetailReads = processMutationSnapshot(organizationId, visibleToSectorAdmin);
        long eventsBeforeDetailReads = countEvents(organizationId, visibleToSectorAdmin);
        assertThat(getProcess(organizationId, ADMIN, visibleToSectorAdmin, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // Keep only an active historical-sector membership; it must not authorize current detail access.
        jdbc.update("UPDATE staff_memberships SET active=false WHERE username=? AND organization_id=?",
                OTHER_STAGE_MEMBER, organizationId);
        addDepartmentMember(organizationId, OTHER_STAGE_MEMBER, intakeDepartmentId);
        ResponseEntity<String> historicalOnlyDetail = getProcess(
                organizationId, OTHER_STAGE_MEMBER, visibleToSectorAdmin, String.class);
        assertThat(historicalOnlyDetail.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertUnchanged(organizationId, visibleToSectorAdmin, beforeDetailReads, eventsBeforeDetailReads);

        jdbc.update("UPDATE staff_memberships SET active=false WHERE username=? AND organization_id=? AND department_id=?",
                OTHER_STAGE_MEMBER, organizationId, intakeDepartmentId);
        jdbc.update("UPDATE staff_memberships SET active=true WHERE username=? AND organization_id=? AND department_id=?",
                OTHER_STAGE_MEMBER, organizationId, analysisDepartmentId);
        ResponseEntity<Map> currentDepartmentDetail = getProcess(
                organizationId, OTHER_STAGE_MEMBER, visibleToSectorAdmin, Map.class);
        assertThat(currentDepartmentDetail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(currentDepartmentDetail.getBody()).containsEntry("id", visibleToSectorAdmin.toString());
        assertUnchanged(organizationId, visibleToSectorAdmin, beforeDetailReads, eventsBeforeDetailReads);

        ResponseEntity<String> crossTenantDetail = getProcess(
                otherOrganizationId, ADMIN, visibleToSectorAdmin, String.class);
        ResponseEntity<String> absentDetail = getProcess(
                otherOrganizationId, ADMIN, UUID.randomUUID(), String.class);
        assertThat(crossTenantDetail.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(crossTenantDetail.getBody()).isEqualTo(absentDetail.getBody());
        assertUnchanged(organizationId, visibleToSectorAdmin, beforeDetailReads, eventsBeforeDetailReads);

        ResponseEntity<String> oversizedPage = listProcesses(organizationId, ADMIN, 0, 101, null, String.class);
        assertThat(oversizedPage.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<String> tooDeepPage = listProcesses(organizationId, ADMIN, 10_001, 25, null, String.class);
        assertThat(tooDeepPage.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<?, ?> betaPage = Objects.requireNonNull(
                listProcesses(otherOrganizationId, ADMIN, 0, 100, "Beta exclusiva", Map.class).getBody());
        assertThat((List<?>) betaPage.get("items")).hasSize(1);
        assertThat(((Map<?, ?>) ((List<?>) betaPage.get("items")).getFirst()).get("id"))
                .isEqualTo(betaProcessId.toString());
    }

    @Test
    void processNumbersUseCustomPerSubjectAndGeneralSequencesAndKeepWorkflowSnapshots() {
        List<Map<String, Object>> steps = List.of(
                step("Recebimento", intakeDepartmentId), step("Análise", analysisDepartmentId));
        String subjectKey = "numbering-permit";
        Map<String, Object> invalidPattern = workflowBody(subjectKey, "Licença", steps,
                "BY_SUBJECT", "{ano}-{numero}", 0);
        assertThat(createWorkflow(organizationId, ADMIN, invalidPattern, String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM administrative_workflow_heads WHERE organization_id=? AND subject_key=?
                """, Integer.class, organizationId, subjectKey)).isZero();

        ResponseEntity<Map> versionOne = createWorkflow(organizationId, ADMIN,
                workflowBody(subjectKey, "Licença", steps, "BY_SUBJECT", "{assunto}/{ano}/{numero}", 0));
        assertThat(versionOne.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(versionOne.getBody()).containsEntry("numberingMode", "BY_SUBJECT")
                .containsEntry("numberPattern", "{assunto}/{ano}/{numero}");

        Map<?, ?> first = Objects.requireNonNull(postProcess(organizationId, ADMIN, Map.of(
                "subjectKey", subjectKey, "title", "Primeiro pedido", "initialDepartmentId", intakeDepartmentId))
                .getBody());
        Map<?, ?> second = Objects.requireNonNull(postProcess(organizationId, ADMIN, Map.of(
                "subjectKey", subjectKey, "title", "Segundo pedido", "initialDepartmentId", intakeDepartmentId))
                .getBody());
        String firstNumber = first.get("processNumber").toString();
        assertThat(firstNumber).isEqualTo(subjectKey + "/2026/1");
        assertThat(second.get("processNumber").toString()).isEqualTo(subjectKey + "/2026/2");

        String otherSubjectKey = "numbering-archive";
        assertThat(createWorkflow(organizationId, ADMIN,
                workflowBody(otherSubjectKey, "Arquivo", steps, "BY_SUBJECT", "{assunto}/{ano}/{numero}", 0))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> otherSubjectProcess = Objects.requireNonNull(postProcess(organizationId, ADMIN, Map.of(
                "subjectKey", otherSubjectKey, "title", "Pedido de arquivo", "initialDepartmentId", intakeDepartmentId))
                .getBody());
        assertThat(otherSubjectProcess.get("processNumber").toString()).isEqualTo(otherSubjectKey + "/2026/1");

        Map<String, Object> versionTwoBody = workflowBody(subjectKey, "Licença", steps,
                "GENERAL_SEQUENCE", "GERAL-{ano}-{numero}", 1);
        assertThat(createWorkflow(organizationId, ADMIN, versionTwoBody).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        Map<?, ?> priorSnapshot = Objects.requireNonNull(getProcess(organizationId, ADMIN,
                UUID.fromString(first.get("id").toString())).getBody());
        assertThat(priorSnapshot.get("processNumber")).isEqualTo(firstNumber);
        assertThat(priorSnapshot.get("workflowVersion")).isEqualTo(1);

        Map<?, ?> generalOne = Objects.requireNonNull(postProcess(organizationId, ADMIN, Map.of(
                "subjectKey", subjectKey, "title", "Pedido em sequência geral", "initialDepartmentId", intakeDepartmentId))
                .getBody());
        assertThat(generalOne.get("processNumber").toString()).isEqualTo("GERAL-2026-1");
        String otherGeneralSubject = "numbering-general-other";
        assertThat(createWorkflow(organizationId, ADMIN,
                workflowBody(otherGeneralSubject, "Outro assunto", steps,
                        "GENERAL_SEQUENCE", "GERAL-{ano}-{numero}", 0)).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        Map<?, ?> generalTwo = Objects.requireNonNull(postProcess(organizationId, ADMIN, Map.of(
                "subjectKey", otherGeneralSubject, "title", "Outro processo", "initialDepartmentId", intakeDepartmentId))
                .getBody());
        assertThat(generalTwo.get("processNumber").toString()).isEqualTo("GERAL-2026-2");

        String defaultGeneralSubject = "numbering-general-default";
        Map<String, Object> defaultGeneralWorkflow = new HashMap<>(workflowBody(defaultGeneralSubject,
                "Assunto geral padrão", steps));
        defaultGeneralWorkflow.put("numberingMode", "GENERAL_SEQUENCE");
        assertThat(createWorkflow(organizationId, ADMIN, defaultGeneralWorkflow).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        Map<?, ?> defaultGeneralView = Objects.requireNonNull(currentWorkflow(
                organizationId, ADMIN, defaultGeneralSubject, Map.class).getBody());
        assertThat(defaultGeneralView.get("numberPattern")).isEqualTo("{numero}");
        Map<?, ?> defaultGeneralProcess = Objects.requireNonNull(postProcess(organizationId, ADMIN, Map.of(
                "subjectKey", defaultGeneralSubject, "title", "Número geral padrão",
                "initialDepartmentId", intakeDepartmentId)).getBody());
        assertThat(defaultGeneralProcess.get("processNumber")).isEqualTo("3");

        Map<?, ?> mixedModeBySubject = Objects.requireNonNull(postProcess(organizationId, ADMIN, Map.of(
                "subjectKey", otherSubjectKey, "title", "Novo processo do assunto",
                "initialDepartmentId", intakeDepartmentId)).getBody());
        assertThat(mixedModeBySubject.get("processNumber")).isEqualTo(otherSubjectKey + "/2026/2");

        assertThat(createWorkflow(otherOrganizationId, ADMIN,
                workflowBody(subjectKey, "Licença Beta", List.of(
                                step("Recebimento", otherOrganizationDepartmentId),
                                step("Análise", otherOrganizationDepartmentId)),
                        "BY_SUBJECT", "{assunto}/{ano}/{numero}", 0)).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        Map<?, ?> betaProcess = Objects.requireNonNull(postProcess(otherOrganizationId, ADMIN, Map.of(
                "subjectKey", subjectKey, "title", "Pedido de Beta", "initialDepartmentId", otherOrganizationDepartmentId))
                .getBody());
        assertThat(betaProcess.get("processNumber")).isEqualTo(firstNumber);

        String authorizationSubject = "numbering-authorization";
        assertThat(createWorkflow(organizationId, ADMIN,
                workflowBody(authorizationSubject, "Autorização", steps,
                        "BY_SUBJECT", "{assunto}-{numero}", 0)).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        ResponseEntity<Map> denied = postProcess(organizationId, OTHER_STAGE_MEMBER, Map.of(
                "subjectKey", authorizationSubject, "title", "Negado", "initialDepartmentId", intakeDepartmentId));
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        Map<?, ?> authorized = Objects.requireNonNull(postProcess(organizationId, ADMIN, Map.of(
                "subjectKey", authorizationSubject, "title", "Autorizado", "initialDepartmentId", intakeDepartmentId))
                .getBody());
        assertThat(authorized.get("processNumber")).isEqualTo(authorizationSubject + "-1");

        Map<?, ?> searched = Objects.requireNonNull(listProcesses(
                organizationId, ADMIN, 0, 25, firstNumber, Map.class).getBody());
        assertThat((List<?>) searched.get("items")).anySatisfy(item ->
                assertThat(((Map<?, ?>) item).get("processNumber")).isEqualTo(firstNumber));

        String carryForwardSubject = "numbering-carry-forward";
        assertThat(createWorkflow(organizationId, ADMIN, workflowBody(carryForwardSubject, "Configuração", steps,
                "BY_SUBJECT", "REG-{assunto}/{numero}", 0)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> incompatibleModeChange = new HashMap<>(
                workflowBody(carryForwardSubject, "Configuração", steps, 1));
        incompatibleModeChange.put("numberingMode", "GENERAL_SEQUENCE");
        assertThat(createWorkflow(organizationId, ADMIN, incompatibleModeChange).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("""
                SELECT current_version FROM administrative_workflow_heads
                 WHERE organization_id=? AND subject_key=?
                """, Integer.class, organizationId, carryForwardSubject)).isEqualTo(1);
        Map<String, Object> carryForwardV2 = workflowBody(carryForwardSubject, "Configuração", steps, 1);
        ResponseEntity<Map> carried = createWorkflow(organizationId, ADMIN, carryForwardV2);
        assertThat(carried.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(carried.getBody()).containsEntry("version", 2).containsEntry("numberingMode", "BY_SUBJECT")
                .containsEntry("numberPattern", "REG-{assunto}/{numero}");
        ResponseEntity<Map> replay = createWorkflow(organizationId, ADMIN, carryForwardV2);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody()).containsEntry("version", 2).containsEntry("numberPattern", "REG-{assunto}/{numero}");
        Map<?, ?> carryForwardProcess = Objects.requireNonNull(postProcess(organizationId, ADMIN, Map.of(
                "subjectKey", carryForwardSubject, "title", "Após republicação",
                "initialDepartmentId", intakeDepartmentId)).getBody());
        assertThat(carryForwardProcess.get("processNumber")).isEqualTo("REG-" + carryForwardSubject + "/1");
    }

    @Test
    void processNumberSequenceRejectsLongMaxValueAndNumbersCannotBeChanged() {
        String subjectKey = "numbering-overflow";
        assertThat(createWorkflow(organizationId, ADMIN, workflowBody(subjectKey, "Limite", List.of(
                step("Recebimento", intakeDepartmentId), step("Análise", analysisDepartmentId)),
                "GENERAL_SEQUENCE", "LIM-{numero}", 0)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        jdbc.update("""
                INSERT INTO administrative_process_number_counters(organization_id, counter_scope, next_value)
                VALUES(?, 'GENERAL', ?)
                """, organizationId, Long.MAX_VALUE);

        ResponseEntity<Map> overflow = postProcess(organizationId, ADMIN, Map.of(
                "subjectKey", subjectKey, "title", "Não deve consumir o último valor",
                "initialDepartmentId", intakeDepartmentId));
        assertThat(overflow.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM administrative_processes
                 WHERE organization_id=? AND workflow_subject_key=?
                """, Integer.class, organizationId, subjectKey)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT next_value FROM administrative_process_number_counters
                 WHERE organization_id=? AND counter_scope='GENERAL'
                """, Long.class, organizationId)).isEqualTo(Long.MAX_VALUE);

        jdbc.update("DELETE FROM administrative_process_number_counters WHERE organization_id=?", organizationId);
        Map<?, ?> process = Objects.requireNonNull(postProcess(organizationId, ADMIN, Map.of(
                "subjectKey", subjectKey, "title", "Número imutável",
                "initialDepartmentId", intakeDepartmentId)).getBody());
        UUID processId = UUID.fromString(process.get("id").toString());
        assertThat(process.get("processNumber")).isEqualTo("LIM-1");
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE administrative_processes SET process_number='EDITADO'
                 WHERE organization_id=? AND id=?
                """, organizationId, processId)).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("""
                SELECT process_number FROM administrative_processes WHERE organization_id=? AND id=?
                """, String.class, organizationId, processId)).isEqualTo("LIM-1");
    }

    @Test
    void concurrentProcessOpeningsReceiveUniqueGeneralNumbers() throws Exception {
        String subjectKey = "numbering-concurrent";
        assertThat(createWorkflow(organizationId, ADMIN,
                workflowBody(subjectKey, "Fila concorrente", List.of(
                        step("Recebimento", intakeDepartmentId), step("Análise", analysisDepartmentId)),
                        "GENERAL_SEQUENCE", "FILA-{numero}", 0)).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        int count = 8;
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(count);
        try {
            List<Future<ResponseEntity<Map>>> requests = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                int requestIndex = index;
                requests.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start latch timed out");
                    return postProcess(organizationId, ADMIN, Map.of(
                            "subjectKey", subjectKey, "title", "Concorrente " + requestIndex,
                            "initialDepartmentId", intakeDepartmentId));
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<String> numbers = new ArrayList<>();
            for (Future<ResponseEntity<Map>> request : requests) {
                ResponseEntity<Map> response = request.get(15, TimeUnit.SECONDS);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                numbers.add(Objects.requireNonNull(response.getBody()).get("processNumber").toString());
            }
            assertThat(numbers).doesNotHaveDuplicates()
                    .containsExactlyInAnyOrder("FILA-1", "FILA-2", "FILA-3", "FILA-4",
                            "FILA-5", "FILA-6", "FILA-7", "FILA-8");
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void processDeadlinesAreAppendOnlyAuthorizedTenantScopedAndOrdered() {
        String subjectKey = "process-multiple-deadlines";
        assertThat(createWorkflow(organizationId, ADMIN, workflowBody(subjectKey, "Análise documental", List.of(
                step("Recebimento", intakeDepartmentId),
                step("Análise", analysisDepartmentId)))).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID processId = createProcess(subjectKey, intakeDepartmentId, "Projeto de reforma da praça");

        OffsetDateTime laterDueAt = OffsetDateTime.parse("2026-11-20T17:00:00-03:00");
        OffsetDateTime earlierDueAt = OffsetDateTime.parse("2026-11-12T12:30:00-03:00");
        ResponseEntity<Map> first = addProcessDeadline(organizationId, ADMIN, processId,
                Map.of("title", "Enviar plantas revisadas", "dueAt", laterDueAt));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getHeaders().getCacheControl()).isEqualTo("no-store");
        List<?> firstDeadlines = (List<?>) Objects.requireNonNull(first.getBody()).get("deadlines");
        assertThat(firstDeadlines).hasSize(1);
        Map<?, ?> firstDeadline = (Map<?, ?>) firstDeadlines.getFirst();
        assertThat(firstDeadline.get("title")).isEqualTo("Enviar plantas revisadas");
        assertThat(firstDeadline.get("createdBy")).isEqualTo(ADMIN);
        assertThat(firstDeadline.get("createdAt")).isNotNull();
        assertThat(OffsetDateTime.parse(firstDeadline.get("dueAt").toString()))
                .isEqualTo(laterDueAt);

        ResponseEntity<String> wrongDepartment = addProcessDeadline(organizationId, OTHER_STAGE_MEMBER,
                processId, Map.of("title", "Prazo de setor alheio", "dueAt", earlierDueAt), String.class);
        assertThat(wrongDepartment.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(countProcessDeadlines(organizationId, processId)).isEqualTo(1);

        addDepartmentMember(organizationId, OTHER_STAGE_MEMBER, intakeDepartmentId);
        ResponseEntity<Map> second = addProcessDeadline(organizationId, OTHER_STAGE_MEMBER, processId,
                Map.of("title", "Completar formulário", "dueAt", earlierDueAt));
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        List<?> orderedDeadlines = (List<?>) Objects.requireNonNull(second.getBody()).get("deadlines");
        assertThat(orderedDeadlines).hasSize(2);
        assertThat(((Map<?, ?>) orderedDeadlines.getFirst()).get("title")).isEqualTo("Completar formulário");
        assertThat(((Map<?, ?>) orderedDeadlines.getLast()).get("title")).isEqualTo("Enviar plantas revisadas");

        ResponseEntity<Map> reloaded = getProcess(organizationId, ADMIN, processId, Map.class);
        assertThat((List<?>) Objects.requireNonNull(reloaded.getBody()).get("deadlines"))
                .usingRecursiveComparison().isEqualTo(orderedDeadlines);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM administrative_process_deadlines
                 WHERE organization_id=? AND process_id=? AND created_by=? AND created_at IS NOT NULL
                """, Integer.class, organizationId, processId, OTHER_STAGE_MEMBER)).isEqualTo(1);
        UUID firstDeadlineId = UUID.fromString(((Map<?, ?>) orderedDeadlines.getLast()).get("id").toString());
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE administrative_process_deadlines SET title='Alteração indevida'
                 WHERE organization_id=? AND process_id=? AND id=?
                """, organizationId, processId, firstDeadlineId)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                DELETE FROM administrative_process_deadlines
                 WHERE organization_id=? AND process_id=? AND id=?
                """, organizationId, processId, firstDeadlineId)).isInstanceOf(DataAccessException.class);
        assertThat(countProcessDeadlines(organizationId, processId)).isEqualTo(2);

        UUID anotherProcess = UUID.randomUUID();
        ResponseEntity<String> crossTenant = addProcessDeadline(otherOrganizationId, ADMIN, processId,
                Map.of("title", "Tentativa de acesso", "dueAt", earlierDueAt), String.class);
        ResponseEntity<String> absent = addProcessDeadline(otherOrganizationId, ADMIN, anotherProcess,
                Map.of("title", "Tentativa de acesso", "dueAt", earlierDueAt), String.class);
        assertThat(crossTenant.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(crossTenant.getBody()).isEqualTo(absent.getBody());
        assertThat(countProcessDeadlines(organizationId, processId)).isEqualTo(2);

        ResponseEntity<String> invalid = addProcessDeadline(organizationId, ADMIN, processId,
                Map.of("title", "  ", "dueAt", laterDueAt), String.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(countProcessDeadlines(organizationId, processId)).isEqualTo(2);
    }

    @Test
    void versionsDefinitionsSnapshotsProcessesAndAllowsOnlySequentialAdvancementWithinTenant() {
        String subjectKey = "building-permit";
        ResponseEntity<Map> versionOneResponse = createWorkflow(organizationId, ADMIN,
                workflowBody(subjectKey, "Licença de obra", List.of(
                        step("Recebimento", intakeDepartmentId),
                        step("Análise", analysisDepartmentId),
                        step("Parecer jurídico", legalDepartmentId),
                        step("Decisão", decisionDepartmentId))));
        assertThat(versionOneResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(versionOneResponse.getBody()).containsEntry("version", 1);

        ResponseEntity<Map> createdProcess = postProcess(organizationId, ADMIN, Map.of(
                "subjectKey", subjectKey,
                "title", "Licença fictícia 2026-001",
                "initialDepartmentId", intakeDepartmentId));
        assertThat(createdProcess.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID processId = UUID.fromString(Objects.requireNonNull(createdProcess.getBody()).get("id").toString());

        jdbc.update("UPDATE departments SET name=? WHERE organization_id=? AND id=?",
                "Análise renomeada", organizationId, analysisDepartmentId);
        Map<?, ?> workflowOneSnapshot = Objects.requireNonNull(
                currentWorkflow(organizationId, ADMIN, subjectKey, Map.class).getBody());
        assertWorkflowDepartmentName(workflowOneSnapshot, 2, "Análise");
        Map<?, ?> processSnapshotBeforeAdvance = Objects.requireNonNull(
                getProcess(organizationId, ADMIN, processId).getBody());
        assertProcessDepartmentName(processSnapshotBeforeAdvance, 2, "Análise");

        ResponseEntity<Map> versionTwoResponse = createWorkflow(organizationId, ADMIN,
                workflowBody(subjectKey, "Licença de obra", List.of(
                        step("Recebimento revisado", intakeDepartmentId),
                        step("Análise revisada", analysisDepartmentId),
                        step("Decisão revisada", decisionDepartmentId)), 1));
        assertThat(versionTwoResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(versionTwoResponse.getBody()).containsEntry("version", 2);
        assertWorkflowDepartmentName(Objects.requireNonNull(versionTwoResponse.getBody()), 2, "Análise renomeada");

        ResponseEntity<Map> betaVersion = createWorkflow(otherOrganizationId, ADMIN,
                workflowBody(subjectKey, "Licença Beta", List.of(
                        step("Entrada Beta", otherOrganizationDepartmentId),
                        step("Conclusão Beta", otherOrganizationDepartmentId))));
        assertThat(betaVersion.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(betaVersion.getBody()).containsEntry("version", 1);

        Map<?, ?> processBeforeTransition = Objects.requireNonNull(
                getProcess(organizationId, ADMIN, processId).getBody());
        assertThat(processBeforeTransition.get("workflowVersion")).isEqualTo(1);
        assertThat(processBeforeTransition.get("currentStepOrder")).isEqualTo(1);
        assertThat(processBeforeTransition.get("currentStepName")).isEqualTo("Recebimento");
        assertThat((List<?>) processBeforeTransition.get("steps")).hasSize(4);

        ProcessMutationSnapshot firstStageBeforeInvalidTransitions = processMutationSnapshot(organizationId, processId);
        long eventsBeforeInvalidTransitions = countEvents(organizationId, processId);
        ResponseEntity<String> skippedFromFirst = advance(organizationId, ADMIN, processId,
                3, legalDepartmentId, String.class);
        assertThat(skippedFromFirst.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertUnchanged(organizationId, processId, firstStageBeforeInvalidTransitions,
                eventsBeforeInvalidTransitions);

        ResponseEntity<String> wrongStageActor = advance(organizationId, OTHER_STAGE_MEMBER, processId,
                2, analysisDepartmentId, String.class);
        assertThat(wrongStageActor.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertUnchanged(organizationId, processId, firstStageBeforeInvalidTransitions,
                eventsBeforeInvalidTransitions);

        ResponseEntity<Map> firstAdvance = advance(organizationId, ADMIN, processId,
                2, analysisDepartmentId, Map.class);
        assertThat(firstAdvance.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstAdvance.getBody()).containsEntry("currentStepOrder", 2)
                .containsEntry("workflowVersion", 1)
                .containsEntry("currentDepartmentName", "Análise");

        ProcessMutationSnapshot secondStageBeforeInvalidTransitions = processMutationSnapshot(organizationId, processId);
        long eventsAtSecondStage = countEvents(organizationId, processId);
        ResponseEntity<String> rollback = advance(organizationId, ADMIN, processId,
                1, intakeDepartmentId, String.class);
        assertThat(rollback.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertUnchanged(organizationId, processId, secondStageBeforeInvalidTransitions,
                eventsAtSecondStage);

        ResponseEntity<String> skippedFromSecond = advance(organizationId, ADMIN, processId,
                4, decisionDepartmentId, String.class);
        assertThat(skippedFromSecond.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertUnchanged(organizationId, processId, secondStageBeforeInvalidTransitions,
                eventsAtSecondStage);

        ResponseEntity<Map> secondAdvance = advance(organizationId, ADMIN, processId,
                3, legalDepartmentId, Map.class);
        assertThat(secondAdvance.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondAdvance.getBody()).containsEntry("currentStepOrder", 3)
                .containsEntry("currentStepName", "Parecer jurídico")
                .containsEntry("workflowVersion", 1);
        ProcessMutationSnapshot thirdStageBeforeForeignTenant = processMutationSnapshot(organizationId, processId);
        long eventsAtThirdStage = countEvents(organizationId, processId);

        Map<?, ?> processAfterDefinitionUpdate = Objects.requireNonNull(
                getProcess(organizationId, ADMIN, processId).getBody());
        assertThat(processAfterDefinitionUpdate.get("workflowVersion")).isEqualTo(1);
        assertThat(processAfterDefinitionUpdate.get("currentStepName")).isEqualTo("Parecer jurídico");
        assertThat((List<?>) processAfterDefinitionUpdate.get("steps")).hasSize(4);

        ResponseEntity<String> foreignProcessRead = getProcess(otherOrganizationId, ADMIN, processId, String.class);
        assertThat(foreignProcessRead.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<String> foreignProcessAdvance = advance(otherOrganizationId, ADMIN, processId,
                4, otherOrganizationDepartmentId, String.class);
        assertThat(foreignProcessAdvance.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertUnchanged(organizationId, processId, thirdStageBeforeForeignTenant, eventsAtThirdStage);
        assertThat(currentWorkflow(otherOrganizationId, ADMIN, subjectKey, String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(currentWorkflow(organizationId, ADMIN, subjectKey, Map.class).getBody())
                .containsEntry("version", 2);
        assertEventTimestampsStrictlyIncrease(organizationId, processId);
    }

    @Test
    void processDecisionsAreConfiguredPerDepartmentSnapshottedAndTenantScoped() {
        String subjectKey = "process-decision-policy";
        addDepartmentMember(organizationId, OTHER_STAGE_MEMBER, analysisDepartmentId);
        jdbc.update("UPDATE staff_memberships SET role_code='ADMIN' WHERE username=? AND organization_id=? AND department_id=?",
                OTHER_STAGE_MEMBER, organizationId, analysisDepartmentId);

        assertThat(createWorkflow(organizationId, ADMIN, workflowBody(subjectKey, "Obra pública", List.of(
                step("Recebimento", List.of(intakeDepartmentId, analysisDepartmentId), List.of(intakeDepartmentId)),
                step("Análise", decisionDepartmentId)))).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> configuredWorkflow = Objects.requireNonNull(
                currentWorkflow(organizationId, ADMIN, subjectKey, Map.class).getBody());
        Map<?, ?> intakePolicy = (Map<?, ?>) ((List<?>) ((Map<?, ?>) ((List<?>) configuredWorkflow.get("steps")).getFirst())
                .get("departments")).stream()
                .filter(department -> ((Map<?, ?>) department).get("id").equals(intakeDepartmentId.toString()))
                .findFirst().orElseThrow();
        assertThat(intakePolicy.get("canDecide")).isEqualTo(true);

        UUID deferidoProcess = createProcess(subjectKey, intakeDepartmentId, "Processo deferido");
        UUID indeferidoProcess = createProcess(subjectKey, intakeDepartmentId, "Processo indeferido");
        UUID disabledDepartmentProcess = createProcess(subjectKey, analysisDepartmentId,
                "Setor da etapa sem permissão decisória");
        UUID oldSnapshotProcess = createProcess(subjectKey, intakeDepartmentId, "Snapshot anterior");

        ProcessMutationSnapshot beforeUnauthorizedActor = processMutationSnapshot(organizationId, deferidoProcess);
        long eventsBeforeUnauthorizedActor = countEvents(organizationId, deferidoProcess);
        assertThat(decide(organizationId, OTHER_STAGE_MEMBER, deferidoProcess, "DEFERIDO", String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertUnchanged(organizationId, deferidoProcess, beforeUnauthorizedActor, eventsBeforeUnauthorizedActor);

        Map<?, ?> deferred = Objects.requireNonNull(
                decide(organizationId, ADMIN, deferidoProcess, "DEFERIDO", Map.class).getBody());
        assertThat(deferred.get("status")).isEqualTo("ACTIVE");
        assertThat(deferred.get("decisionOutcome")).isEqualTo("DEFERIDO");
        assertThat(deferred.get("decisionActor")).isEqualTo(ADMIN);
        assertThat(deferred.get("decisionAt")).isNotNull();
        List<?> deferredEvents = (List<?>) deferred.get("events");
        assertThat(deferredEvents).hasSize(2);
        Map<?, ?> decisionEvent = (Map<?, ?>) deferredEvents.getLast();
        assertThat(decisionEvent.get("action")).isEqualTo("DECIDED");
        assertThat(decisionEvent.get("decisionOutcome")).isEqualTo("DEFERIDO");
        assertThat(decisionEvent.get("fromStepOrder")).isEqualTo(1);
        assertThat(decisionEvent.get("toStepOrder")).isEqualTo(1);
        assertThat(decisionEvent.get("fromDepartmentId")).isEqualTo(intakeDepartmentId.toString());
        assertThat(decisionEvent.get("toDepartmentId")).isEqualTo(intakeDepartmentId.toString());
        long deferredEventCount = countEvents(organizationId, deferidoProcess);
        assertThat(decide(organizationId, ADMIN, deferidoProcess, "DEFERIDO", Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(countEvents(organizationId, deferidoProcess)).isEqualTo(deferredEventCount);

        addDepartmentMember(organizationId, OTHER_STAGE_MEMBER, intakeDepartmentId);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM organization_memberships
                 WHERE username=? AND organization_id=? AND role_code='ADMIN' AND active=true
                """, Integer.class, OTHER_STAGE_MEMBER, organizationId)).isZero();
        Map<?, ?> sectorMemberDecision = Objects.requireNonNull(
                decide(organizationId, OTHER_STAGE_MEMBER, indeferidoProcess, "INDEFERIDO", Map.class).getBody());
        assertThat(sectorMemberDecision.get("decisionOutcome")).isEqualTo("INDEFERIDO");
        assertThat(sectorMemberDecision.get("decisionActor")).isEqualTo(OTHER_STAGE_MEMBER);
        assertThat(sectorMemberDecision.get("status")).isEqualTo("ACTIVE");
        Map<?, ?> deferredPage = Objects.requireNonNull(
                listProcesses(organizationId, ADMIN, 0, 100, "Processo deferido", Map.class).getBody());
        List<?> deferredItems = (List<?>) deferredPage.get("items");
        assertThat(deferredItems).hasSize(1);
        assertThat(((Map<?, ?>) deferredItems.getFirst()).get("decisionOutcome")).isEqualTo("DEFERIDO");

        Map<?, ?> changedDecision = Objects.requireNonNull(
                decide(organizationId, ADMIN, deferidoProcess, "INDEFERIDO", Map.class).getBody());
        assertThat(changedDecision.get("status")).isEqualTo("ACTIVE");
        assertThat(changedDecision.get("decisionOutcome")).isEqualTo("INDEFERIDO");
        List<?> changedDecisionEvents = (List<?>) changedDecision.get("events");
        assertThat(changedDecisionEvents).hasSize(3);
        List<String> changedDecisionOutcomes = changedDecisionEvents.stream()
                .map(event -> (Map<?, ?>) event)
                .filter(event -> "DECIDED".equals(event.get("action")))
                .map(event -> (String) event.get("decisionOutcome"))
                .toList();
        assertThat(changedDecisionOutcomes).containsExactly("DEFERIDO", "INDEFERIDO");

        ProcessMutationSnapshot beforeDisabledDepartment = processMutationSnapshot(organizationId, disabledDepartmentProcess);
        long eventsBeforeDisabledDepartment = countEvents(organizationId, disabledDepartmentProcess);
        assertThat(decide(organizationId, ADMIN, disabledDepartmentProcess, "DEFERIDO", String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertUnchanged(organizationId, disabledDepartmentProcess, beforeDisabledDepartment, eventsBeforeDisabledDepartment);

        assertThat(createWorkflow(organizationId, ADMIN, workflowBody(subjectKey, "Obra pública", List.of(
                step("Recebimento v2", List.of(intakeDepartmentId, analysisDepartmentId)),
                step("Análise v2", decisionDepartmentId)), 1)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> oldSnapshot = Objects.requireNonNull(getProcess(organizationId, ADMIN, oldSnapshotProcess).getBody());
        Map<?, ?> oldIntakeDepartment = (Map<?, ?>) ((List<?>) ((Map<?, ?>) ((List<?>) oldSnapshot.get("steps")).getFirst())
                .get("departments")).stream()
                .filter(department -> ((Map<?, ?>) department).get("id").equals(intakeDepartmentId.toString()))
                .findFirst().orElseThrow();
        assertThat(oldIntakeDepartment.get("canDecide")).isEqualTo(true);
        assertThat(decide(organizationId, ADMIN, oldSnapshotProcess, "INDEFERIDO", Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        UUID newVersionProcess = createProcess(subjectKey, intakeDepartmentId, "Novo snapshot sem decisão");
        Map<?, ?> newSnapshot = Objects.requireNonNull(getProcess(organizationId, ADMIN, newVersionProcess).getBody());
        Map<?, ?> newIntakeDepartment = (Map<?, ?>) ((List<?>) ((Map<?, ?>) ((List<?>) newSnapshot.get("steps")).getFirst())
                .get("departments")).stream()
                .filter(department -> ((Map<?, ?>) department).get("id").equals(intakeDepartmentId.toString()))
                .findFirst().orElseThrow();
        assertThat(newIntakeDepartment.get("canDecide")).isEqualTo(false);
        ProcessMutationSnapshot beforeNewVersionDenied = processMutationSnapshot(organizationId, newVersionProcess);
        long eventsBeforeNewVersionDenied = countEvents(organizationId, newVersionProcess);
        assertThat(decide(organizationId, ADMIN, newVersionProcess, "DEFERIDO", String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertUnchanged(organizationId, newVersionProcess, beforeNewVersionDenied, eventsBeforeNewVersionDenied);

        String absentProcessResponse = getProcess(otherOrganizationId, ADMIN, UUID.randomUUID(), String.class).getBody();
        ProcessMutationSnapshot beforeCrossTenantDecision = processMutationSnapshot(organizationId, deferidoProcess);
        long eventsBeforeCrossTenantDecision = countEvents(organizationId, deferidoProcess);
        ResponseEntity<String> foreignTenantDecision = decide(
                otherOrganizationId, ADMIN, deferidoProcess, "INDEFERIDO", String.class);
        assertThat(foreignTenantDecision.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(foreignTenantDecision.getBody()).isEqualTo(absentProcessResponse);
        assertUnchanged(organizationId, deferidoProcess, beforeCrossTenantDecision,
                eventsBeforeCrossTenantDecision);
    }

    @Test
    void concurrentSameProcessDecisionIsSerializedAndDoesNotDuplicateHistory() throws Exception {
        String subjectKey = "process-decision-concurrent";
        assertThat(createWorkflow(organizationId, ADMIN, workflowBody(subjectKey, "Concurrent decision", List.of(
                step("Recebimento", List.of(intakeDepartmentId), List.of(intakeDepartmentId)),
                step("Análise", analysisDepartmentId)))).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID processId = createProcess(subjectKey, intakeDepartmentId);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<ResponseEntity<Map>>> requests = List.of(1, 2).stream().map(index -> executor.submit(() -> {
                awaitLatch(start);
                return decide(organizationId, ADMIN, processId, "DEFERIDO", Map.class);
            })).toList();
            start.countDown();
            List<ResponseEntity<Map>> responses = new ArrayList<>();
            for (Future<ResponseEntity<Map>> request : requests) responses.add(request.get(15, TimeUnit.SECONDS));
            assertThat(responses).extracting(ResponseEntity::getStatusCode)
                    .containsExactly(HttpStatus.OK, HttpStatus.OK);
            assertThat(countEvents(organizationId, processId)).isEqualTo(2);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM administrative_process_events
                     WHERE organization_id=? AND process_id=? AND action='DECIDED'
                    """, Integer.class, organizationId, processId)).isEqualTo(1);
            assertThat(getProcess(organizationId, ADMIN, processId).getBody())
                    .containsEntry("decisionOutcome", "DEFERIDO").containsEntry("status", "ACTIVE");
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void workflowConfigurationRequiresOrganizationAdministratorAndDepartmentsMustBelongToTenant() {
        Map<String, Object> requestBody = workflowBody("permit", "Permit", List.of(
                step("Entrada", intakeDepartmentId), step("Decisão", decisionDepartmentId)));
        ResponseEntity<String> sectorOnlyAdministrator = createWorkflow(organizationId, OTHER_STAGE_MEMBER,
                requestBody, String.class);
        assertThat(sectorOnlyAdministrator.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM administrative_workflows WHERE organization_id=?",
                Integer.class, organizationId)).isZero();

        ResponseEntity<String> foreignDepartment = createWorkflow(organizationId, ADMIN,
                workflowBody("permit", "Permit", List.of(
                        step("Entrada", intakeDepartmentId), step("Decisão", otherOrganizationDepartmentId))),
                String.class);
        assertThat(foreignDepartment.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM administrative_workflows WHERE organization_id=?",
                Integer.class, organizationId)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM administrative_workflow_heads WHERE organization_id=?",
                Integer.class, organizationId)).isZero();
    }

    @Test
    void processOpeningWaitsForMembershipRevocationAndRejectsWithoutCreatingProcessOrEvent() throws Exception {
        String subjectKey = "opening-membership-race";
        assertThat(createWorkflow(organizationId, ADMIN, workflowBody(subjectKey, "Opening race", List.of(
                step("Entrada", intakeDepartmentId), step("Decisão", decisionDepartmentId))))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        CountDownLatch revocationHeld = new CountDownLatch(1);
        CountDownLatch permitRevocationCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            Future<?> revocation = executor.submit(() -> transaction.executeWithoutResult(status -> {
                jdbc.update("""
                        UPDATE staff_memberships SET active=false
                         WHERE username=? AND organization_id=? AND department_id=?
                        """, ADMIN, organizationId, intakeDepartmentId);
                revocationHeld.countDown();
                awaitLatch(permitRevocationCommit);
            }));
            assertThat(revocationHeld.await(10, TimeUnit.SECONDS)).isTrue();

            Future<ResponseEntity<String>> opening = executor.submit(() -> postProcess(organizationId, ADMIN,
                    Map.of("subjectKey", subjectKey, "title", "Abertura concorrente",
                            "initialDepartmentId", intakeDepartmentId), String.class));
            awaitPostgresLockWait(opening, "FOR UPDATE OF sm");
            permitRevocationCommit.countDown();
            revocation.get(10, TimeUnit.SECONDS);
            assertThat(opening.get(10, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertNoProcessOrEvent(organizationId, subjectKey);
        } finally {
            permitRevocationCommit.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void processOpeningWaitsForDepartmentDeactivationAndRejectsWithoutCreatingProcessOrEvent() throws Exception {
        String subjectKey = "opening-department-race";
        assertThat(createWorkflow(organizationId, ADMIN, workflowBody(subjectKey, "Opening race", List.of(
                step("Entrada", intakeDepartmentId), step("Decisão", decisionDepartmentId))))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        CountDownLatch deactivationHeld = new CountDownLatch(1);
        CountDownLatch permitDeactivationCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            Future<?> deactivation = executor.submit(() -> transaction.executeWithoutResult(status -> {
                jdbc.update("UPDATE departments SET active=false WHERE organization_id=? AND id=?",
                        organizationId, intakeDepartmentId);
                deactivationHeld.countDown();
                awaitLatch(permitDeactivationCommit);
            }));
            assertThat(deactivationHeld.await(10, TimeUnit.SECONDS)).isTrue();

            Future<ResponseEntity<String>> opening = executor.submit(() -> postProcess(organizationId, ADMIN,
                    Map.of("subjectKey", subjectKey, "title", "Abertura concorrente",
                            "initialDepartmentId", intakeDepartmentId), String.class));
            awaitPostgresLockWait(opening, "FOR SHARE OF d");
            permitDeactivationCommit.countDown();
            deactivation.get(10, TimeUnit.SECONDS);
            assertThat(opening.get(10, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertNoProcessOrEvent(organizationId, subjectKey);
        } finally {
            permitDeactivationCommit.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void workflowPublicationUsesCompareAndSwapAndReplaysOnlyTheSameIdempotencyKeyAndPayload() {
        Map<String, Object> publication = workflowBody("permit-retry", "Permit", List.of(
                step("Entrada", intakeDepartmentId), step("Decisão", decisionDepartmentId)));
        ResponseEntity<Map> first = createWorkflow(organizationId, ADMIN, publication);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody()).containsEntry("version", 1);

        ResponseEntity<Map> replay = createWorkflow(organizationId, ADMIN, publication);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody()).containsEntry("version", 1)
                .containsEntry("id", Objects.requireNonNull(first.getBody()).get("id"));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM administrative_workflows WHERE organization_id=? AND subject_key=?
                """, Integer.class, organizationId, "permit-retry")).isEqualTo(1);

        Map<String, Object> changedPayload = new java.util.HashMap<>(publication);
        changedPayload.put("subjectName", "Different permit");
        assertThat(createWorkflow(organizationId, ADMIN, changedPayload, String.class).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        Map<String, Object> stalePublication = workflowBody("permit-retry", "Permit", List.of(
                step("Entrada", intakeDepartmentId), step("Decisão", decisionDepartmentId)));
        assertThat(createWorkflow(organizationId, ADMIN, stalePublication, String.class).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM administrative_workflows WHERE organization_id=? AND subject_key=?
                """, Integer.class, organizationId, "permit-retry")).isEqualTo(1);

        ResponseEntity<Map> nextVersion = createWorkflow(organizationId, ADMIN,
                workflowBody("permit-retry", "Permit", List.of(
                        step("Entrada", intakeDepartmentId), step("Decisão revisada", decisionDepartmentId)), 1));
        assertThat(nextVersion.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(nextVersion.getBody()).containsEntry("version", 2);
        assertThat(createWorkflow(organizationId, ADMIN, publication).getBody())
                .containsEntry("version", 1);
    }

    @Test
    void idempotencyFramingDistinguishesNamesThatContainTheFormerDelimiters() {
        Map<String, Object> firstPayload = workflowBody("hash-ambiguity", "Permit", List.of(
                step("foo", intakeDepartmentId),
                step("bar\n" + analysisDepartmentId + "\n--step--\nbaz", legalDepartmentId),
                step("qux", decisionDepartmentId)));
        ResponseEntity<Map> first = createWorkflow(organizationId, ADMIN, firstPayload);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> distinctPayloadWithSameLegacyEncoding = new java.util.HashMap<>(firstPayload);
        distinctPayloadWithSameLegacyEncoding.put("steps", List.of(
                step("foo", intakeDepartmentId),
                step("bar", analysisDepartmentId),
                step("baz", legalDepartmentId),
                step("qux", decisionDepartmentId)));
        assertThat(createWorkflow(organizationId, ADMIN, distinctPayloadWithSameLegacyEncoding, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM administrative_workflows WHERE organization_id=? AND subject_key=?
                """, Integer.class, organizationId, "hash-ambiguity")).isEqualTo(1);
    }

    @Test
    void concurrentPublicationSerializesSameKeyRetriesAndExpectedVersions() throws Exception {
        String subjectKey = "publication-concurrent";
        Map<String, Object> initial = workflowBody(subjectKey, "Permit", List.of(
                step("Entrada", intakeDepartmentId), step("Decisão", decisionDepartmentId)));
        List<ResponseEntity<Map>> retries = postWorkflowsConcurrently(List.of(initial, initial));
        assertThat(retries).extracting(ResponseEntity::getStatusCode)
                .containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.OK);
        assertThat(retries.getFirst().getBody().get("id"))
                .isEqualTo(retries.get(1).getBody().get("id"));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM administrative_workflows WHERE organization_id=? AND subject_key=?
                """, Integer.class, organizationId, subjectKey)).isEqualTo(1);

        Map<String, Object> candidateA = workflowBody(subjectKey, "Permit A", List.of(
                step("Entrada A", intakeDepartmentId), step("Decisão A", decisionDepartmentId)), 1);
        Map<String, Object> candidateB = workflowBody(subjectKey, "Permit B", List.of(
                step("Entrada B", intakeDepartmentId), step("Decisão B", decisionDepartmentId)), 1);
        List<ResponseEntity<Map>> competingVersions = postWorkflowsConcurrently(List.of(candidateA, candidateB));
        assertThat(competingVersions).extracting(ResponseEntity::getStatusCode)
                .containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM administrative_workflows WHERE organization_id=? AND subject_key=?
                """, Integer.class, organizationId, subjectKey)).isEqualTo(2);
        assertThat(currentWorkflow(organizationId, ADMIN, subjectKey, Map.class).getBody())
                .containsEntry("version", 2);
    }

    @Test
    void publicationWaitsForDepartmentDeactivationAndDoesNotPersistPartialStages() throws Exception {
        String subjectKey = "department-publication-race";
        Map<String, Object> body = workflowBody(subjectKey, "Permit", List.of(
                step("Entrada", intakeDepartmentId), step("Análise", analysisDepartmentId)));
        UUID idempotencyKey = (UUID) body.get("idempotencyKey");
        CountDownLatch deactivationLockHeld = new CountDownLatch(1);
        CountDownLatch permitDeactivationCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            Future<?> deactivation = executor.submit(() -> transaction.executeWithoutResult(status -> {
                jdbc.update("UPDATE departments SET active=false WHERE organization_id=? AND id=?",
                        organizationId, analysisDepartmentId);
                deactivationLockHeld.countDown();
                awaitLatch(permitDeactivationCommit);
            }));
            assertThat(deactivationLockHeld.await(10, TimeUnit.SECONDS)).isTrue();

            Future<ResponseEntity<String>> publication = executor.submit(() ->
                    createWorkflow(organizationId, ADMIN, body, String.class));
            awaitPostgresLockWait(publication, "FOR SHARE");
            permitDeactivationCommit.countDown();
            deactivation.get(10, TimeUnit.SECONDS);
            assertThat(publication.get(10, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM administrative_workflows WHERE organization_id=? AND subject_key=?
                    """, Integer.class, organizationId, subjectKey)).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM administrative_workflow_heads WHERE organization_id=? AND subject_key=?
                    """, Integer.class, organizationId, subjectKey)).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM administrative_workflow_publication_keys
                     WHERE organization_id=? AND idempotency_key=?
                    """, Integer.class, organizationId, idempotencyKey)).isZero();
        } finally {
            permitDeactivationCommit.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentAdvancesWaitOnTheSameAssignedMembershipLockWithoutDeadlock() throws Exception {
        String subjectA = "membership-order-a";
        String subjectB = "membership-order-b";
        assertThat(createWorkflow(organizationId, ADMIN, workflowBody(subjectA, "Workflow A", List.of(
                step("Entrada", List.of(intakeDepartmentId, analysisDepartmentId)),
                step("Decisão", legalDepartmentId)))).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createWorkflow(organizationId, ADMIN, workflowBody(subjectB, "Workflow B", List.of(
                step("Entrada", List.of(analysisDepartmentId, intakeDepartmentId)),
                step("Decisão", decisionDepartmentId)))).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        UUID processA = createProcess(subjectA, intakeDepartmentId);
        UUID processB = createProcess(subjectB, intakeDepartmentId);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch permitMembershipUnlock = new CountDownLatch(1);
        CountDownLatch membershipLockHeld = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            Future<?> membershipLock = executor.submit(() -> transaction.executeWithoutResult(status -> {
                List<String> locked = jdbc.query("""
                        SELECT username FROM staff_memberships
                         WHERE username=? AND organization_id=? AND department_id=? AND active=true
                         FOR UPDATE
                        """, (rs, row) -> rs.getString("username"), ADMIN, organizationId,
                        intakeDepartmentId);
                if (locked.size() != 1) throw new IllegalStateException("Expected one seeded membership row.");
                membershipLockHeld.countDown();
                awaitLatch(permitMembershipUnlock);
            }));
            assertThat(membershipLockHeld.await(10, TimeUnit.SECONDS)).isTrue();

            Future<ResponseEntity<Map>> advanceA = executor.submit(() -> {
                awaitLatch(start);
                return advance(organizationId, ADMIN, processA, 2, legalDepartmentId, Map.class);
            });
            Future<ResponseEntity<Map>> advanceB = executor.submit(() -> {
                awaitLatch(start);
                return advance(organizationId, ADMIN, processB, 2, decisionDepartmentId, Map.class);
            });
            start.countDown();
            awaitPostgresLockWaiters(2, "FOR UPDATE OF sm");
            permitMembershipUnlock.countDown();
            membershipLock.get(10, TimeUnit.SECONDS);
            assertThat(advanceA.get(10, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(advanceB.get(10, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.OK);
        } finally {
            start.countDown();
            permitMembershipUnlock.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void currentDepartmentDeactivationCannotBeBypassedByAlternateStepMembership() throws Exception {
        String subjectKey = "department-deactivation-race";
        assertThat(createWorkflow(organizationId, ADMIN,
                workflowBody(subjectKey, "Department race", List.of(
                        step("Entrada", List.of(intakeDepartmentId, analysisDepartmentId)),
                        step("Decisão", decisionDepartmentId))))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID processId = createProcess(subjectKey, intakeDepartmentId);
        assertThat(jdbc.queryForObject("""
                SELECT active FROM staff_memberships
                 WHERE organization_id=? AND username=? AND department_id=?
                """, Boolean.class, organizationId, ADMIN, analysisDepartmentId)).isTrue();
        ProcessMutationSnapshot before = processMutationSnapshot(organizationId, processId);
        long eventsBefore = countEvents(organizationId, processId);

        CountDownLatch deactivationLockHeld = new CountDownLatch(1);
        CountDownLatch permitDeactivationCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            Future<?> deactivation = executor.submit(() -> transaction.executeWithoutResult(status -> {
                jdbc.update("UPDATE departments SET active=false WHERE organization_id=? AND id=?",
                        organizationId, intakeDepartmentId);
                deactivationLockHeld.countDown();
                awaitLatch(permitDeactivationCommit);
            }));
            assertThat(deactivationLockHeld.await(10, TimeUnit.SECONDS)).isTrue();

            Future<ResponseEntity<String>> advancement = executor.submit(() -> advance(
                    organizationId, ADMIN, processId, 2, decisionDepartmentId, String.class));
            awaitPostgresLockWait(advancement, "FOR SHARE OF d");
            permitDeactivationCommit.countDown();
            deactivation.get(10, TimeUnit.SECONDS);
            assertThat(advancement.get(10, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertUnchanged(organizationId, processId, before, eventsBefore);
            assertThat(jdbc.queryForObject("""
                    SELECT active FROM departments WHERE organization_id=? AND id=?
                    """, Boolean.class, organizationId, intakeDepartmentId)).isFalse();
        } finally {
            permitDeactivationCommit.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void advanceRejectsMembershipOnlyInAlternateDepartmentOfCurrentStep() {
        String subjectKey = "alternate-stage-membership";
        assertThat(createWorkflow(organizationId, ADMIN,
                workflowBody(subjectKey, "Alternate membership", List.of(
                        step("Entrada e triagem", List.of(intakeDepartmentId, analysisDepartmentId)),
                        step("Decisão", decisionDepartmentId))))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID processId = createProcess(subjectKey, intakeDepartmentId);
        jdbc.update("""
                DELETE FROM staff_memberships
                 WHERE organization_id=? AND username=? AND department_id=?
                """, organizationId, OTHER_STAGE_MEMBER, decisionDepartmentId);
        addDepartmentMember(organizationId, OTHER_STAGE_MEMBER, analysisDepartmentId);
        ProcessMutationSnapshot before = processMutationSnapshot(organizationId, processId);
        long eventsBefore = countEvents(organizationId, processId);

        ResponseEntity<String> unauthorizedAdvance = advance(
                organizationId, OTHER_STAGE_MEMBER, processId, 2, decisionDepartmentId, String.class);

        assertThat(unauthorizedAdvance.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertUnchanged(organizationId, processId, before, eventsBefore);
    }

    @Test
    void advanceChecksCurrentMembershipBeforeRevealingInactiveDestination() {
        String subjectKey = "destination-state-oracle";
        assertThat(createWorkflow(organizationId, ADMIN,
                workflowBody(subjectKey, "Destination state", List.of(
                        step("Entrada", intakeDepartmentId), step("Decisão", decisionDepartmentId))))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID processId = createProcess(subjectKey, intakeDepartmentId);
        ProcessMutationSnapshot before = processMutationSnapshot(organizationId, processId);
        long eventsBefore = countEvents(organizationId, processId);
        jdbc.update("UPDATE departments SET active=false WHERE organization_id=? AND id=?",
                organizationId, decisionDepartmentId);

        ResponseEntity<String> unauthorizedAdvance = advance(
                organizationId, OTHER_STAGE_MEMBER, processId, 2, decisionDepartmentId, String.class);

        assertThat(unauthorizedAdvance.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertUnchanged(organizationId, processId, before, eventsBefore);
    }

    @Test
    void advanceWaitsForDestinationDepartmentDeactivationAndRejectsWithoutMutation() throws Exception {
        String subjectKey = "destination-deactivation-race";
        assertThat(createWorkflow(organizationId, ADMIN,
                workflowBody(subjectKey, "Destination race", List.of(
                        step("Entrada", intakeDepartmentId), step("Decisão", decisionDepartmentId))))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID processId = createProcess(subjectKey, intakeDepartmentId);
        ProcessMutationSnapshot before = processMutationSnapshot(organizationId, processId);
        long eventsBefore = countEvents(organizationId, processId);

        CountDownLatch deactivationHeld = new CountDownLatch(1);
        CountDownLatch permitDeactivationCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            Future<?> deactivation = executor.submit(() -> transaction.executeWithoutResult(status -> {
                jdbc.update("UPDATE departments SET active=false WHERE organization_id=? AND id=?",
                        organizationId, decisionDepartmentId);
                deactivationHeld.countDown();
                awaitLatch(permitDeactivationCommit);
            }));
            assertThat(deactivationHeld.await(10, TimeUnit.SECONDS)).isTrue();

            Future<ResponseEntity<String>> advancement = executor.submit(() -> advance(
                    organizationId, ADMIN, processId, 2, decisionDepartmentId, String.class));
            awaitPostgresLockWait(advancement, "FOR SHARE OF d");
            permitDeactivationCommit.countDown();
            deactivation.get(10, TimeUnit.SECONDS);
            assertThat(advancement.get(10, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertUnchanged(organizationId, processId, before, eventsBefore);
            assertThat(jdbc.queryForObject("""
                    SELECT active FROM departments WHERE organization_id=? AND id=?
                    """, Boolean.class, organizationId, decisionDepartmentId)).isFalse();
        } finally {
            permitDeactivationCommit.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void advanceWaitsForInFlightMembershipRevocationAndRevalidatesWithoutMutation() throws Exception {
        String subjectKey = "membership-race";
        assertThat(createWorkflow(organizationId, ADMIN,
                workflowBody(subjectKey, "Membership race", List.of(
                        step("Entrada", intakeDepartmentId), step("Decisão", decisionDepartmentId))))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> processResponse = Objects.requireNonNull(postProcess(organizationId, ADMIN, Map.of(
                "subjectKey", subjectKey,
                "title", "Processo para corrida de membership",
                "initialDepartmentId", intakeDepartmentId)).getBody());
        UUID processId = UUID.fromString(processResponse.get("id").toString());
        ProcessMutationSnapshot before = processMutationSnapshot(organizationId, processId);
        long eventCountBefore = countEvents(organizationId, processId);

        CountDownLatch revocationLockHeld = new CountDownLatch(1);
        CountDownLatch permitRevocationCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            Future<?> revocation = executor.submit(() -> transaction.executeWithoutResult(status -> {
                jdbc.update("""
                        UPDATE staff_memberships SET active=false
                         WHERE username=? AND organization_id=? AND department_id=?
                        """, ADMIN, organizationId, intakeDepartmentId);
                revocationLockHeld.countDown();
                awaitLatch(permitRevocationCommit);
            }));
            assertThat(revocationLockHeld.await(10, TimeUnit.SECONDS)).isTrue();

            Future<ResponseEntity<String>> advancement = executor.submit(() -> advance(
                    organizationId, ADMIN, processId, 2, decisionDepartmentId, String.class));
            awaitPostgresLockWait(advancement, "FOR UPDATE OF sm");

            permitRevocationCommit.countDown();
            revocation.get(10, TimeUnit.SECONDS);
            assertThat(advancement.get(10, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertUnchanged(organizationId, processId, before, eventCountBefore);
            assertThat(jdbc.queryForObject("""
                    SELECT active FROM staff_memberships
                     WHERE username=? AND organization_id=? AND department_id=?
                    """, Boolean.class, ADMIN, organizationId, intakeDepartmentId)).isFalse();
        } finally {
            permitRevocationCommit.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void processDetailWaitsForCurrentDepartmentMembershipRevocationAndDeniesWithoutMutation() throws Exception {
        String subjectKey = "detail-membership-race";
        assertThat(createWorkflow(organizationId, ADMIN, workflowBody(subjectKey, "Detail membership race", List.of(
                step("Entrada", intakeDepartmentId), step("Decisão", decisionDepartmentId))))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        addDepartmentMember(organizationId, OTHER_STAGE_MEMBER, intakeDepartmentId);
        UUID processId = createProcess(subjectKey, intakeDepartmentId);
        ProcessMutationSnapshot before = processMutationSnapshot(organizationId, processId);
        long eventsBefore = countEvents(organizationId, processId);

        CountDownLatch revocationLockHeld = new CountDownLatch(1);
        CountDownLatch permitRevocationCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            Future<?> revocation = executor.submit(() -> transaction.executeWithoutResult(status -> {
                jdbc.update("""
                        UPDATE staff_memberships SET active=false
                         WHERE username=? AND organization_id=? AND department_id=?
                        """, OTHER_STAGE_MEMBER, organizationId, intakeDepartmentId);
                revocationLockHeld.countDown();
                awaitLatch(permitRevocationCommit);
            }));
            assertThat(revocationLockHeld.await(10, TimeUnit.SECONDS)).isTrue();

            Future<ResponseEntity<String>> detail = executor.submit(() ->
                    getProcess(organizationId, OTHER_STAGE_MEMBER, processId, String.class));
            awaitPostgresLockWait(detail, "FOR SHARE OF sm");

            permitRevocationCommit.countDown();
            revocation.get(10, TimeUnit.SECONDS);
            assertThat(detail.get(10, TimeUnit.SECONDS).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(jdbc.queryForObject("""
                    SELECT active FROM staff_memberships
                     WHERE username=? AND organization_id=? AND department_id=?
                    """, Boolean.class, OTHER_STAGE_MEMBER, organizationId, intakeDepartmentId)).isFalse();
            assertUnchanged(organizationId, processId, before, eventsBefore);
        } finally {
            permitRevocationCommit.countDown();
            executor.shutdownNow();
        }
    }

    private ResponseEntity<Map> createWorkflow(UUID organization, String actor, Map<String, Object> body) {
        return createWorkflow(organization, actor, body, Map.class);
    }

    private <T> ResponseEntity<T> createWorkflow(UUID organization, String actor, Object body, Class<T> type) {
        return http.exchange("/api/v1/staff/administrative-workflows", HttpMethod.POST,
                request(organization, actor, body), type);
    }

    private <T> ResponseEntity<T> currentWorkflow(UUID organization, String actor, String subjectKey, Class<T> type) {
        return http.exchange("/api/v1/staff/administrative-workflows/" + subjectKey,
                HttpMethod.GET, request(organization, actor, null), type);
    }

    private ResponseEntity<Map> postProcess(UUID organization, String actor, Object body) {
        return http.exchange("/api/v1/staff/administrative-processes", HttpMethod.POST,
                request(organization, actor, body), Map.class);
    }

    private <T> ResponseEntity<T> postProcess(UUID organization, String actor, Object body, Class<T> type) {
        return http.exchange("/api/v1/staff/administrative-processes", HttpMethod.POST,
                request(organization, actor, body), type);
    }

    private ResponseEntity<Map> getProcess(UUID organization, String actor, UUID processId) {
        return getProcess(organization, actor, processId, Map.class);
    }

    private <T> ResponseEntity<T> getProcess(UUID organization, String actor, UUID processId, Class<T> type) {
        return http.exchange("/api/v1/staff/administrative-processes/" + processId,
                HttpMethod.GET, request(organization, actor, null), type);
    }

    private ResponseEntity<Map> addProcessDeadline(UUID organization, String actor, UUID processId, Object body) {
        return addProcessDeadline(organization, actor, processId, body, Map.class);
    }

    private <T> ResponseEntity<T> addProcessDeadline(UUID organization, String actor, UUID processId,
                                                      Object body, Class<T> type) {
        return http.exchange("/api/v1/staff/administrative-processes/" + processId + "/deadlines",
                HttpMethod.POST, request(organization, actor, body), type);
    }

    private <T> ResponseEntity<T> advance(UUID organization, String actor, UUID processId,
                                          int targetStep, UUID destinationDepartment, Class<T> type) {
        return http.exchange("/api/v1/staff/administrative-processes/" + processId + "/advance",
                HttpMethod.POST, request(organization, actor, Map.of(
                        "toStepOrder", targetStep, "destinationDepartmentId", destinationDepartment)), type);
    }

    private <T> ResponseEntity<T> decide(UUID organization, String actor, UUID processId,
                                         String outcome, Class<T> type) {
        return http.exchange("/api/v1/staff/administrative-processes/" + processId + "/decision",
                HttpMethod.POST, request(organization, actor, Map.of("outcome", outcome)), type);
    }

    private HttpEntity<Object> request(UUID organization, String actor, Object body) {
        HttpHeaders headers = new HttpHeaders();
        String credentials = Base64.getEncoder().encodeToString((actor + ":" + PASSWORD)
                .getBytes(StandardCharsets.UTF_8));
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + credentials);
        headers.set("X-Organization-Id", organization.toString());
        if (body != null) headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private Map<String, Object> workflowBody(String subjectKey, String subjectName,
                                             List<Map<String, Object>> steps) {
        return workflowBody(subjectKey, subjectName, steps, 0);
    }

    private Map<String, Object> workflowBody(String subjectKey, String subjectName,
                                             List<Map<String, Object>> steps, int expectedVersion) {
        return Map.of("subjectKey", subjectKey, "subjectName", subjectName, "steps", steps,
                "expectedVersion", expectedVersion, "idempotencyKey", UUID.randomUUID());
    }

    private Map<String, Object> workflowBody(String subjectKey, String subjectName,
                                             List<Map<String, Object>> steps, String numberingMode,
                                             String numberPattern, int expectedVersion) {
        Map<String, Object> body = new HashMap<>(workflowBody(subjectKey, subjectName, steps, expectedVersion));
        body.put("numberingMode", numberingMode);
        body.put("numberPattern", numberPattern);
        return body;
    }

    private Map<String, Object> step(String name, UUID departmentId) {
        return Map.of("name", name, "departmentIds", List.of(departmentId));
    }

    private Map<String, Object> step(String name, List<UUID> departmentIds) {
        return Map.of("name", name, "departmentIds", departmentIds);
    }

    private Map<String, Object> step(String name, List<UUID> departmentIds, List<UUID> decisionDepartmentIds) {
        return Map.of("name", name, "departmentIds", departmentIds,
                "decisionDepartmentIds", decisionDepartmentIds);
    }

    private UUID createProcess(String subjectKey, UUID initialDepartmentId) {
        return createProcess(organizationId, ADMIN, subjectKey, initialDepartmentId,
                "Processo concorrente " + subjectKey);
    }

    private UUID createProcess(String subjectKey, UUID initialDepartmentId, String title) {
        return createProcess(organizationId, ADMIN, subjectKey, initialDepartmentId, title);
    }

    private UUID createProcess(UUID organization, String actor, String subjectKey,
                               UUID initialDepartmentId, String title) {
        Map<?, ?> process = Objects.requireNonNull(postProcess(organization, actor, Map.of(
                "subjectKey", subjectKey,
                "title", title,
                "initialDepartmentId", initialDepartmentId)).getBody());
        return UUID.fromString(process.get("id").toString());
    }

    private <T> ResponseEntity<T> listProcesses(UUID organization, String actor, Integer page,
                                               Integer size, String query, Class<T> type) {
        StringBuilder path = new StringBuilder("/api/v1/staff/administrative-processes");
        List<String> parameters = new ArrayList<>();
        if (page != null) parameters.add("page=" + page);
        if (size != null) parameters.add("size=" + size);
        if (query != null) parameters.add("q=" + java.net.URLEncoder.encode(query, StandardCharsets.UTF_8));
        if (!parameters.isEmpty()) path.append('?').append(String.join("&", parameters));
        return http.exchange(java.net.URI.create(path.toString()), HttpMethod.GET,
                request(organization, actor, null), type);
    }

    private List<ResponseEntity<Map>> postWorkflowsConcurrently(List<Map<String, Object>> bodies) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(bodies.size());
        try {
            List<Future<ResponseEntity<Map>>> requests = bodies.stream().map(body -> executor.submit(() -> {
                awaitLatch(start);
                return createWorkflow(organizationId, ADMIN, body);
            })).toList();
            start.countDown();
            List<ResponseEntity<Map>> responses = new ArrayList<>();
            for (Future<ResponseEntity<Map>> request : requests) responses.add(request.get(15, TimeUnit.SECONDS));
            return List.copyOf(responses);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private void awaitPostgresLockWait(Future<?> request, String sqlFragment) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (request.isDone()) {
                throw new AssertionError("Expected the request to block on a database lock, but it completed: "
                        + request.get());
            }
            Integer waiting = jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                     WHERE wait_event_type='Lock' AND query ILIKE ?
                    """, Integer.class, "%" + sqlFragment + "%");
            if (waiting != null && waiting > 0) return;
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for the request to block on SQL: " + sqlFragment);
    }

    private void awaitPostgresLockWaiters(int expectedCount, String sqlFragment) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                     WHERE wait_event_type='Lock' AND query ILIKE ?
                    """, Integer.class, "%" + sqlFragment + "%");
            if (waiting != null && waiting >= expectedCount) return;
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for " + expectedCount + " database lock waiters: " + sqlFragment);
    }

    private void assertUnchanged(UUID organization, UUID processId,
                                 ProcessMutationSnapshot expected, long expectedEvents) {
        assertThat(processMutationSnapshot(organization, processId)).isEqualTo(expected);
        assertThat(countEvents(organization, processId)).isEqualTo(expectedEvents);
    }

    private void assertNoProcessOrEvent(UUID organization, String subjectKey) {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM administrative_processes
                 WHERE organization_id=? AND workflow_subject_key=?
                """, Integer.class, organization, subjectKey)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM administrative_process_events e
                  JOIN administrative_processes p
                    ON p.organization_id=e.organization_id AND p.id=e.process_id
                 WHERE p.organization_id=? AND p.workflow_subject_key=?
                """, Integer.class, organization, subjectKey)).isZero();
    }

    private ProcessMutationSnapshot processMutationSnapshot(UUID organization, UUID processId) {
        return jdbc.queryForObject("""
                SELECT current_step_order, current_department_id, status, decision_outcome,
                       decision_actor, decision_at, updated_at
                  FROM administrative_processes WHERE organization_id=? AND id=?
                """, (rs, row) -> new ProcessMutationSnapshot(rs.getInt("current_step_order"),
                rs.getObject("current_department_id", UUID.class), rs.getString("status"),
                rs.getString("decision_outcome"), rs.getString("decision_actor"),
                rs.getObject("decision_at", java.time.OffsetDateTime.class),
                rs.getObject("updated_at", java.time.OffsetDateTime.class)), organization, processId);
    }

    private long countEvents(UUID organization, UUID processId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM administrative_process_events
                 WHERE organization_id=? AND process_id=?
                """, Long.class, organization, processId);
    }

    private int countProcessDeadlines(UUID organization, UUID processId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM administrative_process_deadlines
                 WHERE organization_id=? AND process_id=?
                """, Integer.class, organization, processId);
    }

    private void assertWorkflowDepartmentName(Map<?, ?> workflow, int stepOrder, String expectedName) {
        List<?> steps = (List<?>) workflow.get("steps");
        Map<?, ?> step = (Map<?, ?>) steps.get(stepOrder - 1);
        List<?> departments = (List<?>) step.get("departments");
        assertThat(((Map<?, ?>) departments.getFirst()).get("name")).isEqualTo(expectedName);
    }

    private void assertProcessDepartmentName(Map<?, ?> process, int stepOrder, String expectedName) {
        List<?> steps = (List<?>) process.get("steps");
        Map<?, ?> step = (Map<?, ?>) steps.get(stepOrder - 1);
        List<?> departments = (List<?>) step.get("departments");
        assertThat(((Map<?, ?>) departments.getFirst()).get("name")).isEqualTo(expectedName);
    }

    private void assertEventTimestampsStrictlyIncrease(UUID organization, UUID processId) {
        List<OffsetDateTime> timestamps = jdbc.query("""
                SELECT occurred_at FROM administrative_process_events
                 WHERE organization_id=? AND process_id=? ORDER BY id
                """, (rs, row) -> rs.getObject("occurred_at", OffsetDateTime.class), organization, processId);
        assertThat(timestamps).hasSizeGreaterThan(1);
        for (int index = 1; index < timestamps.size(); index++) {
            assertThat(timestamps.get(index)).isAfter(timestamps.get(index - 1));
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for test latch.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for test latch.", exception);
        }
    }

    private void insertOrganization(UUID id, String name) {
        jdbc.update("INSERT INTO organizations(id,slug,name) VALUES(?,?,?)",
                id, "workflow-" + id, name);
    }

    private void insertDepartment(UUID organization, UUID department, String name) {
        jdbc.update("INSERT INTO departments(id,organization_id,name) VALUES(?,?,?)",
                department, organization, name);
    }

    private void addOrganizationAdmin(UUID organization, String username) {
        jdbc.update("INSERT INTO organization_memberships(username,organization_id,role_code,active) VALUES(?,?,'ADMIN',true)",
                username, organization);
    }

    private void addDepartmentMember(UUID organization, String username, UUID department) {
        jdbc.update("INSERT INTO staff_memberships(username,organization_id,department_id,role_code,active) VALUES(?,?,?,'STAFF',true)",
                username, organization, department);
    }

    private record ProcessMutationSnapshot(int currentStepOrder, UUID currentDepartmentId,
                                           String status, String decisionOutcome, String decisionActor,
                                           java.time.OffsetDateTime decisionAt,
                                           java.time.OffsetDateTime updatedAt) {}
}
