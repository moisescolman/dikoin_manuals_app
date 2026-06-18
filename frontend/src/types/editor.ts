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
}

export interface EditorSection {
  id: string
  backendId?: number
  sortOrder: number
  sectionNumber?: string
  titleEs: string
  titleEn?: string
  status: 'READY' | 'PENDING' | 'IMPORTED' | 'REVIEWED'
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
    return parsed.text ?? parsed.latex ?? parsed.src ?? parsed.title ?? block.contentJson
  } catch {
    return block.contentJson
  }
}

export function blockContentToJson(block: EditorBlock): string {
  if (block.type === 'tabla') {
    const rows = block.content
      .split('\n')
      .filter(Boolean)
      .map((line) => line.split('|').map((cell) => cell.trim()))
    const columns = rows[0] ?? []
    return JSON.stringify({ type: 'table', columns, rows: rows.slice(1) })
  }
  if (block.type === 'lista-ul' || block.type === 'lista-ol') {
    return JSON.stringify({
      type: block.type === 'lista-ul' ? 'unordered_list' : 'ordered_list',
      items: block.content.split('\n').filter(Boolean),
    })
  }
  if (block.type === 'formula') {
    return JSON.stringify({ type: 'formula', latex: block.content })
  }
  if (block.type === 'enlace') {
    const [text = 'Enlace', href = 'https://'] = block.content.split('|')
    return JSON.stringify({ type: 'link', text: text.trim(), href: href.trim() })
  }
  if (block.type === 'imagen') {
    return JSON.stringify({ type: 'image', src: block.content, caption: '' })
  }
  if (block.type === 'grafico') {
    return JSON.stringify({ type: 'chart', title: block.content, printable: true })
  }
  if (block.type === 'nota-ref') {
    return JSON.stringify({ type: 'notice_ref', noticeTemplateId: Number(block.content) })
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
    titleEs: section.titleEs,
    titleEn: section.titleEn,
    status: (section.completionStatus as EditorSection['status']) || 'PENDING',
    collapsed: false,
    blocks: section.blocks.map((block) => ({
      id: randomId('block'),
      backendId: block.id,
      type: contentTypeFromJson(block.contentJson) || backendBlockTypeToEditor(block.blockType),
      content: parseContent(block),
      languageCode: block.languageCode,
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
      titleEs: section.titleEs,
      titleEn: section.titleEn,
      completionStatus: section.status,
      blocks: section.blocks.map((block, blockIndex) => ({
        id: block.backendId,
        sortOrder: blockIndex + 1,
        blockType: editorBlockTypeToBackend(block.type),
        languageCode: block.languageCode,
        contentJson: blockContentToJson(block),
      })),
    })),
  }
}

function contentTypeFromJson(contentJson: string): EditorBlockType | null {
  try {
    const parsed = JSON.parse(contentJson)
    if (parsed.type === 'notice_ref') return 'nota-ref'
    if (parsed.type === 'reusable_block_ref') return 'bloque-ref'
  } catch {
    return null
  }
  return null
}
