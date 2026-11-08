package br.com.nexo.gov.protocol;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.Collections;
import java.util.HashMap;

public final class ProtocolDtos {
    private ProtocolDtos() {}

    public record CreateRequest(
            @NotBlank @Size(max = 80) String organizationSlug,
            @NotBlank @Size(max = 180) String applicantName,
            @NotBlank @Email @Size(max = 254) String applicantEmail,
            @NotNull UUID subjectId,
            @NotBlank @Size(max = 8000) String initialMessage,
            @Valid @Size(max = 20) List<@Valid ParticipantIdentityInput> participants) {
        public CreateRequest(String organizationSlug, String applicantName, String applicantEmail,
                             UUID subjectId, String initialMessage) {
            this(organizationSlug, applicantName, applicantEmail, subjectId, initialMessage, List.of());
        }

        public CreateRequest {
            participants = participants == null ? List.of() : List.copyOf(participants);
        }
    }

    public record ParticipantIdentityInput(@NotBlank @Size(max = 180) String name,
                                           @Size(max = 64) Map<String, String> identityValues) {
        public ParticipantIdentityInput {
            identityValues = identityValues == null ? Map.of()
                    : Collections.unmodifiableMap(new HashMap<>(identityValues));
        }
    }

    public record PublicParticipantField(String key, String label, String inputType, boolean required, int order) {}
    public record ParticipantInvitation(UUID id, String name, String token) {}
    public record ProtocolParticipantView(UUID id, String name, boolean active, OffsetDateTime createdAt) {}
    public record ParticipantIdentityValue(String key, String label, String value) {}

    public record Created(UUID id, String protocolNumber, String status, OffsetDateTime createdAt,
                          String trackingToken, String receiptPath, String assignedDepartmentName,
                          List<ParticipantInvitation> participantInvitations) {}

    public record OrganizationOption(UUID id, String slug, String name) {}
    public record DepartmentOption(UUID id, String name) {}

    public record TransitionRequest(UUID destinationDepartmentId,
                                    @Size(max = 500) String note,
                                    Boolean visibleToApplicant) {}

    public record PublicInteractionRequest(@NotBlank @Size(max = 8000) String message) {}

    public record StaffInteractionRequest(@NotBlank @Size(max = 8000) String message,
                                          @NotBlank @Pattern(regexp = "PUBLIC|INTERNAL") String visibility) {}

    public record ForwardRequest(@NotNull UUID destinationDepartmentId,
                                 @Size(max = 500) String note,
                                 Boolean visibleToApplicant) {}

    public enum ProtocolReadStatus {
        UNREAD_BY_DEPARTMENT,
        READ_BY_OTHER,
        READ_BY_ME
    }

    public record ProtocolReadReceipt(UUID protocolId, UUID departmentId, OffsetDateTime readAt,
                                      ProtocolReadStatus readStatus) {}

    public record ProtocolSummary(UUID id, String protocolNumber, String subject, String applicantName,
                                  String status, UUID assignedDepartmentId, String assignedDepartmentName,
                                  OffsetDateTime createdAt, OffsetDateTime updatedAt, int attachmentCount,
                                  List<String> interactionAllowedExtensions, ProtocolReadStatus readStatus) {}

    public record EventView(String action, String summary, OffsetDateTime createdAt) {}
    public record TrackingView(UUID id, String protocolNumber, String subject, String status, OffsetDateTime createdAt,
                               List<EventView> events, List<ProtocolAttachmentService.AttachmentView> attachments,
                               List<InteractionView> interactions, List<String> interactionAllowedExtensions,
                               boolean applicantView, String participantName,
                               List<ProtocolParticipantView> participants,
                               List<ParticipantIdentityValue> participantFields) {}

    public record InteractionAttachmentView(UUID id, String filename, String mediaType, long sizeBytes,
                                            String downloadPath) {}
    public record InteractionView(UUID id, String author, String department, String message, String visibility,
                                  OffsetDateTime createdAt, List<InteractionAttachmentView> attachments) {}
}
