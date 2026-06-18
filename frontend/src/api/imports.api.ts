import { http } from './http'
import type { ImportJobResponse, LanguageCode } from '@/types/api'

export async function importManual(params: {
  file: File
  productId: number
  manualCode: string
  title: string
  languageCode: LanguageCode
  type: 'documents' | 'pdf'
}) {
  const form = new FormData()
  form.append('file', params.file)
  form.append('productId', String(params.productId))
  form.append('manualCode', params.manualCode)
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
