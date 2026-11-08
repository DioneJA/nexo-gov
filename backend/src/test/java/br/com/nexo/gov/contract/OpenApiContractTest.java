package br.com.nexo.gov.contract;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiContractTest {
    private static final String CONTRACT = "contracts/openapi/nexogov-protocol-v1.yaml";

    @Test
    @SuppressWarnings("unchecked")
    void contractParsesAndDescribesOptionalProtocolDeadlineDays() throws Exception {
        Path contractPath = repositoryRoot().resolve(CONTRACT);
        Map<String, Object> document;
        try (InputStream source = Files.newInputStream(contractPath)) {
            document = new Yaml().load(source);
        }

        assertThat(document.get("openapi")).isEqualTo("3.0.3");
        Map<String, Object> components = (Map<String, Object>) document.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> subject = (Map<String, Object>) schemas.get("OrganizationProtocolSubject");
        assertThat((List<String>) subject.get("required"))
                .containsExactly("id", "name", "onlineAvailable")
                .doesNotContain("automaticDeadlineDays");

        Map<String, Object> properties = (Map<String, Object>) subject.get("properties");
        Map<String, Object> deadline = (Map<String, Object>) properties.get("automaticDeadlineDays");
        assertThat(deadline.get("type")).isEqualTo("integer");
        assertThat(deadline.get("minimum")).isEqualTo(1);
        assertThat(deadline.get("nullable")).isEqualTo(true);
        assertThat(deadline.get("description").toString()).contains("omitted from responses when unconfigured");

        Map<String, Object> paths = (Map<String, Object>) document.get("paths");
        Map<String, Object> subjectPath = (Map<String, Object>) paths.get("/staff/organization/protocol-subjects/{id}");
        Map<String, Object> update = (Map<String, Object>) subjectPath.get("put");
        Map<String, Object> updateResponses = (Map<String, Object>) update.get("responses");
        Map<String, Object> success = (Map<String, Object>) updateResponses.get("200");
        assertThat(success.get("description").toString())
                .contains("omitting automaticDeadlineDays preserves its current value")
                .contains("sending null clears it");
    }

    @Test
    @SuppressWarnings("unchecked")
    void organizationContactsContractDefinesTenantAdminScopeAndCsvLimits() throws Exception {
        Path contractPath = repositoryRoot().resolve(CONTRACT);
        Map<String, Object> document;
        try (InputStream source = Files.newInputStream(contractPath)) {
            document = new Yaml().load(source);
        }
        Map<String, Object> paths = (Map<String, Object>) document.get("paths");
        Map<String, Object> contacts = (Map<String, Object>) paths.get("/staff/organization/contacts");
        assertThat(contacts).containsKeys("get", "post");
        assertThat(contacts.get("get").toString()).contains("organization administrators only", "10000", "100");
        Map<String, Object> importOperation = (Map<String, Object>) ((Map<String, Object>) paths
                .get("/staff/organization/contacts/import")).get("post");
        Map<String, Object> requestBody = (Map<String, Object>) importOperation.get("requestBody");
        assertThat(requestBody.get("description").toString()).contains("1,000 rows", "1 MiB", "all-or-nothing");
        Map<String, Object> content = (Map<String, Object>) requestBody.get("content");
        assertThat(content).containsKey("text/csv");
        Map<String, Object> responses = (Map<String, Object>) importOperation.get("responses");
        assertThat(responses).containsKeys("400", "403", "404", "413");

        Map<String, Object> components = (Map<String, Object>) document.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> type = (Map<String, Object>) schemas.get("OrganizationContactType");
        assertThat(type.get("enum")).isEqualTo(List.of("PERSON", "MUNICIPAL_DEPARTMENT", "LEGAL_ENTITY"));
        assertThat(schemas).containsKeys("OrganizationContact", "OrganizationContactPage", "OrganizationContactImportResult");
    }

    @Test
    @SuppressWarnings("unchecked")
    void organizationBrandingContractDefinesTokenAllowlistAndNoStoreTenantRoutes() throws Exception {
        Path contractPath = repositoryRoot().resolve(CONTRACT);
        Map<String, Object> document;
        try (InputStream source = Files.newInputStream(contractPath)) {
            document = new Yaml().load(source);
        }
        Map<String, Object> paths = (Map<String, Object>) document.get("paths");
        Map<String, Object> publicBranding = (Map<String, Object>) paths
                .get("/public/organizations/{organizationSlug}/branding");
        Map<String, Object> publicGet = (Map<String, Object>) publicBranding.get("get");
        assertThat(publicGet.get("operationId")).isEqualTo("getPublicOrganizationBranding");
        assertThat(publicGet.toString()).contains("no-store", "excludes internal identifiers");

        Map<String, Object> staffBranding = (Map<String, Object>) paths.get("/staff/organization/branding");
        assertThat(staffBranding).containsKeys("get", "put");
        assertThat(staffBranding.toString()).contains("OrganizationId", "no-store", "active organization");
        Map<String, Object> staffGetResponses = (Map<String, Object>) ((Map<String, Object>) staffBranding.get("get")).get("responses");
        Map<String, Object> staffPutResponses = (Map<String, Object>) ((Map<String, Object>) staffBranding.get("put")).get("responses");
        assertThat(staffGetResponses.get("403").toString()).contains("inactive organization");
        assertThat(staffPutResponses.get("403").toString()).contains("inactive organization");
        assertThat(staffGetResponses.get("404").toString()).contains("after tenant membership validation");
        assertThat(staffPutResponses.get("404").toString()).contains("after tenant membership validation");
        Map<String, Object> organizationHeader = (Map<String, Object>) ((Map<String, Object>) document.get("components"))
                .get("parameters");
        assertThat(organizationHeader.get("OrganizationId").toString()).contains("X-Organization-Id");

        Map<String, Object> components = (Map<String, Object>) document.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> tokens = (Map<String, Object>) schemas.get("OrganizationAccentToken");
        assertThat(tokens.get("enum")).isEqualTo(List.of("NEXO_NAVY", "NEXO_BLUE", "ACTION_BLUE", "SUCCESS_GREEN"));
        assertThat(schemas).containsKeys("OrganizationBrandingInput", "OrganizationBranding", "PublicOrganizationBranding");
        Map<String, Object> input = (Map<String, Object>) schemas.get("OrganizationBrandingInput");
        assertThat((List<String>) input.get("required")).containsExactly("accentToken");
    }

    @Test
    @SuppressWarnings("unchecked")
    void administrativeProcessListContractDocumentsBoundsAndCompactProjection() throws Exception {
        Path contractPath = repositoryRoot().resolve(CONTRACT);
        Map<String, Object> document;
        try (InputStream source = Files.newInputStream(contractPath)) {
            document = new Yaml().load(source);
        }

        Map<String, Object> paths = (Map<String, Object>) document.get("paths");
        Map<String, Object> listPath = (Map<String, Object>) paths.get("/staff/administrative-processes");
        Map<String, Object> get = (Map<String, Object>) listPath.get("get");
        assertThat(get.get("operationId")).isEqualTo("listAdministrativeProcesses");
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) get.get("parameters");
        Map<String, Map<String, Object>> byName = parameters.stream()
                .filter(parameter -> parameter.containsKey("name"))
                .collect(java.util.stream.Collectors.toMap(parameter -> parameter.get("name").toString(),
                        parameter -> parameter));
        assertParameterBounds(byName.get("page"), 0, 10000, 0);
        assertParameterBounds(byName.get("size"), 1, 100, 25);
        Map<String, Object> query = byName.get("q");
        Map<String, Object> querySchema = (Map<String, Object>) query.get("schema");
        assertThat(querySchema.get("maxLength")).isEqualTo(120);

        Map<String, Object> components = (Map<String, Object>) document.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> pageSchema = (Map<String, Object>) schemas.get("AdministrativeProcessPage");
        Map<String, Object> properties = (Map<String, Object>) pageSchema.get("properties");
        Map<String, Object> items = (Map<String, Object>) properties.get("items");
        Map<String, Object> itemSchema = (Map<String, Object>) items.get("items");
        assertThat(itemSchema.get("$ref")).isEqualTo("#/components/schemas/AdministrativeProcessSummary");
        Map<String, Object> summary = (Map<String, Object>) schemas.get("AdministrativeProcessSummary");
        assertThat((List<String>) summary.get("required")).doesNotContain("events", "steps");
        assertThat(get.toString()).contains("current department", "active membership");

        Map<String, Object> detailPath = (Map<String, Object>) paths.get("/staff/administrative-processes/{processId}");
        Map<String, Object> detailGet = (Map<String, Object>) detailPath.get("get");
        Map<String, Object> detailResponses = (Map<String, Object>) detailGet.get("responses");
        assertThat(((Map<String, Object>) detailResponses.get("403")).get("description").toString())
                .contains("active organization administrator", "current active department");
        assertThat(((Map<String, Object>) detailResponses.get("404")).get("description").toString())
                .contains("another organization", "same indistinguishable not-found response");
        assertThat(schemas).containsKey("AdministrativeProcessView");
    }

    @Test
    @SuppressWarnings("unchecked")
    void administrativeProcessNumberingContractDefinesTenantScopedPoliciesAndSnapshots() throws Exception {
        Path contractPath = repositoryRoot().resolve(CONTRACT);
        Map<String, Object> document;
        try (InputStream source = Files.newInputStream(contractPath)) {
            document = new Yaml().load(source);
        }

        Map<String, Object> paths = (Map<String, Object>) document.get("paths");
        Map<String, Object> workflowPost = (Map<String, Object>) ((Map<String, Object>) paths
                .get("/staff/administrative-workflows")).get("post");
        assertThat(workflowPost.toString()).contains("process numbering policy", "invalid numbering pattern");

        Map<String, Object> components = (Map<String, Object>) document.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> createRequest = (Map<String, Object>) schemas
                .get("AdministrativeWorkflowCreateRequest");
        Map<String, Object> workflowProperties = (Map<String, Object>) createRequest.get("properties");
        Map<String, Object> mode = (Map<String, Object>) workflowProperties.get("numberingMode");
        Map<String, Object> pattern = (Map<String, Object>) workflowProperties.get("numberPattern");
        assertThat(mode.get("enum")).isEqualTo(List.of("BY_SUBJECT", "GENERAL_SEQUENCE"));
        assertThat(mode.get("description").toString())
                .contains("one sequence across all subjects", "later versions inherit the current mode");
        assertThat(pattern.get("description").toString())
                .contains("{numero}", "{ano}", "{assunto}", "Unknown or incomplete placeholders are rejected",
                        "first-version default is {numero}", "inherits the current pattern", "NEXOGOV_PROCESS_NUMBERING_ZONE_ID");

        Map<String, Object> workflowView = (Map<String, Object>) schemas.get("AdministrativeWorkflowView");
        assertThat((List<String>) workflowView.get("required")).contains("numberingMode", "numberPattern");
        Map<String, Object> summary = (Map<String, Object>) schemas.get("AdministrativeProcessSummary");
        assertThat((List<String>) summary.get("required")).contains("processNumber");
        Map<String, Object> summaryProperties = (Map<String, Object>) summary.get("properties");
        Map<String, Object> processNumber = (Map<String, Object>) summaryProperties.get("processNumber");
        assertThat(processNumber.get("nullable")).isEqualTo(true);
        assertThat(processNumber.get("description").toString()).contains("legacy processes", "server-generated number");
        Map<String, Object> listPath = (Map<String, Object>) paths.get("/staff/administrative-processes");
        Map<String, Object> listOperation = (Map<String, Object>) listPath.get("get");
        List<Map<String, Object>> listParameters = (List<Map<String, Object>>) listOperation.get("parameters");
        assertThat(listParameters.stream().filter(parameter -> "q".equals(parameter.get("name")))
                .findFirst().orElseThrow().get("description").toString()).contains("process number");
    }

    @Test
    @SuppressWarnings("unchecked")
    void administrativeProcessDecisionContractDefinesPolicyAndOutcomes() throws Exception {
        Path contractPath = repositoryRoot().resolve(CONTRACT);
        Map<String, Object> document;
        try (InputStream source = Files.newInputStream(contractPath)) {
            document = new Yaml().load(source);
        }

        Map<String, Object> paths = (Map<String, Object>) document.get("paths");
        Map<String, Object> decisionPath = (Map<String, Object>) paths
                .get("/staff/administrative-processes/{processId}/decision");
        Map<String, Object> post = (Map<String, Object>) decisionPath.get("post");
        assertThat(post.get("operationId")).isEqualTo("decideAdministrativeProcess");
        Map<String, Object> requestBody = (Map<String, Object>) post.get("requestBody");
        Map<String, Object> requestContent = (Map<String, Object>) requestBody.get("content");
        Map<String, Object> json = (Map<String, Object>) requestContent.get("application/json");
        Map<String, Object> requestSchema = (Map<String, Object>) json.get("schema");
        assertThat(requestSchema.get("$ref")).isEqualTo("#/components/schemas/AdministrativeProcessDecisionRequest");

        Map<String, Object> components = (Map<String, Object>) document.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> decisionRequest = (Map<String, Object>) schemas.get("AdministrativeProcessDecisionRequest");
        Map<String, Object> decisionProperties = (Map<String, Object>) decisionRequest.get("properties");
        Map<String, Object> outcome = (Map<String, Object>) decisionProperties.get("outcome");
        assertThat(outcome.get("enum")).isEqualTo(List.of("DEFERIDO", "INDEFERIDO"));
        Map<String, Object> department = (Map<String, Object>) schemas.get("WorkflowDepartment");
        Map<String, Object> departmentProperties = (Map<String, Object>) department.get("properties");
        assertThat(departmentProperties).containsKey("canDecide");

        Map<String, Object> event = (Map<String, Object>) schemas.get("AdministrativeProcessEvent");
        Map<String, Object> eventProperties = (Map<String, Object>) event.get("properties");
        assertThat(eventProperties.get("action").toString()).contains("DECIDED");
        assertThat(eventProperties).containsKey("decisionOutcome");
        assertThat(post.get("responses").toString()).contains("current department", "idempotent", "does not close");
    }

    @Test
    @SuppressWarnings("unchecked")
    void administrativeWorkflowContractDescribesDecisionPolicyConfigurationAndSnapshots() throws Exception {
        Path contractPath = repositoryRoot().resolve(CONTRACT);
        Map<String, Object> document;
        try (InputStream source = Files.newInputStream(contractPath)) {
            document = new Yaml().load(source);
        }

        Map<String, Object> paths = (Map<String, Object>) document.get("paths");
        Map<String, Object> workflowPath = (Map<String, Object>) paths.get("/staff/administrative-workflows");
        Map<String, Object> publish = (Map<String, Object>) workflowPath.get("post");
        assertThat(publish.get("operationId")).isEqualTo("publishAdministrativeWorkflow");
        assertThat(publish.get("responses").toString()).contains("active organization administrator", "Idempotent");

        Map<String, Object> publishBody = (Map<String, Object>) publish.get("requestBody");
        Map<String, Object> publishContent = (Map<String, Object>) publishBody.get("content");
        Map<String, Object> publishJson = (Map<String, Object>) publishContent.get("application/json");
        Map<String, Object> publishSchema = (Map<String, Object>) publishJson.get("schema");
        assertThat(publishSchema.get("$ref")).isEqualTo("#/components/schemas/AdministrativeWorkflowCreateRequest");

        Map<String, Object> components = (Map<String, Object>) document.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        Map<String, Object> workflowStep = (Map<String, Object>) schemas.get("AdministrativeWorkflowStepRequest");
        assertThat((List<String>) workflowStep.get("required")).containsExactly("name", "departmentIds");
        Map<String, Object> workflowStepProperties = (Map<String, Object>) workflowStep.get("properties");
        Map<String, Object> decisionDepartments = (Map<String, Object>) workflowStepProperties.get("decisionDepartmentIds");
        assertThat(decisionDepartments.get("nullable")).isEqualTo(true);
        assertThat(decisionDepartments.get("description").toString())
                .contains("Omitted or null means no department", "snapshotted");
        Map<String, Object> decisionDepartmentItems = (Map<String, Object>) decisionDepartments.get("items");
        assertThat(decisionDepartmentItems.get("format")).isEqualTo("uuid");

        Map<String, Object> currentWorkflowPath = (Map<String, Object>) paths
                .get("/staff/administrative-workflows/{subjectKey}");
        assertThat(((Map<String, Object>) currentWorkflowPath.get("get")).get("operationId"))
                .isEqualTo("getCurrentAdministrativeWorkflow");
        Map<String, Object> createProcessPath = (Map<String, Object>) paths.get("/staff/administrative-processes");
        assertThat(((Map<String, Object>) createProcessPath.get("post")).get("operationId"))
                .isEqualTo("openAdministrativeProcess");
        Map<String, Object> advancePath = (Map<String, Object>) paths
                .get("/staff/administrative-processes/{processId}/advance");
        assertThat(((Map<String, Object>) advancePath.get("post")).get("operationId"))
                .isEqualTo("advanceAdministrativeProcess");
    }

    @SuppressWarnings("unchecked")
    private void assertParameterBounds(Map<String, Object> parameter, int minimum, int maximum, int defaultValue) {
        Map<String, Object> schema = (Map<String, Object>) parameter.get("schema");
        assertThat(schema.get("minimum")).isEqualTo(minimum);
        assertThat(schema.get("maximum")).isEqualTo(maximum);
        assertThat(schema.get("default")).isEqualTo(defaultValue);
    }

    private static Path repositoryRoot() {
        for (Path current = Path.of("").toAbsolutePath(); current != null; current = current.getParent()) {
            if (Files.isRegularFile(current.resolve(CONTRACT))) return current;
        }
        throw new IllegalStateException("Could not locate " + CONTRACT + " from the test working directory.");
    }
}
