import { http } from './http'
import type { ProductCategoryResponse, ProductDeleteImpactResponse, ProductFamilyResponse, ProductRequest, ProductResponse } from '@/types/api'

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

export async function getProductDeleteImpact(id: number) {
  const { data } = await http.get<ProductDeleteImpactResponse>(`/products/${id}/delete-impact`)
  return data
}

export async function uploadProductImage(id: number, file: File) {
  const form = new FormData()
  form.append('file', file)
  const { data } = await http.post<ProductResponse>(`/products/${id}/image`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return data
}

export async function deleteProductImage(id: number) {
  const { data } = await http.delete<ProductResponse>(`/products/${id}/image`)
  return data
}

export async function applyProductImageToManuals(id: number, manualIds: number[]) {
  const { data } = await http.post<ProductDeleteImpactResponse>(`/products/${id}/image/apply`, { manualIds })
  return data
}

export async function deleteProduct(id: number, deactivateManualIds: number[] = []) {
  await http.delete(`/products/${id}`, { data: { deactivateManualIds } })
}
