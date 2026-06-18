import { http } from './http'
import type { ExportJobResponse } from '@/types/api'

export async function exportManualPdf(manualId: number) {
  const { data } = await http.post<ExportJobResponse>(`/export-jobs/manuals/${manualId}/pdf`)
  return data
}
