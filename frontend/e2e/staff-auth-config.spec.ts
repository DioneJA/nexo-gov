import { expect, test } from '@playwright/test'
import { isOidcCallbackLocation, readOidcConfiguration, resolveStaffAuthMode } from '../src/auth/config'

test('OIDC @oidc fails closed in production when configuration is missing', () => {
  expect(resolveStaffAuthMode({}, 'https://portal.example.gov/servidor/', false)).toBe('unavailable')
  expect(resolveStaffAuthMode({ VITE_STAFF_AUTH_MODE: 'local-basic' }, 'https://portal.example.gov/servidor/', false)).toBe('unavailable')
})

test('OIDC @oidc accepts only the configured callback origin and path', () => {
  const expected = 'https://portal.example.gov/servidor/'
  expect(isOidcCallbackLocation(expected, 'https://portal.example.gov/servidor/?code=a&state=b')).toBe(true)
  expect(isOidcCallbackLocation(expected, 'https://portal.example.gov/cidadao/?code=a&state=b')).toBe(false)
  expect(isOidcCallbackLocation(expected, 'https://other.example.gov/servidor/?code=a&state=b')).toBe(false)
})

test('OIDC @oidc requires an OpenID scope and rejects refresh-token scope', () => {
  const base = {
    VITE_OIDC_ISSUER: 'https://id.example.gov',
    VITE_OIDC_CLIENT_ID: 'nexogov-public-client',
    VITE_OIDC_REDIRECT_URI: 'https://portal.example.gov/servidor/'
  }
  expect(readOidcConfiguration({ ...base, VITE_OIDC_SCOPE: 'profile email' }, 'https://portal.example.gov/servidor/', false).configured).toBe(false)
  expect(readOidcConfiguration({ ...base, VITE_OIDC_SCOPE: 'openid profile offline_access' }, 'https://portal.example.gov/servidor/', false).configured).toBe(false)
})
