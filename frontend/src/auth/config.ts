export type StaffAuthMode = 'local-basic' | 'oidc' | 'unavailable'

export interface OidcEnvironment {
  VITE_STAFF_AUTH_MODE?: string
  VITE_OIDC_ISSUER?: string
  VITE_OIDC_CLIENT_ID?: string
  VITE_OIDC_REDIRECT_URI?: string
  VITE_OIDC_SCOPE?: string
}

export interface OidcClientConfiguration {
  issuer: string
  clientId: string
  redirectUri: string
  scope: string
}

export type OidcConfigurationResult =
  | { configured: true; configuration: OidcClientConfiguration }
  | { configured: false; reason: 'missing' | 'invalid' | 'insecure' }

function isLoopback(hostname: string) {
  return hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '[::1]'
}

export function readOidcConfiguration(
  environment: OidcEnvironment,
  currentUrl: string,
  development: boolean
): OidcConfigurationResult {
  const issuerValue = environment.VITE_OIDC_ISSUER?.trim()
  const clientId = environment.VITE_OIDC_CLIENT_ID?.trim()
  const redirectValue = environment.VITE_OIDC_REDIRECT_URI?.trim()
  const scope = environment.VITE_OIDC_SCOPE?.trim() || 'openid profile'

  if (!issuerValue || !clientId || !redirectValue)
    return { configured: false, reason: 'missing' }

  try {
    const current = new URL(currentUrl)
    const issuer = new URL(issuerValue)
    const redirect = new URL(redirectValue)
    const localHttpAllowed = development && isLoopback(issuer.hostname) && isLoopback(redirect.hostname)

    if ((issuer.protocol !== 'https:' || redirect.protocol !== 'https:') && !localHttpAllowed)
      return { configured: false, reason: 'insecure' }
    if (issuer.username || issuer.password || issuer.search || issuer.hash)
      return { configured: false, reason: 'invalid' }
    if (redirect.username || redirect.password || redirect.search || redirect.hash)
      return { configured: false, reason: 'invalid' }
    if (redirect.origin !== current.origin)
      return { configured: false, reason: 'invalid' }
    const requestedScopes = scope.split(/\s+/)
    if (!requestedScopes.includes('openid') || requestedScopes.includes('offline_access'))
      return { configured: false, reason: 'invalid' }

    return {
      configured: true,
      configuration: {
        issuer: issuer.href.replace(/\/$/, ''),
        clientId,
        redirectUri: redirect.href,
        scope
      }
    }
  } catch {
    return { configured: false, reason: 'invalid' }
  }
}

export function resolveStaffAuthMode(
  environment: OidcEnvironment,
  currentUrl: string,
  development: boolean
): StaffAuthMode {
  if (development && environment.VITE_STAFF_AUTH_MODE === 'local-basic')
    return 'local-basic'
  return readOidcConfiguration(environment, currentUrl, development).configured
    ? 'oidc'
    : 'unavailable'
}

export function isOidcCallbackLocation(redirectUri: string, callbackUrl: string) {
  try {
    const expected = new URL(redirectUri)
    const actual = new URL(callbackUrl)
    return expected.origin === actual.origin
      && expected.pathname === actual.pathname
      && !expected.search
      && !expected.hash
  } catch {
    return false
  }
}
