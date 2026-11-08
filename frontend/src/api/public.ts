export interface Organization {
  id: string
  slug: string
  name: string
}

export interface PublicAttachmentRequirement {
  id: string
  name: string
  required: boolean
  allowedExtensions: string[]
  active: boolean
}

export interface PublicProtocolAttachment {
  id: string
  filename: string
  mediaType: string
  sizeBytes: number
  downloadCount: number
  uploadedAt: string
  downloadPath: string
}

export interface PublicProtocolInteractionAttachment {
  id: string
  filename: string
  mediaType: string
  sizeBytes: number
  downloadPath: string
}

export interface PublicProtocolInteraction {
  id: string
  author: string
  department?: string | null
  message: string
  visibility?: 'PUBLIC' | 'INTERNAL'
  createdAt: string
  attachments: PublicProtocolInteractionAttachment[]
}

export interface PublicProtocolSubject {
  id: string
  name: string
  onlineAvailable: boolean
  maximumAttachmentSizeBytes: number
  attachmentRequirements: PublicAttachmentRequirement[]
  participantFields: PublicParticipantField[]
}

export interface PublicParticipantField {
  key: string
  label: string
  inputType: 'TEXT' | 'EMAIL' | 'PHONE' | 'DOCUMENT'
  required: boolean
  order: number
}

export interface PublicParticipantInvitation {
  id: string
  name: string
  token: string
}

export interface PublicProtocolTracking {
  id: string
  protocolNumber: string
  subject: string
  status: string
  createdAt: string
  events: Array<{ action: string; summary: string; createdAt: string }>
  attachments: PublicProtocolAttachment[]
  interactions?: PublicProtocolInteraction[]
  interactionAllowedExtensions?: string[]
  applicantView: boolean
  participantName?: string | null
  participants: Array<{ id: string; name: string; active: boolean; createdAt: string }>
  participantFields: Array<{ key: string; label: string; value: string }>
}

interface PublicRequestOptions {
  headers?: Record<string, string>
  responseType?: 'json' | 'blob'
  timeoutMs?: number
}

interface PublicResponse<T> {
  data: T
  status: number
  headers: Headers
}

const apiBase = (import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1').replace(/\/+$/, '')

function requestUrl(path: string) {
  if (/^https?:\/\//i.test(path)) return path
  return `${apiBase}/${path.replace(/^\/+/, '')}`
}

async function request<T>(method: 'GET' | 'POST', path: string, body?: unknown, options: PublicRequestOptions = {}): Promise<PublicResponse<T>> {
  const headers = new Headers(options.headers)
  const init: RequestInit = { method, headers, signal: AbortSignal.timeout(options.timeoutMs ?? 15_000) }
  if (body !== undefined) {
    if (body instanceof FormData) {
      init.body = body
    } else {
      headers.set('Content-Type', 'application/json')
      init.body = JSON.stringify(body)
    }
  }

  const response = await fetch(requestUrl(path), init)
  const data = options.responseType === 'blob'
    ? await response.blob()
    : response.headers.get('content-type')?.includes('application/json')
      ? await response.json()
      : await response.text()
  if (!response.ok) {
    throw { response: { status: response.status, data } }
  }
  return { data: data as T, status: response.status, headers: response.headers }
}

export const publicApi = {
  get<T>(path: string, options?: PublicRequestOptions) {
    return request<T>('GET', path, undefined, options)
  },
  post<T>(path: string, body?: unknown, options?: PublicRequestOptions) {
    return request<T>('POST', path, body, options)
  }
}
