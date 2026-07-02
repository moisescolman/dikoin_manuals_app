<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch, type CSSProperties } from 'vue'
import { toBackendUrl } from '@/api/http'
import { getAssets } from '@/api/assets.api'
import { getNotices } from '@/api/notices.api'
import { getReusableBlocks } from '@/api/reusable-blocks.api'
import { getReusableFragments } from '@/api/reusable-fragments.api'
import { getActiveTemplate } from '@/api/templates.api'
import type { AssetResponse, BlockType, LanguageCode, ManualBlockResponse, ManualDetailResponse, ManualSectionResponse, NoticeTemplateResponse, ReusableBlockResponse, ReusableFragmentResponse, TemplateResponse } from '@/types/api'

const props = withDefaults(defineProps<{ manual: ManualDetailResponse; language?: LanguageCode; minPages?: number }>(), {
  minPages: 0,
})
const emit = defineEmits<{
  pageCount: [count: number]
}>()
const notices = ref<NoticeTemplateResponse[]>([])
const reusableBlocks = ref<ReusableBlockResponse[]>([])
const reusableFragments = ref<ReusableFragmentResponse[]>([])
const productImages = ref<AssetResponse[]>([])
const activeTemplate = ref<TemplateResponse | null>(null)
const measurementRef = ref<HTMLElement | null>(null)
const measurePageRef = ref<HTMLElement | null>(null)
const contentPages = ref<ContentUnit[][]>([])
const renderedLogoSrc = ref('')
const renderedProductImageSrc = ref('')

const headerDefaults = {
  showLogo: true,
  showCompanyName: true,
  showManualCode: true,
}

const layoutDefaults = {
  theme: {
    primary: '#007cb8',
    secondary: '#0f3a54',
    accent: '#f97316',
    text: '#17202a',
    muted: '#607789',
    border: '#b8cce3',
    pageBackground: '#ffffff',
    fontFamily: 'Arial',
    baseFontSize: 11,
  },
  page: {
    marginTop: 14,
    marginRight: 14,
    marginBottom: 14,
    marginLeft: 14,
  },
  cover: {
    showLogo: true,
    showProductImage: true,
    showDate: true,
    showProductCode: true,
    showDocumentVersion: true,
    alignment: 'center',
    logoPosition: 'center',
    productImagePosition: 'center',
    titleSize: 32,
    subtitleSize: 15,
    codeSize: 11,
  },
  index: {
    title: 'Índice',
    titleSize: 24,
    level1Size: 13,
    level2Size: 12,
    level3Size: 11,
    dottedLeaders: true,
    lineSpacing: 1.35,
    pageNumberSize: 11,
  },
  header: {
    enabled: true,
    showLogo: true,
    showCompanyName: true,
    showManualCode: true,
    height: 10,
    background: '#ffffff',
    textColor: '#17202a',
    borderColor: '#007cb8',
  },
  footer: {
    enabled: true,
    showContact: false,
    showWebsite: false,
    showPageNumber: true,
    height: 8,
    background: '#007cb8',
    textColor: '#ffffff',
    borderColor: '#007cb8',
  },
  content: {
    sectionTitleBackground: '#007cb8',
    sectionTitleColor: '#ffffff',
    sectionTitleSize: 14,
    subsectionTitleColor: '#0f3a54',
    subsectionTitleSize: 13,
    bodySize: 12,
    lineHeight: 1.42,
    tableHeaderBackground: '#007cb8',
    tableHeaderColor: '#ffffff',
    tableBorderColor: '#b8cce3',
    tableCellPadding: 6,
    tableFontSize: 10,
    tableStripe: true,
  },
}

interface RenderBlock {
  blockType: BlockType
  languageCode: LanguageCode
  contentJson: string
}

interface HeadingInfo {
  number: string
  level: number
  text: string
}

interface TocEntry {
  key: string | number
  title: string
  page?: number
  level: number
}

interface TableCellRender {
  text: string
  colspan: number
  rowspan: number
  header: boolean
}

type ContentUnit =
  | { key: string; kind: 'section'; section: ManualSectionResponse; index: number }
  | { key: string; kind: 'block'; block: ManualBlockResponse }
  | { key: string; kind: 'image-group'; blocks: ManualBlockResponse[] }
type BlockContentUnit =
  | { key: string; kind: 'block'; block: ManualBlockResponse }
  | { key: string; kind: 'image-group'; blocks: ManualBlockResponse[] }

let resizeObserver: ResizeObserver | null = null
let paginationRun = 0
const IMAGE_REFERENCE_WIDTH = 680
const IMAGE_FLOW_MARGIN = 12

const contentSections = computed(() => {
  return (props.manual.activeVersion?.sections || [])
    .filter((section) => section.visible !== false)
    .filter((section) => !isGeneratedTocSource(section))
})

const contentUnits = computed<ContentUnit[]>(() => {
  const units: ContentUnit[] = []
  contentSections.value.forEach((section, index) => {
    units.push({ key: `section-${section.id}`, kind: 'section', section, index })
    units.push(...contentUnitsForBlocks(visibleBlocks(section), `section-${section.id}`))
  })
  return units
})

function contentUnitsForBlocks(blocks: ManualBlockResponse[], prefix = 'blocks'): BlockContentUnit[] {
  const units: BlockContentUnit[] = []
  let imageGroup: ManualBlockResponse[] = []
  const flushImageGroup = () => {
    if (imageGroup.length) {
      units.push({ key: `${prefix}-image-group-${imageGroup.map((block) => block.id).join('-')}`, kind: 'image-group', blocks: imageGroup })
    }
    imageGroup = []
  }

  blocks.forEach((block) => {
    splitBlock(block).forEach((split, splitIndex) => {
      if (isAbsoluteFlowImageBlock(split)) {
        imageGroup.push(split)
        return
      }
      flushImageGroup()
      units.push({ key: `${prefix}-block-${block.id}-${splitIndex}`, kind: 'block', block: split })
    })
  })
  flushImageGroup()
  return units
}

const contentSignature = computed(() => {
  return contentSections.value.map((section) => {
    return [
      section.id,
      section.sectionNumber,
      section.titleEs,
      section.titleEn,
      visibleBlocks(section).map((block) => `${block.id}:${block.sortOrder}:${block.blockType}:${block.contentJson}`).join('|'),
    ].join(':')
  }).join('||')
})

const headingNumberMap = computed(() => {
  const entries = new Map<number, HeadingInfo>()
  contentSections.value.forEach((section, sectionIndex) => {
    let h1 = 0
    let h2 = 0
    let h3 = 0
    const sectionPrefix = section.sectionNumber || String(sectionIndex + 1)

    visibleBlocks(section).forEach((block) => {
      if (block.blockType !== 'HEADING') return

      const parsed = content(block.contentJson)
      const level = Math.min(3, Math.max(1, Number(parsed.level || 1)))
      let number = ''

      if (level === 1) {
        h1++
        h2 = 0
        h3 = 0
        number = `${sectionPrefix}.${h1}`
      } else if (level === 2) {
        if (!h1) h1 = 1
        h2++
        h3 = 0
        number = `${sectionPrefix}.${h1}.${h2}`
      } else {
        if (!h1) h1 = 1
        if (!h2) h2 = 1
        h3++
        number = `${sectionPrefix}.${h1}.${h2}.${h3}`
      }

      entries.set(block.id, {
        number,
        level,
        text: parsed.text || '',
      })
    })
  })
  return entries
})

const tocEntries = computed<TocEntry[]>(() => {
  const entries: TocEntry[] = []
  contentSections.value.forEach((section, index) => {
    entries.push({
      key: `section-${section.id}`,
      title: sectionTitle(section, index),
      page: contentPageForSection(section.id),
      level: 0,
    })

    visibleBlocks(section).forEach((block) => {
      if (block.blockType !== 'HEADING') return
      const heading = headingInfo(block)
      entries.push({
        key: `heading-${block.id}`,
        title: `${heading.number} ${heading.text}`,
        page: contentPageForBlock(block.id),
        level: heading.level,
      })
    })
  })
  return entries
})

