package br.com.nexo.gov.protocol;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ProtocolDtos {
    private ProtocolDtos() {}

    public record CreateRequest(
            @NotBlank @Size(max = 80) String organizationSlug,
            @NotBlank @Size(max = 180) String applicantName,
            @NotBlank @Email @Size(max = 254) String applicantEmail,
            @NotBlank @Size(max = 240) String subject,
            @NotBlank @Size(max = 8000) String initialMessage) {}

    public record Created(UUID id, String protocolNumber, String status, OffsetDateTime createdAt,
                          String trackingToken, String receiptPath) {}

    public record OrganizationOption(UUID id, String slug, String name) {}
    public record DepartmentOption(UUID id, String name) {}

    public record TransitionRequest(UUID destinationDepartmentId,
                                    @Size(max = 500) String note,
                                    Boolean visibleToApplicant) {}

    public record ProtocolSummary(UUID id, String protocolNumber, String subject, String applicantName,
                                  String status, UUID assignedDepartmentId, String assignedDepartmentName,
                                  OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

    public record EventView(String action, String summary, OffsetDateTime createdAt) {}
    public record TrackingView(String protocolNumber, String subject, String status, OffsetDateTime createdAt,
                               List<EventView> events) {}
}
