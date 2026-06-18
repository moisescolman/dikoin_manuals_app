import { http } from './http'
import type { AssetResponse, AssetType } from '@/types/api'

export async function getAssets(params?: { manualId?: number; assetType?: AssetType }) {
  const { data } = await http.get<AssetResponse[]>('/assets', { params })
  return data
}

export async function uploadAsset(params: { file: File; assetType: AssetType; manualId?: number }) {
  const form = new FormData()
  form.append('file', params.file)
  form.append('assetType', params.assetType)
  if (params.manualId) form.append('manualId', String(params.manualId))

  const { data } = await http.post<AssetResponse>('/assets', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return data
}
