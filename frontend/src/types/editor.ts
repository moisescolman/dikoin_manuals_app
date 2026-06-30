import type { BlockType, LanguageCode, ManualBlockResponse, ManualSectionResponse, ManualVersionRequest } from './api'

export type EditorBlockType =
  | 'titulo'
  | 'subtitulo'
  | 'parrafo'
  | 'lista-ul'
  | 'lista-ol'
  | 'tabla'
  | 'advertencia'
  | 'nota'
  | 'nota-ref'
  | 'fragmento-ref'
  | 'imagen'
  | 'enlace'
  | 'formula'
  | 'grafico'
  | 'bloque-ref'

export interface EditorBlock {
  id: string
  backendId?: number
  type: EditorBlockType
  content: string
  languageCode: LanguageCode
  data?: Record<string, unknown>
}

export interface EditorSection {
  id: string
  backendId?: number
  sortOrder: number
  sectionNumber?: string
  parentSectionId?: number
  linkedReusableSectionId?: number
  level: number
  titleEs: string
  titleEn?: string
  status: 'DRAFT' | 'COMPLETED' | 'REVIEW' | 'APPROVED'
  visible: boolean
  collapsed: boolean
  blocks: EditorBlock[]
}

export function randomId(prefix = 'id') {
  return `${prefix}_${Math.random().toString(36).slice(2, 9)}`
}

export function backendBlockTypeToEditor(type: BlockType): EditorBlockType {
  const map: Record<BlockType, EditorBlockType> = {
    HEADING: 'titulo',
    PARAGRAPH: 'parrafo',
    ORDERED_LIST: 'lista-ol',
    UNORDERED_LIST: 'lista-ul',
    TABLE: 'tabla',
    IMAGE: 'imagen',
    CHART: 'grafico',
    FORMULA: 'formula',
    NOTE: 'nota',
    WARNING: 'advertencia',
    INFO_BOX: 'nota',
    PAGE_BREAK: 'parrafo',
  }
  return map[type]
}

export function editorBlockTypeToBackend(type: EditorBlockType): BlockType {
  const map: Record<EditorBlockType, BlockType> = {
    titulo: 'HEADING',
    subtitulo: 'HEADING',
    parrafo: 'PARAGRAPH',
    'lista-ul': 'UNORDERED_LIST',
    'lista-ol': 'ORDERED_LIST',
    tabla: 'TABLE',
    advertencia: 'WARNING',
    nota: 'NOTE',
    'nota-ref': 'NOTE',
    'fragmento-ref': 'PARAGRAPH',
    imagen: 'IMAGE',
    enlace: 'PARAGRAPH',
    formula: 'FORMULA',
    grafico: 'CHART',
    'bloque-ref': 'PARAGRAPH',
  }
  return map[type]
}

export function parseContent(block: ManualBlockResponse): string {
  try {
    const parsed = JSON.parse(block.contentJson)
    if (parsed.type === 'heading') {
      return parsed.text ?? ''
    }
    if (block.blockType === 'TABLE' && Array.isArray(parsed.rows)) {
      return parsed.rows.map((row: unknown[]) => row.join('|')).join('\n')
    }
    if (Array.isArray(parsed.items)) {
      return parsed.items.join('\n')
    }
    if (parsed.type === 'link') {
      return `${parsed.text ?? 'Enlace'}|${parsed.href ?? 'https://'}`
    }
    if (parsed.type === 'notice_ref') {
      return String(parsed.noticeTemplateId ?? '')
    }
    if (parsed.type === 'reusable_block_ref') {
      return String(parsed.reusableBlockId ?? '')
    }
    if (parsed.type === 'reusable_fragment_ref') {
      return String(parsed.reusableFragmentId ?? '')
    }
    return parsed.text ?? parsed.latex ?? parsed.src ?? parsed.title ?? block.contentJson
  } catch {
    return block.contentJson
  }
}

function parseBlockData(block: ManualBlockResponse): Record<string, unknown> | undefined {
  const linkData: Record<string, unknown> = {
    ...(typeof block.reusableBlockId === 'number' ? { reusableBlockId: block.reusableBlockId } : {}),
    ...(typeof block.reusableFragmentId === 'number' ? { reusableFragmentId: block.reusableFragmentId } : {}),
  }
  try {
    const parsed = JSON.parse(block.contentJson)
    if (!parsed || typeof parsed !== 'object') return Object.keys(linkData).length ? linkData : undefined
    return { ...(parsed as Record<string, unknown>), ...linkData }
  } catch {
    return Object.keys(linkData).length ? linkData : undefined
  }
}

