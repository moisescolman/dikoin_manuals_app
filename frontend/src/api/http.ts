import axios from 'axios'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'

export const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
})

export function toBackendUrl(value?: string | null) {
  if (!value) return ''
  if (/^(https?:|data:|blob:)/.test(value)) return value

  const apiUrl = new URL(API_BASE_URL)
  if (value.startsWith('/')) {
    return `${apiUrl.origin}${value}`
  }

  return `${API_BASE_URL.replace(/\/$/, '')}/${value.replace(/^\//, '')}`
}

http.interceptors.request.use((config) => {
  const raw = localStorage.getItem('dikoin_user')
  if (raw) {
    try {
      const user = JSON.parse(raw)
      if (user?.token) {
        config.headers.Authorization = `Bearer ${user.token}`
      }
    } catch {
      localStorage.removeItem('dikoin_user')
    }
  }
  return config
})

export function getApiError(error: unknown) {
  if (axios.isAxiosError(error)) {
    return error.response?.data?.message || error.message || 'Error de conexión con el backend'
  }
  return error instanceof Error ? error.message : 'Error inesperado'
}
