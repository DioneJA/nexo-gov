package br.com.nexo.gov.protocol;

import br.com.nexo.gov.protocol.ProtocolExceptions.ForbiddenAction;
import br.com.nexo.gov.protocol.ProtocolExceptions.InvalidTransition;
import br.com.nexo.gov.protocol.ProtocolExceptions.NotFound;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@RestControllerAdvice
public class ProtocolExceptionHandler {
    @ExceptionHandler(NotFound.class) ResponseEntity<Map<String, String>> notFound() { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Registro não encontrado.")); }
    @ExceptionHandler(ForbiddenAction.class) ResponseEntity<Map<String, String>> forbidden() { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Ação não autorizada.")); }
    @ExceptionHandler(InvalidTransition.class) ResponseEntity<Map<String, String>> conflict(InvalidTransition exception) { return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage())); }
    @ExceptionHandler(ProtocolSubjectService.SubjectNameConflict.class) ResponseEntity<Map<String, String>> subjectConflict(ProtocolSubjectService.SubjectNameConflict exception) { return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage())); }
    @ExceptionHandler(ProtocolSubjectService.SubjectRoutingUnavailable.class) ResponseEntity<Map<String, String>> subjectRoutingUnavailable(ProtocolSubjectService.SubjectRoutingUnavailable exception) { return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage())); }
    @ExceptionHandler(ProtocolAttachmentService.AttachmentRejected.class) ResponseEntity<Map<String, Object>> attachmentRejected(ProtocolAttachmentService.AttachmentRejected exception) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("message", exception.getMessage());
        if (exception.attachmentRequirementId() != null) body.put("attachmentRequirementId", exception.attachmentRequirementId().toString());
        return ResponseEntity.badRequest().body(body);
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<Map<String, String>> uploadTooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of(
                "message", "O envio dos documentos excede o limite total configurado. Reduza o tamanho ou a quantidade dos arquivos."));
    }
    @ExceptionHandler(ProtocolAttachmentPolicyService.AttachmentRequirementNameConflict.class) ResponseEntity<Map<String, String>> attachmentRequirementConflict(ProtocolAttachmentPolicyService.AttachmentRequirementNameConflict exception) { return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage())); }
    @ExceptionHandler(ProtocolSubjectParticipantFieldService.ParticipantFieldConflict.class) ResponseEntity<Map<String, String>> participantFieldConflict(ProtocolSubjectParticipantFieldService.ParticipantFieldConflict exception) { return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage())); }
}
