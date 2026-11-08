package br.com.nexo.gov.protocol;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;
import java.util.List;

public final class ProtocolSubjectDtos {
    private ProtocolSubjectDtos() {}

    public record SubjectView(UUID id, String name, boolean onlineAvailable,
                              UUID destinationDepartmentId, String destinationDepartmentName,
                              Integer automaticDeadlineDays,
                              List<AttachmentRequirementView> attachmentRequirements) {}
    public record PublicSubjectView(UUID id, String name, boolean onlineAvailable, long maximumAttachmentSizeBytes,
                                   List<AttachmentRequirementView> attachmentRequirements,
                                   List<ProtocolDtos.PublicParticipantField> participantFields) {}
    public record AttachmentRequirementView(UUID id, String name, boolean required,
                                            List<String> allowedExtensions, boolean active) {}
    public record AttachmentRequirementRequest(@jakarta.validation.constraints.NotBlank @Size(max = 120) String name,
                                               @NotNull Boolean required,
                                               @Size(min = 1, max = 8) List<@jakarta.validation.constraints.NotBlank @Size(max = 8) String> allowedExtensions) {}
    public record SubjectCreateRequest(@NotBlank @Size(max = 180) String name,
                                       @NotNull Boolean onlineAvailable,
                                       UUID destinationDepartmentId,
                                       @Min(1) Integer automaticDeadlineDays) {}
    public static final class SubjectUpdateRequest {
        @NotBlank @Size(max = 180)
        private String name;
        @NotNull
        private Boolean onlineAvailable;
        private UUID destinationDepartmentId;
        @Min(1)
        private Integer automaticDeadlineDays;
        private boolean automaticDeadlineDaysPresent;

        public SubjectUpdateRequest() {}

        @JsonSetter("name")
        public void setName(String name) { this.name = name; }

        @JsonSetter("onlineAvailable")
        public void setOnlineAvailable(Boolean onlineAvailable) { this.onlineAvailable = onlineAvailable; }

        @JsonSetter("destinationDepartmentId")
        public void setDestinationDepartmentId(UUID destinationDepartmentId) {
            this.destinationDepartmentId = destinationDepartmentId;
        }

        @JsonSetter("automaticDeadlineDays")
        public void setAutomaticDeadlineDays(Integer automaticDeadlineDays) {
            this.automaticDeadlineDays = automaticDeadlineDays;
            this.automaticDeadlineDaysPresent = true;
        }

        public String name() { return name; }
        public Boolean onlineAvailable() { return onlineAvailable; }
        public UUID destinationDepartmentId() { return destinationDepartmentId; }
        public Integer automaticDeadlineDays() { return automaticDeadlineDays; }
        public boolean automaticDeadlineDaysPresent() { return automaticDeadlineDaysPresent; }
    }

    public enum ParticipantIdentityFieldType { TEXT, EMAIL, PHONE, DOCUMENT }

    public record ParticipantIdentityFieldInput(
            @NotBlank @Size(max = 64) @Pattern(regexp = "[a-z][a-z0-9_]{0,63}") String key,
            @NotBlank @Size(max = 120) String label,
            @NotNull ParticipantIdentityFieldType inputType,
            @NotNull Boolean required,
            @NotNull @Min(0) @Max(1000) Integer order) {}

    public record ParticipantIdentityFieldUpdate(
            @NotBlank @Size(max = 120) String label,
            @NotNull ParticipantIdentityFieldType inputType,
            @NotNull Boolean required,
            @NotNull @Min(0) @Max(1000) Integer order) {}

    public record ParticipantIdentityFieldView(UUID id, UUID subjectId, String key, String label,
                                               ParticipantIdentityFieldType inputType, boolean required,
                                               int order) {}
}
