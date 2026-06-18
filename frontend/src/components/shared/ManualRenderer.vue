<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { toBackendUrl } from '@/api/http'
import { getNotices } from '@/api/notices.api'
import { getReusableBlocks } from '@/api/reusable-blocks.api'
import { getActiveTemplate } from '@/api/templates.api'
import type { BlockType, LanguageCode, ManualDetailResponse, ManualSectionResponse, NoticeTemplateResponse, ReusableBlockResponse, TemplateResponse } from '@/types/api'

const props = defineProps<{ manual: ManualDetailResponse; language?: LanguageCode }>()
const notices = ref<NoticeTemplateResponse[]>([])
const reusableBlocks = ref<ReusableBlockResponse[]>([])
const activeTemplate = ref<TemplateResponse | null>(null)

const headerDefaults = {
  showLogo: true,
  showCompanyName: true,
  showManualCode: true,
}

const footerDefaults = {
  showContact: true,
  showWebsite: true,
  showPageNumber: true,
}

interface RenderBlock {
  blockType: BlockType
  languageCode: LanguageCode
  contentJson: string
}

onMounted(async () => {
  const [loadedNotices, loadedBlocks] = await Promise.all([
    getNotices(),
    getReusableBlocks(),
  ])
  notices.value = loadedNotices
  reusableBlocks.value = loadedBlocks
  try {
    activeTemplate.value = await getActiveTemplate()
  } catch {
    activeTemplate.value = null
  }
})

function activeLanguage() {
  return props.language || 'ES'
}

