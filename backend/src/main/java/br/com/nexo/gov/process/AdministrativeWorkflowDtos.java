package br.com.nexo.gov.process;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AdministrativeWorkflowDtos {
    private AdministrativeWorkflowDtos() {}

    public record WorkflowStepRequest(
            @NotBlank @Size(max = 180) String name,
            @NotEmpty @Size(max = 20) List<@NotNull UUID> departmentIds,
            @Size(max = 20) List<@NotNull UUID> decisionDepartmentIds) {}

    public record WorkflowCreateRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9._-]{0,79}") String subjectKey,
            @NotBlank @Size(max = 180) String subjectName,
            @NotNull @PositiveOrZero Integer expectedVersion,
            @NotNull UUID idempotencyKey,
            @NotEmpty @Size(min = 2, max = 30) List<@NotNull @Valid WorkflowStepRequest> steps) {}

    public record WorkflowDepartmentView(UUID id, String name, boolean canDecide) {}
    public record WorkflowStepView(int order, String name, List<WorkflowDepartmentView> departments) {}
    public record WorkflowView(UUID id, String subjectKey, String subjectName, int version,
                               OffsetDateTime createdAt, List<WorkflowStepView> steps) {}
    public record WorkflowPublicationResult(WorkflowView workflow, boolean replayed) {}

    public record ProcessCreateRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9._-]{0,79}") String subjectKey,
            @NotBlank @Size(max = 240) String title,
            @NotNull UUID initialDepartmentId) {}

    public record ProcessAdvanceRequest(@Min(1) int toStepOrder, @NotNull UUID destinationDepartmentId) {}
    public enum ProcessDecision { DEFERIDO, INDEFERIDO }
    public record ProcessDecisionRequest(@NotNull ProcessDecision outcome) {}

    public record ProcessStepView(int order, String name, List<WorkflowDepartmentView> departments) {}
    public record ProcessEventView(long id, String actor, String action, Integer fromStepOrder,
                                   int toStepOrder, UUID fromDepartmentId, UUID toDepartmentId,
                                   String decisionOutcome, OffsetDateTime occurredAt) {}
    public record ProcessSummaryView(UUID id, String subjectKey, String subjectName, UUID workflowId,
                                     int workflowVersion, String title, int currentStepOrder,
                                     String currentStepName, UUID currentDepartmentId,
                                     String currentDepartmentName, String status, String decisionOutcome,
                                     OffsetDateTime createdAt) {}
    public record ProcessPageView(List<ProcessSummaryView> items, int page, int size, boolean hasNext) {}
    public record ProcessView(UUID id, String subjectKey, String subjectName, UUID workflowId,
                              int workflowVersion, String title, int currentStepOrder,
                              String currentStepName, UUID currentDepartmentId,
                              String currentDepartmentName, String status, String decisionOutcome,
                              String decisionActor, OffsetDateTime decisionAt,
                              OffsetDateTime createdAt, List<ProcessStepView> steps,
                              List<ProcessEventView> events) {}
}
