package br.com.nexo.gov.protocol;

import br.com.nexo.gov.protocol.ProtocolDtos.*;
import br.com.nexo.gov.protocol.ProtocolDtos.InteractionView;
import br.com.nexo.gov.protocol.ProtocolDtos.PublicInteractionRequest;
import br.com.nexo.gov.protocol.ProtocolDtos.StaffInteractionRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ProtocolController {
    private final ProtocolService service;
    private final ProtocolInteractionService interactions;
    public ProtocolController(ProtocolService service, ProtocolInteractionService interactions) {
        this.service = service;
        this.interactions = interactions;
    }

    @GetMapping("/public/organizations") public List<OrganizationOption> organizations() { return service.organizations(); }
    @PostMapping("/public/protocols") public Created create(@Valid @RequestBody CreateRequest request) { return service.create(request); }

    @PostMapping(value = "/public/protocols", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Created createWithAttachments(@RequestPart("request") @Valid CreateRequest request,
            @RequestPart(name = "files", required = false) List<MultipartFile> files,
            @RequestParam(name = "attachmentRequirementIds", required = false) List<UUID> requirementIds) {
        return service.create(request, files, requirementIds);
    }

    @GetMapping("/public/protocols/tracking")
    public ResponseEntity<TrackingView> track(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(service.track(bearer(authorization)));
    }

    @PostMapping(value = "/public/protocols/{id}/interactions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InteractionView> createPublicInteraction(@PathVariable UUID id,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestPart("request") @Valid PublicInteractionRequest request,
            @RequestPart(name = "files", required = false) List<MultipartFile> files) {
        return ResponseEntity.status(201).body(interactions.createPublic(id, bearer(authorization), request.message(), files));
    }

    @PostMapping("/public/protocols/{id}/participants/{participantId}/revoke")
    public ResponseEntity<Void> revokeParticipant(@PathVariable UUID id, @PathVariable UUID participantId,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        service.revokeParticipant(id, participantId, bearer(authorization));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/public/protocols/{id}/receipt")
    public ResponseEntity<byte[]> receipt(@PathVariable UUID id, @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        byte[] pdf = service.receipt(id, bearer(authorization));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("comprovante-protocolo.pdf", StandardCharsets.UTF_8).build().toString())
                .body(pdf);
    }

    @GetMapping("/public/protocols/{id}/attachments/{attachmentId}/download")
    public ResponseEntity<byte[]> downloadPublicAttachment(@PathVariable UUID id, @PathVariable UUID attachmentId,
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return attachmentResponse(service.downloadPublicAttachment(id, attachmentId, bearer(authorization)));
    }

    @GetMapping("/staff/departments") public List<DepartmentOption> departments() { return service.departments(); }
    @GetMapping("/staff/protocols")
    public ResponseEntity<List<ProtocolSummary>> list(@RequestParam(required = false) String status,
                                                       Authentication auth) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(service.list(status, auth.getName()));
    }
    @PostMapping("/staff/protocols/{id}/read")
    public ResponseEntity<ProtocolReadReceipt> recordRead(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(service.recordRead(id, auth.getName()));
    }
    @GetMapping("/staff/protocols/{id}/attachments")
    public ResponseEntity<List<ProtocolAttachmentService.AttachmentView>> staffAttachments(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(service.staffAttachments(id, auth.getName()));
    }
    @GetMapping("/staff/protocols/{id}/access-history")
    public ResponseEntity<List<ProtocolDocumentAccessService.AccessHistoryEvent>> staffAccessHistory(@PathVariable UUID id,
                                                                                                      Authentication auth) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(service.accessHistory(id, auth.getName()));
    }
    @GetMapping("/staff/protocols/{id}/attachments/{attachmentId}/download")
    public ResponseEntity<byte[]> downloadStaffAttachment(@PathVariable UUID id, @PathVariable UUID attachmentId, Authentication auth) {
        return attachmentResponse(service.downloadStaffAttachment(id, attachmentId, auth.getName()));
    }
    @GetMapping("/staff/protocols/{id}/interactions")
    public ResponseEntity<List<InteractionView>> staffInteractions(@PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(interactions.staffList(id, auth.getName()));
    }
    @PostMapping(value = "/staff/protocols/{id}/interactions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InteractionView> createStaffInteractionJson(@PathVariable UUID id, Authentication auth,
            @Valid @RequestBody StaffInteractionRequest request) {
        return ResponseEntity.status(201).body(interactions.createStaff(id, auth.getName(), request, List.of()));
    }
    @PostMapping(value = "/staff/protocols/{id}/interactions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InteractionView> createStaffInteractionMultipart(@PathVariable UUID id, Authentication auth,
            @RequestPart("request") @Valid StaffInteractionRequest request,
            @RequestPart(name = "files", required = false) List<MultipartFile> files) {
        return ResponseEntity.status(201).body(interactions.createStaff(id, auth.getName(), request, files));
    }
    @PostMapping("/staff/protocols/{id}/reopen")
    public ProtocolSummary reopen(@PathVariable UUID id, Authentication auth) {
        return service.reopen(id, auth.getName());
    }
    @PostMapping("/staff/protocols/{id}/forward") public ProtocolSummary forward(@PathVariable UUID id, Authentication auth, @Valid @RequestBody ForwardRequest request) { return service.forward(id, auth.getName(), request); }
    @PostMapping("/staff/protocols/{id}/receive") public ProtocolSummary receive(@PathVariable UUID id, Authentication auth, @Valid @RequestBody(required = false) TransitionRequest request) { return service.receive(id, auth.getName(), request); }
    @PostMapping("/staff/protocols/{id}/dispatch") public ProtocolSummary dispatch(@PathVariable UUID id, Authentication auth, @Valid @RequestBody(required = false) TransitionRequest request) { return service.dispatch(id, auth.getName(), request); }
    @PostMapping("/staff/protocols/{id}/complete") public ProtocolSummary complete(@PathVariable UUID id, Authentication auth, @Valid @RequestBody(required = false) TransitionRequest request) { return service.complete(id, auth.getName(), request); }

    private static String bearer(String value) {
        if (value == null || !value.regionMatches(true, 0, "Bearer ", 0, 7) || value.length() <= 7)
            throw new ProtocolExceptions.NotFound();
        return value.substring(7).trim();
    }

    private static ResponseEntity<byte[]> attachmentResponse(ProtocolAttachmentService.Downloaded attachment) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(attachment.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(attachment.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(attachment.content());
    }
}
