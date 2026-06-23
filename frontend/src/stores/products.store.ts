import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getApiError } from '@/api/http'
import { createProduct, getProductCategories, getProductFamilies, getProducts, updateProduct } from '@/api/products.api'
import type { ProductCategoryResponse, ProductFamilyResponse, ProductRequest, ProductResponse } from '@/types/api'

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

  return { products, families, categories, loading, error, fetchProducts, fetchTaxonomy, saveProduct }
})
