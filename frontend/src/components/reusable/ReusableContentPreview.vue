<script setup lang="ts">
import { computed } from 'vue'
import { toBackendUrl } from '@/api/http'
import type { LanguageCode, ReusableBlockResponse } from '@/types/api'

type Snapshot = {
  blockType: string
  languageCode?: LanguageCode
  contentJson: string | Record<string, unknown>
  plainText?: string
}

const props = defineProps<{
  item?: ReusableBlockResponse
  language: LanguageCode
}>()

const parsed = computed(() => {
  try {
    return props.item ? JSON.parse(props.item.contentJson) : {}
  } catch {
    return {}
  }
})

const title = computed(() => props.language === 'EN'
  ? props.item?.titleEn || props.item?.title || 'Content'
  : props.item?.titleEs || props.item?.title || 'Contenido')

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

function imageSource(block: Snapshot) {
  return toBackendUrl(String((content(block) as Record<string, any>).src || ''))
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

      <p v-if="!blocks.length" class="empty">No hay contenido en {{ language }}.</p>

      <template v-for="entry in pageBlocks" :key="entry.index">
        <h3 v-if="entry.block.blockType === 'HEADING'">
          <span>{{ headingNumber(entry) }}</span>
          {{ text(entry.block) }}
        </h3>
        <ul v-else-if="entry.block.blockType === 'UNORDERED_LIST'">
          <li v-for="listEntry in items(entry.block)" :key="listEntry">{{ listEntry }}</li>
        </ul>
        <ol v-else-if="entry.block.blockType === 'ORDERED_LIST'">
          <li v-for="listEntry in items(entry.block)" :key="listEntry">{{ listEntry }}</li>
        </ol>
        <table v-else-if="entry.block.blockType === 'TABLE'">
          <tr v-for="(row, rowIndex) in rows(entry.block)" :key="rowIndex">
            <component :is="rowIndex === 0 ? 'th' : 'td'" v-for="(cell, cellIndex) in row" :key="cellIndex">
              {{ cell }}
            </component>
          </tr>
        </table>
        <img v-else-if="entry.block.blockType === 'IMAGE' && imageSource(entry.block)" :src="imageSource(entry.block)" alt="" />
        <aside v-else-if="['NOTE', 'WARNING', 'INFO_BOX'].includes(entry.block.blockType)">
          {{ text(entry.block) }}
        </aside>
        <p v-else>{{ text(entry.block) }}</p>
      </template>
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
  box-shadow: 0 8px 24px rgba(15, 23, 42, .12);
  color: #111827;
  font-family: Arial, sans-serif;
  font-size: 11pt;
  line-height: 1.5;
  position: relative;
}

header {
  display: flex;
  gap: 10px;
  align-items: baseline;
  border-bottom: 2px solid var(--dikoin-blue);
}

header span,
h2,
h3 {
  color: var(--dikoin-blue-dark);
}

h2 {
  margin: 0 0 8px;
  font-size: 18pt;
}

h3 {
  margin: 18px 0 6px;
  font-size: 13pt;
}

h3 span {
  margin-right: 6px;
}

p { white-space: pre-wrap; }
table { width: 100%; border-collapse: collapse; margin: 12px 0; }
th, td { border: 1px solid #94a3b8; padding: 6px 8px; text-align: left; }
th { background: #eaf3fa; }
img { max-width: 100%; height: auto; }
aside { margin: 12px 0; padding: 10px 12px; border-left: 4px solid var(--dikoin-orange); background: #fff7ed; }
.empty { color: #64748b; font-style: italic; }
.continuation { margin-bottom: 18px; padding-bottom: 8px; border-bottom: 1px solid #cbd5e1; color: #64748b; font-size: 9pt; }
footer { position: absolute; right: 14mm; bottom: 8mm; color: #64748b; font-size: 9pt; }
</style>
