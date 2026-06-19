import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getApiError } from '@/api/http'
import { deleteAsset, getAssets, uploadAsset } from '@/api/assets.api'
import type { AssetResponse, AssetType } from '@/types/api'

export const useAssetsStore = defineStore('assets', () => {
  const assets = ref<AssetResponse[]>([])
  const loading = ref(false)
  const error = ref('')

  async function fetchAssets(params?: { manualId?: number; assetType?: AssetType }) {
    loading.value = true
    error.value = ''
    try {
      assets.value = await getAssets(params)
    } catch (err) {
      error.value = getApiError(err)
    } finally {
      loading.value = false
    }
  }

  async function upload(file: File, assetType: AssetType, manualId?: number) {
    const asset = await uploadAsset({ file, assetType, manualId })
    await fetchAssets()
    return asset
  }

  async function remove(id: number) {
    await deleteAsset(id)
    assets.value = assets.value.filter((asset) => asset.id !== id)
  }

  return { assets, loading, error, fetchAssets, upload, remove }
})
