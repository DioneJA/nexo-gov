import { createHash, createSign, generateKeyPairSync } from 'node:crypto'
import { expect, test, type Page, type Route } from '@playwright/test'

const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 })
const keyId = 'nexogov-e2e-signing-key'
const clientId = 'nexogov-e2e-public-client'
const accessToken = 'nexogov-e2e-access-token'
const organizations = [
  { id: 'org-e2e-001', slug: 'municipio-alfa', name: 'Município Alfa' },
  { id: 'org-e2e-002', slug: 'municipio-beta', name: 'Município Beta' }
]
const organization = organizations[1]

async function persistentBrowserStorage(page: Page) {
  return page.evaluate(() => {
    const read = (store: Storage) => Array.from({ length: store.length }, (_, index) => {
      const key = store.key(index) || ''
      return [key, store.getItem(key)]
    })
    return { local: read(localStorage), session: read(sessionStorage) }
  })
}

function encode(value: string | object) {
  return Buffer.from(typeof value === 'string' ? value : JSON.stringify(value)).toString('base64url')
}

function signedIdToken(issuer: string, nonce: string, now: number) {
  const header = encode({ alg: 'RS256', typ: 'JWT', kid: keyId })
  const payload = encode({
    iss: issuer,
    sub: 'opaque-e2e-subject',
    aud: clientId,
    exp: now + 300,
    iat: now,
    nonce
  })
  const content = `${header}.${payload}`
  const signer = createSign('RSA-SHA256')
  signer.update(content)
  signer.end()
  return `${content}.${signer.sign(privateKey).toString('base64url')}`
}

interface ProviderOptions {
  alterState?: boolean
  alterNonce?: boolean
}

async function installApiMocks(page: Page, organizationResponseDelay = 0) {
  const staffRequests: Array<{ authorization: string; organizationId: string }> = []
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/public/organizations')) {
      if (organizationResponseDelay > 0)
        await new Promise(resolve => setTimeout(resolve, organizationResponseDelay))
      await route.fulfill({ json: organizations })
      return
    }
    if (!url.pathname.includes('/staff/')) {
      await route.fulfill({ status: 404, json: { message: 'Not found' } })
      return
    }

    const headers = route.request().headers()
    staffRequests.push({
      authorization: headers.authorization || '',
      organizationId: headers['x-organization-id'] || ''
    })
    if (headers.authorization !== `Bearer ${accessToken}`) {
      await route.fulfill({ status: 401, json: { message: 'Unauthorized' } })
      return
    }

    const path = url.pathname.replace('/api/v1', '')
    if (path === '/staff/departments' || path === '/staff/protocols' || path === '/staff/organization/organogram') {
      await route.fulfill({ json: [] })
    } else if (path === '/staff/organization/department-management') {
      await route.fulfill({ json: { canManageDepartments: false } })
    } else if (path === '/staff/administrative-processes') {
      await route.fulfill({ json: { items: [], page: 0, size: 25, hasNext: false } })
    } else {
      await route.fulfill({ json: {} })
    }
  })
  return staffRequests
}

async function installProvider(page: Page, options: ProviderOptions = {}) {
  const issuerPort = process.env.NEXOGOV_E2E_OIDC_PORT || '41731'
  const webPort = process.env.NEXOGOV_E2E_WEB_PORT || '9000'
  const issuer = `http://127.0.0.1:${issuerPort}/issuer`
  const redirectUri = `http://localhost:${webPort}/servidor/`
  const cors = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type'
  }
  let authorizationRequest: URL | null = null
  let tokenRequest: URLSearchParams | null = null
  let tokenRequestCount = 0
  let logoutRequest: URL | null = null

  const jwk = publicKey.export({ format: 'jwk' })
  await page.route(`${issuer}/**`, async (route: Route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (request.method() === 'OPTIONS') {
      await route.fulfill({ status: 204, headers: { ...cors, 'Access-Control-Max-Age': '600' } })
      return
    }
    if (url.pathname === '/issuer/.well-known/openid-configuration') {
      await route.fulfill({
        headers: cors,
        json: {
          issuer,
          authorization_endpoint: `${issuer}/authorize`,
          token_endpoint: `${issuer}/token`,
          jwks_uri: `${issuer}/jwks`,
          end_session_endpoint: `${issuer}/logout`,
          response_types_supported: ['code'],
          response_modes_supported: ['query'],
          subject_types_supported: ['public'],
          id_token_signing_alg_values_supported: ['RS256'],
          token_endpoint_auth_methods_supported: ['none'],
          grant_types_supported: ['authorization_code'],
          code_challenge_methods_supported: ['S256']
        }
      })
      return
    }
    if (url.pathname === '/issuer/jwks') {
      await route.fulfill({ headers: cors, json: { keys: [{ ...jwk, kid: keyId, use: 'sig', alg: 'RS256' }] } })
      return
    }
    if (url.pathname === '/issuer/authorize') {
      authorizationRequest = url
      const callback = new URL(redirectUri)
      callback.searchParams.set('code', 'e2e-authorization-code')
      const state = url.searchParams.get('state') || ''
      callback.searchParams.set('state', options.alterState ? `${state}-altered` : state)
      await route.fulfill({ status: 302, headers: { Location: callback.href } })
      return
    }
    if (url.pathname === '/issuer/token') {
      tokenRequestCount++
      tokenRequest = new URLSearchParams(request.postData() || '')
      const nonce = authorizationRequest?.searchParams.get('nonce') || ''
      const now = Math.floor(Date.now() / 1000)
      await route.fulfill({
        headers: cors,
        json: {
          access_token: accessToken,
          token_type: 'Bearer',
          expires_in: 300,
          id_token: signedIdToken(issuer, options.alterNonce ? `${nonce}-altered` : nonce, now)
        }
      })
      return
    }
    if (url.pathname === '/issuer/logout') {
      logoutRequest = url
      const callback = new URL(url.searchParams.get('post_logout_redirect_uri') || redirectUri)
      const state = url.searchParams.get('state')
      if (state) callback.searchParams.set('state', state)
      await route.fulfill({ status: 302, headers: { Location: callback.href } })
      return
    }
    await route.fulfill({ status: 404, headers: cors, body: 'Not found' })
  })

  return {
    issuer,
    get authorizationRequest() { return authorizationRequest },
    get tokenRequest() { return tokenRequest },
    get tokenRequestCount() { return tokenRequestCount },
    get logoutRequest() { return logoutRequest }
  }
}

