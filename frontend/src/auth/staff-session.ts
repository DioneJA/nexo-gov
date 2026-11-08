import {
  InMemoryWebStorage,
  UserManager,
  WebStorageStateStore
} from 'oidc-client-ts'
import {
  isOidcCallbackLocation,
  readOidcConfiguration,
  resolveStaffAuthMode,
  type OidcClientConfiguration,
  type StaffAuthMode
} from './config'

let accessToken: string | null = null
let accessTokenExpiresAt: number | null = null
let basicCredentials: { username: string; password: string } | null = null
let manager: UserManager | null = null
let managerConfiguration: OidcClientConfiguration | null = null

class BrowserMemoryStorage implements Storage {
  private readonly values = new Map<string, string>()

  get length() { return this.values.size }
  clear() { this.values.clear() }
  getItem(key: string) { return this.values.get(String(key)) ?? null }
  key(index: number) { return [...this.values.keys()][index] ?? null }
  removeItem(key: string) { this.values.delete(String(key)) }
  setItem(key: string, value: string) { this.values.set(String(key), String(value)) }
}

function environment() {
  return import.meta.env
}

export function staffAuthMode(currentUrl = window.location.href): StaffAuthMode {
  return resolveStaffAuthMode(environment(), currentUrl, import.meta.env.DEV)
}

function oidcConfiguration(currentUrl = window.location.href) {
  const result = readOidcConfiguration(environment(), currentUrl, import.meta.env.DEV)
  return result.configured ? result.configuration : null
}

function userManager(): UserManager {
  const configuration = oidcConfiguration()
  if (!configuration) throw new Error('OIDC is not configured for this application.')
  if (manager && managerConfiguration?.issuer === configuration.issuer
    && managerConfiguration.clientId === configuration.clientId
    && managerConfiguration.redirectUri === configuration.redirectUri
    && managerConfiguration.scope === configuration.scope)
    return manager

  managerConfiguration = configuration
  manager = new UserManager({
    authority: configuration.issuer,
    client_id: configuration.clientId,
    redirect_uri: configuration.redirectUri,
    post_logout_redirect_uri: configuration.redirectUri,
    response_type: 'code',
    scope: configuration.scope,
    automaticSilentRenew: false,
    monitorSession: false,
    loadUserInfo: false,
    revokeTokensOnSignout: false,
    // Only the short-lived OIDC transaction (state, nonce and PKCE verifier)
    // survives the full-page redirect. No user or token is written here.
    stateStore: new WebStorageStateStore({
      store: window.sessionStorage,
      prefix: 'nexogov.oidc.transaction.'
    }),
    // Access and ID tokens exist only in this JavaScript realm.
    userStore: new WebStorageStateStore({
      store: new InMemoryWebStorage(),
      prefix: 'nexogov.oidc.user.'
    })
  })
  return manager
}

function createNonce() {
  const bytes = crypto.getRandomValues(new Uint8Array(32))
  const binary = Array.from(bytes, value => String.fromCharCode(value)).join('')
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

export function setLocalBasicCredentials(username: string, password: string) {
  accessToken = null
  accessTokenExpiresAt = null
  basicCredentials = { username, password }
}

export function setOidcAccessToken(token: string, expiresAt?: number) {
  basicCredentials = null
  accessToken = token
  accessTokenExpiresAt = expiresAt ? expiresAt * 1000 : null
}

export function staffAuthorizationHeader() {
  if (accessToken && (!accessTokenExpiresAt || Date.now() < accessTokenExpiresAt)) return 'Bearer ' + accessToken
  if (accessTokenExpiresAt && Date.now() >= accessTokenExpiresAt) {
    accessToken = null
    accessTokenExpiresAt = null
  }
  if (!basicCredentials) return null
  return 'Basic ' + btoa(basicCredentials.username + ':' + basicCredentials.password)
}

export function hasStaffAuthorization() {
  return staffAuthorizationHeader() !== null
}

export function clearStaffAuthorization() {
  accessToken = null
  accessTokenExpiresAt = null
  basicCredentials = null
}

export async function beginOidcSignIn(organizationId: string) {
  const currentManager = userManager()
  const configuration = managerConfiguration
  if (!configuration || !isOidcCallbackLocation(configuration.redirectUri, window.location.href))
    throw new Error('A URL de retorno precisa apontar para esta área do servidor.')
  await currentManager.signinRedirect({ state: { organizationId }, nonce: createNonce() })
}

export interface OidcCallbackResult {
  handled: boolean
  organizationId?: string
  signedOut?: boolean
  error?: string
}

function removeCallbackParameters(url: URL) {
  for (const key of ['code', 'state', 'session_state', 'error', 'error_description', 'iss', 'access_token', 'id_token', 'token_type', 'expires_in'])
    url.searchParams.delete(key)
  window.history.replaceState(window.history.state, '', url.pathname + url.search + url.hash)
}

async function clearOidcTransactionState(currentManager: UserManager) {
  const store = currentManager.settings.stateStore
  for (const key of await store.getAllKeys()) await store.remove(key)
}

function organizationFromState(state: unknown) {
  if (!state || typeof state !== 'object') return ''
  const organizationId = (state as { organizationId?: unknown }).organizationId
  return typeof organizationId === 'string' ? organizationId : ''
}

export async function completeOidcCallback(currentUrl = window.location.href): Promise<OidcCallbackResult> {
  const callback = new URL(currentUrl)
  const hasCode = callback.searchParams.has('code')
  const hasError = callback.searchParams.has('error')
  const hasState = callback.searchParams.has('state')
  if (!hasCode && !hasError && !hasState) return { handled: false }

  const configuration = oidcConfiguration(currentUrl)
  if (!configuration) {
    removeCallbackParameters(callback)
    clearStaffAuthorization()
    return {
      handled: true,
      error: 'O login não pôde ser concluído. Confira a configuração da área do servidor.'
    }
  }

  const currentManager = userManager()
  if (!isOidcCallbackLocation(configuration.redirectUri, currentUrl)) {
    removeCallbackParameters(callback)
    clearStaffAuthorization()
    await currentManager.removeUser()
    await clearOidcTransactionState(currentManager)
    return {
      handled: true,
      error: 'O retorno do provedor chegou a um endereço diferente do configurado.'
    }
  }

  const responseUrl = callback.href
  removeCallbackParameters(callback)
  clearStaffAuthorization()
  try {
    if (hasCode || hasError) {
      const user = await currentManager.signinCallback(responseUrl)
      if (!user?.access_token || user.expired)
        throw new Error('The provider did not return a usable access token.')
      setOidcAccessToken(user.access_token, user.expires_at)
      return { handled: true, organizationId: organizationFromState(user.state) }
    }

    const response = await currentManager.signoutCallback(responseUrl)
    return { handled: true, signedOut: true, organizationId: organizationFromState(response?.userState) }
  } catch {
    clearStaffAuthorization()
    await currentManager.removeUser()
    await clearOidcTransactionState(currentManager)
    return {
      handled: true,
      error: 'Não foi possível validar o retorno do provedor. Inicie o acesso novamente.'
    }
  }
}

export async function signOutStaff(organizationId: string) {
  clearStaffAuthorization()
  if (!manager) return
  try {
    const user = await manager.getUser()
    if (user) {
      await manager.signoutRedirect({ state: { organizationId } })
      return
    }
    await manager.removeUser()
  } catch {
    await manager.removeUser()
    throw new Error('A sessão local foi encerrada, mas o provedor não concluiu a saída.')
  }
}
