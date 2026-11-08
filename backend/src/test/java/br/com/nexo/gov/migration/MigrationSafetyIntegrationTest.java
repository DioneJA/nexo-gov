package br.com.nexo.gov.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@Testcontainers
class MigrationSafetyIntegrationTest {
    private static final String ORGANIZATION_ID = "10000000-0000-4000-8000-000000000001";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.4-alpine");

    @Test
    void refusesCaseInsensitiveLegacyDepartmentDuplicatesWithoutChangingRows() throws Exception {
        flyway(null).clean();
        flyway("1").migrate();
        try (Connection connection = connection();
             PreparedStatement organization = connection.prepareStatement(
                     "INSERT INTO organizations(id,slug,name) VALUES(?,?,?)")) {
            organization.setObject(1, UUID.fromString(ORGANIZATION_ID));
            organization.setString(2, "migration-safety");
            organization.setString(3, "Migration Safety");
            organization.executeUpdate();
            try (Statement departments = connection.createStatement()) {
                departments.executeUpdate("INSERT INTO departments(id,organization_id,name) VALUES " +
                        "('20000000-0000-4000-8000-000000000001','" + ORGANIZATION_ID + "','Obras')," +
                        "('20000000-0000-4000-8000-000000000002','" + ORGANIZATION_ID + "','obras')");
            }
        }

        Throwable migrationFailure = catchThrowable(() -> flyway(null).migrate());
        assertThat(migrationFailure).isInstanceOf(FlywayException.class);
        assertThat(causeMessages(migrationFailure)).contains("existing duplicates require manual reconciliation");
        assertThat(causeMessages(migrationFailure)).contains("20000000-0000-4000-8000-000000000001");
        assertThat(causeMessages(migrationFailure)).contains("20000000-0000-4000-8000-000000000002");
        assertThat(causeMessages(migrationFailure)).contains("Obras");
        assertThat(causeMessages(migrationFailure)).contains("obras");

        try (Connection connection = connection();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT count(*) FROM departments WHERE organization_id=?")) {
            query.setObject(1, UUID.fromString(ORGANIZATION_ID));
            try (ResultSet result = query.executeQuery()) {
                result.next();
                assertThat(result.getInt(1)).isEqualTo(2);
            }
            try (PreparedStatement names = connection.prepareStatement(
                    "SELECT name FROM departments WHERE organization_id=? ORDER BY id")) {
                names.setObject(1, UUID.fromString(ORGANIZATION_ID));
                try (ResultSet result = names.executeQuery()) {
                    List<String> storedNames = new ArrayList<>();
                    while (result.next()) storedNames.add(result.getString(1));
                    assertThat(storedNames).containsExactly("Obras", "obras");
                }
            }
        }
    }

    @Test
    void refusesTrimmedDepartmentDuplicatesBeforeReplacingTheLegacyIndex() throws Exception {
        flyway(null).clean();
        flyway("25").migrate();
        try (Connection connection = connection(); Statement sql = connection.createStatement()) {
            sql.executeUpdate("DROP INDEX departments_organization_name_case_insensitive_idx");
            sql.executeUpdate("CREATE UNIQUE INDEX departments_organization_name_case_insensitive_idx " +
                    "ON departments (organization_id, lower(name))");
            sql.executeUpdate("INSERT INTO organizations(id,slug,name) VALUES " +
                    "('10000000-0000-4000-8000-000000000041','trimmed-index-safety','Trimmed Index Safety')");
            sql.executeUpdate("INSERT INTO departments(id,organization_id,name) VALUES " +
                    "('20000000-0000-4000-8000-000000000041','10000000-0000-4000-8000-000000000041',' Public Works')," +
                    "('20000000-0000-4000-8000-000000000042','10000000-0000-4000-8000-000000000041','public works ')");
        }

        Throwable migrationFailure = catchThrowable(() -> flyway(null).migrate());
        assertThat(migrationFailure).isInstanceOf(FlywayException.class);
        assertThat(causeMessages(migrationFailure))
                .contains("trimmed, case-insensitive department name uniqueness")
                .contains("20000000-0000-4000-8000-000000000041")
                .contains("20000000-0000-4000-8000-000000000042")
                .contains("No records were changed");

        try (Connection connection = connection(); Statement sql = connection.createStatement()) {
            try (ResultSet rows = sql.executeQuery(
                    "SELECT name FROM departments WHERE id IN " +
                            "('20000000-0000-4000-8000-000000000041','20000000-0000-4000-8000-000000000042') " +
                            "ORDER BY id")) {
                List<String> names = new ArrayList<>();
                while (rows.next()) names.add(rows.getString(1));
                assertThat(names).containsExactly(" Public Works", "public works ");
            }
            try (ResultSet oldIndex = sql.executeQuery(
                    "SELECT indexdef FROM pg_indexes WHERE schemaname='public' " +
                            "AND indexname='departments_organization_name_case_insensitive_idx'")) {
                assertThat(oldIndex.next()).isTrue();
                assertThat(oldIndex.getString(1)).contains("lower((name)::text)").doesNotContain("btrim");
            }
            try (ResultSet migration = sql.executeQuery(
                    "SELECT count(*) FROM flyway_schema_history WHERE version='26' AND success=true")) {
                migration.next();
                assertThat(migration.getInt(1)).isZero();
            }
        }
    }

