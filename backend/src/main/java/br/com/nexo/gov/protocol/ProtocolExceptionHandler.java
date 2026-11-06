package br.com.nexo.gov.protocol;

import br.com.nexo.gov.protocol.ProtocolExceptions.ForbiddenAction;
import br.com.nexo.gov.protocol.ProtocolExceptions.InvalidTransition;
import br.com.nexo.gov.protocol.ProtocolExceptions.NotFound;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ProtocolExceptionHandler {
    @ExceptionHandler(NotFound.class) ResponseEntity<Map<String, String>> notFound() { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Registro não encontrado.")); }
    @ExceptionHandler(ForbiddenAction.class) ResponseEntity<Map<String, String>> forbidden() { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Ação não autorizada.")); }
    @ExceptionHandler(InvalidTransition.class) ResponseEntity<Map<String, String>> conflict(InvalidTransition exception) { return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage())); }
}
