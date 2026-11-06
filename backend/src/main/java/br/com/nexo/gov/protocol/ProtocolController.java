package br.com.nexo.gov.protocol;

import br.com.nexo.gov.protocol.ProtocolDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ProtocolController {
    private final ProtocolService service;
    public ProtocolController(ProtocolService service) { this.service = service; }

    @GetMapping("/public/organizations") public List<OrganizationOption> organizations() { return service.organizations(); }
    @PostMapping("/public/protocols") public Created create(@Valid @RequestBody CreateRequest request) { return service.create(request); }

    @GetMapping("/public/protocols/tracking")
    public TrackingView track(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return service.track(bearer(authorization));
    }

    @GetMapping("/public/protocols/{id}/receipt")
    public ResponseEntity<byte[]> receipt(@PathVariable UUID id, @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        byte[] pdf = service.receipt(id, bearer(authorization));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("comprovante-protocolo.pdf", StandardCharsets.UTF_8).build().toString())
                .body(pdf);
    }

    @GetMapping("/staff/departments") public List<DepartmentOption> departments() { return service.departments(); }
    @GetMapping("/staff/protocols") public List<ProtocolSummary> list(@RequestParam(required = false) String status) { return service.list(status); }
    @PostMapping("/staff/protocols/{id}/forward") public ProtocolSummary forward(@PathVariable UUID id, Authentication auth, @Valid @RequestBody TransitionRequest request) { return service.forward(id, auth.getName(), request); }
    @PostMapping("/staff/protocols/{id}/receive") public ProtocolSummary receive(@PathVariable UUID id, Authentication auth, @Valid @RequestBody(required = false) TransitionRequest request) { return service.receive(id, auth.getName(), request); }
    @PostMapping("/staff/protocols/{id}/dispatch") public ProtocolSummary dispatch(@PathVariable UUID id, Authentication auth, @Valid @RequestBody(required = false) TransitionRequest request) { return service.dispatch(id, auth.getName(), request); }
    @PostMapping("/staff/protocols/{id}/complete") public ProtocolSummary complete(@PathVariable UUID id, Authentication auth, @Valid @RequestBody(required = false) TransitionRequest request) { return service.complete(id, auth.getName(), request); }

    private static String bearer(String value) {
        if (value == null || !value.regionMatches(true, 0, "Bearer ", 0, 7) || value.length() <= 7)
            throw new ProtocolExceptions.NotFound();
        return value.substring(7).trim();
    }
}
