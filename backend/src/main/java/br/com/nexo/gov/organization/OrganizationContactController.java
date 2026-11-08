package br.com.nexo.gov.organization;

import br.com.nexo.gov.protocol.ProtocolExceptions.NotFound;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;

import static br.com.nexo.gov.organization.OrganizationContactDtos.*;

@RestController
@Validated
@RequestMapping("/api/v1/staff/organization")
public class OrganizationContactController {
    private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);
    private final OrganizationContactService service;

    public OrganizationContactController(OrganizationContactService service) {
        this.service = service;
    }

    @GetMapping("/contact-management")
    public ResponseEntity<ContactManagementCapabilities> capabilities(Authentication authentication) {
        return privateResponse(service.capabilities(authentication.getName()));
    }

    @GetMapping("/contacts")
    public ResponseEntity<ContactPage> list(Authentication authentication,
                                            @RequestParam(defaultValue = "") @Size(max = 120) String q,
                                            @RequestParam(defaultValue = "false") boolean includeArchived,
                                            @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
                                            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return privateResponse(service.list(authentication.getName(), q, includeArchived, page, size));
    }

    @PostMapping("/contacts")
    public ResponseEntity<ContactView> create(Authentication authentication, @Valid @RequestBody ContactInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(service.create(authentication.getName(), input));
    }

    @PutMapping("/contacts/{id}")
    public ResponseEntity<ContactView> update(Authentication authentication, @PathVariable UUID id,
                                              @Valid @RequestBody ContactInput input) {
        return privateResponse(service.update(authentication.getName(), id, input));
    }

    @PutMapping("/contacts/{id}/active")
    public ResponseEntity<ContactActiveResult> setActive(Authentication authentication, @PathVariable UUID id,
                                                        @Valid @RequestBody ContactActiveRequest input) {
        return privateResponse(service.setActive(authentication.getName(), id, input.active()));
    }

    @GetMapping(value = "/contacts/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(Authentication authentication,
                                         @RequestParam(defaultValue = "false") boolean includeArchived) {
        byte[] csv = service.exportCsv(authentication.getName(), includeArchived);
        return ResponseEntity.ok().contentType(TEXT_CSV).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"contatos.csv\"")
                .header("X-Content-Type-Options", "nosniff").body(csv);
    }

    @PostMapping(value = "/contacts/import", consumes = "text/csv")
    public ResponseEntity<ContactImportResult> importCsv(Authentication authentication, HttpServletRequest request) throws IOException {
        byte[] body = request.getInputStream().readNBytes(OrganizationContactCsv.MAX_BYTES + 1);
        if (body.length > OrganizationContactCsv.MAX_BYTES) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).cacheControl(CacheControl.noStore())
                    .body(null);
        }
        return privateResponse(service.importCsv(authentication.getName(), body));
    }

    @ExceptionHandler(OrganizationContactCsv.CsvException.class)
    public ResponseEntity<Map<String, String>> invalidCsv(OrganizationContactCsv.CsvException failure) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("message", failure.getMessage()));
    }

    @ExceptionHandler(NotFound.class)
    public ResponseEntity<Void> notFound() {
        return ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build();
    }

    private static <T> ResponseEntity<T> privateResponse(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

}
