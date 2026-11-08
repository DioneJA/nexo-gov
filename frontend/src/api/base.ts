import axios from 'axios'

export function createApiClient() {
  return axios.create({
    baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1',
    timeout: 15000
  })
}
