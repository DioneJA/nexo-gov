package br.com.nexo.gov.organization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.net.URI;

public final class OrganizationPrivacyNoticeDtos {
    private OrganizationPrivacyNoticeDtos() {}

    public record PrivacyNoticeInput(
            @JsonProperty(value = "explanatoryText", required = true)
            @Size(max = 2000) @Pattern(regexp = "^[\\P{Cc}\\r\\n]*$") String explanatoryText,
            @JsonProperty(value = "privacyPolicyUrl", required = true)
            @Size(max = 2048) @Pattern(regexp = "^(?:https://[^\\s@]+)?$") String privacyPolicyUrl
    ) {
        @AssertTrue(message = "Informe juntos um texto e um link HTTPS válido, ou deixe os dois em branco.")
        public boolean isPrivacyNoticeConfiguredTogether() {
            String text = normalize(explanatoryText);
            String link = normalize(privacyPolicyUrl);
            if (text == null && link == null) return true;
            if (text == null || link == null) return false;
            try {
                URI uri = URI.create(link);
                return "https".equalsIgnoreCase(uri.getScheme())
                        && uri.getHost() != null
                        && uri.getRawUserInfo() == null
                        && (uri.getPort() < 0 || uri.getPort() <= 65535);
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }

        private static String normalize(String value) {
            if (value == null) return null;
            String normalized = value.strip();
            return normalized.isEmpty() ? null : normalized;
        }
    }

    public record PrivacyNoticeView(
            @JsonInclude(JsonInclude.Include.ALWAYS) String explanatoryText,
            @JsonInclude(JsonInclude.Include.ALWAYS) String privacyPolicyUrl,
            boolean canManagePrivacyNotice
    ) {}

    public record PublicPrivacyNoticeView(
            @JsonInclude(JsonInclude.Include.ALWAYS) String explanatoryText,
            @JsonInclude(JsonInclude.Include.ALWAYS) String privacyPolicyUrl
    ) {}
}
