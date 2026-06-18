import { http } from './http'
import type { LoginRequest, LoginResponse } from '@/types/api'

export async function login(request: LoginRequest) {
  const { data } = await http.post<LoginResponse>('/auth/login', request)
  return data
}
