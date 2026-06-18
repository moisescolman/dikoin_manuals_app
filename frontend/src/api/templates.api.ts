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
