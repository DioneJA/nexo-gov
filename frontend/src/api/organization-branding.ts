export const organizationAccentTokens = [
  'NEXO_NAVY',
  'NEXO_BLUE',
  'ACTION_BLUE',
  'SUCCESS_GREEN'
] as const

export type OrganizationAccentToken = typeof organizationAccentTokens[number]

export interface PublicOrganizationBranding {
  organizationSlug: string
  displayName: string
  accentToken: OrganizationAccentToken
}

export interface StaffOrganizationBranding {
  displayName: string
  displayNameOverride: string | null
  accentToken: OrganizationAccentToken
  canManageBranding: boolean
}

export const organizationAccentOptions: Array<{ label: string; value: OrganizationAccentToken }> = [
  { label: 'Azul institucional', value: 'NEXO_BLUE' },
  { label: 'Azul de ação', value: 'ACTION_BLUE' },
  { label: 'Azul-marinho NEXO', value: 'NEXO_NAVY' },
  { label: 'Verde de confirmação', value: 'SUCCESS_GREEN' }
]

export function organizationAccentClass(token?: OrganizationAccentToken | null) {
  return `branding-accent-${token && organizationAccentTokens.includes(token) ? token : 'ACTION_BLUE'}`
}
