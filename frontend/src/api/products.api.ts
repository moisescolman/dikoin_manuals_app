import { http } from './http'
import type { ProductRequest, ProductResponse } from '@/types/api'

export async function getProducts(search?: string) {
  const { data } = await http.get<ProductResponse[]>('/products', { params: { search: search || undefined } })
  return data
}

export async function createProduct(request: ProductRequest) {
  const { data } = await http.post<ProductResponse>('/products', request)
  return data
}

export async function updateProduct(id: number, request: ProductRequest) {
  const { data } = await http.put<ProductResponse>(`/products/${id}`, request)
  return data
}