const totalPages = computed(() => 2 + contentPages.value.length)
const blankPages = computed(() => Math.max(0, props.minPages - totalPages.value))

onMounted(async () => {
  const [loadedNotices, loadedBlocks, loadedFragments] = await Promise.all([
    getNotices(),
    getReusableBlocks(),
    getReusableFragments(),
  ])
  notices.value = loadedNotices
  reusableBlocks.value = loadedBlocks
  reusableFragments.value = loadedFragments
  try {
    productImages.value = await getAssets({ manualId: props.manual.id, assetType: 'PRODUCT_IMAGE' })
    renderedProductImageSrc.value = await loadProductImageDataUrl()
  } catch {
    productImages.value = []
    renderedProductImageSrc.value = ''
  }
  try {
    activeTemplate.value = await getActiveTemplate()
    renderedLogoSrc.value = await loadLogoDataUrl()
  } catch {
    activeTemplate.value = null
    renderedLogoSrc.value = ''
  }
  resizeObserver = new ResizeObserver(() => schedulePagination())
  if (measurePageRef.value) {
    resizeObserver.observe(measurePageRef.value)
  }
  schedulePagination()
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
})

watch(
  () => [props.manual.id, props.manual.activeVersion?.id, props.language, contentSignature.value, activeTemplate.value?.id],
  () => schedulePagination(),
)

watch(totalPages, (count) => emit('pageCount', count), { immediate: true })

function activeLanguage() {
  return props.language || 'ES'
}

function content(blockJson: string): Record<string, any> {
  try {
    return JSON.parse(blockJson)
  } catch {
    return { text: blockJson }
  }
}

function blockText(block: ManualBlockResponse) {
  const parsed = content(block.contentJson)
  return typeof parsed.text === 'string' ? parsed.text : ''
}

function splitBlock(block: ManualBlockResponse): ManualBlockResponse[] {
  if (block.blockType !== 'PARAGRAPH') {
    return [block]
  }

  const text = blockText(block)
  const chunks = text.split(/\n{1,}/).map((item: string) => item.trim()).filter(Boolean)
  if (chunks.length <= 1) {
    return [block]
  }

  return chunks.map((chunk: string, index: number) => ({
    ...block,
    id: Number(`${block.id}${index}`),
    contentJson: JSON.stringify({ ...content(block.contentJson), text: chunk }),
  }))
}

function isAbsoluteFlowImageBlock(block: ManualBlockResponse) {
  if (block.blockType !== 'IMAGE') return false
  return true
}

function tableRows(blockJson: string) {
  const parsed = content(blockJson)
  if (Array.isArray(parsed.rows)) {
    if (Array.isArray(parsed.columns) && parsed.columns.length) {
      return [parsed.columns, ...parsed.rows]
    }
    return parsed.rows
  }
  return []
}

function tableCellRows(blockJson: string): TableCellRender[][] {
  const parsed = content(blockJson)
  const tableJson = parsed.json
  if (tableJson && Array.isArray(tableJson.content)) {
    return tableJson.content.map((row: Record<string, any>, rowIndex: number) => {
      if (!Array.isArray(row.content)) return []
      return row.content.map((cell: Record<string, any>) => ({
        text: plainTextFromJson(cell).replace(/\n+/g, ' ').trim(),
        colspan: Math.max(1, Number(cell.attrs?.colspan || 1)),
        rowspan: Math.max(1, Number(cell.attrs?.rowspan || 1)),
        header: cell.type === 'tableHeader' || (rowIndex === 0 && tableHasHeader(blockJson)),
      }))
    })
  }

  return tableRows(blockJson).map((row: string[], rowIndex: number) => row.map((cell) => ({
    text: cell,
    colspan: 1,
    rowspan: 1,
    header: rowIndex === 0 && tableHasHeader(blockJson),
  })))
}

function tableHasHeader(blockJson: string) {
  const parsed = content(blockJson)
  return parsed.hasHeader ?? parsed.json?.attrs?.hasHeader ?? true
}

function imageFigureStyle(blockJson: string): CSSProperties {
  const parsed = content(blockJson)
  if (imageLayout(parsed) === 'absolute-flow') return {}
  const align = parsed.json?.attrs?.align || parsed.align || 'inline'
  if (align === 'center') return { display: 'flex', flexDirection: 'column', alignItems: 'center' }
  if (align === 'right') return { display: 'flex', flexDirection: 'column', alignItems: 'flex-end' }
  if (align === 'inline') return { display: 'inline-block', marginLeft: '0', marginRight: '8px', verticalAlign: 'top' }
  return { display: 'flex', flexDirection: 'column', alignItems: 'flex-start' }
}

function movableBlockStyle(blockJson: string) {
  const parsed = content(blockJson)
  const align = parsed.json?.attrs?.align || parsed.align || 'left'
  const width = parsed.json?.attrs?.width || parsed.width
  return {
    width: typeof width === 'number' ? `${width}px` : width,
    marginLeft: align === 'center' || align === 'right' ? 'auto' : '0',
    marginRight: align === 'center' ? 'auto' : align === 'right' ? '0' : 'auto',
  }
}

function tableStyle(blockJson: string): CSSProperties {
  if (isWideTable(blockJson)) {
    return {
      ...movableBlockStyle(blockJson),
      width: '100%',
      maxWidth: '100%',
    }
  }
  const width = readableTableWidth(tableWidth(blockJson), tableMinimumWidth(blockJson))
  return {
    ...movableBlockStyle(blockJson),
    width,
  }
}

function tableWidth(blockJson: string) {
  const parsed = content(blockJson)
  const width = parsed.json?.attrs?.width || parsed.width
  const text = String(width || '')
  if (!text) return '100%'
  return /^\d+(\.\d+)?$/.test(text) ? `${text}px` : text
}

function tableColumnWidths(blockJson: string): Array<number | undefined> {
  if (isWideTable(blockJson)) return []

  const parsed = content(blockJson)
  const tableJson = parsed.json
  if (!tableJson || !Array.isArray(tableJson.content)) return []

  const widths: Array<number | undefined> = []
  const textLengths: number[] = []
  tableJson.content.forEach((row: Record<string, any>) => {
    if (!Array.isArray(row.content)) return
    let columnIndex = 0
    row.content.forEach((cell: Record<string, any>) => {
      const colspan = Math.max(1, Number(cell.attrs?.colspan || 1))
      const colwidth = Array.isArray(cell.attrs?.colwidth) ? cell.attrs.colwidth : []
      const textLength = plainTextFromJson(cell).replace(/\s+/g, ' ').trim().length
      for (let spanIndex = 0; spanIndex < colspan; spanIndex += 1) {
        const width = Number(colwidth[spanIndex])
        if (Number.isFinite(width) && width > 0 && !widths[columnIndex]) {
          widths[columnIndex] = width
        }
        textLengths[columnIndex] = Math.max(textLengths[columnIndex] || 0, Math.ceil(textLength / colspan))
        columnIndex += 1
      }
    })
  })

  const columnCount = Math.max(widths.length, textLengths.length)
  for (let columnIndex = 0; columnIndex < columnCount; columnIndex += 1) {
    if (!widths[columnIndex]) {
      widths[columnIndex] = inferredTableColumnWidth(textLengths[columnIndex] || 0, columnCount)
    }
  }
  return widths
}

function tableColumnWidthStyle(width?: number) {
  return width ? { width: `${width}px` } : undefined
}

function tableColumnCount(blockJson: string) {
  return tableCellRows(blockJson).reduce((max, row) => {
    const count = row.reduce((total, cell) => total + cell.colspan, 0)
    return Math.max(max, count)
  }, 0)
}

function isWideTable(blockJson: string) {
  return tableColumnCount(blockJson) >= 7
}

function inferredTableColumnWidth(textLength: number, columnCount: number) {
  if (textLength <= 3) return 42
  if (textLength <= 10) return columnCount >= 5 ? 72 : 90
  const charWidth = columnCount >= 5 ? 5.8 : 6.8
  const baseWidth = Math.ceil(textLength * charWidth)
  return Math.min(columnCount >= 5 ? 170 : 260, Math.max(columnCount >= 5 ? 96 : 150, baseWidth))
}

