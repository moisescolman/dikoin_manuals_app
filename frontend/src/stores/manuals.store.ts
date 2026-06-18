import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getApiError } from '@/api/http'
import { changeManualStatus, deleteManual, getManual, getManuals, publishManualVersion, saveManualVersion, setManualEnabled } from '@/api/manuals.api'
import type { ManualDetailResponse, ManualStatus, ManualSummaryResponse, ManualVersionRequest } from '@/types/api'

export const useManualsStore = defineStore('manuals', () => {
  const manuals = ref<ManualSummaryResponse[]>([])
  const current = ref<ManualDetailResponse | null>(null)
  const loading = ref(false)
  const error = ref('')

  async function fetchManuals(search = '') {
    loading.value = true
    error.value = ''
    try {
      manuals.value = await getManuals(search)
    } catch (err) {
      error.value = getApiError(err)
    } finally {
      loading.value = false
    }
  }

  async function fetchManual(id: number) {
    loading.value = true
    error.value = ''
    try {
      current.value = await getManual(id)
      return current.value
    } catch (err) {
      error.value = getApiError(err)
      throw err
    } finally {
      loading.value = false
    }
  }

  async function saveVersion(manualId: number, request: ManualVersionRequest) {
    loading.value = true
    error.value = ''
    try {
      await saveManualVersion(manualId, request)
      await fetchManual(manualId)
    } catch (err) {
      error.value = getApiError(err)
      throw err
    } finally {
      loading.value = false
    }
  }

  async function publishVersion(manualId: number, versionId: number, notes?: string) {
    await publishManualVersion(manualId, versionId, notes)
    await fetchManual(manualId)
  }

  async function removeManual(manualId: number) {
    loading.value = true
    error.value = ''
    try {
      await deleteManual(manualId)
      manuals.value = manuals.value.filter((manual) => manual.id !== manualId)
    } catch (err) {
      error.value = getApiError(err)
      throw err
    } finally {
      loading.value = false
    }
  }

  async function updateStatus(manualId: number, status: ManualStatus, notes?: string) {
    loading.value = true
    error.value = ''
    try {
      current.value = await changeManualStatus(manualId, status, notes)
      await fetchManuals()
      return current.value
    } catch (err) {
      error.value = getApiError(err)
      throw err
    } finally {
      loading.value = false
    }
  }

  async function updateEnabled(manualId: number, enabled: boolean) {
    loading.value = true
    error.value = ''
    try {
      current.value = await setManualEnabled(manualId, enabled)
      await fetchManuals()
      return current.value
    } catch (err) {
      error.value = getApiError(err)
      throw err
    } finally {
      loading.value = false
    }
  }

  return { manuals, current, loading, error, fetchManuals, fetchManual, saveVersion, publishVersion, removeManual, updateStatus, updateEnabled }
})