function content(blockJson: string) {
  try {
    return JSON.parse(blockJson)
  } catch {
    return { text: blockJson }
  }
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

function footerConfig() {
  return parseConfig(activeTemplate.value?.footerConfigJson, footerDefaults)
}

function templateFooterParts() {
  const template = activeTemplate.value
  if (!template) return ['DIKOIN S.L. · Documentación técnica']

  const config = footerConfig()
  const parts: string[] = []
  if (config.showContact) {
    const contact = [template.contactEmail, template.contactPhone].filter(Boolean).join(' · ')
    if (contact) parts.push(contact)
  }
  if (config.showWebsite && template.website) parts.push(template.website)
  if (config.showPageNumber) parts.push('Página 1')
  return parts
}
</script>

<template>
  <div class="manual-paper">
    <div class="paper-header">
      <div v-if="headerConfig().showLogo" class="logo">
        <img v-if="templateLogo()" :src="templateLogo()" alt="Logo plantilla" />
        <span v-else>DK</span>
      </div>
      <strong v-if="headerConfig().showCompanyName">{{ templateCompany() }}</strong>
      <span v-if="headerConfig().showManualCode">Ref.: {{ manual.code }} · {{ activeLanguage() }} · v{{ manual.activeVersion?.versionNumber }}</span>
    </div>
    <div class="header-line"></div>

    <h1>{{ manualTitle() }}</h1>
    <p class="text-muted">Producto: {{ manual.productCode }} · {{ manual.productName }}</p>

    <div class="toc">
      <strong>Índice</strong>
      <ol>
        <li v-for="(section, index) in manual.activeVersion?.sections || []" :key="section.id">
          {{ sectionTitle(section, index) }}
        </li>
      </ol>
    </div>

    <section v-for="(section, index) in manual.activeVersion?.sections || []" :key="section.id" class="doc-section">
      <h2>{{ sectionTitle(section, index) }}</h2>
      <div v-for="block in visibleBlocks(section)" :key="block.id" class="doc-block">
        <div v-if="content(block.contentJson).type === 'notice_ref'" class="linked-note">
          <strong>{{ noticeById(content(block.contentJson).noticeTemplateId)?.titleEs || 'Nota' }}</strong>
          <p>{{ noticeById(content(block.contentJson).noticeTemplateId)?.contentEs || 'Nota no encontrada' }}</p>
        </div>
        <div v-else-if="content(block.contentJson).type === 'reusable_block_ref'" class="linked-block">
          <strong>{{ reusableBlockById(content(block.contentJson).reusableBlockId)?.title || 'Bloque común' }}</strong>
          <div v-for="(innerBlock, innerIndex) in reusableRenderBlocks(content(block.contentJson).reusableBlockId)" :key="innerIndex" class="doc-block">
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
        <h3 v-else-if="block.blockType === 'HEADING'">{{ content(block.contentJson).text }}</h3>
        <p v-else-if="content(block.contentJson).type === 'link'"><a :href="content(block.contentJson).href" target="_blank">{{ content(block.contentJson).text }}</a></p>
        <p v-else-if="block.blockType === 'PARAGRAPH'">{{ content(block.contentJson).text }}</p>
        <ul v-else-if="block.blockType === 'UNORDERED_LIST'">
          <li v-for="item in content(block.contentJson).items" :key="item">{{ item }}</li>
        </ul>
        <ol v-else-if="block.blockType === 'ORDERED_LIST'">
          <li v-for="item in content(block.contentJson).items" :key="item">{{ item }}</li>
        </ol>
        <table v-else-if="block.blockType === 'TABLE'" class="doc-table">
          <tbody>
            <tr v-for="(row, rowIndex) in tableRows(block.contentJson)" :key="rowIndex">
              <template v-if="rowIndex === 0">
                <th v-for="(cell, cellIndex) in row" :key="`h-${cellIndex}`">{{ cell }}</th>
              </template>
              <template v-else>
                <td v-for="(cell, cellIndex) in row" :key="`c-${cellIndex}`">{{ cell }}</td>
              </template>
            </tr>
          </tbody>
        </table>
        <div v-else-if="block.blockType === 'WARNING'" class="warning">ADVERTENCIA: {{ content(block.contentJson).text }}</div>
        <div v-else-if="block.blockType === 'NOTE'" class="note">NOTA: {{ content(block.contentJson).text }}</div>
        <div v-else-if="block.blockType === 'FORMULA'" class="formula">{{ content(block.contentJson).latex }}</div>
        <div v-else-if="block.blockType === 'IMAGE'" class="image-placeholder">Imagen: {{ content(block.contentJson).src }}</div>
        <div v-else class="text-muted">{{ block.contentJson }}</div>
      </div>
    </section>

    <div v-if="templateFooterParts().length" class="paper-footer">
      <span v-for="part in templateFooterParts()" :key="part">{{ part }}</span>
    </div>
  </div>
</template>

<style scoped>
.manual-paper {
  width: min(210mm, 100%);
  min-height: 297mm;
  margin: 0 auto;
  padding: 14mm;
  background: #fff;
  border: 1px solid var(--border);
  box-shadow: 0 10px 30px rgba(0,0,0,.08);
}
.paper-header { display: flex; align-items: flex-end; gap: 8px; }
.paper-header span { margin-left: auto; font-size: 11px; color: var(--muted-foreground); }
.logo { width: 52px; height: 30px; display: grid; place-items: center; background: var(--dikoin-blue); color: #fff; font-size: 10px; font-weight: 800; overflow: hidden; }
.logo img { width: 100%; height: 100%; object-fit: contain; background: #fff; }
.header-line { height: 2px; background: var(--dikoin-blue); margin: 6px 0 22px; }
.manual-paper h1 { color: var(--dikoin-blue-dark); font-size: 22px; }
.toc { background: var(--dikoin-blue-lighter); padding: 12px; margin: 20px 0; }
.doc-section h2 { background: var(--dikoin-blue); color: #fff; padding: 6px 9px; font-size: 14px; }
.doc-block h3 { color: var(--dikoin-blue); font-size: 14px; }
.doc-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.doc-table th { background: var(--dikoin-blue); color: #fff; text-align: left; padding: 6px; }
.doc-table td { border: 1px solid #b8cce3; padding: 6px; }
.doc-table tr:nth-child(odd) td { background: var(--dikoin-blue-light); }
.warning { border-left: 4px solid var(--dikoin-orange); background: var(--dikoin-orange-light); padding: 9px; }
.note { border-left: 4px solid var(--dikoin-blue); background: var(--dikoin-blue-light); padding: 9px; }
.linked-note { border-left: 4px solid var(--dikoin-orange); background: #fff7ed; color: #78350f; padding: 10px; }
.linked-note p { margin: 6px 0 0; color: inherit; }
.linked-block { border: 1px solid var(--border); border-left: 4px solid var(--dikoin-blue); padding: 10px; background: #f8fbfe; }
.linked-block > strong { color: var(--dikoin-blue-dark); }
.formula { font-family: Georgia, serif; font-size: 22px; padding: 12px; }
.image-placeholder { border: 1px dashed var(--border); padding: 24px; text-align: center; color: var(--muted-foreground); }
.paper-footer { margin-top: 28px; padding: 5px 8px; background: var(--dikoin-blue); color: #fff; font-size: 11px; display: flex; justify-content: space-between; gap: 12px; }
</style>
