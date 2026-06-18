import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getApiError } from '@/api/http'
import { createProduct, getProducts, updateProduct } from '@/api/products.api'
import type { ProductRequest, ProductResponse } from '@/types/api'

export const useProductsStore = defineStore('products', () => {
  const products = ref<ProductResponse[]>([])
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

  return { products, loading, error, fetchProducts, saveProduct }
})
