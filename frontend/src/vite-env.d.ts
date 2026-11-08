/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_URL?: string
  readonly VITE_STAFF_AUTH_MODE?: string
  readonly VITE_OIDC_ISSUER?: string
  readonly VITE_OIDC_CLIENT_ID?: string
  readonly VITE_OIDC_REDIRECT_URI?: string
  readonly VITE_OIDC_SCOPE?: string
}