    @Test
    void replacesTheLegacyDepartmentIndexAndRejectsTrimmedCaseDuplicates() throws Exception {
        flyway(null).clean();
        flyway("25").migrate();
        try (Connection connection = connection(); Statement sql = connection.createStatement()) {
            sql.executeUpdate("DROP INDEX departments_organization_name_case_insensitive_idx");
            sql.executeUpdate("CREATE UNIQUE INDEX departments_organization_name_case_insensitive_idx " +
                    "ON departments (organization_id, lower(name))");
            sql.executeUpdate("INSERT INTO organizations(id,slug,name) VALUES " +
                    "('10000000-0000-4000-8000-000000000051','trimmed-index-success','Trimmed Index Success')");
            sql.executeUpdate("INSERT INTO departments(id,organization_id,name) VALUES " +
                    "('20000000-0000-4000-8000-000000000051','10000000-0000-4000-8000-000000000051','Finance')");
        }

        flyway(null).migrate();

        try (Connection connection = connection(); Statement sql = connection.createStatement()) {
            try (ResultSet index = sql.executeQuery(
                    "SELECT indexdef FROM pg_indexes WHERE schemaname='public' " +
                            "AND indexname='departments_organization_name_case_insensitive_idx'")) {
                assertThat(index.next()).isTrue();
                assertThat(index.getString(1)).contains("lower").contains("btrim");
            }
            Throwable duplicateInsert = catchThrowable(() -> sql.executeUpdate(
                    "INSERT INTO departments(id,organization_id,name) VALUES " +
                            "('20000000-0000-4000-8000-000000000052','10000000-0000-4000-8000-000000000051',' finance ' )"));
            assertThat(duplicateInsert).isNotNull();
            assertThat(causeMessages(duplicateInsert)).contains("departments_organization_name_case_insensitive_idx");
            try (ResultSet count = sql.executeQuery(
                    "SELECT count(*) FROM departments WHERE organization_id='10000000-0000-4000-8000-000000000051'")) {
                count.next();
                assertThat(count.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void importsOnlyUnambiguousKnownRegistrationDatesAndLeavesOtherHistoryUnknown() throws Exception {
        flyway(null).clean();
        flyway("5").migrate();
        try (Connection connection = connection(); Statement sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO organizations(id,slug,name) VALUES " +
                    "('10000000-0000-4000-8000-000000000011','identity-date-a','A')," +
                    "('10000000-0000-4000-8000-000000000012','identity-date-b','B')," +
                    "('10000000-0000-4000-8000-000000000013','identity-date-c','C')," +
                    "('10000000-0000-4000-8000-000000000014','identity-date-d','D')");
            sql.executeUpdate("INSERT INTO staff_profiles(organization_id,username,registration_date,registration_date_source) VALUES " +
                    "('10000000-0000-4000-8000-000000000011','same-date-user','2024-02-03','IMPORTED')," +
                    "('10000000-0000-4000-8000-000000000012','same-date-user','2024-02-03','IMPORTED')," +
                    "('10000000-0000-4000-8000-000000000013','conflicting-user','2024-02-03','IMPORTED')," +
                    "('10000000-0000-4000-8000-000000000014','conflicting-user','2024-02-04','IMPORTED')," +
                    "('10000000-0000-4000-8000-000000000011','unknown-user',NULL,'UNKNOWN')");
        }

        flyway(null).migrate();

        try (Connection connection = connection()) {
            assertAccountDate(connection, "same-date-user", "2024-02-03", "IMPORTED");
            assertAccountDate(connection, "conflicting-user", null, "UNKNOWN");
            assertAccountDate(connection, "unknown-user", null, "UNKNOWN");
        }
    }

    @Test
    void scopesEmptyExternalIdentityLinksPerOrganizationWithoutBackfill() throws Exception {
        flyway(null).clean();
        flyway("8").migrate();

        UUID organizationA = UUID.fromString("10000000-0000-4000-8000-000000000021");
        UUID organizationB = UUID.fromString("10000000-0000-4000-8000-000000000022");
        UUID departmentA = UUID.fromString("20000000-0000-4000-8000-000000000021");
        UUID departmentB = UUID.fromString("20000000-0000-4000-8000-000000000022");
        try (Connection connection = connection(); Statement sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO organizations(id,slug,name) VALUES " +
                    "('" + organizationA + "','identity-scope-a','Identity Scope A')," +
                    "('" + organizationB + "','identity-scope-b','Identity Scope B')");
            sql.executeUpdate("INSERT INTO departments(id,organization_id,name) VALUES " +
                    "('" + departmentA + "','" + organizationA + "','A')," +
                    "('" + departmentB + "','" + organizationB + "','B')");
            sql.executeUpdate("INSERT INTO staff_memberships(username,organization_id,department_id,role_code) VALUES " +
                    "('shared-legacy-user','" + organizationA + "','" + departmentA + "','STAFF')," +
                    "('shared-legacy-user','" + organizationB + "','" + departmentB + "','STAFF')");
            sql.executeUpdate("INSERT INTO user_accounts(username) VALUES ('shared-legacy-user')");
        }

        flyway(null).migrate();

        try (Connection connection = connection(); Statement sql = connection.createStatement()) {
            try (ResultSet columns = sql.executeQuery(
                    "SELECT count(*) FROM information_schema.columns " +
                            "WHERE table_schema=current_schema() AND table_name='external_identity_links' " +
                            "AND column_name='organization_id' AND is_nullable='NO'")) {
                columns.next();
                assertThat(columns.getInt(1)).isEqualTo(1);
            }
            try (ResultSet activeColumn = sql.executeQuery(
                    "SELECT count(*) FROM information_schema.columns " +
                            "WHERE table_schema=current_schema() AND table_name='external_identity_links' " +
                            "AND column_name='active' AND is_nullable='NO' AND column_default='false'")) {
                activeColumn.next();
                assertThat(activeColumn.getInt(1)).isEqualTo(1);
            }
            try (ResultSet links = sql.executeQuery("SELECT count(*) FROM external_identity_links")) {
                links.next();
                assertThat(links.getInt(1)).isZero();
            }
            try (ResultSet memberships = sql.executeQuery("SELECT count(*) FROM staff_memberships WHERE username='shared-legacy-user'")) {
                memberships.next();
                assertThat(memberships.getInt(1)).isEqualTo(2);
            }
            try (ResultSet accounts = sql.executeQuery("SELECT count(*) FROM user_accounts WHERE username='shared-legacy-user'")) {
                accounts.next();
                assertThat(accounts.getInt(1)).isEqualTo(1);
            }

            sql.executeUpdate("INSERT INTO external_identity_links(organization_id,issuer,subject,username,active,linked_by) VALUES " +
                    "('" + organizationA + "','https://id.example.test','same-subject','shared-legacy-user',true,'TEST')," +
                    "('" + organizationB + "','https://id.example.test','same-subject','shared-legacy-user',false,'TEST')");
            try (ResultSet scopedLinks = sql.executeQuery(
                    "SELECT count(*) FROM external_identity_links WHERE issuer='https://id.example.test' AND subject='same-subject'")) {
                scopedLinks.next();
                assertThat(scopedLinks.getInt(1)).isEqualTo(2);
            }
            try (ResultSet linkStates = sql.executeQuery(
                    "SELECT bool_and(active) FROM external_identity_links " +
                            "WHERE issuer='https://id.example.test' AND subject='same-subject' AND organization_id='" + organizationA + "'")) {
                linkStates.next();
                assertThat(linkStates.getBoolean(1)).isTrue();
            }
            try (ResultSet linkStates = sql.executeQuery(
                    "SELECT bool_and(active) FROM external_identity_links " +
                            "WHERE issuer='https://id.example.test' AND subject='same-subject' AND organization_id='" + organizationB + "'")) {
                linkStates.next();
                assertThat(linkStates.getBoolean(1)).isFalse();
            }
        }
    }

    @Test
    void blocksGlobalExternalIdentityLinksWithoutChangingLegacyData() throws Exception {
        flyway(null).clean();
        flyway("8").migrate();

        UUID organization = UUID.fromString("10000000-0000-4000-8000-000000000031");
        UUID department = UUID.fromString("20000000-0000-4000-8000-000000000031");
        try (Connection connection = connection(); Statement sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO organizations(id,slug,name) VALUES " +
                    "('" + organization + "','identity-legacy-link','Identity Legacy Link')");
            sql.executeUpdate("INSERT INTO departments(id,organization_id,name) VALUES " +
                    "('" + department + "','" + organization + "','Legacy')");
            sql.executeUpdate("INSERT INTO staff_memberships(username,organization_id,department_id,role_code) VALUES " +
                    "('legacy-linked-user','" + organization + "','" + department + "','STAFF')");
            sql.executeUpdate("INSERT INTO user_accounts(username) VALUES ('legacy-linked-user')");
            sql.executeUpdate("INSERT INTO external_identity_links(issuer,subject,username,linked_by) " +
                    "VALUES ('https://id.example.test','legacy-subject','legacy-linked-user','LEGACY_TEST')");
        }

        Throwable migrationFailure = catchThrowable(() -> flyway(null).migrate());
        assertThat(migrationFailure).isInstanceOf(FlywayException.class);
        assertThat(causeMessages(migrationFailure))
                .contains("globally scoped external identity link(s) require organization reconciliation")
                .contains("V9 does not convert or remove legacy links");

        try (Connection connection = connection(); Statement sql = connection.createStatement()) {
            try (ResultSet legacyLinks = sql.executeQuery(
                    "SELECT count(*) FROM external_identity_links WHERE issuer='https://id.example.test' " +
                            "AND subject='legacy-subject' AND username='legacy-linked-user'")) {
                legacyLinks.next();
                assertThat(legacyLinks.getInt(1)).isEqualTo(1);
            }
            try (ResultSet tenantColumn = sql.executeQuery(
                    "SELECT count(*) FROM information_schema.columns " +
                            "WHERE table_schema=current_schema() AND table_name='external_identity_links' " +
                            "AND column_name IN ('organization_id','active')")) {
                tenantColumn.next();
                assertThat(tenantColumn.getInt(1)).isZero();
            }
            try (ResultSet memberships = sql.executeQuery(
                    "SELECT count(*) FROM staff_memberships WHERE username='legacy-linked-user'")) {
                memberships.next();
                assertThat(memberships.getInt(1)).isEqualTo(1);
            }
            try (ResultSet accounts = sql.executeQuery(
                    "SELECT count(*) FROM user_accounts WHERE username='legacy-linked-user'")) {
                accounts.next();
                assertThat(accounts.getInt(1)).isEqualTo(1);
            }
        }
    }

    private static Flyway flyway(String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false);
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static String causeMessages(Throwable failure) {
        List<String> messages = new ArrayList<>();
        for (Throwable cause = failure; cause != null; cause = cause.getCause())
            if (cause.getMessage() != null) messages.add(cause.getMessage());
        return String.join("\n", messages);
    }

    private static void assertAccountDate(Connection connection, String username, String expectedDate, String expectedSource)
            throws Exception {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT registration_date::text,registration_date_source FROM user_accounts WHERE username=?")) {
            query.setString(1, username);
            try (ResultSet result = query.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("registration_date")).isEqualTo(expectedDate);
                assertThat(result.getString("registration_date_source")).isEqualTo(expectedSource);
            }
        }
    }
}
