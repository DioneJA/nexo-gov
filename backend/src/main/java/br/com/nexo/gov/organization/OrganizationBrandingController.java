package br.com.nexo.gov.organization;

import br.com.nexo.gov.organization.OrganizationBrandingDtos.OrganizationBrandingInput;
import br.com.nexo.gov.protocol.ProtocolExceptions.ForbiddenAction;
import br.com.nexo.gov.protocol.ProtocolExceptions.NotFound;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class OrganizationBrandingController {
    private final OrganizationBrandingService service;

    public OrganizationBrandingController(OrganizationBrandingService service) {
        this.service = service;
    }

    @GetMapping("/public/organizations/{organizationSlug}/branding")
    public ResponseEntity<?> publicBranding(@PathVariable String organizationSlug) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.publicBranding(organizationSlug));
    }

    @GetMapping("/staff/organization/branding")
    public ResponseEntity<?> staffBranding(Authentication authentication) {
        return privateResponse(service.staff(authentication.getName()));
    }

    @PutMapping("/staff/organization/branding")
    public ResponseEntity<?> publishBranding(Authentication authentication,
                                             @Valid @RequestBody OrganizationBrandingInput input) {
        return privateResponse(service.publish(authentication.getName(), input));
    }

    @ExceptionHandler(NotFound.class)
    public ResponseEntity<Map<String, String>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(CacheControl.noStore())
                .body(Map.of("message", "Organização não encontrada."));
    }

    @ExceptionHandler(ForbiddenAction.class)
    public ResponseEntity<Map<String, String>> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).cacheControl(CacheControl.noStore())
                .body(Map.of("message", "Ação não autorizada."));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> badRequest() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(Map.of("message", "Confira os campos da identidade visual."));
    }

    private static <T> ResponseEntity<T> privateResponse(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
