package br.com.nexo.gov.security;

import br.com.nexo.core.identity.ExternalIdentity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Profile("!local")
public class PersistentIdentityFilter extends OncePerRequestFilter {
    private final OrganizationExternalIdentityResolver identities;

    public PersistentIdentityFilter(OrganizationExternalIdentityResolver identities) {
        this.identities = identities;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/v1/public/")
                || request.getRequestURI().startsWith("/actuator/")
                || request.getRequestURI().startsWith("/v3/api-docs/")
                || request.getRequestURI().startsWith("/swagger-ui");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String issuer = jwtAuthentication.getToken().getIssuer() == null
                ? null : jwtAuthentication.getToken().getIssuer().toString();
        String subject = jwtAuthentication.getToken().getSubject();
        if (issuer == null || subject == null || issuer.isBlank() || subject.isBlank()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        UUID organizationId;
        try {
            organizationId = UUID.fromString(request.getHeader("X-Organization-Id"));
        } catch (RuntimeException exception) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "X-Organization-Id ausente ou inválido");
            return;
        }

        // The header selects the identity-link namespace only. TenantMembershipFilter
        // still proves active organization membership and suspension status.
        var actorId = identities.resolveActorId(new ExternalIdentity(issuer, subject), organizationId);
        if (actorId.isEmpty()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // External roles/scopes do not grant Gov permissions. Tenant and resource access
        // are checked by application-side memberships and domain authorization.
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(actorId.get(), null, AuthorityUtils.NO_AUTHORITIES));
        chain.doFilter(request, response);
    }
}
