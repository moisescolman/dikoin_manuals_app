import { http } from './http'
import type { ProductCategoryResponse, ProductFamilyResponse, ProductRequest, ProductResponse } from '@/types/api'

export async function getProducts(search?: string) {
  const { data } = await http.get<ProductResponse[]>('/products', { params: { search: search || undefined } })
  return data
}

export async function getProductFamilies() {
  const { data } = await http.get<ProductFamilyResponse[]>('/products/families')
  return data
}

export async function getProductCategories() {
  const { data } = await http.get<ProductCategoryResponse[]>('/products/categories')
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
