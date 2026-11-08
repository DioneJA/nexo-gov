package br.com.nexo.gov.protocol;

import br.com.nexo.gov.protocol.ProtocolSubjectDtos.SubjectCreateRequest;
import br.com.nexo.gov.protocol.ProtocolSubjectDtos.SubjectUpdateRequest;
import br.com.nexo.gov.protocol.ProtocolSubjectDtos.SubjectView;
import br.com.nexo.gov.protocol.ProtocolSubjectDtos.PublicSubjectView;
import br.com.nexo.gov.protocol.ProtocolSubjectDtos.AttachmentRequirementRequest;
import br.com.nexo.gov.protocol.ProtocolSubjectDtos.AttachmentRequirementView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ProtocolSubjectController {
    private final ProtocolSubjectService service;
    private final ProtocolAttachmentPolicyService attachmentPolicyService;

    public ProtocolSubjectController(ProtocolSubjectService service, ProtocolAttachmentPolicyService attachmentPolicyService) {
        this.service = service;
        this.attachmentPolicyService = attachmentPolicyService;
    }

    @GetMapping("/public/organizations/{organizationSlug}/protocol-subjects")
    public List<PublicSubjectView> publicOnlineSubjects(@PathVariable String organizationSlug) {
        return service.listPublicOnlineSubjects(organizationSlug);
    }

    @GetMapping("/staff/organization/protocol-subjects")
    public List<SubjectView> organizationSubjects(Authentication authentication) {
        return service.listOrganizationSubjects(authentication.getName());
    }

    @PostMapping("/staff/organization/protocol-subjects")
    public ResponseEntity<SubjectView> createOrganizationSubject(Authentication authentication,
                                                                 @Valid @RequestBody SubjectCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(authentication.getName(), request));
    }

    @PutMapping("/staff/organization/protocol-subjects/{id}")
    public SubjectView updateOrganizationSubject(Authentication authentication, @PathVariable UUID id,
                                                 @Valid @RequestBody SubjectUpdateRequest request) {
        return service.update(authentication.getName(), id, request);
    }

    @PostMapping("/staff/organization/protocol-subjects/{subjectId}/attachment-requirements")
    public ResponseEntity<AttachmentRequirementView> createAttachmentRequirement(Authentication authentication,
            @PathVariable UUID subjectId, @Valid @RequestBody AttachmentRequirementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attachmentPolicyService.create(authentication.getName(), subjectId, request));
    }

    @PutMapping("/staff/organization/protocol-subjects/{subjectId}/attachment-requirements/{requirementId}")
    public AttachmentRequirementView updateAttachmentRequirement(Authentication authentication,
            @PathVariable UUID subjectId, @PathVariable UUID requirementId,
            @Valid @RequestBody AttachmentRequirementRequest request) {
        return attachmentPolicyService.update(authentication.getName(), subjectId, requirementId, request);
    }

    @DeleteMapping("/staff/organization/protocol-subjects/{subjectId}/attachment-requirements/{requirementId}")
    public ResponseEntity<Void> deactivateAttachmentRequirement(Authentication authentication,
            @PathVariable UUID subjectId, @PathVariable UUID requirementId) {
        attachmentPolicyService.deactivate(authentication.getName(), subjectId, requirementId);
        return ResponseEntity.noContent().build();
    }
}
