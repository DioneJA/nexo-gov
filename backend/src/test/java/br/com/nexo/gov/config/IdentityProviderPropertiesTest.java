package br.com.nexo.gov.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityProviderPropertiesTest {
    @Test void productionRequiresHttpsForIssuerAndJwkSet() {
        assertThat(new IdentityProviderProperties(
                "https://identity.example.gov/tenant",
                "https://identity.example.gov/tenant/jwks",
                "nexogov-api").isConfigured()).isTrue();

        assertThat(new IdentityProviderProperties(
                "http://identity.example.gov/tenant",
                "https://identity.example.gov/tenant/jwks",
                "nexogov-api").isConfigured()).isFalse();
        assertThat(new IdentityProviderProperties(
                "https://identity.example.gov/tenant",
                "http://identity.example.gov/tenant/jwks",
                "nexogov-api").isConfigured()).isFalse();
    }

    @Test void testProfileMayUseHttpOnlyForLoopbackEndpoints() {
        assertThat(new IdentityProviderProperties(
                "http://localhost:9001/issuer",
                "http://127.0.0.1:9002/jwks",
                "nexogov-api").isConfigured(true)).isTrue();

        assertThat(new IdentityProviderProperties(
                "https://identity.example.gov/tenant",
                "http://jwks.example.gov/jwks",
                "nexogov-api").isConfigured(true)).isFalse();
    }

    @Test void invalidOrIncompleteConfigurationStaysUnconfigured() {
        assertThat(new IdentityProviderProperties("not a URL", "https://identity.example.gov/jwks", "nexogov-api")
                .isConfigured()).isFalse();
        assertThat(new IdentityProviderProperties("https://identity.example.gov", "", "nexogov-api")
                .isConfigured()).isFalse();
        assertThat(new IdentityProviderProperties("https://identity.example.gov", "https://identity.example.gov/jwks", " ")
                .isConfigured()).isFalse();
    }
}
