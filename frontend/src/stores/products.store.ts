import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getApiError } from '@/api/http'
import {
  applyProductImageToManuals,
  createProduct,
  deleteProduct,
  deleteProductImage,
  getProductCategories,
  getProductDeleteImpact,
  getProductFamilies,
  getProducts,
  updateProduct,
  uploadProductImage,
} from '@/api/products.api'
import type { ProductCategoryResponse, ProductDeleteImpactResponse, ProductFamilyResponse, ProductRequest, ProductResponse } from '@/types/api'

export const useProductsStore = defineStore('products', () => {
  const products = ref<ProductResponse[]>([])
  const families = ref<ProductFamilyResponse[]>([])
  const categories = ref<ProductCategoryResponse[]>([])
  const loading = ref(false)
  const error = ref('')

  async function fetchProducts(search = '') {
    loading.value = true
    error.value = ''
    try {
      products.value = await getProducts(search)
    } catch (err) {
      error.value = getApiError(err)
    } finally {
      loading.value = false
    }
  }

  async function saveProduct(payload: ProductRequest, id?: number) {
    const saved = id ? await updateProduct(id, payload) : await createProduct(payload)
    await fetchProducts()
    return saved
  }

  async function saveProductImage(id: number, file: File) {
    const saved = await uploadProductImage(id, file)
    await fetchProducts()
    return saved
  }

  async function removeProductImage(id: number) {
    const saved = await deleteProductImage(id)
    await fetchProducts()
    return saved
  }

  async function fetchDeleteImpact(id: number): Promise<ProductDeleteImpactResponse> {
    return getProductDeleteImpact(id)
  }

  async function applyImageToManuals(id: number, manualIds: number[]) {
    return applyProductImageToManuals(id, manualIds)
  }

  async function removeProduct(id: number, deactivateManualIds: number[] = []) {
    loading.value = true
    error.value = ''
    try {
      await deleteProduct(id, deactivateManualIds)
      await fetchProducts()
    } catch (err) {
      error.value = getApiError(err)
      throw err
    } finally {
      loading.value = false
    }
  }

  async function fetchTaxonomy() {
    error.value = ''
    try {
      const [loadedFamilies, loadedCategories] = await Promise.all([
        getProductFamilies(),
        getProductCategories(),
      ])
      families.value = loadedFamilies
      categories.value = loadedCategories
    } catch (err) {
      error.value = getApiError(err)
    }
  }

  return {
    products,
    families,
    categories,
    loading,
    error,
    fetchProducts,
    fetchTaxonomy,
    saveProduct,
    saveProductImage,
    removeProductImage,
    fetchDeleteImpact,
    applyImageToManuals,
    removeProduct,
  }
})
