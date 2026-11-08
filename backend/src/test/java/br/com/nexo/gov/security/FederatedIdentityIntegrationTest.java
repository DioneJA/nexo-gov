package br.com.nexo.gov.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"production", "test"})
@Testcontainers
class FederatedIdentityIntegrationTest {
    private static final String ISSUER = "https://identity.example.test/municipality";
    private static final String AUDIENCE = "nexogov-api";
    private static final String KEY_ID = "nexogov-test-signing-key";
    private static final RSAKey SIGNING_KEY = generateSigningKey();
    private static final HttpServer JWKS_SERVER = startJwksServer();

    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.4-alpine");

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("nexogov.identity.issuer-uri", () -> ISSUER);
        registry.add("nexogov.identity.jwk-set-uri", () -> "http://127.0.0.1:" + JWKS_SERVER.getAddress().getPort() + "/jwks");
        registry.add("nexogov.identity.audience", () -> AUDIENCE);
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @AfterAll static void stopJwksServer() {
        JWKS_SERVER.stop(0);
    }

    @Test void organizationScopedLinkWithSharedUsernameDoesNotGrantAccessToAnotherOrganization() {
        String actor = "shared-federated-staff-" + UUID.randomUUID();
        String subject = "idp-subject-" + UUID.randomUUID();
        UUID organizationA = organizationWithStaff(actor);
        UUID organizationB = organizationWithStaff(actor);
        link(organizationA, subject, actor, true);
        link(organizationB, subject, actor, false);

        jdbc.update("INSERT INTO staff_profiles(organization_id,username,registration_date,registration_date_source) " +
                        "VALUES(?,?,NULL,'UNKNOWN')",
                organizationA, actor);
        jdbc.update("UPDATE user_accounts SET registration_date=?,registration_date_source='IMPORTED' WHERE username=?",
                java.sql.Date.valueOf("2024-02-03"), actor);

        assertThat(call(subject, ISSUER, List.of(AUDIENCE), Instant.now().plus(5, ChronoUnit.MINUTES), organizationA).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM staff_profiles WHERE organization_id=? AND username=? AND last_online_at IS NOT NULL",
                Integer.class, organizationA, actor)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT registration_date IS NULL AND registration_date_source='UNKNOWN' " +
                        "FROM staff_profiles WHERE organization_id=? AND username=?", Boolean.class, organizationA, actor))
                .isTrue();

        assertThat(call(subject, ISSUER, List.of(AUDIENCE), Instant.now().plus(5, ChronoUnit.MINUTES), organizationB).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        jdbc.update("UPDATE user_accounts SET active=false WHERE username=?", actor);
        assertThat(call(subject, ISSUER, List.of(AUDIENCE), Instant.now().plus(5, ChronoUnit.MINUTES), organizationA).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(subject, ISSUER, List.of(AUDIENCE), Instant.now().plus(5, ChronoUnit.MINUTES), organizationB).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        jdbc.update("UPDATE user_accounts SET active=true WHERE username=?", actor);
        assertThat(call(subject, ISSUER, List.of(AUDIENCE), Instant.now().plus(5, ChronoUnit.MINUTES), organizationA).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(call(subject, ISSUER, List.of(AUDIENCE), Instant.now().plus(5, ChronoUnit.MINUTES), organizationB).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test void rejectsUnlinkedAndInactiveOrganizationScopedIdentities() {
        String unlinkedActor = "unlinked-staff-" + UUID.randomUUID();
        UUID organization = organizationWithStaff(unlinkedActor);
        String unknownSubject = "unlinked-" + UUID.randomUUID();
        jdbc.update("INSERT INTO user_accounts(username,active) VALUES(?,true)", unlinkedActor);
        assertThat(call(unknownSubject, ISSUER, List.of(AUDIENCE), Instant.now().plus(5, ChronoUnit.MINUTES), organization).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        String inactiveActor = "inactive-staff-" + UUID.randomUUID();
        UUID inactiveOrganization = organizationWithStaff(inactiveActor);
        String inactiveSubject = "inactive-subject-" + UUID.randomUUID();
        link(inactiveOrganization, inactiveSubject, inactiveActor, false);
        assertThat(call(inactiveSubject, ISSUER, List.of(AUDIENCE), Instant.now().plus(5, ChronoUnit.MINUTES), inactiveOrganization).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test void organizationScopedIdentityStillRequiresMembershipAndUnsuspendedAccess() {
        String noMembershipActor = "no-membership-" + UUID.randomUUID();
        UUID noMembershipOrganization = emptyOrganization();
        String noMembershipSubject = "no-membership-subject-" + UUID.randomUUID();
        link(noMembershipOrganization, noMembershipSubject, noMembershipActor, true);
        assertThat(call(noMembershipSubject, ISSUER, List.of(AUDIENCE), Instant.now().plus(5, ChronoUnit.MINUTES), noMembershipOrganization).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        String suspendedActor = "suspended-org-staff-" + UUID.randomUUID();
        UUID suspendedOrganization = organizationWithStaff(suspendedActor);
        String suspendedSubject = "suspended-org-subject-" + UUID.randomUUID();
        link(suspendedOrganization, suspendedSubject, suspendedActor, true);
        assertThat(call(suspendedSubject, ISSUER, List.of(AUDIENCE), Instant.now().plus(5, ChronoUnit.MINUTES), suspendedOrganization).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        jdbc.update("INSERT INTO organization_member_access_status(organization_id,username,access_active,created_by,updated_by) " +
                        "VALUES(?,?,false,'TEST','TEST')",
                suspendedOrganization, suspendedActor);
        assertThat(call(suspendedSubject, ISSUER, List.of(AUDIENCE), Instant.now().plus(5, ChronoUnit.MINUTES), suspendedOrganization).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test void rejectsWrongIssuerAudienceAndExpiredTokens() {
        UUID organization = emptyOrganization();
        Instant future = Instant.now().plus(5, ChronoUnit.MINUTES);
        assertThat(call("subject", "https://wrong.example.test", List.of(AUDIENCE), future, organization).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call("subject", ISSUER, List.of("another-api"), future, organization).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call("subject", ISSUER, List.of(AUDIENCE), Instant.now().minus(5, ChronoUnit.MINUTES), organization).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<String> call(String subject, String issuer, List<String> audiences, Instant expiresAt, UUID organization) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token(subject, issuer, audiences, expiresAt));
        headers.set("X-Organization-Id", organization.toString());
        return http.exchange("/api/v1/staff/protocols", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private String token(String subject, String issuer, List<String> audiences, Instant expiresAt) {
        JwtEncoder encoder = new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(new JWKSet(SIGNING_KEY)));
        Instant now = Instant.now();
        Instant issuedAt = expiresAt.isBefore(now) ? expiresAt.minus(5, ChronoUnit.MINUTES) : now.minus(30, ChronoUnit.SECONDS);
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(subject).audience(audiences)
                .issuedAt(issuedAt).expiresAt(expiresAt).build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY_ID).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private UUID organizationWithStaff(String actor) {
        UUID organization = emptyOrganization();
        UUID department = UUID.randomUUID();
        jdbc.update("INSERT INTO departments(id,organization_id,name) VALUES(?,?,?)", department, organization, "Protocolo " + actor);
        jdbc.update("INSERT INTO staff_memberships(username,organization_id,department_id,role_code) VALUES(?,?,?,'STAFF')",
                actor, organization, department);
        return organization;
    }

    private UUID emptyOrganization() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO organizations(id,slug,name) VALUES(?,?,?)", id, "federated-" + id, "Órgão de teste");
        return id;
    }

    private void link(UUID organizationId, String subject, String actor, boolean active) {
        jdbc.update("INSERT INTO user_accounts(username) VALUES(?) ON CONFLICT(username) DO NOTHING", actor);
        jdbc.update("INSERT INTO external_identity_links(organization_id,issuer,subject,username,active,linked_by) " +
                        "VALUES(?,?,?,?,?,'TEST_BOOTSTRAP')",
                organizationId, ISSUER, subject, actor, active);
    }

    private static RSAKey generateSigningKey() {
        try {
            return new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        } catch (JOSEException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static HttpServer startJwksServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] body = new ObjectMapper().writeValueAsBytes(Map.of("keys", List.of(SIGNING_KEY.toPublicJWK().toJSONObject())));
            server.createContext("/jwks", exchange -> respond(exchange, body));
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, byte[] body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
