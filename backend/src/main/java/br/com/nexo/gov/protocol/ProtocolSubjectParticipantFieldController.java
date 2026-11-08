package br.com.nexo.gov.protocol;

import br.com.nexo.gov.protocol.ProtocolSubjectDtos.ParticipantIdentityFieldInput;
import br.com.nexo.gov.protocol.ProtocolSubjectDtos.ParticipantIdentityFieldUpdate;
import br.com.nexo.gov.protocol.ProtocolSubjectDtos.ParticipantIdentityFieldView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff/organization/protocol-subjects/{subjectId}/participant-fields")
public class ProtocolSubjectParticipantFieldController {
    private final ProtocolSubjectParticipantFieldService service;

    public ProtocolSubjectParticipantFieldController(ProtocolSubjectParticipantFieldService service) {
        this.service = service;
    }

    @GetMapping
    public List<ParticipantIdentityFieldView> list(Authentication authentication, @PathVariable UUID subjectId) {
        return service.list(authentication.getName(), subjectId);
    }

    @PostMapping
    public ResponseEntity<ParticipantIdentityFieldView> create(Authentication authentication,
            @PathVariable UUID subjectId, @Valid @RequestBody ParticipantIdentityFieldInput request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(authentication.getName(), subjectId, request));
    }

    @PutMapping("/{fieldId}")
    public ParticipantIdentityFieldView update(Authentication authentication, @PathVariable UUID subjectId,
            @PathVariable UUID fieldId, @Valid @RequestBody ParticipantIdentityFieldUpdate request) {
        return service.update(authentication.getName(), subjectId, fieldId, request);
    }

    @DeleteMapping("/{fieldId}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable UUID subjectId,
            @PathVariable UUID fieldId) {
        service.delete(authentication.getName(), subjectId, fieldId);
        return ResponseEntity.noContent().build();
    }
}
