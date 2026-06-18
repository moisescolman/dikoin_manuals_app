import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getApiError } from '@/api/http'
import { getActiveTemplate, getTemplates, updateTemplate } from '@/api/templates.api'
import type { TemplateResponse } from '@/types/api'

export const useTemplatesStore = defineStore('templates', () => {
  const templates = ref<TemplateResponse[]>([])
  const active = ref<TemplateResponse | null>(null)
  const loading = ref(false)
  const error = ref('')

  async function fetchTemplates() {
    loading.value = true
    error.value = ''
    try {
      templates.value = await getTemplates()
      active.value = await getActiveTemplate()
    } catch (err) {
      error.value = getApiError(err)
    } finally {
      loading.value = false
    }
  }

  async function saveTemplate(id: number, payload: Partial<TemplateResponse>) {
    active.value = await updateTemplate(id, payload)
    await fetchTemplates()
  }

  return { templates, active, loading, error, fetchTemplates, saveTemplate }
})
