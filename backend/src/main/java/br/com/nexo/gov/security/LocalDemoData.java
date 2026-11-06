package br.com.nexo.gov.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("local")
public class LocalDemoData implements ApplicationRunner {
    public static final UUID ORG_ALPHA = UUID.fromString("10000000-0000-4000-8000-000000000001");
    public static final UUID ORG_BETA = UUID.fromString("10000000-0000-4000-8000-000000000002");
    public static final UUID ALPHA_PROTOCOL = UUID.fromString("20000000-0000-4000-8000-000000000001");
    public static final UUID ALPHA_WORKS = UUID.fromString("20000000-0000-4000-8000-000000000002");
    public static final UUID BETA_PROTOCOL = UUID.fromString("20000000-0000-4000-8000-000000000003");
    public static final UUID BETA_WORKS = UUID.fromString("20000000-0000-4000-8000-000000000004");

    private final JdbcTemplate jdbc;
    private final String username;

    public LocalDemoData(JdbcTemplate jdbc, @Value("${NEXOGOV_DEMO_USERNAME:servidor}") String username) {
        this.jdbc = jdbc; this.username = username;
    }

    @Override public void run(ApplicationArguments args) {
        jdbc.update("INSERT INTO organizations(id,slug,name) VALUES(?,?,?) ON CONFLICT(id) DO NOTHING", ORG_ALPHA, "municipio-alfa", "Município Alfa (demonstração)");
        jdbc.update("INSERT INTO organizations(id,slug,name) VALUES(?,?,?) ON CONFLICT(id) DO NOTHING", ORG_BETA, "municipio-beta", "Município Beta (demonstração)");
        department(ALPHA_PROTOCOL, ORG_ALPHA, "Protocolo Geral"); department(ALPHA_WORKS, ORG_ALPHA, "Secretaria de Obras");
        department(BETA_PROTOCOL, ORG_BETA, "Protocolo Geral"); department(BETA_WORKS, ORG_BETA, "Secretaria de Obras");
        membership(ORG_ALPHA, ALPHA_PROTOCOL); membership(ORG_ALPHA, ALPHA_WORKS);
        membership(ORG_BETA, BETA_PROTOCOL); membership(ORG_BETA, BETA_WORKS);
    }

    private void department(UUID id, UUID organizationId, String name) {
        jdbc.update("INSERT INTO departments(id,organization_id,name) VALUES(?,?,?) ON CONFLICT(id) DO NOTHING", id, organizationId, name);
    }

    private void membership(UUID organizationId, UUID departmentId) {
        jdbc.update("INSERT INTO staff_memberships(username,organization_id,department_id,role_code) VALUES(?,?,?,'ADMIN') ON CONFLICT DO NOTHING", username, organizationId, departmentId);
    }
}