export function blockContentToJson(block: EditorBlock): string {
  const savedAttrs = (block.data?.json as { attrs?: Record<string, unknown> } | undefined)?.attrs || {}
  if (block.type === 'nota-ref') {
    return JSON.stringify({ type: 'notice_ref', noticeTemplateId: Number(block.content) })
  }
  if (block.type === 'fragmento-ref') {
    return JSON.stringify({ type: 'reusable_fragment_ref', reusableFragmentId: Number(block.content) })
  }
  if (block.type === 'imagen') {
    return JSON.stringify({
      type: 'image',
      src: block.content,
      caption: block.data?.caption || '',
      assetId: block.data?.assetId,
      width: savedAttrs.width || block.data?.width,
      height: savedAttrs.height || block.data?.height,
      align: savedAttrs.align || block.data?.align || 'inline',
      offsetX: savedAttrs.offsetX || block.data?.offsetX || 0,
      offsetY: savedAttrs.offsetY || block.data?.offsetY || 0,
      json: block.data?.json,
    })
  }
  if (block.type === 'tabla') {
    if (block.data?.json || block.data?.rows || block.data?.columns) {
      return JSON.stringify({
        type: 'table',
        columns: block.data?.columns || [],
        rows: block.data?.rows || [],
        width: savedAttrs.width || block.data?.width,
        hasHeader: block.data?.hasHeader !== false,
        align: savedAttrs.align || block.data?.align || 'left',
        json: block.data?.json,
      })
    }
    const rows = block.content
      .split('\n')
      .filter(Boolean)
      .map((line) => line.split('|').map((cell) => cell.trim()))
    const columns = rows[0] ?? []
    return JSON.stringify({
      type: 'table',
      columns,
      rows: rows.slice(1),
      width: savedAttrs.width || block.data?.width,
      hasHeader: block.data?.hasHeader !== false,
      align: savedAttrs.align || block.data?.align || 'left',
    })
  }
  if (block.type === 'formula') {
    return JSON.stringify({
      type: 'formula',
      latex: block.content,
      mathml: block.data?.mathml || null,
      omml: block.data?.omml || null,
      displayMode: block.data?.displayMode || 'block',
      numbered: Boolean(block.data?.numbered),
      equationNumber: block.data?.equationNumber || null,
      caption: block.data?.caption || '',
      align: block.data?.align || 'center',
      width: block.data?.width,
      offsetX: block.data?.offsetX || 0,
      offsetY: block.data?.offsetY || 0,
    })
  }
  if (block.data?.json || block.data?.html) {
    return JSON.stringify({
      type: block.data.type || block.type,
      text: block.content,
      ...block.data,
    })
  }
  if (block.type === 'titulo') {
    return JSON.stringify({ type: 'heading', level: Number(block.data?.level || 1), text: block.content })
  }
  if (block.type === 'lista-ul' || block.type === 'lista-ol') {
    return JSON.stringify({
      type: block.type === 'lista-ul' ? 'unordered_list' : 'ordered_list',
      items: block.content.split('\n').filter(Boolean),
    })
  }
  if (block.type === 'enlace') {
    const [text = 'Enlace', href = 'https://'] = block.content.split('|')
    return JSON.stringify({ type: 'link', text: text.trim(), href: href.trim() })
  }
  if (block.type === 'grafico') {
    return JSON.stringify({ type: 'chart', title: block.content, printable: true })
  }
  if (block.type === 'bloque-ref') {
    return JSON.stringify({ type: 'reusable_block_ref', reusableBlockId: Number(block.content) })
  }
  return JSON.stringify({ type: block.type, text: block.content })
}

export function sectionsFromBackend(sections: ManualSectionResponse[] = []): EditorSection[] {
  return sections.map((section, index) => ({
    id: randomId('section'),
    backendId: section.id,
    sortOrder: section.sortOrder ?? index + 1,
    sectionNumber: section.sectionNumber,
    parentSectionId: section.parentSectionId,
    linkedReusableSectionId: section.linkedReusableSectionId,
    level: section.level || 1,
    titleEs: section.titleEs,
    titleEn: section.titleEn,
    status: normalizeSectionStatus(section.completionStatus),
    visible: section.visible !== false,
    collapsed: false,
    blocks: section.blocks.map((block) => ({
      id: randomId('block'),
      backendId: block.id,
      type: contentTypeFromJson(block.contentJson) || backendBlockTypeToEditor(block.blockType),
      content: parseContent(block),
      languageCode: block.languageCode,
      data: parseBlockData(block),
    })),
  }))
}

export function versionRequestFromEditor(params: {
  versionNumber: string
  status: ManualVersionRequest['status']
  active: boolean
  esReady: boolean
  enReady: boolean
  changeNotes?: string
  sections: EditorSection[]
}): ManualVersionRequest {
  return {
    versionNumber: params.versionNumber,
    status: params.status,
    active: params.active,
    esReady: params.esReady,
    enReady: params.enReady,
    changeNotes: params.changeNotes,
    sections: params.sections.map((section, sectionIndex) => ({
      id: section.backendId,
      sortOrder: sectionIndex + 1,
      sectionNumber: section.sectionNumber || String(sectionIndex + 1),
      parentSectionId: section.parentSectionId,
      linkedReusableSectionId: section.linkedReusableSectionId,
      level: section.level,
      titleEs: section.titleEs,
      titleEn: section.titleEn,
      completionStatus: section.status,
      visible: section.visible,
      blocks: section.blocks.map((block, blockIndex) => ({
        id: block.backendId,
        sortOrder: blockIndex + 1,
        blockType: editorBlockTypeToBackend(block.type),
        languageCode: block.languageCode,
        contentJson: blockContentToJson(block),
        plainText: block.content,
        assetId: typeof block.data?.assetId === 'number' ? block.data.assetId : undefined,
        reusableBlockId: typeof block.data?.reusableBlockId === 'number' ? block.data.reusableBlockId : undefined,
        reusableFragmentId: typeof block.data?.reusableFragmentId === 'number' ? block.data.reusableFragmentId : undefined,
      })),
    })),
  }
}

function normalizeSectionStatus(status?: string): EditorSection['status'] {
  const normalized = String(status || '').toUpperCase()
  if (normalized === 'REVIEWED' || normalized === 'REVIEW') return 'REVIEW'
  if (normalized === 'DRAFT' || normalized === 'PENDING' || normalized === 'IMPORTED') return 'REVIEW'
  return 'APPROVED'
}

function contentTypeFromJson(contentJson: string): EditorBlockType | null {
  try {
    const parsed = JSON.parse(contentJson)
    if (parsed.type === 'notice_ref') return 'nota-ref'
    if (parsed.type === 'reusable_block_ref') return 'bloque-ref'
    if (parsed.type === 'reusable_fragment_ref') return 'fragmento-ref'
  } catch {
    return null
  }
  return null
}