test.describe('OIDC provider flows', () => {
test.skip(process.env.NEXOGOV_E2E_AUTH_MODE !== 'oidc', 'Execute ./dev oidc-test para rodar os fluxos com provedor simulado.')

test('OIDC @oidc explains disabled organization and sign-in controls while loading', async ({ page }) => {
  await installApiMocks(page, 900)
  await page.goto('/servidor/')

  await expect(page.getByRole('status')).toContainText('Preparando o acesso e carregando organizações')
  await expect(page.getByRole('combobox', { name: 'Organização' })).toBeDisabled()
  await expect(page.getByRole('button', { name: 'Entrar com conta institucional' })).toBeDisabled()
  await expect(page.getByRole('combobox', { name: 'Organização' })).toBeEnabled()
})

test('OIDC @oidc completes Code+PKCE, preserves organization and sends bearer to staff API', async ({ page }) => {
  const staffRequests = await installApiMocks(page)
  const provider = await installProvider(page)

  await page.goto('/servidor/')
  await page.getByRole('combobox', { name: 'Organização' }).click()
  await page.getByRole('option', { name: 'Município Beta' }).click()
  await page.getByRole('button', { name: 'Entrar com conta institucional' }).click()
  await expect(page.getByRole('heading', { name: 'Protocolos' })).toBeVisible()

  const authRequest = provider.authorizationRequest
  const tokenRequest = provider.tokenRequest
  expect(authRequest?.searchParams.get('response_type')).toBe('code')
  expect(authRequest?.searchParams.get('client_id')).toBe(clientId)
  expect(authRequest?.searchParams.get('code_challenge_method')).toBe('S256')
  expect(authRequest?.searchParams.get('code_challenge')).toBeTruthy()
  expect(authRequest?.searchParams.get('nonce')).toBeTruthy()
  const verifier = tokenRequest?.get('code_verifier') || ''
  const derivedChallenge = createHash('sha256').update(verifier).digest('base64url')
  expect(verifier.length).toBeGreaterThanOrEqual(43)
  expect(derivedChallenge).toBe(authRequest?.searchParams.get('code_challenge'))
  expect(tokenRequest?.get('client_id')).toBe(clientId)
  expect(tokenRequest?.has('client_secret')).toBe(false)
  expect(tokenRequest?.has('code_verifier')).toBe(true)
  expect(staffRequests.length).toBeGreaterThanOrEqual(4)
  expect(staffRequests.every(request => request.authorization === `Bearer ${accessToken}`)).toBe(true)
  expect(staffRequests.every(request => request.organizationId === organization.id)).toBe(true)
  expect(JSON.stringify(await persistentBrowserStorage(page))).not.toMatch(/eyJ[a-zA-Z0-9._-]{20,}/)
  expect(JSON.stringify(await persistentBrowserStorage(page))).not.toContain(accessToken)

  await page.getByRole('button', { name: 'Sair da área do servidor' }).click()
  await expect(page.getByRole('button', { name: 'Entrar com conta institucional' })).toBeVisible()
  await expect(page.getByRole('combobox', { name: 'Organização' })).toHaveValue('Município Beta')
  expect(provider.logoutRequest?.searchParams.has('post_logout_redirect_uri')).toBe(true)
  expect(await persistentBrowserStorage(page)).toEqual({ local: [], session: [] })
})

test('OIDC @oidc rejects altered state before exchanging the code', async ({ page }) => {
  const staffRequests = await installApiMocks(page)
  const provider = await installProvider(page, { alterState: true })

  await page.goto('/servidor/')
  await page.getByRole('button', { name: 'Entrar com conta institucional' }).click()
  await expect(page.getByRole('alert')).toContainText('Não foi possível validar o retorno do provedor')
  expect(provider.tokenRequestCount).toBe(0)
  expect(staffRequests).toHaveLength(0)
})

test('OIDC @oidc rejects an ID token with a mismatched nonce before staff API access', async ({ page }) => {
  const staffRequests = await installApiMocks(page)
  const provider = await installProvider(page, { alterNonce: true })

  await page.goto('/servidor/')
  await page.getByRole('button', { name: 'Entrar com conta institucional' }).click()
  await expect(page.getByRole('alert')).toContainText('Não foi possível validar o retorno do provedor')
  expect(provider.tokenRequestCount).toBe(1)
  expect(staffRequests).toHaveLength(0)
})
})
