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
                    "SELECT count(*) FROM staff_memberships sm JOIN organizations o ON o.id=sm.organization_id " +
                            "WHERE sm.username=? AND sm.organization_id=? AND sm.active=true AND o.active=true",
                    Integer.class, actor, tenant);
            return memberships != null && memberships > 0;
        })) {
            chain.doFilter(request, response);
        } catch (TenantAccessDeniedException denied) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
    }
}
