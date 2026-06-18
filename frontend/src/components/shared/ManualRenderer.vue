<script setup lang="ts">
import type { LanguageCode, ManualDetailResponse, ManualSectionResponse } from '@/types/api'

const props = defineProps<{ manual: ManualDetailResponse; language?: LanguageCode }>()

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

function manualTitle() {
  if (activeLanguage() === 'EN') {
    return props.manual.activeVersion?.enReady ? props.manual.title : ''
  }
  return props.manual.title
}
</script>

<template>
  <div class="manual-paper">
    <div class="paper-header">
      <div class="logo">DK</div>
      <strong>DIKOIN</strong>
      <span>Ref.: {{ manual.code }} · {{ activeLanguage() }} · v{{ manual.activeVersion?.versionNumber }}</span>
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
        <h3 v-if="block.blockType === 'HEADING'">{{ content(block.contentJson).text }}</h3>
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

    <div class="paper-footer">DIKOIN S.L. · Documentación técnica</div>
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
.logo { width: 28px; height: 20px; display: grid; place-items: center; background: var(--dikoin-blue); color: #fff; font-size: 10px; font-weight: 800; }
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
.formula { font-family: Georgia, serif; font-size: 22px; padding: 12px; }
.image-placeholder { border: 1px dashed var(--border); padding: 24px; text-align: center; color: var(--muted-foreground); }
.paper-footer { margin-top: 28px; padding: 5px 8px; background: var(--dikoin-blue); color: #fff; font-size: 11px; }
</style>
