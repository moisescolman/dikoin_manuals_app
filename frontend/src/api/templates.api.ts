import { http } from './http'
import type { TemplateResponse } from '@/types/api'

export async function getTemplates() {
  const { data } = await http.get<TemplateResponse[]>('/templates')
  return data
}

export async function getActiveTemplate() {
  const { data } = await http.get<TemplateResponse>('/templates/active')
  return data
}

export async function updateTemplate(id: number, request: Partial<TemplateResponse>) {
  const { data } = await http.put<TemplateResponse>(`/templates/${id}`, request)
  return data
}

export async function createTemplate(request: Partial<TemplateResponse>) {
  const { data } = await http.post<TemplateResponse>('/templates', request)
  return data
}

export async function activateTemplate(id: number) {
  const { data } = await http.post<TemplateResponse>(`/templates/${id}/activate`)
  return data
}

export async function uploadTemplateLogo(id: number, file: File, onProgress?: (progress: number) => void) {
  const form = new FormData()
  form.append('file', file)

  const { data } = await http.post<TemplateResponse>(`/templates/${id}/logo`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (event) => {
      if (!event.total || !onProgress) return
      onProgress(Math.round((event.loaded * 100) / event.total))
    },
  })
  return data
}
