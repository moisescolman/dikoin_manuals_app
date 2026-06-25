import { http } from './http'
import type {
  ManualCreateRequest,
  ManualDetailResponse,
  ManualStatus,
  ManualSummaryResponse,
  ManualVersionRequest,
  ManualVersionResponse,
} from '@/types/api'

export async function getManuals(search?: string) {
  const { data } = await http.get<ManualSummaryResponse[]>('/manuals', { params: { search: search || undefined } })
  return data
}

export async function getManual(id: number) {
  const { data } = await http.get<ManualDetailResponse>(`/manuals/${id}`)
  return data
}

export async function createManual(request: ManualCreateRequest) {
  const { data } = await http.post<ManualDetailResponse>('/manuals', request)
  return data
}

export async function saveManualVersion(manualId: number, request: ManualVersionRequest) {
  const { data } = await http.put<ManualVersionResponse>(`/manuals/${manualId}/versions`, request)
  return data
}

export async function publishManualVersion(manualId: number, versionId: number, changeNotes?: string) {
  const { data } = await http.post<ManualVersionResponse>(`/manuals/${manualId}/versions/${versionId}/publish`, { changeNotes })
  return data
}

export async function deleteManual(manualId: number) {
  await http.delete(`/manuals/${manualId}`)
}

export async function setManualEnabled(manualId: number, enabled: boolean) {
  const { data } = await http.patch<ManualDetailResponse>(`/manuals/${manualId}/enabled`, { enabled })
  return data
}

export async function changeManualStatus(manualId: number, status: ManualStatus, changeNotes?: string) {
  const manual = await getManual(manualId)
  const version = manual.activeVersion
  if (!version) {
    throw new Error('El manual no tiene una versión activa')
  }

  if (status === 'PUBLISHED') {
    await publishManualVersion(manualId, version.id, changeNotes || 'Publicado desde frontend')
    return getManual(manualId)
  }

  await saveManualVersion(manualId, {
    versionNumber: version.versionNumber,
    status,
    active: true,
    esReady: version.esReady,
    enReady: version.enReady,
    changeNotes: changeNotes || `Estado cambiado a ${status}`,
    sections: version.sections.map((section) => ({
      id: section.id,
      sortOrder: section.sortOrder,
      sectionNumber: section.sectionNumber,
      parentSectionId: section.parentSectionId,
      level: section.level,
      titleEs: section.titleEs,
      titleEn: section.titleEn,
      completionStatus: section.completionStatus,
      visible: section.visible,
      blocks: section.blocks.map((block) => ({
        id: block.id,
        sortOrder: block.sortOrder,
        blockType: block.blockType,
        languageCode: block.languageCode,
        contentJson: block.contentJson,
        plainText: block.plainText,
        assetId: block.assetId,
        reusableBlockId: block.reusableBlockId,
      })),
    })),
  })
  return getManual(manualId)
}
