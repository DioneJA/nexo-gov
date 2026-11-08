package br.com.nexo.gov.organization;

import br.com.nexo.core.tenant.TenantContext;
import br.com.nexo.gov.organization.OrganizationBrandingDtos.AccentToken;
import br.com.nexo.gov.organization.OrganizationBrandingDtos.OrganizationBrandingInput;
import br.com.nexo.gov.organization.OrganizationBrandingDtos.OrganizationBrandingView;
import br.com.nexo.gov.organization.OrganizationBrandingDtos.PublicOrganizationBrandingView;
import br.com.nexo.gov.protocol.ProtocolExceptions.NotFound;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OrganizationBrandingService {
    private static final AccentToken DEFAULT_ACCENT = AccentToken.ACTION_BLUE;

    private final JdbcTemplate jdbc;
    private final OrganizationAdminService organizationAdmin;

    public OrganizationBrandingService(JdbcTemplate jdbc, OrganizationAdminService organizationAdmin) {
        this.jdbc = jdbc;
        this.organizationAdmin = organizationAdmin;
    }

    public OrganizationBrandingView staff(String actor) {
        UUID organizationId = TenantContext.requireTenantId();
        BrandingState state = load(organizationId);
        return new OrganizationBrandingView(state.displayName(), state.displayNameOverride(), state.accentToken(),
                organizationAdmin.isOrganizationAdmin(actor, organizationId));
    }

    public PublicOrganizationBrandingView publicBranding(String slug) {
        List<PublicOrganizationBrandingView> branding = jdbc.query("""
                        SELECT o.slug,
                               COALESCE(b.display_name_override, o.name) AS display_name,
                               COALESCE(b.accent_token, 'ACTION_BLUE') AS accent_token
                          FROM organizations o
                          LEFT JOIN organization_branding b ON b.organization_id=o.id
                         WHERE o.slug=? AND o.active=true
                        """, (rs, row) -> new PublicOrganizationBrandingView(
                        rs.getString("slug"), rs.getString("display_name"),
                        AccentToken.valueOf(rs.getString("accent_token"))), slug);
        if (branding.isEmpty()) throw new NotFound();
        return branding.getFirst();
    }

    @Transactional
    public OrganizationBrandingView publish(String actor, OrganizationBrandingInput input) {
        UUID organizationId = TenantContext.requireTenantId();
        organizationAdmin.requireOrganizationAdmin(actor, organizationId);
        String override = normalizeDisplayName(input.displayNameOverride());
        AccentToken accent = input.accentToken();

        int changed = jdbc.update("""
                INSERT INTO organization_branding
                    (organization_id, display_name_override, accent_token, created_by, updated_by)
                VALUES(?,?,?,?,?)
                ON CONFLICT (organization_id) DO UPDATE SET
                    display_name_override=EXCLUDED.display_name_override,
                    accent_token=EXCLUDED.accent_token,
                    updated_by=EXCLUDED.updated_by,
                    updated_at=now()
                WHERE organization_branding.display_name_override IS DISTINCT FROM EXCLUDED.display_name_override
                   OR organization_branding.accent_token IS DISTINCT FROM EXCLUDED.accent_token
                """, organizationId, override, accent.name(), actor, actor);
        if (changed == 1) {
            jdbc.update("""
                    INSERT INTO organization_audit_events
                        (organization_id,actor_username,action,entity_type,entity_id,summary)
                    VALUES(?,?,'ORGANIZATION_BRANDING_PUBLISHED','ORGANIZATION_BRANDING',?,?)
                    """, organizationId, actor, organizationId, "Identidade visual da organização atualizada.");
        }
        return staff(actor);
    }

    private BrandingState load(UUID organizationId) {
        List<BrandingState> branding = jdbc.query("""
                        SELECT o.name AS organization_name,
                               b.display_name_override,
                               COALESCE(b.accent_token, 'ACTION_BLUE') AS accent_token
                          FROM organizations o
                          LEFT JOIN organization_branding b ON b.organization_id=o.id
                         WHERE o.id=? AND o.active=true
                        """, (rs, row) -> {
                    String organizationName = rs.getString("organization_name");
                    String override = rs.getString("display_name_override");
                    return new BrandingState(override == null ? organizationName : override, override,
                            AccentToken.valueOf(rs.getString("accent_token")));
                }, organizationId);
        if (branding.isEmpty()) throw new NotFound();
        return branding.getFirst();
    }

    private static String normalizeDisplayName(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record BrandingState(String displayName, String displayNameOverride, AccentToken accentToken) {}
}
