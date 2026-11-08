package br.com.nexo.gov.organization;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class OrganizationBrandingDtos {
    private OrganizationBrandingDtos() {}

    public enum AccentToken {
        NEXO_NAVY,
        NEXO_BLUE,
        ACTION_BLUE,
        SUCCESS_GREEN
    }

    public record OrganizationBrandingInput(
            @Size(max = 160) @Pattern(regexp = "^[^\\p{Cc}]*$") String displayNameOverride,
            @NotNull AccentToken accentToken
    ) {}

    public record OrganizationBrandingView(
            String displayName,
            @JsonInclude(JsonInclude.Include.ALWAYS) String displayNameOverride,
            AccentToken accentToken,
            boolean canManageBranding
    ) {}

    public record PublicOrganizationBrandingView(
            String organizationSlug,
            String displayName,
            AccentToken accentToken
    ) {}
}
