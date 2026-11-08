package br.com.nexo.gov.process;

import br.com.nexo.gov.process.AdministrativeWorkflowDtos.ProcessAdvanceRequest;
import br.com.nexo.gov.process.AdministrativeWorkflowDtos.ProcessCreateRequest;
import br.com.nexo.gov.process.AdministrativeWorkflowDtos.ProcessDecisionRequest;
import br.com.nexo.gov.process.AdministrativeWorkflowDtos.ProcessPageView;
import br.com.nexo.gov.process.AdministrativeWorkflowDtos.ProcessView;
import br.com.nexo.gov.process.AdministrativeWorkflowDtos.WorkflowCreateRequest;
import br.com.nexo.gov.process.AdministrativeWorkflowDtos.WorkflowPublicationResult;
import br.com.nexo.gov.process.AdministrativeWorkflowDtos.WorkflowView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

import static br.com.nexo.gov.process.AdministrativeWorkflowService.WorkflowConflict;
import static br.com.nexo.gov.process.AdministrativeWorkflowService.WorkflowForbidden;
import static br.com.nexo.gov.process.AdministrativeWorkflowService.WorkflowNotFound;
import static br.com.nexo.gov.process.AdministrativeWorkflowService.InvalidProcessListQuery;

@RestController
@RequestMapping("/api/v1/staff")
public class AdministrativeWorkflowController {
    private final AdministrativeWorkflowService service;

    public AdministrativeWorkflowController(AdministrativeWorkflowService service) {
        this.service = service;
    }

    @PostMapping("/administrative-workflows")
    public ResponseEntity<WorkflowView> createWorkflow(Authentication authentication,
                                                       @Valid @RequestBody WorkflowCreateRequest request) {
        WorkflowPublicationResult result = service.createWorkflow(authentication.getName(), request);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(result.workflow());
    }

    @GetMapping("/administrative-workflows/{subjectKey}")
    public WorkflowView currentWorkflow(Authentication authentication, @PathVariable String subjectKey) {
        return service.currentWorkflow(authentication.getName(), subjectKey);
    }

    @PostMapping("/administrative-processes")
    public ResponseEntity<ProcessView> createProcess(Authentication authentication,
                                                     @Valid @RequestBody ProcessCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createProcess(authentication.getName(), request));
    }

    @GetMapping("/administrative-processes")
    public ResponseEntity<ProcessPageView> listProcesses(Authentication authentication,
                                                         @RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false) Integer size,
                                                         @RequestParam(required = false) String q) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.listProcesses(authentication.getName(), page, size, q));
    }

    @GetMapping("/administrative-processes/{processId}")
    public ProcessView getProcess(Authentication authentication, @PathVariable UUID processId) {
        return service.getProcess(authentication.getName(), processId);
    }

    @PostMapping("/administrative-processes/{processId}/advance")
    public ProcessView advance(Authentication authentication, @PathVariable UUID processId,
                               @Valid @RequestBody ProcessAdvanceRequest request) {
        return service.advance(authentication.getName(), processId, request);
    }

    @PostMapping("/administrative-processes/{processId}/decision")
    public ProcessView decide(Authentication authentication, @PathVariable UUID processId,
                              @Valid @RequestBody ProcessDecisionRequest request) {
        return service.decide(authentication.getName(), processId, request);
    }

    @ExceptionHandler(WorkflowNotFound.class)
    public ResponseEntity<Map<String, String>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Registro não encontrado."));
    }

    @ExceptionHandler(WorkflowForbidden.class)
    public ResponseEntity<Map<String, String>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Ação não autorizada."));
    }

    @ExceptionHandler(WorkflowConflict.class)
    public ResponseEntity<Map<String, String>> conflict(WorkflowConflict exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(InvalidProcessListQuery.class)
    public ResponseEntity<Map<String, String>> invalidProcessListQuery(InvalidProcessListQuery exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }
}
