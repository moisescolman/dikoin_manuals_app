<script setup lang="ts">
import type { CSSProperties } from 'vue'
import { computed } from 'vue'
import { toBackendUrl } from '@/api/http'
import type { LanguageCode, ReusableBlockResponse, ReusableFragmentResponse } from '@/types/api'

type Snapshot = {
  blockType: string
  languageCode?: LanguageCode
  contentJson: string | Record<string, unknown>
  plainText?: string
}

type TableCellRender = {
  text: string
  colspan: number
  rowspan: number
  header: boolean
}

const props = defineProps<{
  item?: ReusableBlockResponse | ReusableFragmentResponse
  language: LanguageCode
}>()

const parsed = computed(() => {
  try {
    return props.item ? JSON.parse(props.item.contentJson) : {}
  } catch {
    return {}
  }
})

const title = computed(() => {
  if (!props.item) return props.language === 'EN' ? 'Content' : 'Contenido'
  if (!('titleEs' in props.item)) return props.item.title
  return props.language === 'EN'
    ? props.item.titleEn || props.item.title || 'Content'
    : props.item.titleEs || props.item.title || 'Contenido'
})

const blocks = computed<Snapshot[]>(() => {
  const value = Array.isArray(parsed.value.blocks) ? parsed.value.blocks as Snapshot[] : []
  return value.filter((block) => !block.languageCode || block.languageCode === props.language)
})

const pages = computed(() => {
  const result: Array<Array<{ block: Snapshot; index: number }>> = []
  let page: Array<{ block: Snapshot; index: number }> = []
  let used = 0
  blocks.value.forEach((block, index) => {
    const weight = blockWeight(block)
    if (page.length && used + weight > 760) {
      result.push(page)
      page = []
      used = 0
    }
    page.push({ block, index })
    used += weight
  })
  if (page.length || !result.length) result.push(page)
  return result
})

function content(block: Snapshot) {
  if (typeof block.contentJson === 'object') return block.contentJson
  try {
    return JSON.parse(block.contentJson)
  } catch {
    return { text: block.plainText || block.contentJson }
  }
}

function plainTextFromJson(node: Record<string, any> | undefined): string {
  if (!node) return ''
  const parts: string[] = []
  const visit = (value: any) => {
    if (!value) return
    if (typeof value.text === 'string') parts.push(value.text)
    if (Array.isArray(value.content)) value.content.forEach(visit)
  }
  visit(node)
  return parts.join('')
}

function text(block: Snapshot) {
  const value = content(block) as Record<string, any>
  return value.text || value.title || value.caption || value.latex || block.plainText || ''
}

function items(block: Snapshot) {
  const value = content(block) as Record<string, any>
  return Array.isArray(value.items) ? value.items : []
}

function rows(block: Snapshot) {
  const value = content(block) as Record<string, any>
  const columns = Array.isArray(value.columns) ? value.columns : []
  const body = Array.isArray(value.rows) ? value.rows : []
  return columns.length ? [columns, ...body] : body
}

function tableCellRows(block: Snapshot): TableCellRender[][] {
  const parsed = content(block) as Record<string, any>
  const tableJson = parsed.json
  if (tableJson && Array.isArray(tableJson.content)) {
    return tableJson.content.map((row: Record<string, any>, rowIndex: number) => {
      if (!Array.isArray(row.content)) return []
      return row.content.map((cell: Record<string, any>) => ({
        text: plainTextFromJson(cell).replace(/\n+/g, ' ').trim(),
        colspan: Math.max(1, Number(cell.attrs?.colspan || 1)),
        rowspan: Math.max(1, Number(cell.attrs?.rowspan || 1)),
        header: cell.type === 'tableHeader' || (rowIndex === 0 && hasHeader(block)),
      }))
    })
  }

  return rows(block).map((row: string[], rowIndex: number) => row.map((cell) => ({
    text: cell,
    colspan: 1,
    rowspan: 1,
    header: rowIndex === 0 && hasHeader(block),
  })))
}

function imageSource(block: Snapshot) {
  const value = content(block) as Record<string, any>
  const src = value.src || value.text || value.json?.attrs?.src || ''
  return src ? toBackendUrl(String(src)) : ''
}

function hasHeader(block: Snapshot) {
  const value = content(block) as Record<string, any>
  return value.hasHeader ?? value.json?.attrs?.hasHeader ?? true
}

function imageFigureStyle(block: Snapshot): CSSProperties {
  const value = content(block) as Record<string, any>
  const align = value.json?.attrs?.align || value.align || 'inline'
  if (align === 'center') return { display: 'flex', flexDirection: 'column', alignItems: 'center' }
  if (align === 'right') return { display: 'flex', flexDirection: 'column', alignItems: 'flex-end' }
  if (align === 'inline') return { display: 'inline-block', marginLeft: '0', marginRight: '8px', verticalAlign: 'top' }
  return { display: 'flex', flexDirection: 'column', alignItems: 'flex-start' }
}

