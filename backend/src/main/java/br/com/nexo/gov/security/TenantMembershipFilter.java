package br.com.nexo.gov.security;

import br.com.nexo.core.tenant.TenantContext;
import br.com.nexo.core.tenant.TenantAccessDeniedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class TenantMembershipFilter extends OncePerRequestFilter {
    public static final String ORGANIZATION_ID_ATTRIBUTE = TenantMembershipFilter.class.getName() + ".organizationId";
    private final JdbcTemplate jdbc;

    public TenantMembershipFilter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/v1/public/")
                || request.getRequestURI().startsWith("/actuator/")
                || request.getRequestURI().startsWith("/v3/api-docs/")
                || request.getRequestURI().startsWith("/swagger-ui");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        UUID organizationId;
        try { organizationId = UUID.fromString(request.getHeader("X-Organization-Id")); }
        catch (RuntimeException exception) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "X-Organization-Id ausente ou inválido");
            return;
        }

        request.setAttribute(ORGANIZATION_ID_ATTRIBUTE, organizationId);
        try (TenantContext.Scope ignored = TenantContext.openVerified(organizationId, authentication.getName(), (actor, tenant) -> {
            Integer memberships = jdbc.queryForObject(
                    "SELECT count(*) FROM organizations o WHERE o.id=? AND o.active=true AND (" +
                            "NOT EXISTS (SELECT 1 FROM organization_member_access_status access_status " +
                            "WHERE access_status.organization_id=o.id AND access_status.username=? AND access_status.access_active=false) AND (" +
                            "EXISTS (SELECT 1 FROM staff_memberships sm WHERE sm.username=? AND sm.organization_id=o.id AND sm.active=true) OR " +
                            "EXISTS (SELECT 1 FROM organization_memberships om WHERE om.username=? AND om.organization_id=o.id AND om.active=true AND om.role_code='ADMIN')))",
                    Integer.class, tenant, actor, actor, actor);
            return memberships != null && memberships > 0;
        })) {
            recordPresence(organizationId, authentication.getName());
            chain.doFilter(request, response);
        } catch (TenantAccessDeniedException denied) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    private void recordPresence(UUID organizationId, String actor) {
        jdbc.update("""
                INSERT INTO staff_profiles(organization_id,username,last_online_at)
                VALUES(?,?,now())
                ON CONFLICT (organization_id,username) DO UPDATE SET
                    last_online_at=now(),
                    updated_at=now()
                WHERE staff_profiles.last_online_at IS NULL
                   OR staff_profiles.last_online_at < now() - interval '30 seconds'
                """, organizationId, actor);
    }
}