function tableMinimumWidth(blockJson: string) {
  const widths = tableColumnWidths(blockJson).filter((width): width is number => Boolean(width))
  if (!widths.length) return 0
  return Math.min(widths.reduce((total, width) => total + width, 0), 680)
}

function readableTableWidth(width: string | undefined, minimumWidth: number) {
  if (!minimumWidth) return width || '100%'
  const safeWidth = `min(100%, ${minimumWidth}px)`
  if (!width) return safeWidth
  const match = width.match(/^(\d+(?:\.\d+)?)px$/)
  if (match && Number(match[1]) < minimumWidth) {
    return safeWidth
  }
  return width
}

function imageSource(blockJson: string) {
  const parsed = content(blockJson)
  const src = parsed.src || parsed.text || parsed.json?.attrs?.src || ''
  return src ? toBackendUrl(src) : ''
}

function imageWidth(blockJson: string) {
  const parsed = content(blockJson)
  const width = parsed.json?.attrs?.width || parsed.width
  if (!width) return undefined
  const text = String(width)
  return /^\d+(\.\d+)?$/.test(text) ? `${text}px` : text
}

function imageHeight(blockJson: string) {
  const parsed = content(blockJson)
  const height = parsed.json?.attrs?.height || parsed.height
  if (!height) return undefined
  const text = String(height)
  return /^\d+(\.\d+)?$/.test(text) ? `${text}px` : text
}

function sectionTitle(section: ManualSectionResponse, index: number) {
  const title = activeLanguage() === 'EN' ? section.titleEn || '' : section.titleEs
  return `${section.sectionNumber || index + 1}. ${title}`
}

function headingInfo(block: ManualBlockResponse): HeadingInfo {
  const parsed = content(block.contentJson)
  return headingNumberMap.value.get(block.id) || {
    number: '',
    level: Math.min(3, Math.max(1, Number(parsed.level || 1))),
    text: parsed.text || '',
  }
}

function headingTag(block: ManualBlockResponse | RenderBlock) {
  const level = Math.min(3, Math.max(1, Number(content(block.contentJson).level || 1)))
  return `h${Math.min(4, level + 2)}`
}

function headingClass(block: ManualBlockResponse | RenderBlock) {
  const level = Math.min(3, Math.max(1, Number(content(block.contentJson).level || 1)))
  return `doc-heading doc-heading-level-${level}`
}

function headingNumber(block: ManualBlockResponse) {
  return headingInfo(block).number
}

function headingText(block: ManualBlockResponse | RenderBlock) {
  return content(block.contentJson).text || ''
}

function visibleBlocks(section: ManualSectionResponse) {
  return section.blocks.filter((block) => block.languageCode === activeLanguage())
}

function noticeById(id: number) {
  return notices.value.find((notice) => notice.id === id)
}

function noticeTitle(notice?: NoticeTemplateResponse) {
  if (!notice) return 'Nota'
  return activeLanguage() === 'EN'
    ? notice.visibleTitleEn || notice.visibleTitleEs || notice.title || 'Note'
    : notice.visibleTitleEs || notice.title || 'Nota'
}

function noticeContent(notice?: NoticeTemplateResponse) {
  if (!notice) return activeLanguage() === 'EN' ? 'Note not found' : 'Nota no encontrada'
  return activeLanguage() === 'EN'
    ? notice.contentEn || notice.contentEs || ''
    : notice.contentEs || ''
}

function reusableBlockById(id: number) {
  return reusableBlocks.value.find((block) => block.id === id)
}

function reusableFragmentById(id: number) {
  return reusableFragments.value.find((fragment) => fragment.id === id)
}

function reusableRenderBlocks(id: number) {
  const reusable = reusableBlockById(id)
  return reusableBlocksFromContent(reusable?.contentJson)
}

function reusableFragmentRenderBlocks(id: number) {
  const fragment = reusableFragmentById(id)
  return reusableBlocksFromContent(fragment?.contentJson)
}

function reusableBlocksFromContent(contentJson?: string) {
  if (!contentJson) return []
  try {
    const parsed = JSON.parse(contentJson)
    return Array.isArray(parsed.blocks)
      ? parsed.blocks.filter((block: RenderBlock) => block.languageCode === activeLanguage())
      : []
  } catch {
    return []
  }
}

function manualTitle() {
  if (activeLanguage() === 'EN') {
    return props.manual.activeVersion?.enReady ? props.manual.title : ''
  }
  return props.manual.title
}

function templateLogo() {
  const template = activeTemplate.value
  if (!template) return ''
  if (template.logoUrl) return toBackendUrl(template.logoUrl)
  if (template.logoAssetId) return toBackendUrl(`/api/v1/assets/${template.logoAssetId}/file`)
  return toBackendUrl(template.logoPath)
}

function logoSrc() {
  return renderedLogoSrc.value || templateLogo()
}

function productImageSrc() {
  const image = productImages.value[0]
  return renderedProductImageSrc.value || (image?.fileUrl ? toBackendUrl(image.fileUrl) : '')
}

async function loadLogoDataUrl() {
  const src = templateLogo()
  if (!src || src.startsWith('data:')) return src

  try {
    const response = await fetch(src)
    if (!response.ok) return src
    const blob = await response.blob()
    return await blobToDataUrl(blob)
  } catch {
    return src
  }
}

async function loadProductImageDataUrl() {
  const image = productImages.value[0]
  const src = image?.fileUrl ? toBackendUrl(image.fileUrl) : ''
  if (!src || src.startsWith('data:')) return src

  try {
    const response = await fetch(src)
    if (!response.ok) return src
    const blob = await response.blob()
    return await blobToDataUrl(blob)
  } catch {
    return src
  }
}

function blobToDataUrl(blob: Blob) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(blob)
  })
}

function templateCompany() {
  return activeTemplate.value?.companyName || 'DIKOIN'
}

function parseConfig<T extends Record<string, boolean>>(value: string | undefined, fallback: T) {
  try {
    const parsed = JSON.parse(value || '{}')
    return { ...fallback, ...(parsed && typeof parsed === 'object' ? parsed : {}) }
  } catch {
    return { ...fallback }
  }
}

function numberValue(value: unknown) {
  if (value === null || value === undefined || value === '') return undefined
  const number = Number(String(value).replace('px', ''))
  return Number.isFinite(number) ? number : undefined
}

function imageLayout(parsed: Record<string, any>) {
  return parsed.layout ?? parsed.json?.attrs?.layout ?? 'absolute-flow'
}

function absoluteImageAttrs(blockJson: string) {
  const parsed = content(blockJson)
  const attrs = parsed.json?.attrs || {}
  const referenceWidth = numberValue(parsed.referenceWidth ?? attrs.referenceWidth) ?? IMAGE_REFERENCE_WIDTH
  const width = Math.max(1, numberValue(parsed.width ?? attrs.width) ?? 300)
  const height = Math.max(1, numberValue(parsed.height ?? attrs.height) ?? 180)
  let x = numberValue(parsed.x ?? attrs.x ?? parsed.offsetX ?? attrs.offsetX)
  if (x === undefined) {
    const align = parsed.align ?? attrs.align
    x = align === 'center' ? (referenceWidth - width) / 2 : align === 'right' ? referenceWidth - width : 0
  }
  return {
    x: Math.min(Math.max(0, x), Math.max(0, referenceWidth - width)),
    offsetY: Math.max(IMAGE_FLOW_MARGIN, numberValue(parsed.offsetY ?? attrs.offsetY) ?? IMAGE_FLOW_MARGIN),
    width: Math.min(referenceWidth, width),
    height,
    zIndex: Math.max(1, numberValue(parsed.zIndex ?? attrs.zIndex) ?? 1),
    referenceWidth,
  }
}

