import { http } from './http'
import type {
  CreateReusableFragmentRequest,
  InsertReusableFragmentRequest,
  ReusableFragmentRequest,
  ReusableFragmentResponse,
  ReusableFragmentUsageResponse,
  ReusableFragmentInsertResponse,
} from '@/types/api'

export async function getReusableFragments(includeInactive = false, search?: string) {
  const { data } = await http.get<ReusableFragmentResponse[]>('/reusable-fragments', { params: { includeInactive, search: search || undefined } })
  return data
}

export async function getReusableFragment(id: number) {
  const { data } = await http.get<ReusableFragmentResponse>(`/reusable-fragments/${id}`)
  return data
}

export async function createReusableFragmentRecord(request: ReusableFragmentRequest) {
  const { data } = await http.post<ReusableFragmentResponse>('/reusable-fragments', request)
  return data
}

export async function createReusableFragment(request: CreateReusableFragmentRequest) {
  const { data } = await http.post<ReusableFragmentResponse>('/reusable-fragments/from-selection', request)
  return data
}

export async function insertReusableFragment(id: number, request: InsertReusableFragmentRequest) {
  const { data } = await http.post<ReusableFragmentInsertResponse>(`/reusable-fragments/${id}/insert`, request)
  return data
}

export async function updateReusableFragment(id: number, request: ReusableFragmentRequest) {
  const { data } = await http.put<ReusableFragmentResponse>(`/reusable-fragments/${id}`, request)
  return data
}

export async function deleteReusableFragment(id: number) {
  await http.delete(`/reusable-fragments/${id}`)
}

export async function getReusableFragmentUsages(id: number) {
  const { data } = await http.get<ReusableFragmentUsageResponse[]>(`/reusable-fragments/${id}/usages`)
  return data
}
