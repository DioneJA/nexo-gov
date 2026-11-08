package br.com.nexo.gov.security;

import br.com.nexo.core.identity.ExternalIdentity;

import java.util.Optional;
import java.util.UUID;

/** Resolves an external identity to its local actor within the selected organization. */
public interface OrganizationExternalIdentityResolver {
    Optional<String> resolveActorId(ExternalIdentity identity, UUID organizationId);
}
