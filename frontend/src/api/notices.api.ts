import { http } from './http'
import type { NoticeTemplateRequest, NoticeTemplateResponse, NoticeType, NoticeUsageResponse } from '@/types/api'

export async function getNotices(type?: NoticeType, search?: string) {
  const { data } = await http.get<NoticeTemplateResponse[]>('/notices', { params: { type, search: search || undefined } })
  return data
}

export async function createNotice(request: NoticeTemplateRequest) {
  const { data } = await http.post<NoticeTemplateResponse>('/notices', request)
  return data
}

export async function updateNotice(id: number, request: NoticeTemplateRequest) {
  const { data } = await http.put<NoticeTemplateResponse>(`/notices/${id}`, request)
  return data
}

export async function getNoticeUsages(id: number) {
  const { data } = await http.get<NoticeUsageResponse[]>(`/notices/${id}/usages`)
  return data
}

export async function deleteNotice(id: number) {
  await http.delete(`/notices/${id}`)
}
