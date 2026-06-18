import { http } from './http'
import type { NoticeTemplateRequest, NoticeTemplateResponse, NoticeType } from '@/types/api'

export async function getNotices(type?: NoticeType) {
  const { data } = await http.get<NoticeTemplateResponse[]>('/notices', { params: { type } })
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
