import { http } from './http'
import type { DashboardResponse } from '@/types/api'

export async function getDashboard() {
  const { data } = await http.get<DashboardResponse>('/dashboard')
  return data
}
