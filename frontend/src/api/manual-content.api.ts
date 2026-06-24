import { http } from './http'
import type {
  ManualBlockRequest,
  ManualBlockResponse,
  ManualSectionPatchRequest,
  ManualSectionRequest,
  ManualSectionResponse,
  MoveBlockRequest,
  ReorderRequest,
} from '@/types/api'

export async function getVersionSections(versionId: number) {
  const { data } = await http.get<ManualSectionResponse[]>(`/manual-content/versions/${versionId}/sections`)
  return data
}

export async function createRootSection(versionId: number, request: ManualSectionRequest) {
  const { data } = await http.post<ManualSectionResponse>(`/manual-content/versions/${versionId}/sections`, request)
  return data
}

export async function createSubsection(parentSectionId: number, request: ManualSectionRequest) {
  const { data } = await http.post<ManualSectionResponse>(`/manual-content/sections/${parentSectionId}/subsections`, request)
  return data
}

export async function updateSection(sectionId: number, request: ManualSectionPatchRequest) {
  const { data } = await http.patch<ManualSectionResponse>(`/manual-content/sections/${sectionId}`, request)
  return data
}

export async function reorderSections(versionId: number, request: ReorderRequest) {
  const { data } = await http.put<ManualSectionResponse[]>(`/manual-content/versions/${versionId}/sections/reorder`, request)
  return data
}

export async function deleteSection(sectionId: number, force = false) {
  await http.delete(`/manual-content/sections/${sectionId}`, { params: { force } })
}

export async function recalculateSectionNumbers(versionId: number) {
  const { data } = await http.post<ManualSectionResponse[]>(`/manual-content/versions/${versionId}/sections/renumber`)
  return data
}

export async function getSectionBlocks(sectionId: number) {
  const { data } = await http.get<ManualBlockResponse[]>(`/manual-content/sections/${sectionId}/blocks`)
  return data
}

export async function createSectionBlock(sectionId: number, request: ManualBlockRequest) {
  const { data } = await http.post<ManualBlockResponse>(`/manual-content/sections/${sectionId}/blocks`, request)
  return data
}

export async function updateBlock(blockId: number, request: ManualBlockRequest) {
  const { data } = await http.put<ManualBlockResponse>(`/manual-content/blocks/${blockId}`, request)
  return data
}

export async function deleteBlock(blockId: number) {
  await http.delete(`/manual-content/blocks/${blockId}`)
}

export async function duplicateBlock(blockId: number) {
  const { data } = await http.post<ManualBlockResponse>(`/manual-content/blocks/${blockId}/duplicate`)
  return data
}

export async function moveBlock(blockId: number, request: MoveBlockRequest) {
  const { data } = await http.post<ManualBlockResponse>(`/manual-content/blocks/${blockId}/move`, request)
  return data
}

export async function reorderBlocks(sectionId: number, request: ReorderRequest) {
  const { data } = await http.put<ManualBlockResponse[]>(`/manual-content/sections/${sectionId}/blocks/reorder`, request)
  return data
}
