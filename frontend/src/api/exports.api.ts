import { http } from './http'
import type { ExportJobResponse, LanguageCode } from '@/types/api'

export async function exportManualPdf(manualId: number, lang: LanguageCode = 'ES') {
  const { data } = await http.post<ExportJobResponse>(`/export-jobs/manuals/${manualId}/pdf`, undefined, { params: { lang } })
  return data
}

export async function downloadExportPdf(jobId: number) {
  const { data } = await http.get<Blob>(`/export-jobs/${jobId}/file`, { responseType: 'blob' })
  return data
}
