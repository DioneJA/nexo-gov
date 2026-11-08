package br.com.nexo.gov.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

@ConfigurationProperties("nexogov.identity")
public record IdentityProviderProperties(String issuerUri, String jwkSetUri, String audience) {
    public boolean isConfigured() {
        return isConfigured(false);
    }

    public boolean isConfigured(boolean allowLoopbackHttp) {
        return hasText(audience)
                && isTrustedEndpoint(issuerUri, allowLoopbackHttp)
                && isTrustedEndpoint(jwkSetUri, allowLoopbackHttp);
    }

    private static boolean isTrustedEndpoint(String value, boolean allowLoopbackHttp) {
        if (!hasText(value)) return false;
        try {
            URI uri = new URI(value);
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null)
                return false;
            if ("https".equalsIgnoreCase(uri.getScheme())) return true;
            return allowLoopbackHttp
                    && "http".equalsIgnoreCase(uri.getScheme())
                    && isLoopback(uri.getHost());
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private static boolean isLoopback(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("localhost")
                || normalized.equals("127.0.0.1")
                || normalized.equals("::1")
                || normalized.equals("[::1]");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
