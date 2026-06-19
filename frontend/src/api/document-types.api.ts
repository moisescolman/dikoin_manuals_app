import { http } from './http'
import type { DocumentTypeResponse } from '@/types/api'

export async function getDocumentTypes() {
  const { data } = await http.get<DocumentTypeResponse[]>('/document-types')
  return data
}
