import { createApiClient } from './base'
import { staffAuthorizationHeader } from '../auth/staff-session'

export interface Department { id: string; name: string }
export interface DepartmentMember {
  username: string
  fullName: string | null
  positionTitle: string | null
  registrationDate: string | null
  registrationDateSource: 'IMPORTED' | 'UNKNOWN' | null
  lastOnlineAt: string | null
  presenceStatus: 'ONLINE' | 'OFFLINE' | 'NOT_OBSERVED'
  roleCode: string
}
export interface OrganogramNode {
  id: string
  name: string
  parentId: string | null
  staff: DepartmentMember[]
  children: OrganogramNode[]
}
export interface DepartmentManagementCapabilities { canManageDepartments: boolean }
export interface OrganizationMember {
  username: string
  fullName: string | null
  positionTitle: string | null
  accessStatus: 'ACTIVE' | 'SUSPENDED' | 'INACTIVE'
  organizationRoles: string[]
  departmentRoles: string[]
}
export interface OrganizationMemberAccessResult {
  username: string
  accessStatus: 'ACTIVE' | 'SUSPENDED' | 'INACTIVE'
  changed: boolean
}
export interface AttachmentRequirement {
  id: string
  name: string
  required: boolean
  allowedExtensions: string[]
  active: boolean
}
export type ParticipantIdentityFieldType = 'TEXT' | 'EMAIL' | 'PHONE' | 'DOCUMENT'
export interface ParticipantIdentityField {
  id: string
  subjectId: string
  key: string
  label: string
  inputType: ParticipantIdentityFieldType
  required: boolean
  order: number
}
export interface ParticipantIdentityFieldInput {
  key: string
  label: string
  inputType: ParticipantIdentityFieldType
  required: boolean
  order: number
}
export interface ProtocolAttachment {
  id: string
  filename: string
  mediaType: string
  sizeBytes: number
  downloadCount: number
  uploadedAt: string
  downloadPath: string
}
export interface ProtocolInteractionAttachment {
  id: string
  filename: string
  mediaType: string
  sizeBytes: number
  downloadPath: string
}
export type ProtocolInteractionVisibility = 'PUBLIC' | 'INTERNAL'
export interface ProtocolInteraction {
  id: string
  author: string
  department?: string | null
  message: string
  visibility: ProtocolInteractionVisibility
  createdAt: string
  attachments: ProtocolInteractionAttachment[]
}
export type ProtocolReadStatus = 'UNREAD_BY_DEPARTMENT' | 'READ_BY_OTHER' | 'READ_BY_ME'
export interface ProtocolReadReceipt {
  protocolId: string
  departmentId: string
  readAt: string
  readStatus: ProtocolReadStatus
}
export interface StaffProtocolSubject {
  id: string
  name: string
  onlineAvailable: boolean
  destinationDepartmentId?: string | null
  destinationDepartmentName?: string | null
  automaticDeadlineDays?: number | null
  attachmentRequirements: AttachmentRequirement[]
}
export interface Protocol {
  id: string
  protocolNumber: string
  subject: string
  applicantName: string
  status: string
  readStatus: ProtocolReadStatus
  assignedDepartmentId?: string
  assignedDepartmentName?: string
  createdAt: string
  updatedAt: string
  attachmentCount?: number
  interactionAllowedExtensions?: string[]
}

export function organizationHeaders(organizationId: string) {
  return { 'X-Organization-Id': organizationId }
}

export const staffApi = createApiClient()
staffApi.interceptors.request.use(config => {
  const authorization = staffAuthorizationHeader()
  if (!authorization) return Promise.reject(new Error('No staff authentication is active.'))
  config.headers.Authorization = authorization
  return config
})
