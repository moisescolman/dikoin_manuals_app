<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { toBackendUrl } from '@/api/http'
import { getNotices } from '@/api/notices.api'
import { getReusableBlocks } from '@/api/reusable-blocks.api'
import { getActiveTemplate } from '@/api/templates.api'
import type { BlockType, LanguageCode, ManualBlockResponse, ManualDetailResponse, ManualSectionResponse, NoticeTemplateResponse, ReusableBlockResponse, TemplateResponse } from '@/types/api'

const props = defineProps<{ manual: ManualDetailResponse; language?: LanguageCode }>()
const notices = ref<NoticeTemplateResponse[]>([])
const reusableBlocks = ref<ReusableBlockResponse[]>([])
const activeTemplate = ref<TemplateResponse | null>(null)
const measurementRef = ref<HTMLElement | null>(null)
const measurePageRef = ref<HTMLElement | null>(null)
const contentPages = ref<ContentUnit[][]>([])
const renderedLogoSrc = ref('')

const headerDefaults = {
  showLogo: true,
  showCompanyName: true,
  showManualCode: true,
}

interface RenderBlock {
  blockType: BlockType
  languageCode: LanguageCode
  contentJson: string
}

type ContentUnit =
  | { key: string; kind: 'section'; section: ManualSectionResponse; index: number }
  | { key: string; kind: 'block'; block: ManualBlockResponse }

let resizeObserver: ResizeObserver | null = null
let paginationRun = 0

const contentSections = computed(() => {
  return (props.manual.activeVersion?.sections || []).filter((section) => !isGeneratedTocSource(section))
})

const contentUnits = computed<ContentUnit[]>(() => {
  const units: ContentUnit[] = []
  contentSections.value.forEach((section, index) => {
    units.push({ key: `section-${section.id}`, kind: 'section', section, index })
    visibleBlocks(section).forEach((block) => {
      splitBlock(block).forEach((split, splitIndex) => {
        units.push({ key: `block-${block.id}-${splitIndex}`, kind: 'block', block: split })
      })
    })
  })
  return units
})

const tocEntries = computed(() => {
  return contentSections.value.map((section, index) => ({
    key: section.id,
    title: sectionTitle(section, index),
    page: contentPageForSection(section.id),
  }))
})

const totalPages = computed(() => 2 + contentPages.value.length)

