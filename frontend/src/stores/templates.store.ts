import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getApiError } from '@/api/http'
import { activateTemplate, createTemplate, deleteTemplate, getActiveTemplate, getTemplates, updateTemplate, uploadTemplateLogo } from '@/api/templates.api'
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
    const updated = await updateTemplate(id, payload)
    active.value = updated.active ? updated : active.value
    await fetchTemplates()
    return updated
  }

  async function addTemplate(payload: Partial<TemplateResponse>) {
    const created = await createTemplate(payload)
    await fetchTemplates()
    return created
  }

  async function setActive(id: number) {
    active.value = await activateTemplate(id)
    await fetchTemplates()
    return active.value
  }

  async function uploadLogo(id: number, file: File, onProgress?: (progress: number) => void) {
    const updated = await uploadTemplateLogo(id, file, onProgress)
    await fetchTemplates()
    return updated
  }

  async function removeTemplate(id: number) {
    await deleteTemplate(id)
    await fetchTemplates()
  }

  return { templates, active, loading, error, fetchTemplates, saveTemplate, addTemplate, setActive, uploadLogo, removeTemplate }
})
