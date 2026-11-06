import axios from 'axios'

export const api = axios.create({ baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1', timeout: 15000 })

export interface Organization { id: string; slug: string; name: string }
export interface Department { id: string; name: string }
export interface Protocol {
  id: string; protocolNumber: string; subject: string; applicantName: string; status: string
  assignedDepartmentId?: string; assignedDepartmentName?: string; createdAt: string; updatedAt: string
}

export function staffHeaders(organizationId: string, username: string, password: string) {
  return { 'X-Organization-Id': organizationId, Authorization: `Basic ${btoa(`${username}:${password}`)}` }
}
