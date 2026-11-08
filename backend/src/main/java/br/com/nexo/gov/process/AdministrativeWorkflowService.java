package br.com.nexo.gov.process;

import br.com.nexo.core.tenant.TenantContext;
import br.com.nexo.gov.process.AdministrativeWorkflowDtos.ProcessAdvanceRequest;
import br.com.nexo.gov.process.AdministrativeWorkflowDtos.ProcessCreateRequest;
import br.com.nexo.gov.process.AdministrativeWorkflowDtos.ProcessDecision;
import br.com.nexo.gov.process.AdministrativeWorkflowDtos.ProcessDecisionRequest;
import br.com.nexo.gov.process.AdministrativeWorkflowDtos.ProcessDeadlineCreateRequest;
import br.com.nexo.gov.process.AdministrativeWorkflowDtos.ProcessDeadlineView;
import br.com.nexo.gov.process.AdministrativeWorkflowDtos.ProcessEventView;
import br.com.nexo.gov.process.AdministrativeWorkflowDtos.ProcessPageView;
import br.com.nexo.gov.process.AdministrativeWorkflowDtos.ProcessSummaryView;
import br.com.nexo.gov.process.AdministrativeWorkflowDtos.ProcessStepView;
import br.com.nexo.gov.process.AdministrativeWorkflowDtos.ProcessView;
import br.com.nexo.gov.process.AdministrativeWorkflowDtos.WorkflowCreateRequest;
import br.com.nexo.gov.process.AdministrativeWorkflowDtos.WorkflowDepartmentView;
import br.com.nexo.gov.process.AdministrativeWorkflowDtos.WorkflowPublicationResult;
import br.com.nexo.gov.process.AdministrativeWorkflowDtos.WorkflowStepRequest;
import br.com.nexo.gov.process.AdministrativeWorkflowDtos.WorkflowStepView;
import br.com.nexo.gov.process.AdministrativeWorkflowDtos.WorkflowView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AdministrativeWorkflowService {
    static final int DEFAULT_PROCESS_PAGE_SIZE = 25;
    static final int MAX_PROCESS_PAGE_SIZE = 100;
    static final int MAX_PROCESS_PAGE_INDEX = 10_000;
    static final int MAX_PROCESS_SEARCH_LENGTH = 120;
    private final JdbcTemplate jdbc;

    public AdministrativeWorkflowService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public WorkflowPublicationResult createWorkflow(String actor, WorkflowCreateRequest request) {
        UUID organizationId = TenantContext.requireTenantId();
        requireOrganizationAdministrator(actor, organizationId);

        String subjectKey = request.subjectKey().trim();
        String subjectName = request.subjectName().trim();
        validateDecisionDepartments(request.steps());
        String requestHash = workflowRequestHash(request, subjectKey, subjectName);

        jdbc.update("""
                INSERT INTO administrative_workflow_publication_keys(
                    organization_id, idempotency_key, subject_key, request_hash)
                VALUES(?,?,?,?) ON CONFLICT (organization_id, idempotency_key) DO NOTHING
                """, organizationId, request.idempotencyKey(), subjectKey, requestHash);
        PublicationKey publicationKey = jdbc.query("""
                SELECT subject_key, request_hash, workflow_id, workflow_version
                  FROM administrative_workflow_publication_keys
                 WHERE organization_id=? AND idempotency_key=? FOR UPDATE
                """, (rs, row) -> new PublicationKey(rs.getString("subject_key"),
                        rs.getString("request_hash"), rs.getObject("workflow_id", UUID.class),
                        (Integer) rs.getObject("workflow_version")),
                organizationId, request.idempotencyKey()).stream().findFirst().orElseThrow(WorkflowNotFound::new);
        if (!publicationKey.subjectKey().equals(subjectKey)
                || !publicationKey.requestHash().equals(requestHash)) {
            throw new WorkflowConflict("A chave de idempotência já foi usada com outro conteúdo.");
        }
        if (publicationKey.workflowId() != null) {
            return new WorkflowPublicationResult(
                    workflowView(organizationId, publicationKey.workflowId(), publicationKey.workflowVersion()), true);
        }

        jdbc.update("""
                INSERT INTO administrative_workflow_heads(organization_id, subject_key, subject_name)
                VALUES(?,?,?) ON CONFLICT (organization_id, subject_key) DO NOTHING
                """, organizationId, subjectKey, subjectName);
        jdbc.query("""
                SELECT subject_key FROM administrative_workflow_heads
                 WHERE organization_id=? AND subject_key=? FOR UPDATE
                """, (rs, row) -> rs.getString(1), organizationId, subjectKey)
                .stream().findFirst().orElseThrow(WorkflowNotFound::new);

        Integer currentVersionValue = jdbc.queryForObject("""
                SELECT current_version FROM administrative_workflow_heads
                 WHERE organization_id=? AND subject_key=?
                """, Integer.class, organizationId, subjectKey);
        int currentVersion = currentVersionValue == null ? 0 : currentVersionValue;
        if (currentVersion != request.expectedVersion()) {
            throw new WorkflowConflict("A versão esperada não é mais a atual; recarregue o fluxo antes de publicar.");
        }
        Map<UUID, String> departmentNames = lockActiveWorkflowDepartments(organizationId, request.steps());

        int version = currentVersion + 1;
        UUID workflowId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO administrative_workflows(
                    id, organization_id, subject_key, subject_name, version, created_by)
                VALUES(?,?,?,?,?,?)
                """, workflowId, organizationId, subjectKey, subjectName, version, actor);

        for (int index = 0; index < request.steps().size(); index++) {
            WorkflowStepRequest step = request.steps().get(index);
            int stepOrder = index + 1;
            int insertedStep = jdbc.update("""
                    INSERT INTO administrative_workflow_steps(
                        organization_id, workflow_id, workflow_version, step_order, step_name)
                    VALUES(?,?,?,?,?)
                    """, organizationId, workflowId, version, stepOrder, step.name().trim());
            if (insertedStep != 1) {
                throw new WorkflowConflict("Uma etapa do fluxo não foi persistida integralmente.");
            }
            for (UUID departmentId : step.departmentIds()) {
                int linked = jdbc.update("""
                    INSERT INTO administrative_workflow_step_departments(
                        organization_id, workflow_id, workflow_version, step_order,
                        department_id, department_name, can_decide)
                    VALUES(?,?,?,?,?,?,?)
                    """, organizationId, workflowId, version, stepOrder, departmentId,
                        departmentNames.get(departmentId), decisionDepartmentIds(step).contains(departmentId));
                if (linked != 1) throw new WorkflowConflict("O vínculo de setor da etapa não foi persistido.");
            }
        }

        jdbc.update("""
                UPDATE administrative_workflow_heads
                   SET subject_name=?, current_workflow_id=?, current_version=?, updated_at=now()
                 WHERE organization_id=? AND subject_key=?
                """, subjectName, workflowId, version, organizationId, subjectKey);

        int recorded = jdbc.update("""
                UPDATE administrative_workflow_publication_keys
                   SET workflow_id=?, workflow_version=?
                 WHERE organization_id=? AND idempotency_key=? AND workflow_id IS NULL
                """, workflowId, version, organizationId, request.idempotencyKey());
        if (recorded != 1) throw new WorkflowConflict("A publicação associada à chave não pôde ser registrada.");
        return new WorkflowPublicationResult(workflowView(organizationId, workflowId, version), false);
    }

    private String workflowRequestHash(WorkflowCreateRequest request, String subjectKey, String subjectName) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateHashString(digest, subjectKey);
            updateHashString(digest, subjectName);
            updateHashInt(digest, request.expectedVersion());
            updateHashInt(digest, request.steps().size());
            for (WorkflowStepRequest step : request.steps()) {
                updateHashString(digest, step.name().trim());
                List<UUID> departments = step.departmentIds().stream().sorted().toList();
                updateHashInt(digest, departments.size());
                for (UUID departmentId : departments) updateHashString(digest, departmentId.toString());
                List<UUID> decisionDepartments = decisionDepartmentIds(step).stream().sorted().toList();
                updateHashInt(digest, decisionDepartments.size());
                for (UUID departmentId : decisionDepartments) updateHashString(digest, departmentId.toString());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform.", exception);
        }
    }

    private void updateHashString(MessageDigest digest, String value) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        updateHashInt(digest, encoded.length);
        digest.update(encoded);
    }

    private void updateHashInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }

    @Transactional(readOnly = true)
    public WorkflowView currentWorkflow(String actor, String subjectKey) {
        UUID organizationId = TenantContext.requireTenantId();
        requireOrganizationAdministrator(actor, organizationId);
        WorkflowPointer reference = jdbc.query("""
                SELECT current_workflow_id, current_version
                  FROM administrative_workflow_heads
                 WHERE organization_id=? AND subject_key=? AND current_workflow_id IS NOT NULL
                """, (rs, row) -> new WorkflowPointer(
                        rs.getObject("current_workflow_id", UUID.class), rs.getInt("current_version")),
                organizationId, subjectKey).stream().findFirst().orElseThrow(WorkflowNotFound::new);
        return workflowView(organizationId, reference.id(), reference.version());
    }

    @Transactional
    public ProcessView createProcess(String actor, ProcessCreateRequest request) {
        UUID organizationId = TenantContext.requireTenantId();
        WorkflowSelection reference = jdbc.query("""
                SELECT h.current_workflow_id, h.current_version, w.subject_name
                  FROM administrative_workflow_heads h
                  JOIN administrative_workflows w
                    ON w.organization_id=h.organization_id
                   AND w.subject_key=h.subject_key
                   AND w.id=h.current_workflow_id
                   AND w.version=h.current_version
                 WHERE h.organization_id=? AND h.subject_key=?
                 FOR SHARE OF h
                """, (rs, row) -> new WorkflowSelection(rs.getObject("current_workflow_id", UUID.class),
                        rs.getString("subject_name"), rs.getInt("current_version")),
                organizationId, request.subjectKey()).stream().findFirst().orElseThrow(WorkflowNotFound::new);

        List<WorkflowStepView> definitionSteps = workflowSteps(organizationId, reference.id(), reference.version());
        if (definitionSteps.isEmpty()) throw new WorkflowNotFound();
        WorkflowStepView firstStep = definitionSteps.getFirst();
        UUID initialDepartmentId = request.initialDepartmentId();
        if (firstStep.departments().stream().noneMatch(department -> department.id().equals(initialDepartmentId)))
            throw new WorkflowConflict("O setor inicial precisa pertencer à primeira etapa do fluxo ativo.");
        requireDepartmentMembership(organizationId, initialDepartmentId, actor);

        UUID processId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO administrative_processes(
                    id, organization_id, workflow_subject_key, workflow_id, workflow_version,
                    title, current_step_order, current_department_id, created_by)
                VALUES(?,?,?,?,?,?,1,?,?)
                """, processId, organizationId, request.subjectKey(), reference.id(), reference.version(),
                request.title().trim(), initialDepartmentId, actor);
        jdbc.update("""
                INSERT INTO administrative_process_workflow_steps(
                    organization_id, process_id, step_order, step_name)
                SELECT organization_id, ?, step_order, step_name
                  FROM administrative_workflow_steps
                 WHERE organization_id=? AND workflow_id=? AND workflow_version=?
                """, processId, organizationId, reference.id(), reference.version());
        jdbc.update("""
                INSERT INTO administrative_process_step_departments(
                    organization_id, process_id, step_order, department_id, department_name, can_decide)
                SELECT organization_id, ?, step_order, department_id, department_name, can_decide
                  FROM administrative_workflow_step_departments
                 WHERE organization_id=? AND workflow_id=? AND workflow_version=?
                """, processId, organizationId, reference.id(), reference.version());
        jdbc.update("""
                INSERT INTO administrative_process_events(
                    organization_id, process_id, actor, action, to_step_order, to_department_id)
                VALUES(?,?,?,'OPENED',1,?)
                """, organizationId, processId, actor, initialDepartmentId);
        return processView(organizationId, processId);
    }

    @Transactional
    public ProcessView getProcess(String actor, UUID processId) {
        UUID organizationId = TenantContext.requireTenantId();
        UUID currentDepartmentId = jdbc.query("""
                SELECT current_department_id
                  FROM administrative_processes
                 WHERE organization_id=? AND id=?
                 FOR SHARE
                """, (rs, row) -> rs.getObject("current_department_id", UUID.class), organizationId, processId)
                .stream().findFirst().orElseThrow(WorkflowNotFound::new);
        if (!isOrganizationAdministrator(actor, organizationId)
                && !lockCurrentDepartmentMembershipForRead(actor, organizationId, currentDepartmentId)) {
            throw new WorkflowForbidden();
        }
        return processView(organizationId, processId);
    }

    @Transactional
    public ProcessView addProcessDeadline(String actor, UUID processId, ProcessDeadlineCreateRequest request) {
        UUID organizationId = TenantContext.requireTenantId();
        UUID currentDepartmentId = jdbc.query("""
                SELECT current_department_id
                  FROM administrative_processes
                 WHERE organization_id=? AND id=?
                 FOR UPDATE
                """, (rs, row) -> rs.getObject("current_department_id", UUID.class), organizationId, processId)
                .stream().findFirst().orElseThrow(WorkflowNotFound::new);
        if (!lockOrganizationAdministrator(actor, organizationId)
                && !lockCurrentDepartmentMembership(actor, organizationId, currentDepartmentId)) {
            throw new WorkflowForbidden();
        }

        jdbc.update("""
                INSERT INTO administrative_process_deadlines(
                    id, organization_id, process_id, title, due_at, created_by)
                VALUES(?,?,?,?,?,?)
                """, UUID.randomUUID(), organizationId, processId, request.title().trim(),
                request.dueAt(), actor);
        return processView(organizationId, processId);
    }

    @Transactional(readOnly = true)
    public ProcessPageView listProcesses(String actor, Integer requestedPage, Integer requestedSize, String requestedQuery) {
        UUID organizationId = TenantContext.requireTenantId();
        int page = requestedPage == null ? 0 : requestedPage;
        int size = requestedSize == null ? DEFAULT_PROCESS_PAGE_SIZE : requestedSize;
        String query = requestedQuery == null ? "" : requestedQuery.trim();
        if (page < 0 || page > MAX_PROCESS_PAGE_INDEX) {
            throw new InvalidProcessListQuery("A página deve estar entre 0 e 10000.");
        }
        if (size < 1 || size > MAX_PROCESS_PAGE_SIZE) {
            throw new InvalidProcessListQuery("O tamanho deve estar entre 1 e 100.");
        }
        if (query.length() > MAX_PROCESS_SEARCH_LENGTH) {
            throw new InvalidProcessListQuery("A busca aceita até 120 caracteres.");
        }

        long offset = (long) page * size;
        List<ProcessSummaryView> rows = jdbc.query("""
                SELECT p.id, p.workflow_subject_key, w.subject_name, p.workflow_id, p.workflow_version,
                       p.title, p.current_step_order, current_step.step_name AS current_step_name,
                       p.current_department_id, current_department.department_name AS current_department_name,
                       p.status, p.decision_outcome, p.created_at
                  FROM administrative_processes p
                  JOIN administrative_workflows w
                    ON w.organization_id=p.organization_id AND w.subject_key=p.workflow_subject_key
                   AND w.id=p.workflow_id AND w.version=p.workflow_version
                  JOIN administrative_process_workflow_steps current_step
                    ON current_step.organization_id=p.organization_id AND current_step.process_id=p.id
                   AND current_step.step_order=p.current_step_order
                  JOIN administrative_process_step_departments current_department
                    ON current_department.organization_id=p.organization_id
                   AND current_department.process_id=p.id
                   AND current_department.step_order=p.current_step_order
                   AND current_department.department_id=p.current_department_id
                  WHERE p.organization_id=?
                    AND (
                        EXISTS (
                            SELECT 1 FROM organization_memberships om
                             WHERE om.organization_id=p.organization_id AND om.username=?
                               AND om.role_code='ADMIN' AND om.active=true
                        )
                        OR EXISTS (
                            SELECT 1
                              FROM staff_memberships sm
                              JOIN departments d
                                ON d.organization_id=sm.organization_id AND d.id=sm.department_id
                             WHERE sm.organization_id=p.organization_id AND sm.username=?
                               AND sm.department_id=p.current_department_id
                               AND sm.active=true AND d.active=true
                        )
                    )
                    AND (?='' OR strpos(lower(p.title), lower(?)) > 0
                         OR strpos(lower(w.subject_name), lower(?)) > 0
                         OR strpos(lower(p.workflow_subject_key), lower(?)) > 0)
                  ORDER BY p.created_at DESC, p.id DESC
                 LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new ProcessSummaryView(
                rs.getObject("id", UUID.class), rs.getString("workflow_subject_key"),
                rs.getString("subject_name"), rs.getObject("workflow_id", UUID.class),
                rs.getInt("workflow_version"), rs.getString("title"), rs.getInt("current_step_order"),
                rs.getString("current_step_name"), rs.getObject("current_department_id", UUID.class),
                rs.getString("current_department_name"), rs.getString("status"), rs.getString("decision_outcome"),
                rs.getObject("created_at", OffsetDateTime.class)),
                organizationId, actor, actor, query, query, query, query, size + 1, offset);
        boolean hasNext = rows.size() > size;
        List<ProcessSummaryView> items = hasNext ? List.copyOf(rows.subList(0, size)) : List.copyOf(rows);
        return new ProcessPageView(items, page, size, hasNext);
    }

    @Transactional
    public ProcessView advance(String actor, UUID processId, ProcessAdvanceRequest request) {
        UUID organizationId = TenantContext.requireTenantId();
        ProcessState process = jdbc.query("""
                SELECT current_step_order, current_department_id, status
                  FROM administrative_processes
                 WHERE organization_id=? AND id=? FOR UPDATE
                """, (rs, row) -> new ProcessState(rs.getInt("current_step_order"),
                        rs.getObject("current_department_id", UUID.class), rs.getString("status")),
                organizationId, processId).stream().findFirst().orElseThrow(WorkflowNotFound::new);

        if (!"ACTIVE".equals(process.status()))
            throw new WorkflowConflict("O processo não está ativo.");
        int expectedStep = process.currentStepOrder() + 1;
        if (!lockCurrentDepartmentMembership(actor, organizationId, process.currentDepartmentId()))
            throw new WorkflowForbidden();
        if (request.toStepOrder() != expectedStep) {
            throw new WorkflowConflict("O processo só pode avançar para a etapa imediatamente seguinte.");
        }
        Integer nextStageExists = jdbc.queryForObject("""
                SELECT count(*) FROM administrative_process_workflow_steps
                 WHERE organization_id=? AND process_id=? AND step_order=?
                """, Integer.class, organizationId, processId, expectedStep);
        if (nextStageExists == null || nextStageExists == 0)
            throw new WorkflowConflict("O processo já está na última etapa do fluxo.");
        List<DepartmentLock> processDepartments = lockProcessStageDepartments(
                organizationId, processId, process.currentStepOrder(), expectedStep,
                request.destinationDepartmentId());
        DepartmentLock destination = processDepartments.stream()
                .filter(department -> department.id().equals(request.destinationDepartmentId()))
                .findFirst().orElse(null);
        if (destination == null || !destination.active() || !destination.inNextStep()) {
            throw new WorkflowConflict("O setor de destino não pertence à próxima etapa ativa deste processo.");
        }
        int changed = jdbc.update("""
                UPDATE administrative_processes
                   SET current_step_order=?, current_department_id=?, updated_at=now()
                 WHERE organization_id=? AND id=? AND current_step_order=?
                """, expectedStep, request.destinationDepartmentId(), organizationId, processId,
                process.currentStepOrder());
        if (changed != 1) throw new WorkflowConflict("O processo foi alterado por outra solicitação.");
        jdbc.update("""
                INSERT INTO administrative_process_events(
                    organization_id, process_id, actor, action, from_step_order, to_step_order,
                    from_department_id, to_department_id)
                VALUES(?,?,?,'ADVANCED',?,?,?,?)
                """, organizationId, processId, actor, process.currentStepOrder(), expectedStep,
                process.currentDepartmentId(), request.destinationDepartmentId());
        return processView(organizationId, processId);
    }

    @Transactional
    public ProcessView decide(String actor, UUID processId, ProcessDecisionRequest request) {
        UUID organizationId = TenantContext.requireTenantId();
        DecisionState process = jdbc.query("""
                SELECT current_step_order, current_department_id, status, decision_outcome
                  FROM administrative_processes
                 WHERE organization_id=? AND id=? FOR UPDATE
                """, (rs, row) -> new DecisionState(rs.getInt("current_step_order"),
                        rs.getObject("current_department_id", UUID.class), rs.getString("status"),
                        rs.getString("decision_outcome")), organizationId, processId)
                .stream().findFirst().orElseThrow(WorkflowNotFound::new);

        if (!"ACTIVE".equals(process.status())) throw new WorkflowConflict("O processo não está ativo.");
        if (!lockCurrentDepartmentMembership(actor, organizationId, process.currentDepartmentId()))
            throw new WorkflowForbidden();
        Integer eligible = jdbc.queryForObject("""
                SELECT count(*) FROM administrative_process_step_departments
                 WHERE organization_id=? AND process_id=? AND step_order=?
                   AND department_id=? AND can_decide=true
                """, Integer.class, organizationId, processId, process.currentStepOrder(),
                process.currentDepartmentId());
        if (eligible == null || eligible == 0) throw new WorkflowForbidden();

        String outcome = request.outcome().name();
        if (outcome.equals(process.decisionOutcome())) return processView(organizationId, processId);
        int changed = jdbc.update("""
                UPDATE administrative_processes
                   SET decision_outcome=?, decision_actor=?, decision_at=clock_timestamp(),
                       updated_at=clock_timestamp()
                 WHERE organization_id=? AND id=? AND current_step_order=? AND current_department_id=?
                   AND status='ACTIVE' AND decision_outcome IS NOT DISTINCT FROM ?
                """, outcome, actor, organizationId, processId, process.currentStepOrder(),
                process.currentDepartmentId(), process.decisionOutcome());
        if (changed != 1) throw new WorkflowConflict("O processo foi alterado por outra solicitação.");
        jdbc.update("""
                INSERT INTO administrative_process_events(
                    organization_id, process_id, actor, action, from_step_order, to_step_order,
                    from_department_id, to_department_id, decision_outcome)
                VALUES(?,?,?,'DECIDED',?,?,?,?,?)
                """, organizationId, processId, actor, process.currentStepOrder(),
                process.currentStepOrder(), process.currentDepartmentId(),
                process.currentDepartmentId(), outcome);
        return processView(organizationId, processId);
    }

    private void validateDecisionDepartments(List<WorkflowStepRequest> steps) {
        for (WorkflowStepRequest step : steps) {
            List<UUID> configured = decisionDepartmentIds(step);
            if (new HashSet<>(configured).size() != configured.size()) {
                throw new WorkflowConflict("Cada setor decisor deve aparecer uma única vez em cada etapa.");
            }
            if (!step.departmentIds().containsAll(configured)) {
                throw new WorkflowConflict("Os setores decisores devem pertencer à mesma etapa do fluxo.");
            }
        }
    }

    private List<UUID> decisionDepartmentIds(WorkflowStepRequest step) {
        return step.decisionDepartmentIds() == null ? List.of() : step.decisionDepartmentIds();
    }

    private Map<UUID, String> lockActiveWorkflowDepartments(UUID organizationId, List<WorkflowStepRequest> steps) {
        Set<UUID> requestedDepartments = new HashSet<>();
        for (WorkflowStepRequest step : steps) {
            Set<UUID> uniqueDepartments = new HashSet<>(step.departmentIds());
            if (uniqueDepartments.size() != step.departmentIds().size()) {
                throw new WorkflowConflict("Cada setor deve aparecer uma única vez em cada etapa.");
            }
            requestedDepartments.addAll(uniqueDepartments);
        }

        Map<UUID, String> departmentNames = new HashMap<>();
        for (UUID departmentId : requestedDepartments.stream().sorted().toList()) {
            List<DepartmentSnapshot> active = jdbc.query("""
                    SELECT id, name FROM departments
                     WHERE organization_id=? AND id=? AND active=true
                     FOR SHARE
                    """, (rs, row) -> new DepartmentSnapshot(
                    rs.getObject("id", UUID.class), rs.getString("name")), organizationId, departmentId);
            if (active.size() != 1) throw new WorkflowNotFound();
            departmentNames.put(active.getFirst().id(), active.getFirst().name());
        }
        return Map.copyOf(departmentNames);
    }

    private void requireOrganizationAdministrator(String actor, UUID organizationId) {
        if (!isOrganizationAdministrator(actor, organizationId)) throw new WorkflowForbidden();
    }

    private boolean isOrganizationAdministrator(String actor, UUID organizationId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM organization_memberships
                 WHERE username=? AND organization_id=? AND role_code='ADMIN' AND active=true
                """, Integer.class, actor, organizationId);
        return count != null && count > 0;
    }

    private boolean lockOrganizationAdministrator(String actor, UUID organizationId) {
        List<String> memberships = jdbc.query("""
                SELECT username FROM organization_memberships
                 WHERE username=? AND organization_id=? AND role_code='ADMIN' AND active=true
                 FOR UPDATE
                """, (rs, row) -> rs.getString("username"), actor, organizationId);
        return !memberships.isEmpty();
    }

    private void requireDepartmentMembership(UUID organizationId, UUID departmentId, String actor) {
        List<UUID> activeMemberships = jdbc.query("""
                SELECT sm.department_id
                  FROM staff_memberships sm
                  JOIN departments d ON d.organization_id=sm.organization_id AND d.id=sm.department_id
                 WHERE sm.username=? AND sm.organization_id=? AND sm.department_id=?
                   AND sm.active=true AND d.active=true
                 ORDER BY sm.department_id
                 FOR UPDATE OF sm
                 FOR SHARE OF d
                """, (rs, row) -> rs.getObject("department_id", UUID.class),
                actor, organizationId, departmentId);
        if (activeMemberships.isEmpty()) throw new WorkflowForbidden();
    }

    private List<DepartmentLock> lockProcessStageDepartments(UUID organizationId, UUID processId,
                                                              int currentStepOrder, int requestedNextStep,
                                                              UUID destinationDepartmentId) {
        return jdbc.query("""
                SELECT d.id, d.active,
                       EXISTS (
                           SELECT 1 FROM administrative_process_step_departments next_step
                            WHERE next_step.organization_id=d.organization_id
                              AND next_step.process_id=?
                              AND next_step.step_order=?
                              AND next_step.department_id=d.id
                       ) AS in_next_step
                  FROM departments d
                 WHERE d.organization_id=?
                   AND (d.id=? OR EXISTS (
                       SELECT 1 FROM administrative_process_step_departments current_step
                        WHERE current_step.organization_id=d.organization_id
                          AND current_step.process_id=?
                          AND current_step.step_order=?
                          AND current_step.department_id=d.id
                   ))
                 ORDER BY d.id
                 FOR SHARE OF d
                """, (rs, row) -> new DepartmentLock(rs.getObject("id", UUID.class),
                        rs.getBoolean("active"), rs.getBoolean("in_next_step")),
                processId, requestedNextStep, organizationId, destinationDepartmentId,
                processId, currentStepOrder);
    }

    private boolean lockCurrentDepartmentMembership(String actor, UUID organizationId, UUID currentDepartmentId) {
        List<UUID> lockedMemberships = jdbc.query("""
                SELECT sm.department_id
                  FROM staff_memberships sm
                  JOIN departments d
                    ON d.organization_id=sm.organization_id AND d.id=sm.department_id
                 WHERE sm.organization_id=? AND sm.department_id=? AND sm.username=?
                   AND sm.active=true AND d.active=true
                 ORDER BY sm.department_id
                 FOR UPDATE OF sm
                 FOR SHARE OF d
                """, (rs, row) -> rs.getObject("department_id", UUID.class),
                organizationId, currentDepartmentId, actor);
        return !lockedMemberships.isEmpty();
    }

    private boolean lockCurrentDepartmentMembershipForRead(String actor, UUID organizationId,
                                                           UUID currentDepartmentId) {
        List<UUID> lockedMemberships = jdbc.query("""
                SELECT sm.department_id
                  FROM staff_memberships sm
                  JOIN departments d
                    ON d.organization_id=sm.organization_id AND d.id=sm.department_id
                 WHERE sm.organization_id=? AND sm.department_id=? AND sm.username=?
                   AND sm.active=true AND d.active=true
                 ORDER BY sm.department_id
                 FOR SHARE OF sm
                 FOR SHARE OF d
                """, (rs, row) -> rs.getObject("department_id", UUID.class),
                organizationId, currentDepartmentId, actor);
        return !lockedMemberships.isEmpty();
    }

    private WorkflowView workflowView(UUID organizationId, UUID workflowId, int version) {
        WorkflowReference reference = jdbc.query("""
                SELECT id, subject_key, subject_name, version, created_at
                  FROM administrative_workflows
                 WHERE organization_id=? AND id=? AND version=?
                """, (rs, row) -> new WorkflowReference(rs.getObject("id", UUID.class),
                        rs.getString("subject_key"), rs.getString("subject_name"),
                        rs.getInt("version"), rs.getObject("created_at", OffsetDateTime.class)),
                organizationId, workflowId, version).stream().findFirst().orElseThrow(WorkflowNotFound::new);
        return new WorkflowView(reference.id(), reference.subjectKey(), reference.subjectName(),
                reference.version(), reference.createdAt(), workflowSteps(organizationId, workflowId, version));
    }

    private List<WorkflowStepView> workflowSteps(UUID organizationId, UUID workflowId, int version) {
        List<WorkflowStepView> result = new ArrayList<>();
        jdbc.query("""
                SELECT step_order, step_name FROM administrative_workflow_steps
                 WHERE organization_id=? AND workflow_id=? AND workflow_version=? ORDER BY step_order
                """, rs -> {
            int order = rs.getInt("step_order");
            result.add(new WorkflowStepView(order, rs.getString("step_name"), workflowDepartments(
                    "administrative_workflow_step_departments", "workflow_id", "workflow_version",
                    organizationId, workflowId, version, order)));
        }, organizationId, workflowId, version);
        return List.copyOf(result);
    }

    private List<WorkflowDepartmentView> workflowDepartments(String table, String ownerIdColumn,
                                                              String versionColumn, UUID organizationId,
                                                              UUID workflowId, int version, int stepOrder) {
        String sql = """
                SELECT pd.department_id, pd.department_name, pd.can_decide
                  FROM %s pd
                 WHERE pd.organization_id=? AND pd.%s=? AND pd.%s=? AND pd.step_order=?
                 ORDER BY lower(pd.department_name), pd.department_id
                """.formatted(table, ownerIdColumn, versionColumn);
        return jdbc.query(sql, (rs, row) -> new WorkflowDepartmentView(
                rs.getObject("department_id", UUID.class), rs.getString("department_name"),
                rs.getBoolean("can_decide")),
                organizationId, workflowId, version, stepOrder);
    }

    private ProcessView processView(UUID organizationId, UUID processId) {
        ProcessRecord process = jdbc.query("""
                SELECT p.id, p.workflow_subject_key, w.subject_name, p.workflow_id, p.workflow_version,
                       p.title, p.current_step_order, current_step.step_name AS current_step_name,
                       p.current_department_id, current_department.department_name AS current_department_name,
                       p.status, p.decision_outcome, p.decision_actor, p.decision_at, p.created_at
                  FROM administrative_processes p
                  JOIN administrative_workflows w
                    ON w.organization_id=p.organization_id AND w.subject_key=p.workflow_subject_key
                   AND w.id=p.workflow_id AND w.version=p.workflow_version
                  JOIN administrative_process_workflow_steps current_step
                    ON current_step.organization_id=p.organization_id AND current_step.process_id=p.id
                   AND current_step.step_order=p.current_step_order
                  JOIN administrative_process_step_departments current_department
                    ON current_department.organization_id=p.organization_id
                   AND current_department.process_id=p.id
                   AND current_department.step_order=p.current_step_order
                   AND current_department.department_id=p.current_department_id
                 WHERE p.organization_id=? AND p.id=?
                """, (rs, row) -> new ProcessRecord(rs.getObject("id", UUID.class),
                        rs.getString("workflow_subject_key"), rs.getString("subject_name"),
                        rs.getObject("workflow_id", UUID.class), rs.getInt("workflow_version"),
                        rs.getString("title"), rs.getInt("current_step_order"),
                        rs.getString("current_step_name"), rs.getObject("current_department_id", UUID.class),
                        rs.getString("current_department_name"), rs.getString("status"),
                        rs.getString("decision_outcome"), rs.getString("decision_actor"),
                        rs.getObject("decision_at", OffsetDateTime.class),
                        rs.getObject("created_at", OffsetDateTime.class)), organizationId, processId)
                .stream().findFirst().orElseThrow(WorkflowNotFound::new);

        List<ProcessStepView> steps = new ArrayList<>();
        jdbc.query("""
                SELECT step_order, step_name FROM administrative_process_workflow_steps
                 WHERE organization_id=? AND process_id=? ORDER BY step_order
                """, rs -> {
            int order = rs.getInt("step_order");
            steps.add(new ProcessStepView(order, rs.getString("step_name"),
                    processDepartments(organizationId, processId, order)));
        }, organizationId, processId);
        List<ProcessEventView> events = jdbc.query("""
                SELECT id, actor, action, from_step_order, to_step_order,
                       from_department_id, to_department_id, decision_outcome, occurred_at
                  FROM administrative_process_events
                 WHERE organization_id=? AND process_id=? ORDER BY occurred_at, id
                """, (rs, row) -> new ProcessEventView(rs.getLong("id"), rs.getString("actor"),
                        rs.getString("action"), (Integer) rs.getObject("from_step_order"),
                        rs.getInt("to_step_order"), rs.getObject("from_department_id", UUID.class),
                        rs.getObject("to_department_id", UUID.class),
                        rs.getString("decision_outcome"),
                        rs.getObject("occurred_at", OffsetDateTime.class)), organizationId, processId);
        List<ProcessDeadlineView> deadlines = jdbc.query("""
                SELECT id, title, due_at, created_by, created_at
                  FROM administrative_process_deadlines
                 WHERE organization_id=? AND process_id=?
                 ORDER BY due_at, created_at, id
                """, (rs, row) -> new ProcessDeadlineView(rs.getObject("id", UUID.class),
                rs.getString("title"), rs.getObject("due_at", OffsetDateTime.class),
                rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class)),
                organizationId, processId);
        return new ProcessView(process.id(), process.subjectKey(), process.subjectName(), process.workflowId(),
                process.workflowVersion(), process.title(), process.currentStepOrder(), process.currentStepName(),
                process.currentDepartmentId(), process.currentDepartmentName(), process.status(),
                process.decisionOutcome(), process.decisionActor(), process.decisionAt(), process.createdAt(),
                List.copyOf(steps), List.copyOf(events), List.copyOf(deadlines));
    }

    private List<WorkflowDepartmentView> processDepartments(UUID organizationId, UUID processId, int stepOrder) {
        return jdbc.query("""
                SELECT pd.department_id, pd.department_name, pd.can_decide
                  FROM administrative_process_step_departments pd
                 WHERE pd.organization_id=? AND pd.process_id=? AND pd.step_order=?
                ORDER BY lower(pd.department_name), pd.department_id
                """, (rs, row) -> new WorkflowDepartmentView(
                rs.getObject("department_id", UUID.class), rs.getString("department_name"),
                rs.getBoolean("can_decide")),
                organizationId, processId, stepOrder);
    }

    private record WorkflowReference(UUID id, String subjectKey, String subjectName, int version,
                                     OffsetDateTime createdAt) {}
    private record WorkflowSelection(UUID id, String subjectName, int version) {}
    private record WorkflowPointer(UUID id, int version) {}
    private record PublicationKey(String subjectKey, String requestHash, UUID workflowId, Integer workflowVersion) {}
    private record DepartmentSnapshot(UUID id, String name) {}
    private record DepartmentLock(UUID id, boolean active, boolean inNextStep) {}
    private record ProcessState(int currentStepOrder, UUID currentDepartmentId, String status) {}
    private record DecisionState(int currentStepOrder, UUID currentDepartmentId, String status,
                                 String decisionOutcome) {}
    private record ProcessRecord(UUID id, String subjectKey, String subjectName, UUID workflowId,
                                 int workflowVersion, String title, int currentStepOrder,
                                 String currentStepName, UUID currentDepartmentId,
                                 String currentDepartmentName, String status, String decisionOutcome,
                                 String decisionActor, OffsetDateTime decisionAt, OffsetDateTime createdAt) {}

    public static class WorkflowNotFound extends RuntimeException {}
    public static class WorkflowForbidden extends RuntimeException {}
    public static class WorkflowConflict extends RuntimeException {
        public WorkflowConflict(String message) { super(message); }
    }
    public static class InvalidProcessListQuery extends RuntimeException {
        public InvalidProcessListQuery(String message) { super(message); }
    }
}
