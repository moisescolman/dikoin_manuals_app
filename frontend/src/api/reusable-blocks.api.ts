import { http } from './http'
import type { ReusableBlockRequest, ReusableBlockResponse, ReusableBlockUsageResponse } from '@/types/api'

export async function getReusableBlocks(includeInactive = false) {
  const { data } = await http.get<ReusableBlockResponse[]>('/reusable-blocks', { params: { includeInactive } })
  return data
}

export async function getReusableBlock(id: number) {
  const { data } = await http.get<ReusableBlockResponse>(`/reusable-blocks/${id}`)
  return data
}

export async function createReusableBlock(request: ReusableBlockRequest) {
  const { data } = await http.post<ReusableBlockResponse>('/reusable-blocks', request)
  return data
}

export async function updateReusableBlock(id: number, request: ReusableBlockRequest) {
  const { data } = await http.put<ReusableBlockResponse>(`/reusable-blocks/${id}`, request)
  return data
}

export async function getReusableBlockUsages(id: number) {
  const { data } = await http.get<ReusableBlockUsageResponse[]>(`/reusable-blocks/${id}/usages`)
  return data
}
