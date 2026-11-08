package br.com.nexo.gov.config;

import br.com.nexo.gov.security.TenantMembershipFilter;
import br.com.nexo.gov.security.PersistentIdentityFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.List;

@Configuration
@EnableConfigurationProperties(IdentityProviderProperties.class)
public class SecurityConfiguration {
    @Bean FilterRegistrationBean<TenantMembershipFilter> tenantMembershipFilterSecurityChainOnly(
            TenantMembershipFilter tenantFilter) {
        FilterRegistrationBean<TenantMembershipFilter> registration = new FilterRegistrationBean<>(tenantFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean @Profile("local") SecurityFilterChain localSecurityFilterChain(HttpSecurity http, TenantMembershipFilter tenantFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/public/**", "/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .addFilterAfter(tenantFilter, BasicAuthenticationFilter.class)
                .build();
    }

    @Bean @Profile("!local") SecurityFilterChain productionSecurityFilterChain(
            HttpSecurity http,
            TenantMembershipFilter tenantFilter,
            PersistentIdentityFilter identityFilter,
            IdentityProviderProperties identityProvider,
            Environment environment) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        boolean allowLoopbackHttp = environment.acceptsProfiles(Profiles.of("test"));
        if (!identityProvider.isConfigured(allowLoopbackHttp)) {
            return http.authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/public/**", "/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                            .anyRequest().denyAll())
                    .build();
        }

        JwtDecoder decoder = jwtDecoder(identityProvider);
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/public/**", "/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> jwt.decoder(decoder)))
                .addFilterAfter(identityFilter, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(tenantFilter, PersistentIdentityFilter.class)
                .build();
    }

    private JwtDecoder jwtDecoder(IdentityProviderProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(properties.issuerUri());
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<>(JwtClaimNames.AUD,
                (java.util.List<String> values) -> values != null && values.contains(properties.audience()));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, audience));
        return decoder;
    }

    @Bean CorsConfigurationSource corsConfigurationSource(@Value("${nexogov.cors.allowed-origin}") String origin) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(origin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Organization-Id"));
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean @Profile("!local") FilterRegistrationBean<PersistentIdentityFilter> persistentIdentitySecurityChainOnly(
            PersistentIdentityFilter identityFilter) {
        FilterRegistrationBean<PersistentIdentityFilter> registration = new FilterRegistrationBean<>(identityFilter);
        registration.setEnabled(false);
        return registration;
    }
}
