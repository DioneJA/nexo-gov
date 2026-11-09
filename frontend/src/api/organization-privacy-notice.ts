export interface PublicOrganizationPrivacyNotice {
  explanatoryText: string | null
  privacyPolicyUrl: string | null
}

export interface StaffOrganizationPrivacyNotice extends PublicOrganizationPrivacyNotice {
  canManagePrivacyNotice: boolean
}