onMounted(async () => {
  const [loadedNotices, loadedBlocks] = await Promise.all([
    getNotices(),
    getReusableBlocks(),
  ])
  notices.value = loadedNotices
  reusableBlocks.value = loadedBlocks
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
  () => [props.manual.id, props.manual.activeVersion?.id, props.language, contentUnits.value.length, activeTemplate.value?.id],
  () => schedulePagination(),
)

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

function sectionTitle(section: ManualSectionResponse, index: number) {
  const title = activeLanguage() === 'EN' ? section.titleEn || '' : section.titleEs
  return `${section.sectionNumber || index + 1}. ${title}`
}

function visibleBlocks(section: ManualSectionResponse) {
  return section.blocks.filter((block) => block.languageCode === activeLanguage())
}

function noticeById(id: number) {
  return notices.value.find((notice) => notice.id === id)
}

function reusableBlockById(id: number) {
  return reusableBlocks.value.find((block) => block.id === id)
}

function reusableRenderBlocks(id: number) {
  const reusable = reusableBlockById(id)
  if (!reusable) return []
  try {
    const parsed = JSON.parse(reusable.contentJson)
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

function headerConfig() {
  return parseConfig(activeTemplate.value?.headerConfigJson, headerDefaults)
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
  let currentHeight = 0

  measuredItems.forEach((item, index) => {
    const unit = contentUnits.value[index]
    const height = item.offsetHeight
    const startsNewSection = unit.kind === 'section'
    const shouldBreak = currentPage.length > 0 && (currentHeight + height > maxHeight || (startsNewSection && maxHeight - currentHeight < 140))

    if (shouldBreak) {
      pages.push(currentPage)
      currentPage = []
      currentHeight = 0
    }

    currentPage.push(unit)
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
</script>

<template>
  <div class="manual-pages">
    <article class="manual-page cover-page">
      <div class="cover-mark">
        <div v-if="headerConfig().showLogo" class="cover-logo" :class="{ 'logo-image': logoSrc() }">
          <img v-if="logoSrc()" :src="logoSrc()" alt="Logo plantilla" />
          <span v-else>DK</span>
        </div>
        <strong v-if="headerConfig().showCompanyName">{{ templateCompany() }}</strong>
      </div>
      <div class="cover-content">
        <p class="manual-code">{{ manual.code }}</p>
        <h1>{{ manualTitle() }}</h1>
        <p>{{ manual.productCode }} · {{ manual.productName }}</p>
        <span>v{{ manual.activeVersion?.versionNumber }} · {{ activeLanguage() }}</span>
      </div>
      <footer class="paper-footer">1</footer>
    </article>

    <article class="manual-page toc-page">
      <header class="paper-header">
        <div v-if="headerConfig().showLogo" class="logo" :class="{ 'logo-image': logoSrc() }">
          <img v-if="logoSrc()" :src="logoSrc()" alt="Logo plantilla" />
          <span v-else>DK</span>
        </div>
        <strong v-if="headerConfig().showCompanyName">{{ templateCompany() }}</strong>
        <span v-if="headerConfig().showManualCode">Ref.: {{ manual.code }} · {{ activeLanguage() }} · v{{ manual.activeVersion?.versionNumber }}</span>
      </header>
      <div class="header-line"></div>
      <main class="paper-content">
        <h1>Índice</h1>
        <ol class="toc-list">
          <li v-for="entry in tocEntries" :key="entry.key">
            <span>{{ entry.title }}</span>
            <span class="toc-dots"></span>
            <span>{{ entry.page || '' }}</span>
          </li>
        </ol>
      </main>
      <footer class="paper-footer">2</footer>
    </article>

    <article v-for="(pageUnits, pageIndex) in contentPages" :key="pageIndex" class="manual-page">
      <header class="paper-header">
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
          <div v-else class="doc-block">
            <div v-if="content(unit.block.contentJson).type === 'notice_ref'" class="linked-note">
              <strong>{{ noticeById(content(unit.block.contentJson).noticeTemplateId)?.titleEs || 'Nota' }}</strong>
              <p>{{ noticeById(content(unit.block.contentJson).noticeTemplateId)?.contentEs || 'Nota no encontrada' }}</p>
            </div>
            <div v-else-if="content(unit.block.contentJson).type === 'reusable_block_ref'" class="linked-block">
              <strong>{{ reusableBlockById(content(unit.block.contentJson).reusableBlockId)?.title || 'Bloque común' }}</strong>
              <div v-for="(innerBlock, innerIndex) in reusableRenderBlocks(content(unit.block.contentJson).reusableBlockId)" :key="innerIndex" class="doc-block">
                <h3 v-if="innerBlock.blockType === 'HEADING'">{{ content(innerBlock.contentJson).text }}</h3>
                <p v-else-if="innerBlock.blockType === 'PARAGRAPH'">{{ content(innerBlock.contentJson).text }}</p>
                <ul v-else-if="innerBlock.blockType === 'UNORDERED_LIST'">
                  <li v-for="item in content(innerBlock.contentJson).items" :key="item">{{ item }}</li>
                </ul>
                <ol v-else-if="innerBlock.blockType === 'ORDERED_LIST'">
                  <li v-for="item in content(innerBlock.contentJson).items" :key="item">{{ item }}</li>
                </ol>
                <div v-else-if="innerBlock.blockType === 'NOTE'" class="note">NOTA: {{ content(innerBlock.contentJson).text }}</div>
                <div v-else class="text-muted">{{ innerBlock.contentJson }}</div>
              </div>
            </div>
            <h3 v-else-if="unit.block.blockType === 'HEADING'">{{ content(unit.block.contentJson).text }}</h3>
            <p v-else-if="content(unit.block.contentJson).type === 'link'"><a :href="content(unit.block.contentJson).href" target="_blank">{{ content(unit.block.contentJson).text }}</a></p>
            <p v-else-if="unit.block.blockType === 'PARAGRAPH'">{{ content(unit.block.contentJson).text }}</p>
            <ul v-else-if="unit.block.blockType === 'UNORDERED_LIST'">
              <li v-for="item in content(unit.block.contentJson).items" :key="item">{{ item }}</li>
            </ul>
            <ol v-else-if="unit.block.blockType === 'ORDERED_LIST'">
              <li v-for="item in content(unit.block.contentJson).items" :key="item">{{ item }}</li>
            </ol>
            <table v-else-if="unit.block.blockType === 'TABLE'" class="doc-table">
              <tbody>
                <tr v-for="(row, rowIndex) in tableRows(unit.block.contentJson)" :key="rowIndex">
                  <template v-if="rowIndex === 0">
                    <th v-for="(cell, cellIndex) in row" :key="`h-${cellIndex}`">{{ cell }}</th>
                  </template>
                  <template v-else>
                    <td v-for="(cell, cellIndex) in row" :key="`c-${cellIndex}`">{{ cell }}</td>
                  </template>
                </tr>
              </tbody>
            </table>
            <div v-else-if="unit.block.blockType === 'WARNING'" class="warning">ADVERTENCIA: {{ content(unit.block.contentJson).text }}</div>
            <div v-else-if="unit.block.blockType === 'NOTE'" class="note">NOTA: {{ content(unit.block.contentJson).text }}</div>
            <div v-else-if="unit.block.blockType === 'FORMULA'" class="formula">{{ content(unit.block.contentJson).latex }}</div>
            <div v-else-if="unit.block.blockType === 'IMAGE'" class="image-placeholder">Imagen: {{ content(unit.block.contentJson).src }}</div>
            <div v-else class="text-muted">{{ unit.block.contentJson }}</div>
          </div>
        </template>
      </main>
      <footer class="paper-footer">{{ pageIndex + 3 }}</footer>
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
                <div v-else class="doc-block">
                  <h3 v-if="unit.block.blockType === 'HEADING'">{{ content(unit.block.contentJson).text }}</h3>
                  <p v-else-if="unit.block.blockType === 'PARAGRAPH'">{{ content(unit.block.contentJson).text }}</p>
                  <ul v-else-if="unit.block.blockType === 'UNORDERED_LIST'">
                    <li v-for="item in content(unit.block.contentJson).items" :key="item">{{ item }}</li>
                  </ul>
                  <ol v-else-if="unit.block.blockType === 'ORDERED_LIST'">
                    <li v-for="item in content(unit.block.contentJson).items" :key="item">{{ item }}</li>
                  </ol>
                  <table v-else-if="unit.block.blockType === 'TABLE'" class="doc-table">
                    <tbody>
                      <tr v-for="(row, rowIndex) in tableRows(unit.block.contentJson)" :key="rowIndex">
                        <template v-if="rowIndex === 0">
                          <th v-for="(cell, cellIndex) in row" :key="`mh-${cellIndex}`">{{ cell }}</th>
                        </template>
                        <template v-else>
                          <td v-for="(cell, cellIndex) in row" :key="`mc-${cellIndex}`">{{ cell }}</td>
                        </template>
                      </tr>
                    </tbody>
                  </table>
                  <div v-else-if="unit.block.blockType === 'WARNING'" class="warning">ADVERTENCIA: {{ content(unit.block.contentJson).text }}</div>
                  <div v-else-if="unit.block.blockType === 'NOTE'" class="note">NOTA: {{ content(unit.block.contentJson).text }}</div>
                  <div v-else-if="unit.block.blockType === 'FORMULA'" class="formula">{{ content(unit.block.contentJson).latex }}</div>
                  <div v-else-if="unit.block.blockType === 'IMAGE'" class="image-placeholder">Imagen: {{ content(unit.block.contentJson).src }}</div>
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
  padding: 14mm;
  background: #fff;
  border: 1px solid var(--border);
  box-shadow: 0 10px 30px rgba(0,0,0,.08);
  display: grid;
  grid-template-rows: auto auto 1fr auto;
  overflow: hidden;
}

.paper-header {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.paper-header span {
  margin-left: auto;
  font-size: 11px;
  color: var(--muted-foreground);
}

.logo,
.cover-logo {
  width: 52px;
  height: 30px;
  display: grid;
  place-items: center;
  background: var(--dikoin-blue);
  color: #fff;
  font-size: 10px;
  font-weight: 800;
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
  background: var(--dikoin-blue);
  margin: 6px 0 18px;
}

.paper-content {
  min-height: 0;
}

.cover-page {
  grid-template-rows: auto 1fr auto;
}

.cover-mark {
  display: flex;
  align-items: center;
  gap: 10px;
}

.cover-content {
  align-self: center;
  display: grid;
  gap: 12px;
}

.cover-content h1 {
  max-width: 620px;
  margin: 0;
  color: var(--dikoin-blue-dark);
  font-size: 32px;
  line-height: 1.12;
  letter-spacing: 0;
}

.cover-content p,
.cover-content span {
  margin: 0;
  color: var(--muted-foreground);
}

.manual-code {
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-weight: 700;
  color: var(--dikoin-blue) !important;
}

.toc-page h1 {
  margin: 0 0 22px;
  color: var(--dikoin-blue-dark);
  font-size: 24px;
}

.toc-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 9px;
}

.toc-list li {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 8px;
  align-items: baseline;
  font-size: 13px;
}

.toc-dots {
  min-width: 24px;
  border-bottom: 1px dotted #8fa9bb;
}

.doc-section-title h2 {
  margin: 0 0 10px;
  background: var(--dikoin-blue);
  color: #fff;
  padding: 6px 9px;
  font-size: 14px;
  line-height: 1.25;
}

.doc-block {
  margin: 0 0 9px;
  font-size: 12px;
  line-height: 1.42;
}

.doc-block p,
.doc-block ul,
.doc-block ol {
  margin: 0 0 8px;
}

.doc-block h3 {
  margin: 0 0 8px;
  color: var(--dikoin-blue);
  font-size: 14px;
}

.doc-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;
}

.doc-table th {
  background: var(--dikoin-blue);
  color: #fff;
  text-align: left;
  padding: 6px;
}

.doc-table td {
  border: 1px solid #b8cce3;
  padding: 6px;
}

.doc-table tr:nth-child(odd) td {
  background: var(--dikoin-blue-light);
}

.warning {
  border-left: 4px solid var(--dikoin-orange);
  background: var(--dikoin-orange-light);
  padding: 9px;
}

.note {
  border-left: 4px solid var(--dikoin-blue);
  background: var(--dikoin-blue-light);
  padding: 9px;
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

.image-placeholder {
  border: 1px dashed var(--border);
  padding: 24px;
  text-align: center;
  color: var(--muted-foreground);
}

.paper-footer {
  justify-self: center;
  align-self: end;
  min-height: 18px;
  color: var(--muted-foreground);
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