function imageGroupStyle(blocks: ManualBlockResponse[]): CSSProperties {
  const height = Math.max(
    IMAGE_FLOW_MARGIN * 2,
    ...blocks.map((block) => {
      const attrs = absoluteImageAttrs(block.contentJson)
      return attrs.offsetY + attrs.height + IMAGE_FLOW_MARGIN
    }),
  )
  return {
    '--absolute-flow-height': `${height}px`,
    '--absolute-flow-reference-width': `${IMAGE_REFERENCE_WIDTH}px`,
  } as CSSProperties
}

function absoluteImageStyle(blockJson: string): CSSProperties {
  const attrs = absoluteImageAttrs(blockJson)
  return {
    left: `${attrs.x}px`,
    top: `${attrs.offsetY}px`,
    width: `${attrs.width}px`,
    height: `${attrs.height}px`,
    zIndex: attrs.zIndex,
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function mergeDeep<T extends Record<string, unknown>>(base: T, patch: unknown): T {
  if (!isRecord(patch)) return base
  const output = { ...base }
  Object.entries(patch).forEach(([key, value]) => {
    const current = output[key]
    output[key as keyof T] = (isRecord(current) && isRecord(value)
      ? mergeDeep(current, value)
      : value) as T[keyof T]
  })
  return output
}

function layoutConfig() {
  const base = JSON.parse(JSON.stringify(layoutDefaults)) as typeof layoutDefaults
  try {
    const parsed = mergeDeep(base as unknown as Record<string, unknown>, JSON.parse(activeTemplate.value?.layoutConfigJson || '{}')) as unknown as typeof layoutDefaults
    const legacyHeader = parseConfig(activeTemplate.value?.headerConfigJson, headerDefaults)
    parsed.header.showLogo = legacyHeader.showLogo
    parsed.header.showCompanyName = legacyHeader.showCompanyName
    parsed.header.showManualCode = legacyHeader.showManualCode
    return parsed
  } catch {
    return base
  }
}

function headerConfig() {
  return layoutConfig().header
}

function templateCssVars() {
  const config = layoutConfig()
  return {
    '--tpl-primary': config.theme.primary,
    '--tpl-secondary': config.theme.secondary,
    '--tpl-accent': config.theme.accent,
    '--tpl-text': config.theme.text,
    '--tpl-muted': config.theme.muted,
    '--tpl-border': config.theme.border,
    '--tpl-page-bg': config.theme.pageBackground,
    '--tpl-font': config.theme.fontFamily,
    '--tpl-base-size': `${config.theme.baseFontSize}px`,
    '--tpl-margin-top': `${config.page.marginTop}mm`,
    '--tpl-margin-right': `${config.page.marginRight}mm`,
    '--tpl-margin-bottom': `${config.page.marginBottom}mm`,
    '--tpl-margin-left': `${config.page.marginLeft}mm`,
    '--tpl-header-height': `${config.header.height}mm`,
    '--tpl-header-bg': config.header.background,
    '--tpl-header-text': config.header.textColor,
    '--tpl-header-border': config.header.borderColor,
    '--tpl-footer-height': `${config.footer.height}mm`,
    '--tpl-footer-bg': config.footer.background,
    '--tpl-footer-text': config.footer.textColor,
    '--tpl-footer-border': config.footer.borderColor,
    '--tpl-cover-title-size': `${config.cover.titleSize}px`,
    '--tpl-cover-subtitle-size': `${config.cover.subtitleSize}px`,
    '--tpl-cover-code-size': `${config.cover.codeSize}px`,
    '--tpl-index-title-size': `${config.index.titleSize}px`,
    '--tpl-index-level-1': `${config.index.level1Size}px`,
    '--tpl-index-level-2': `${config.index.level2Size}px`,
    '--tpl-index-level-3': `${config.index.level3Size}px`,
    '--tpl-index-line-height': config.index.lineSpacing,
    '--tpl-index-page-size': `${config.index.pageNumberSize}px`,
    '--tpl-section-bg': config.content.sectionTitleBackground,
    '--tpl-section-color': config.content.sectionTitleColor,
    '--tpl-section-size': `${config.content.sectionTitleSize}px`,
    '--tpl-subsection-color': config.content.subsectionTitleColor,
    '--tpl-subsection-size': `${config.content.subsectionTitleSize}px`,
    '--tpl-body-size': `${config.content.bodySize}px`,
    '--tpl-line-height': config.content.lineHeight,
    '--tpl-table-head-bg': config.content.tableHeaderBackground,
    '--tpl-table-head-color': config.content.tableHeaderColor,
    '--tpl-table-border': config.content.tableBorderColor,
    '--tpl-table-padding': `${config.content.tableCellPadding}px`,
    '--tpl-table-size': `${config.content.tableFontSize}px`,
  }
}

function normalizeTitle(value: string) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toUpperCase()
}

function isGeneratedTocSource(section: ManualSectionResponse) {
  return normalizeTitle(section.titleEs || '').includes('INDICE')
}

function schedulePagination() {
  const runId = ++paginationRun
  requestAnimationFrame(async () => {
    await nextTick()
    if (runId !== paginationRun) return
    paginateContent()
  })
}

function plainTextFromJson(node: Record<string, any>): string {
  if (typeof node.text === 'string') return node.text
  if (!Array.isArray(node.content)) return ''
  return node.content.map((child: Record<string, any>) => plainTextFromJson(child)).join(node.type === 'paragraph' ? '' : '\n')
}

function keepWithNext(unit: ContentUnit) {
  return unit.kind === 'section' || (unit.kind === 'block' && unit.block.blockType === 'HEADING')
}

function followerGroupHeight(index: number, measuredItems: HTMLElement[]) {
  if (!keepWithNext(contentUnits.value[index])) return 0
  let height = 0
  for (let cursor = index + 1; cursor < contentUnits.value.length; cursor += 1) {
    height += measuredItems[cursor]?.offsetHeight || 0
    if (!keepWithNext(contentUnits.value[cursor])) break
  }
  return height
}

function paginateContent() {
  const measurement = measurementRef.value
  const page = measurePageRef.value
  if (!measurement || !page || !contentUnits.value.length) {
    contentPages.value = contentUnits.value.length ? [contentUnits.value] : []
    return
  }

  const maxHeight = page.clientHeight
  const measuredItems = Array.from(measurement.querySelectorAll<HTMLElement>('.measure-item'))
  if (!maxHeight || measuredItems.length !== contentUnits.value.length) {
    contentPages.value = contentUnits.value.length ? [contentUnits.value] : []
    return
  }

  const pages: ContentUnit[][] = []
  let currentPage: ContentUnit[] = []
  let currentPageHeights: number[] = []
  let currentHeight = 0

  measuredItems.forEach((item, index) => {
    const unit = contentUnits.value[index]
    const height = item.offsetHeight
    const startsNewSection = unit.kind === 'section'
    const requiredFollowerHeight = followerGroupHeight(index, measuredItems)
    const wouldLeaveTitleAlone = keepWithNext(unit)
      && Boolean(contentUnits.value[index + 1])
      && currentPage.length > 0
      && currentHeight + height + requiredFollowerHeight > maxHeight
    const shouldBreak = currentPage.length > 0 && (
      currentHeight + height > maxHeight
      || (startsNewSection && maxHeight - currentHeight < 140)
      || wouldLeaveTitleAlone
    )

    if (shouldBreak) {
      const carryUnits: ContentUnit[] = []
      const carryHeights: number[] = []
      while (currentPage.length && keepWithNext(currentPage[currentPage.length - 1])) {
        carryUnits.unshift(currentPage.pop() as ContentUnit)
        carryHeights.unshift(currentPageHeights.pop() || 0)
      }
      if (currentPage.length) {
        pages.push(currentPage)
      }
      currentPage = carryUnits
      currentPageHeights = carryHeights
      currentHeight = carryHeights.reduce((total, itemHeight) => total + itemHeight, 0)
    }

    currentPage.push(unit)
    currentPageHeights.push(height)
    currentHeight += height
  })

  if (currentPage.length) {
    pages.push(currentPage)
  }
  contentPages.value = pages
}

function contentPageForSection(sectionId: number) {
  const pageIndex = contentPages.value.findIndex((page) => {
    return page.some((unit) => unit.kind === 'section' && unit.section.id === sectionId)
  })
  return pageIndex >= 0 ? pageIndex + 3 : undefined
}

function contentPageForBlock(blockId: number) {
  const pageIndex = contentPages.value.findIndex((page) => {
    return page.some((unit) => {
      if (unit.kind === 'block') return unit.block.id === blockId
      if (unit.kind === 'image-group') return unit.blocks.some((block) => block.id === blockId)
      return false
    })
  })
  return pageIndex >= 0 ? pageIndex + 3 : undefined
}
</script>

<template>
  <div class="manual-pages" :style="templateCssVars()">
    <article class="manual-page cover-page">
      <div class="cover-mark">
        <div v-if="layoutConfig().cover.showLogo" class="cover-logo" :class="{ 'logo-image': logoSrc() }">
          <img v-if="logoSrc()" :src="logoSrc()" alt="Logo plantilla" />
          <span v-else>DK</span>
        </div>
        <strong v-if="headerConfig().showCompanyName">{{ templateCompany() }}</strong>
      </div>
      <div class="cover-content">
        <div v-if="layoutConfig().cover.showProductImage" class="cover-product">
          <img v-if="productImageSrc()" :src="productImageSrc()" :alt="manual.productName" />
        </div>
        <div class="cover-title-block">
          <h1>{{ manual.productName }}</h1>
          <p>{{ manualTitle() }}</p>
          <strong v-if="layoutConfig().cover.showDocumentVersion">{{ manual.documentTypeName || manual.category || 'Manual' }}</strong>
        </div>
        <p v-if="layoutConfig().cover.showProductCode" class="manual-code">{{ manual.code }}</p>
        <h1>{{ manualTitle() }}</h1>
        <p>{{ manual.productCode }} · {{ manual.productName }}</p>
        <span>v{{ manual.activeVersion?.versionNumber }} · {{ activeLanguage() }}</span>
      </div>
      <footer v-if="layoutConfig().cover.showDate || layoutConfig().footer.showPageNumber" class="paper-footer">{{ layoutConfig().footer.showPageNumber ? '1' : '' }}</footer>
    </article>

    <article class="manual-page toc-page">
      <header v-if="headerConfig().enabled" class="paper-header">
        <div v-if="headerConfig().showLogo" class="logo" :class="{ 'logo-image': logoSrc() }">
          <img v-if="logoSrc()" :src="logoSrc()" alt="Logo plantilla" />
          <span v-else>DK</span>
        </div>
        <strong v-if="headerConfig().showCompanyName">{{ templateCompany() }}</strong>
        <span v-if="headerConfig().showManualCode">Ref.: {{ manual.code }} · {{ activeLanguage() }} · v{{ manual.activeVersion?.versionNumber }}</span>
      </header>
      <div class="header-line"></div>
      <main class="paper-content">
        <h1>{{ layoutConfig().index.title }}</h1>
        <ol class="toc-list" :class="{ 'no-dots': !layoutConfig().index.dottedLeaders }">
          <li v-for="entry in tocEntries" :key="entry.key" :class="`toc-level-${entry.level}`">
            <span>{{ entry.title }}</span>
            <span class="toc-dots"></span>
            <span>{{ entry.page || '' }}</span>
          </li>
        </ol>
      </main>
      <footer v-if="layoutConfig().footer.enabled" class="paper-footer">{{ layoutConfig().footer.showPageNumber ? '2' : '' }}</footer>
    </article>

    <article v-for="(pageUnits, pageIndex) in contentPages" :key="pageIndex" class="manual-page">
      <header v-if="headerConfig().enabled" class="paper-header">
        <div v-if="headerConfig().showLogo" class="logo" :class="{ 'logo-image': logoSrc() }">
          <img v-if="logoSrc()" :src="logoSrc()" alt="Logo plantilla" />
          <span v-else>DK</span>
        </div>
        <strong v-if="headerConfig().showCompanyName">{{ templateCompany() }}</strong>
        <span v-if="headerConfig().showManualCode">Ref.: {{ manual.code }} · {{ activeLanguage() }} · v{{ manual.activeVersion?.versionNumber }}</span>
      </header>
      <div class="header-line"></div>
      <main class="paper-content">
        <template v-for="unit in pageUnits" :key="unit.key">
          <section v-if="unit.kind === 'section'" class="doc-section-title">
            <h2>{{ sectionTitle(unit.section, unit.index) }}</h2>
          </section>
          <div v-else-if="unit.kind === 'image-group'" class="doc-block absolute-flow-group" :style="imageGroupStyle(unit.blocks)">
            <figure v-for="block in unit.blocks" :key="block.id" class="absolute-flow-image" :style="absoluteImageStyle(block.contentJson)">
              <img v-if="imageSource(block.contentJson)" :src="imageSource(block.contentJson)" alt="" />
            </figure>
          </div>
          <div v-else class="doc-block">
            <div v-if="content(unit.block.contentJson).type === 'notice_ref'" class="linked-note">
              <strong>{{ noticeTitle(noticeById(content(unit.block.contentJson).noticeTemplateId)) }}</strong>
              <p>{{ noticeContent(noticeById(content(unit.block.contentJson).noticeTemplateId)) }}</p>
            </div>
            <div v-else-if="content(unit.block.contentJson).type === 'reusable_block_ref'" class="linked-block">
              <strong>
                {{
                  activeLanguage() === 'EN'
                    ? reusableBlockById(content(unit.block.contentJson).reusableBlockId)?.titleEn
                      || reusableBlockById(content(unit.block.contentJson).reusableBlockId)?.title
                    : reusableBlockById(content(unit.block.contentJson).reusableBlockId)?.titleEs
                      || reusableBlockById(content(unit.block.contentJson).reusableBlockId)?.title
                    || 'Sección reutilizable'
                }}
              </strong>
              <template v-for="innerUnit in contentUnitsForBlocks(reusableRenderBlocks(content(unit.block.contentJson).reusableBlockId), `reusable-${unit.block.id}`)" :key="innerUnit.key">
                <div v-if="innerUnit.kind === 'image-group'" class="doc-block absolute-flow-group" :style="imageGroupStyle(innerUnit.blocks)">
                  <figure v-for="block in innerUnit.blocks" :key="block.id" class="absolute-flow-image" :style="absoluteImageStyle(block.contentJson)">
                    <img v-if="imageSource(block.contentJson)" :src="imageSource(block.contentJson)" alt="" />
                  </figure>
                </div>
                <div v-else class="doc-block">
                  <component :is="headingTag(innerUnit.block)" v-if="innerUnit.block.blockType === 'HEADING'" :class="headingClass(innerUnit.block)">{{ headingText(innerUnit.block) }}</component>
                  <p v-else-if="innerUnit.block.blockType === 'PARAGRAPH'">{{ content(innerUnit.block.contentJson).text }}</p>
                  <ul v-else-if="innerUnit.block.blockType === 'UNORDERED_LIST'">
                    <li v-for="item in content(innerUnit.block.contentJson).items" :key="item">{{ item }}</li>
                  </ul>
                  <ol v-else-if="innerUnit.block.blockType === 'ORDERED_LIST'">
                    <li v-for="item in content(innerUnit.block.contentJson).items" :key="item">{{ item }}</li>
                  </ol>
                  <div v-else-if="innerUnit.block.blockType === 'NOTE'" class="note">
                    <strong>{{ content(innerUnit.block.contentJson).title || 'Nota' }}</strong>
                    <p>{{ content(innerUnit.block.contentJson).text }}</p>
                  </div>
                  <div v-else class="text-muted">{{ innerUnit.block.contentJson }}</div>
                </div>
              </template>
            </div>
            <div v-else-if="content(unit.block.contentJson).type === 'reusable_fragment_ref'">
              <template v-for="innerUnit in contentUnitsForBlocks(reusableFragmentRenderBlocks(content(unit.block.contentJson).reusableFragmentId), `fragment-${unit.block.id}`)" :key="innerUnit.key">
                <div v-if="innerUnit.kind === 'image-group'" class="doc-block absolute-flow-group" :style="imageGroupStyle(innerUnit.blocks)">
                  <figure v-for="block in innerUnit.blocks" :key="block.id" class="absolute-flow-image" :style="absoluteImageStyle(block.contentJson)">
                    <img v-if="imageSource(block.contentJson)" :src="imageSource(block.contentJson)" alt="" />
                  </figure>
                </div>
                <div v-else class="doc-block">
                  <component :is="headingTag(innerUnit.block)" v-if="innerUnit.block.blockType === 'HEADING'" :class="headingClass(innerUnit.block)">{{ headingText(innerUnit.block) }}</component>
                  <p v-else-if="innerUnit.block.blockType === 'PARAGRAPH'">{{ content(innerUnit.block.contentJson).text }}</p>
                  <ul v-else-if="innerUnit.block.blockType === 'UNORDERED_LIST'">
                    <li v-for="item in content(innerUnit.block.contentJson).items" :key="item">{{ item }}</li>
                  </ul>
                  <ol v-else-if="innerUnit.block.blockType === 'ORDERED_LIST'">
                    <li v-for="item in content(innerUnit.block.contentJson).items" :key="item">{{ item }}</li>
                  </ol>
                  <div v-else-if="innerUnit.block.blockType === 'NOTE'" class="note">
                    <strong>{{ content(innerUnit.block.contentJson).title || 'Nota' }}</strong>
                    <p>{{ content(innerUnit.block.contentJson).text }}</p>
                  </div>
                  <div v-else class="text-muted">{{ innerUnit.block.contentJson }}</div>
                </div>
              </template>
            </div>
            <component :is="headingTag(unit.block)" v-else-if="unit.block.blockType === 'HEADING'" :class="headingClass(unit.block)">
              <span class="heading-number">{{ headingNumber(unit.block) }}</span>
              <span>{{ headingText(unit.block) }}</span>
            </component>
            <p v-else-if="content(unit.block.contentJson).type === 'link'"><a :href="content(unit.block.contentJson).href" target="_blank">{{ content(unit.block.contentJson).text }}</a></p>
            <p v-else-if="unit.block.blockType === 'PARAGRAPH'">{{ content(unit.block.contentJson).text }}</p>
            <ul v-else-if="unit.block.blockType === 'UNORDERED_LIST'">
              <li v-for="item in content(unit.block.contentJson).items" :key="item">{{ item }}</li>
            </ul>
            <ol v-else-if="unit.block.blockType === 'ORDERED_LIST'">
              <li v-for="item in content(unit.block.contentJson).items" :key="item">{{ item }}</li>
            </ol>
            <table
              v-else-if="unit.block.blockType === 'TABLE'"
              class="doc-table"
              :class="{ 'doc-table-compact': isWideTable(unit.block.contentJson) }"
              :style="tableStyle(unit.block.contentJson)"
            >
              <colgroup v-if="tableColumnWidths(unit.block.contentJson).length">
                <col
                  v-for="(columnWidth, columnIndex) in tableColumnWidths(unit.block.contentJson)"
                  :key="`col-${columnIndex}`"
                  :style="tableColumnWidthStyle(columnWidth)"
                />
              </colgroup>
              <tbody>
                <tr v-for="(row, rowIndex) in tableCellRows(unit.block.contentJson)" :key="rowIndex">
                  <component
                    :is="cell.header ? 'th' : 'td'"
                    v-for="(cell, cellIndex) in row"
                    :key="`${cell.header ? 'h' : 'c'}-${cellIndex}`"
                    :colspan="cell.colspan"
                    :rowspan="cell.rowspan"
                  >
                    {{ cell.text }}
                  </component>
                </tr>
              </tbody>
            </table>
            <div v-else-if="unit.block.blockType === 'WARNING'" class="warning">ADVERTENCIA: {{ content(unit.block.contentJson).text }}</div>
            <div v-else-if="unit.block.blockType === 'NOTE'" class="note">
              <strong>{{ content(unit.block.contentJson).title || 'Nota' }}</strong>
              <p>{{ content(unit.block.contentJson).text }}</p>
            </div>
            <div v-else-if="unit.block.blockType === 'FORMULA'" class="formula" :style="movableBlockStyle(unit.block.contentJson)">{{ content(unit.block.contentJson).latex }}</div>
            <figure v-else-if="unit.block.blockType === 'IMAGE'" class="doc-image" :style="imageFigureStyle(unit.block.contentJson)">
              <img v-if="imageSource(unit.block.contentJson)" :src="imageSource(unit.block.contentJson)" :style="{ width: imageWidth(unit.block.contentJson), height: imageHeight(unit.block.contentJson) }" alt="" />
              <figcaption v-if="content(unit.block.contentJson).caption">{{ content(unit.block.contentJson).caption }}</figcaption>
            </figure>
            <div v-else class="text-muted">{{ unit.block.contentJson }}</div>
          </div>
        </template>
      </main>
      <footer v-if="layoutConfig().footer.enabled" class="paper-footer">{{ layoutConfig().footer.showPageNumber ? pageIndex + 3 : '' }}</footer>
    </article>

    <article v-for="blankIndex in blankPages" :key="`blank-${blankIndex}`" class="manual-page blank-page">
      <header v-if="headerConfig().enabled" class="paper-header">
        <div v-if="headerConfig().showLogo" class="logo" :class="{ 'logo-image': logoSrc() }">
          <img v-if="logoSrc()" :src="logoSrc()" alt="Logo plantilla" />
          <span v-else>DK</span>
        </div>
        <strong v-if="headerConfig().showCompanyName">{{ templateCompany() }}</strong>
        <span v-if="headerConfig().showManualCode">Ref.: {{ manual.code }} · {{ activeLanguage() }} · v{{ manual.activeVersion?.versionNumber }}</span>
      </header>
      <div class="header-line"></div>
      <main class="paper-content"></main>
      <footer v-if="layoutConfig().footer.enabled" class="paper-footer">{{ layoutConfig().footer.showPageNumber ? totalPages + blankIndex : '' }}</footer>
    </article>

    <div class="pagination-measure" aria-hidden="true">
      <article class="manual-page measure-page">
        <header class="paper-header">
          <div class="logo"><span>DK</span></div>
          <strong>{{ templateCompany() }}</strong>
          <span>Ref.: {{ manual.code }} · {{ activeLanguage() }} · v{{ manual.activeVersion?.versionNumber }}</span>
        </header>
        <div class="header-line"></div>
        <main ref="measurePageRef" class="paper-content">
          <div ref="measurementRef">
            <template v-for="unit in contentUnits" :key="unit.key">
              <div class="measure-item">
                <section v-if="unit.kind === 'section'" class="doc-section-title">
                  <h2>{{ sectionTitle(unit.section, unit.index) }}</h2>
                </section>
                <div v-else-if="unit.kind === 'image-group'" class="doc-block absolute-flow-group" :style="imageGroupStyle(unit.blocks)">
                  <figure v-for="block in unit.blocks" :key="block.id" class="absolute-flow-image" :style="absoluteImageStyle(block.contentJson)">
                    <img v-if="imageSource(block.contentJson)" :src="imageSource(block.contentJson)" alt="" />
                  </figure>
                </div>
                <div v-else class="doc-block">
                  <div v-if="content(unit.block.contentJson).type === 'notice_ref'" class="linked-note">
                    <strong>{{ noticeTitle(noticeById(content(unit.block.contentJson).noticeTemplateId)) }}</strong>
                    <p>{{ noticeContent(noticeById(content(unit.block.contentJson).noticeTemplateId)) }}</p>
                  </div>
                  <div v-else-if="content(unit.block.contentJson).type === 'reusable_block_ref'" class="linked-block">
                    <strong>{{ reusableBlockById(content(unit.block.contentJson).reusableBlockId)?.title || 'Sección reutilizable' }}</strong>
                    <template v-for="innerUnit in contentUnitsForBlocks(reusableRenderBlocks(content(unit.block.contentJson).reusableBlockId), `measure-reusable-${unit.block.id}`)" :key="innerUnit.key">
                      <div v-if="innerUnit.kind === 'image-group'" class="doc-block absolute-flow-group" :style="imageGroupStyle(innerUnit.blocks)">
                        <figure v-for="block in innerUnit.blocks" :key="block.id" class="absolute-flow-image" :style="absoluteImageStyle(block.contentJson)">
                          <img v-if="imageSource(block.contentJson)" :src="imageSource(block.contentJson)" alt="" />
                        </figure>
                      </div>
                      <div v-else class="doc-block">
                        <component :is="headingTag(innerUnit.block)" v-if="innerUnit.block.blockType === 'HEADING'" :class="headingClass(innerUnit.block)">{{ headingText(innerUnit.block) }}</component>
                        <p v-else-if="innerUnit.block.blockType === 'PARAGRAPH'">{{ content(innerUnit.block.contentJson).text }}</p>
                        <div v-else class="text-muted">{{ innerUnit.block.contentJson }}</div>
                      </div>
                    </template>
                  </div>
                  <div v-else-if="content(unit.block.contentJson).type === 'reusable_fragment_ref'">
                    <template v-for="innerUnit in contentUnitsForBlocks(reusableFragmentRenderBlocks(content(unit.block.contentJson).reusableFragmentId), `measure-fragment-${unit.block.id}`)" :key="innerUnit.key">
                      <div v-if="innerUnit.kind === 'image-group'" class="doc-block absolute-flow-group" :style="imageGroupStyle(innerUnit.blocks)">
                        <figure v-for="block in innerUnit.blocks" :key="block.id" class="absolute-flow-image" :style="absoluteImageStyle(block.contentJson)">
                          <img v-if="imageSource(block.contentJson)" :src="imageSource(block.contentJson)" alt="" />
                        </figure>
                      </div>
                      <div v-else class="doc-block">
                        <component :is="headingTag(innerUnit.block)" v-if="innerUnit.block.blockType === 'HEADING'" :class="headingClass(innerUnit.block)">{{ headingText(innerUnit.block) }}</component>
                        <p v-else-if="innerUnit.block.blockType === 'PARAGRAPH'">{{ content(innerUnit.block.contentJson).text }}</p>
                        <div v-else class="text-muted">{{ innerUnit.block.contentJson }}</div>
                      </div>
                    </template>
                  </div>
                  <component :is="headingTag(unit.block)" v-else-if="unit.block.blockType === 'HEADING'" :class="headingClass(unit.block)">
                    <span class="heading-number">{{ headingNumber(unit.block) }}</span>
                    <span>{{ headingText(unit.block) }}</span>
                  </component>
                  <p v-else-if="unit.block.blockType === 'PARAGRAPH'">{{ content(unit.block.contentJson).text }}</p>
                  <ul v-else-if="unit.block.blockType === 'UNORDERED_LIST'">
                    <li v-for="item in content(unit.block.contentJson).items" :key="item">{{ item }}</li>
                  </ul>
                  <ol v-else-if="unit.block.blockType === 'ORDERED_LIST'">
                    <li v-for="item in content(unit.block.contentJson).items" :key="item">{{ item }}</li>
                  </ol>
                  <table
                    v-else-if="unit.block.blockType === 'TABLE'"
                    class="doc-table"
                    :class="{ 'doc-table-compact': isWideTable(unit.block.contentJson) }"
                    :style="tableStyle(unit.block.contentJson)"
                  >
                    <colgroup v-if="tableColumnWidths(unit.block.contentJson).length">
                      <col
                        v-for="(columnWidth, columnIndex) in tableColumnWidths(unit.block.contentJson)"
                        :key="`mcol-${columnIndex}`"
                        :style="tableColumnWidthStyle(columnWidth)"
                      />
                    </colgroup>
                    <tbody>
                      <tr v-for="(row, rowIndex) in tableCellRows(unit.block.contentJson)" :key="rowIndex">
                        <component
                          :is="cell.header ? 'th' : 'td'"
                          v-for="(cell, cellIndex) in row"
                          :key="`m${cell.header ? 'h' : 'c'}-${cellIndex}`"
                          :colspan="cell.colspan"
                          :rowspan="cell.rowspan"
                        >
                          {{ cell.text }}
                        </component>
                      </tr>
                    </tbody>
                  </table>
                  <div v-else-if="unit.block.blockType === 'WARNING'" class="warning">ADVERTENCIA: {{ content(unit.block.contentJson).text }}</div>
                  <div v-else-if="unit.block.blockType === 'NOTE'" class="note">NOTA: {{ content(unit.block.contentJson).text }}</div>
                  <div v-else-if="unit.block.blockType === 'FORMULA'" class="formula" :style="movableBlockStyle(unit.block.contentJson)">{{ content(unit.block.contentJson).latex }}</div>
                  <figure v-else-if="unit.block.blockType === 'IMAGE'" class="doc-image" :style="imageFigureStyle(unit.block.contentJson)">
                    <img v-if="imageSource(unit.block.contentJson)" :src="imageSource(unit.block.contentJson)" :style="{ width: imageWidth(unit.block.contentJson), height: imageHeight(unit.block.contentJson) }" alt="" />
                    <figcaption v-if="content(unit.block.contentJson).caption">{{ content(unit.block.contentJson).caption }}</figcaption>
                  </figure>
                  <div v-else class="text-muted">{{ unit.block.contentJson }}</div>
                </div>
              </div>
            </template>
          </div>
        </main>
        <footer class="paper-footer">{{ totalPages }}</footer>
      </article>
    </div>
  </div>
</template>

<style scoped>
.manual-pages {
  display: grid;
  gap: 22px;
  justify-items: center;
}

.manual-page {
  width: min(210mm, 100%);
  min-height: 297mm;
  padding: var(--tpl-margin-top, 14mm) var(--tpl-margin-right, 14mm) var(--tpl-margin-bottom, 14mm) var(--tpl-margin-left, 14mm);
  background: var(--tpl-page-bg, #fff);
  border: 1px solid var(--tpl-border, var(--border));
  box-shadow: 0 10px 30px rgba(0,0,0,.08);
  display: grid;
  grid-template-rows: auto auto 1fr auto;
  overflow: hidden;
  color: var(--tpl-text, var(--foreground));
  font-family: var(--tpl-font, Arial), Arial, sans-serif;
  font-size: var(--tpl-base-size, 11px);
}

.manual-page,
.manual-page * {
  box-sizing: border-box;
}

.paper-header {
  min-height: var(--tpl-header-height, 10mm);
  background: var(--tpl-header-bg, #fff);
  color: var(--tpl-header-text, var(--foreground));
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.paper-header span {
  margin-left: auto;
  font-size: 11px;
  color: var(--tpl-muted, var(--muted-foreground));
}

.logo,
.cover-logo {
  width: 52px;
  height: 30px;
  display: grid;
  place-items: center;
  background: var(--tpl-primary, var(--dikoin-blue));
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  overflow: hidden;
}

.logo.logo-image,
.cover-logo.logo-image {
  width: 34mm;
  height: 10mm;
  background: transparent;
  color: inherit;
}

.cover-logo.logo-image {
  width: 42mm;
  height: 14mm;
}

.logo img,
.cover-logo img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  object-position: left center;
  background: transparent;
}

.header-line {
  height: 2px;
  background: var(--tpl-header-border, var(--tpl-primary, var(--dikoin-blue)));
  margin: 6px 0 18px;
}

.paper-content {
  min-height: 0;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
}

.cover-page {
  grid-template-rows: auto 1fr auto;
}

.cover-mark {
  display: grid;
  justify-items: center;
  gap: 8px;
}

.cover-content {
  align-self: stretch;
  display: grid;
  grid-template-rows: minmax(250px, 1fr) auto auto;
  gap: 22px;
  align-items: end;
  justify-items: center;
  text-align: center;
}

.cover-content > h1,
.cover-content > p:not(.manual-code),
.cover-content > span {
  display: none;
}

.cover-content h1 {
  max-width: 620px;
  margin: 0;
  color: var(--tpl-secondary, var(--dikoin-blue-dark));
  font-size: var(--tpl-cover-title-size, 32px);
  line-height: 1.12;
  letter-spacing: 0;
}

.cover-content p,
.cover-content span {
  margin: 0;
  color: var(--tpl-muted, var(--muted-foreground));
}

.cover-product {
  align-self: center;
  width: 100%;
  min-height: 250px;
  display: grid;
  place-items: center;
}

.cover-product img {
  max-width: 88%;
  max-height: 330px;
  object-fit: contain;
}

.cover-title-block {
  width: 100%;
  display: grid;
  justify-items: center;
  gap: 6px;
}

.cover-title-block p {
  color: var(--tpl-primary, var(--dikoin-blue));
  font-size: var(--tpl-cover-subtitle-size, 15px);
  font-weight: 600;
  border-top: 1px solid var(--tpl-primary, var(--dikoin-blue));
  width: min(100%, 650px);
  padding-top: 6px;
}

.cover-title-block strong {
  width: min(100%, 650px);
  background: var(--tpl-primary, var(--dikoin-blue));
  color: #fff;
  padding: 5px 10px;
}

.manual-code {
  font-family: Consolas, Monaco, 'Courier New', monospace;
  justify-self: start;
  align-self: end;
  font-size: var(--tpl-cover-code-size, 11px);
  font-weight: 400;
  color: var(--tpl-muted, var(--muted-foreground)) !important;
}

.toc-page h1 {
  margin: 0 0 22px;
  color: var(--tpl-secondary, var(--dikoin-blue-dark));
  font-size: var(--tpl-index-title-size, 24px);
}

.toc-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 9px;
  line-height: var(--tpl-index-line-height, 1.35);
}

.toc-list li {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 8px;
  align-items: baseline;
  font-size: var(--tpl-index-level-1, 13px);
}

.toc-list .toc-level-0 {
  font-weight: 600;
  color: var(--tpl-secondary, var(--dikoin-blue-dark));
}

.toc-list .toc-level-1 {
  padding-left: 14px;
}

.toc-list .toc-level-2 {
  padding-left: 30px;
  font-size: var(--tpl-index-level-2, 12px);
}

.toc-list .toc-level-3 {
  padding-left: 46px;
  font-size: var(--tpl-index-level-3, 11px);
  color: var(--tpl-muted, var(--muted-foreground));
}

.toc-dots {
  min-width: 24px;
  border-bottom: 1px dotted var(--tpl-border, #8fa9bb);
}

.toc-list.no-dots .toc-dots {
  border-bottom-color: transparent;
}

.doc-section-title h2 {
  margin: 0 0 10px;
  background: var(--tpl-section-bg, var(--dikoin-blue));
  color: var(--tpl-section-color, #fff);
  padding: 6px 9px;
  font-size: var(--tpl-section-size, 14px);
  line-height: 1.25;
}

.doc-block {
  margin: 0 0 9px;
  font-size: var(--tpl-body-size, 12px);
  line-height: var(--tpl-line-height, 1.42);
  min-width: 0;
  max-width: 100%;
  overflow-wrap: break-word;
  word-break: normal;
}

.doc-block p,
.doc-block ul,
.doc-block ol {
  margin: 0 0 8px;
  max-width: 100%;
  overflow-wrap: break-word;
  word-break: normal;
}

.doc-block ul,
.doc-block ol {
  padding-left: 20px;
}

.doc-block li {
  max-width: 100%;
  overflow-wrap: break-word;
  word-break: normal;
}

.doc-block a {
  overflow-wrap: anywhere;
}

.doc-heading {
  margin: 0 0 8px;
  color: var(--dikoin-blue);
  line-height: 1.25;
}

.doc-heading-level-1 {
  font-size: 14px;
  font-weight: 600;
}

.doc-heading-level-2 {
  font-size: 13px;
  font-weight: 600;
  color: var(--dikoin-blue-dark);
}

.doc-heading-level-3 {
  font-size: 12px;
  font-weight: 600;
  color: var(--foreground);
}

.heading-number {
  margin-right: 6px;
  color: var(--dikoin-blue-dark);
  font-weight: 600;
}

.doc-table {
  width: 100%;
  max-width: 100%;
  table-layout: fixed;
  border-collapse: collapse;
  font-size: var(--tpl-table-size, 11px);
}

.doc-table-compact {
  font-size: calc(var(--tpl-table-size, 11px) - 1px);
}

.doc-table th {
  background: var(--tpl-table-head-bg, var(--dikoin-blue));
  color: var(--tpl-table-head-color, #fff);
  text-align: left;
  padding: var(--tpl-table-padding, 6px);
  overflow-wrap: break-word;
  word-break: normal;
  hyphens: auto;
}

.doc-table td {
  border: 1px solid var(--tpl-table-border, #b8cce3);
  padding: var(--tpl-table-padding, 6px);
  overflow-wrap: break-word;
  word-break: normal;
  hyphens: auto;
}

.doc-table-compact th,
.doc-table-compact td {
  padding: 4px;
  line-height: 1.25;
}

.doc-table tr:nth-child(odd) td {
  background: color-mix(in srgb, var(--tpl-primary, var(--dikoin-blue)) 10%, #fff);
}

.warning {
  border-left: 4px solid var(--dikoin-orange);
  background: var(--dikoin-orange-light);
  padding: 9px;
}

.note {
  border-left: 4px solid var(--dikoin-orange);
  background: #fff7ed;
  color: #78350f;
  padding: 9px;
}

.note p {
  margin: 6px 0 0;
}

.linked-note {
  border-left: 4px solid var(--dikoin-orange);
  background: #fff7ed;
  color: #78350f;
  padding: 10px;
}

.linked-note p {
  margin: 6px 0 0;
  color: inherit;
}

.linked-block {
  border: 1px solid var(--border);
  border-left: 4px solid var(--dikoin-blue);
  padding: 10px;
  background: #f8fbfe;
}

.linked-block > strong {
  color: var(--dikoin-blue-dark);
}

.formula {
  font-family: Georgia, serif;
  font-size: 22px;
  padding: 12px;
}

.doc-image {
  margin: 10px 0;
}

.doc-image img {
  display: block;
  max-width: 100%;
  height: auto;
  object-fit: contain;
}

.absolute-flow-group {
  position: relative;
  width: 100%;
  height: var(--absolute-flow-height);
  margin-top: 12px;
  margin-bottom: 12px;
  overflow: visible;
  background: transparent;
  border: none;
  outline: none;
  break-inside: avoid;
  page-break-inside: avoid;
}

.absolute-flow-image {
  position: absolute;
  margin: 0;
}

.absolute-flow-image img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: fill;
}

.doc-image figcaption {
  margin-top: 4px;
  color: var(--muted-foreground);
  font-size: 10px;
}

.paper-footer {
  justify-self: center;
  align-self: end;
  min-height: var(--tpl-footer-height, 18px);
  min-width: 42px;
  background: var(--tpl-footer-bg, transparent);
  color: var(--tpl-footer-text, var(--muted-foreground));
  border-top: 1px solid var(--tpl-footer-border, transparent);
  padding: 3px 8px;
  font-size: 11px;
}

.pagination-measure {
  position: fixed;
  left: -10000px;
  top: 0;
  visibility: hidden;
  pointer-events: none;
}

.measure-page {
  height: 297mm;
  min-height: 297mm;
  box-shadow: none;
}

.measure-item {
  display: flow-root;
}

@media print {
  @page {
    size: A4;
    margin: 0;
  }

  :global(html),
  :global(body),
  :global(#app) {
    width: 210mm;
    min-height: 100%;
    background: #fff;
  }

  .manual-pages {
    gap: 0;
  }

  .manual-page {
    width: 210mm;
    height: 297mm;
    border: 0;
    box-shadow: none;
    page-break-after: always;
    break-after: page;
  }

  .manual-pages > .manual-page:last-of-type {
    page-break-after: auto;
    break-after: auto;
  }
}
</style>
