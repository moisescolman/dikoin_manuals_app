import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { login as loginApi } from '@/api/auth.api'
import { getApiError } from '@/api/http'
import type { LoginResponse } from '@/types/api'

const STORAGE_KEY = 'dikoin_user'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<LoginResponse | null>(loadUser())
  const loading = ref(false)
  const error = ref('')

  const isAuthenticated = computed(() => !!user.value)
  const isClient = computed(() => user.value?.role === 'CLIENTE')

  async function login(email: string, password: string) {
    loading.value = true
    error.value = ''
    try {
      user.value = await loginApi({ email, password })
      localStorage.setItem(STORAGE_KEY, JSON.stringify(user.value))
      return user.value
    } catch (err) {
      error.value = getApiError(err)
      throw err
    } finally {
      loading.value = false
    }
  }

  function logout() {
    user.value = null
    localStorage.removeItem(STORAGE_KEY)
  }

  return { user, loading, error, isAuthenticated, isClient, login, logout }
})

function loadUser(): LoginResponse | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    localStorage.removeItem(STORAGE_KEY)
    return null
  }
}