function movableBlockStyle(block: Snapshot): CSSProperties {
  const value = content(block) as Record<string, any>
  const align = value.json?.attrs?.align || value.align || 'left'
  const width = value.json?.attrs?.width || value.width
  return {
    width: cssSize(width),
    marginLeft: align === 'center' || align === 'right' ? 'auto' : '0',
    marginRight: align === 'center' ? 'auto' : align === 'right' ? '0' : 'auto',
  }
}

function tableStyle(block: Snapshot): CSSProperties {
  if (isWideTable(block)) {
    return {
      ...movableBlockStyle(block),
      width: '100%',
      maxWidth: '100%',
    }
  }
  const width = readableTableWidth(tableWidth(block), tableMinimumWidth(block))
  return {
    ...movableBlockStyle(block),
    width,
  }
}

function tableWidth(block: Snapshot) {
  const value = content(block) as Record<string, any>
  const width = value.json?.attrs?.width || value.width
  const textValue = String(width || '')
  if (!textValue) return '100%'
  return /^\d+(\.\d+)?$/.test(textValue) ? `${textValue}px` : textValue
}

function tableColumnWidths(block: Snapshot): Array<number | undefined> {
  if (isWideTable(block)) return []
  const parsed = content(block) as Record<string, any>
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
        if (Number.isFinite(width) && width > 0 && !widths[columnIndex]) widths[columnIndex] = width
        textLengths[columnIndex] = Math.max(textLengths[columnIndex] || 0, Math.ceil(textLength / colspan))
        columnIndex += 1
      }
    })
  })

  const columnCount = Math.max(widths.length, textLengths.length)
  for (let columnIndex = 0; columnIndex < columnCount; columnIndex += 1) {
    if (!widths[columnIndex]) widths[columnIndex] = inferredTableColumnWidth(textLengths[columnIndex] || 0, columnCount)
  }
  return widths
}

function tableColumnWidthStyle(width?: number) {
  return width ? { width: `${width}px` } : undefined
}

function tableColumnCount(block: Snapshot) {
  return tableCellRows(block).reduce((max, row) => {
    const count = row.reduce((total, cell) => total + cell.colspan, 0)
    return Math.max(max, count)
  }, 0)
}

function isWideTable(block: Snapshot) {
  return tableColumnCount(block) >= 7
}

function inferredTableColumnWidth(textLength: number, columnCount: number) {
  if (textLength <= 3) return 42
  if (textLength <= 10) return columnCount >= 5 ? 72 : 90
  const charWidth = columnCount >= 5 ? 5.8 : 6.8
  const baseWidth = Math.ceil(textLength * charWidth)
  return Math.min(columnCount >= 5 ? 170 : 260, Math.max(columnCount >= 5 ? 96 : 150, baseWidth))
}

function tableMinimumWidth(block: Snapshot) {
  const widths = tableColumnWidths(block).filter((width): width is number => Boolean(width))
  if (!widths.length) return 0
  return Math.min(widths.reduce((total, width) => total + width, 0), 680)
}

function readableTableWidth(width: string | undefined, minimumWidth: number) {
  if (!minimumWidth) return width || '100%'
  const safeWidth = `min(100%, ${minimumWidth}px)`
  if (!width) return safeWidth
  const match = width.match(/^(\d+(?:\.\d+)?)px$/)
  if (match && Number(match[1]) < minimumWidth) return safeWidth
  return width
}

function imageWidth(block: Snapshot) {
  const value = content(block) as Record<string, any>
  return cssSize(value.json?.attrs?.width || value.width)
}

function imageHeight(block: Snapshot) {
  const value = content(block) as Record<string, any>
  return cssSize(value.json?.attrs?.height || value.height)
}

function imageCaption(block: Snapshot) {
  return String((content(block) as Record<string, any>).caption || '')
}

function cssSize(value: unknown) {
  if (!value) return undefined
  const textValue = String(value)
  return /^\d+(\.\d+)?$/.test(textValue) ? `${textValue}px` : textValue
}

function headingClass(block: Snapshot) {
  const level = Math.min(3, Math.max(1, Number((content(block) as Record<string, any>).level || 1)))
  return `doc-heading doc-heading-level-${level}`
}

function blockWeight(block: Snapshot) {
  if (block.blockType === 'IMAGE') return 320
  if (block.blockType === 'TABLE') return 90 + rows(block).length * 38
  if (block.blockType === 'HEADING') return 70
  return Math.max(55, Math.ceil((text(block).length || items(block).join(' ').length) / 75) * 28)
}

function headingNumber(pageBlock: { block: Snapshot; index: number }) {
  const headingIndex = blocks.value
    .slice(0, pageBlock.index + 1)
    .filter((block) => block.blockType === 'HEADING')
    .length
  return `1.${headingIndex}`
}
</script>

