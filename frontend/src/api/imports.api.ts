import { http } from './http'
import type { ImportJobResponse, LanguageCode } from '@/types/api'

export async function importManual(params: {
  file: File
  productId: number
  manualCode?: string
  documentTypeId?: number
  documentYear?: string
  documentVersion?: string
  title: string
  languageCode: LanguageCode
  type: 'documents' | 'pdf'
}) {
  const form = new FormData()
  form.append('file', params.file)
  form.append('productId', String(params.productId))
  if (params.manualCode) form.append('manualCode', params.manualCode)
  if (params.documentTypeId) form.append('documentTypeId', String(params.documentTypeId))
  if (params.documentYear) form.append('documentYear', params.documentYear)
  if (params.documentVersion) form.append('documentVersion', params.documentVersion)
  form.append('title', params.title)
  form.append('languageCode', params.languageCode)

  const { data } = await http.post<ImportJobResponse>(`/import-jobs/${params.type}`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  })
  return data
}

export async function getImportJobs() {
  const { data } = await http.get<ImportJobResponse[]>('/import-jobs')
  return data
}
