package br.com.nexo.gov.security;

import br.com.nexo.core.identity.ExternalIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcExternalIdentityResolver implements OrganizationExternalIdentityResolver {
    private final JdbcTemplate jdbc;

    public JdbcExternalIdentityResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override public Optional<String> resolveActorId(ExternalIdentity identity, UUID organizationId) {
        List<String> actors = jdbc.query(
                "SELECT l.username FROM external_identity_links l " +
                        "JOIN user_accounts u ON u.username=l.username " +
                        "WHERE l.organization_id=? AND l.issuer=? AND l.subject=? " +
                        "AND l.active=true AND u.active=true",
                (rs, rowNum) -> rs.getString("username"), organizationId, identity.issuer(), identity.subject());
        return actors.stream().findFirst();
    }
}