<template>
  <section class="preview-shell">
    <article v-for="(pageBlocks, pageIndex) in pages" :key="pageIndex" class="page">
      <header v-if="pageIndex === 0">
        <span>1.</span>
        <h2>{{ title }}</h2>
      </header>
      <div v-else class="continuation">{{ title }} · continuación</div>

      <main class="paper-content">
        <p v-if="!blocks.length" class="empty">No hay contenido en {{ language }}.</p>

        <template v-for="entry in pageBlocks" :key="entry.index">
          <h3 v-if="entry.block.blockType === 'HEADING'" :class="headingClass(entry.block)">
            <span class="heading-number">{{ headingNumber(entry) }}</span>
            <span>{{ text(entry.block) }}</span>
          </h3>
          <div v-else class="doc-block">
            <ul v-if="entry.block.blockType === 'UNORDERED_LIST'">
              <li v-for="listEntry in items(entry.block)" :key="listEntry">{{ listEntry }}</li>
            </ul>
            <ol v-else-if="entry.block.blockType === 'ORDERED_LIST'">
              <li v-for="listEntry in items(entry.block)" :key="listEntry">{{ listEntry }}</li>
            </ol>
            <table
              v-else-if="entry.block.blockType === 'TABLE'"
              class="doc-table"
              :class="{ 'doc-table-compact': isWideTable(entry.block) }"
              :style="tableStyle(entry.block)"
            >
              <colgroup v-if="tableColumnWidths(entry.block).length">
                <col
                  v-for="(columnWidth, columnIndex) in tableColumnWidths(entry.block)"
                  :key="`col-${columnIndex}`"
                  :style="tableColumnWidthStyle(columnWidth)"
                />
              </colgroup>
              <tbody>
                <tr v-for="(row, rowIndex) in tableCellRows(entry.block)" :key="rowIndex">
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
            <figure
              v-else-if="entry.block.blockType === 'IMAGE'"
              class="doc-image"
              :style="imageFigureStyle(entry.block)"
            >
              <img v-if="imageSource(entry.block)" :src="imageSource(entry.block)" :style="{ width: imageWidth(entry.block), height: imageHeight(entry.block) }" alt="" />
              <figcaption v-if="imageCaption(entry.block)">{{ imageCaption(entry.block) }}</figcaption>
            </figure>
            <aside v-else-if="['NOTE', 'WARNING', 'INFO_BOX'].includes(entry.block.blockType)" class="note">
              {{ text(entry.block) }}
            </aside>
            <div v-else-if="entry.block.blockType === 'FORMULA'" class="formula" :style="movableBlockStyle(entry.block)">{{ text(entry.block) }}</div>
            <p v-else>{{ text(entry.block) }}</p>
          </div>
        </template>
      </main>
      <footer>{{ pageIndex + 1 }}</footer>
    </article>
  </section>
</template>

<style scoped>
.preview-shell {
  overflow: auto;
  padding: 18px;
  background: #e8edf1;
  display: grid;
  gap: 18px;
  justify-items: center;
}

.page {
  width: min(210mm, 100%);
  min-height: 297mm;
  padding: 14mm;
  background: #fff;
  border: 1px solid var(--border);
  box-shadow: 0 10px 30px rgba(0,0,0,.08);
  color: #111827;
  font-family: Arial, sans-serif;
  display: grid;
  grid-template-rows: auto 1fr auto;
  overflow: hidden;
}

.page,
.page * {
  box-sizing: border-box;
}

header {
  display: flex;
  gap: 10px;
  align-items: baseline;
  border-bottom: 2px solid var(--dikoin-blue);
}

.paper-content {
  min-height: 0;
  padding-top: 0;
}

header span,
h2,
h3 {
  color: var(--dikoin-blue-dark);
}

h2 {
  margin: 0 0 8px;
  font-size: 24px;
  line-height: 1.2;
}

h3 {
  margin: 0 0 8px;
}

.doc-block {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.45;
  color: #1f2937;
  overflow-wrap: break-word;
  word-break: normal;
}

.doc-block p,
.doc-block ul,
.doc-block ol {
  margin: 0 0 8px;
  white-space: pre-wrap;
}

.doc-block ul,
.doc-block ol {
  padding-left: 18px;
}

.doc-block li {
  margin-bottom: 4px;
  overflow-wrap: break-word;
  word-break: normal;
}

.doc-heading {
  margin: 0 0 8px;
  color: var(--dikoin-blue);
  line-height: 1.25;
}

.doc-heading-level-1 {
  margin-top: 20px;
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
  font-size: 11px;
}

.doc-table-compact {
  font-size: 9px;
}

.doc-table th {
  background: var(--dikoin-blue);
  color: #fff;
  text-align: left;
  padding: 6px;
  overflow-wrap: break-word;
  word-break: normal;
  hyphens: auto;
}

.doc-table td {
  border: 1px solid #b8cce3;
  padding: 6px;
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
  background: var(--dikoin-blue-light);
}

.note {
  border-left: 4px solid var(--dikoin-orange);
  background: #fff7ed;
  color: #78350f;
  padding: 9px;
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

.doc-image figcaption {
  margin-top: 4px;
  color: var(--muted-foreground);
  font-size: 10px;
}

.empty { color: #64748b; font-style: italic; }
.continuation { margin-bottom: 18px; padding-bottom: 8px; border-bottom: 1px solid #cbd5e1; color: #64748b; font-size: 9pt; }
footer { justify-self: center; align-self: end; min-height: 18px; color: #64748b; font-size: 9pt; }
</style>
