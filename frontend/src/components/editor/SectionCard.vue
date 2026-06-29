<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ChevronDown, ChevronUp, FileImage, GripVertical, Link, List, Plus, Table, Trash2, Type } from '@lucide/vue'
import { getNotices } from '@/api/notices.api'
import { getReusableBlocks } from '@/api/reusable-blocks.api'
import AppModal from '@/components/shared/AppModal.vue'
import BlockEditor from './BlockEditor.vue'
import type { EditorBlock, EditorBlockType, EditorSection } from '@/types/editor'
import { randomId } from '@/types/editor'
import type { LanguageCode, NoticeTemplateResponse, ReusableBlockResponse } from '@/types/api'

const props = defineProps<{ section: EditorSection; selectedBlockId?: string; language: LanguageCode }>()
const emit = defineEmits<{
  update: [section: EditorSection]
  delete: []
  selectBlock: [id: string]
}>()

const notices = ref<NoticeTemplateResponse[]>([])
const reusableBlocks = ref<ReusableBlockResponse[]>([])
const tableDialogOpen = ref(false)
const tableRows = ref(3)
const tableCols = ref(3)

onMounted(async () => {
  const [loadedNotices, loadedBlocks] = await Promise.all([
    getNotices('NOTE'),
    getReusableBlocks(),
  ])
  notices.value = loadedNotices
  reusableBlocks.value = loadedBlocks
})

const subsections = computed(() => {
  let count = 0
  return visibleBlocks.value.reduce<Record<string, string>>((map, block) => {
    if (block.type === 'titulo' || block.type === 'subtitulo') {
      count++
      map[block.id] = `${props.section.sectionNumber || props.section.sortOrder}.${count}`
    }
    return map
  }, {})
})

const visibleBlocks = computed(() => props.section.blocks.filter((block) => block.languageCode === props.language))

const sectionTitle = computed(() => props.language === 'EN'
  ? props.section.titleEn || ''
  : props.section.titleEs)

function patch(value: Partial<EditorSection>) {
  emit('update', { ...props.section, ...value })
}

function updateBlock(block: EditorBlock) {
  patch({ blocks: props.section.blocks.map((item) => item.id === block.id ? block : item) })
}

function tableContent(rows = 3, cols = 3) {
  return Array.from({ length: rows }, (_, rowIndex) =>
    Array.from({ length: cols }, (_, colIndex) => rowIndex === 0 ? `Cabecera ${colIndex + 1}` : '').join('|')
  ).join('\n')
}

function addBlock(afterId?: string, type: EditorBlockType = 'parrafo', content?: string) {
  const defaultContent: Record<EditorBlockType, string> = {
    titulo: 'Nuevo título',
    subtitulo: 'Nuevo subtítulo',
    parrafo: 'Nuevo bloque de contenido.',
    'lista-ul': 'Elemento 1\nElemento 2',
    'lista-ol': 'Paso 1\nPaso 2',
    tabla: tableContent(3, 3),
    advertencia: 'Advertencia importante.',
    nota: 'Nota informativa.',
    'nota-ref': '',
    'fragmento-ref': '',
    imagen: '/api/v1/assets/{id}/file',
    enlace: 'Texto del enlace|https://',
    formula: 'P = ρ · g · H · Q',
    grafico: 'Gráfico imprimible',
    'bloque-ref': '',
  }
  const block: EditorBlock = { id: randomId('block'), type, content: content ?? defaultContent[type], languageCode: props.language }
  const blocks = [...props.section.blocks]
  const lastVisibleIndex = [...blocks].map((item, index) => ({ item, index })).reverse().find(({ item }) => item.languageCode === props.language)?.index
  const index = afterId ? blocks.findIndex((item) => item.id === afterId) + 1 : (lastVisibleIndex === undefined ? blocks.length : lastVisibleIndex + 1)
  blocks.splice(index, 0, block)
  patch({ blocks })
  emit('selectBlock', block.id)
}

function updateTitle(value: string) {
  patch(props.language === 'EN' ? { titleEn: value } : { titleEs: value })
}

function deleteBlock(id: string) {
  patch({ blocks: props.section.blocks.filter((block) => block.id !== id) })
}

function insertTable() {
  tableRows.value = 3
  tableCols.value = 3
  tableDialogOpen.value = true
}

function confirmInsertTable() {
  addBlock(undefined, 'tabla', tableContent(Math.max(Number(tableRows.value) || 1, 1), Math.max(Number(tableCols.value) || 1, 1)))
  tableDialogOpen.value = false
}

function insertNotice(event: Event) {
  const select = event.target as HTMLSelectElement
  if (!select.value) return
  addBlock(undefined, 'nota-ref', select.value)
  select.value = ''
}

function insertReusableBlock(event: Event) {
  const select = event.target as HTMLSelectElement
  if (!select.value) return
  addBlock(undefined, 'bloque-ref', select.value)
  select.value = ''
}
</script>

<template>
  <article class="section-card">
    <header class="section-head">
      <button class="collapse" @click="patch({ collapsed: !section.collapsed })">
        <ChevronDown v-if="section.collapsed" :size="16" />
        <ChevronUp v-else :size="16" />
      </button>
      <span class="section-num">{{ section.sectionNumber || section.sortOrder }}</span>
      <input class="section-title" :value="sectionTitle" @input="updateTitle(($event.target as HTMLInputElement).value)" />
      <span class="drag-hint"><GripVertical :size="14" /> Arrastrar</span>
      <span class="section-status">{{ section.status }}</span>
      <button class="icon-btn danger" title="Eliminar sección" @click="emit('delete')"><Trash2 :size="14" /></button>
    </header>

    <div v-if="!section.collapsed" class="section-body">
      <div class="section-toolbar">
        <button @click="addBlock(undefined, 'titulo')"><Type :size="14" /> Título</button>
        <button @click="addBlock(undefined, 'parrafo')"><Type :size="14" /> Texto</button>
        <button @click="addBlock(undefined, 'lista-ul')"><List :size="14" /> Lista</button>
        <button @click="insertTable"><Table :size="14" /> Tabla</button>
        <button @click="addBlock(undefined, 'imagen')"><FileImage :size="14" /> Imagen</button>
        <button @click="addBlock(undefined, 'enlace')"><Link :size="14" /> Enlace</button>
        <select class="toolbar-select" @change="insertNotice">
          <option value="">Insertar nota</option>
          <option v-for="notice in notices" :key="notice.id" :value="notice.id">
            {{ notice.code }} - {{ notice.title }}
          </option>
        </select>
        <select class="toolbar-select" @change="insertReusableBlock">
          <option value="">Insertar bloque</option>
          <option v-for="block in reusableBlocks" :key="block.id" :value="block.id">
            {{ block.code }} - {{ block.title }}
          </option>
        </select>
      </div>
      <BlockEditor
        v-for="block in visibleBlocks"
        :key="block.id"
        :block="block"
        :selected="selectedBlockId === block.id"
        :sub-number="subsections[block.id]"
        @select="emit('selectBlock', block.id)"
        @update="updateBlock"
        @delete="deleteBlock(block.id)"
        @add-after="addBlock(block.id)"
      />
      <button class="add-block" @click="addBlock()"><Plus :size="14" /> Añadir bloque</button>
    </div>

    <AppModal v-if="tableDialogOpen" title="Insertar tabla" @close="tableDialogOpen = false">
      <div class="table-dialog-grid">
        <label>Filas <input v-model.number="tableRows" class="field" type="number" min="1" /></label>
        <label>Columnas <input v-model.number="tableCols" class="field" type="number" min="1" /></label>
      </div>
      <template #footer>
        <button type="button" class="btn btn-outline" @click="tableDialogOpen = false">Cancelar</button>
        <button type="button" class="btn btn-primary" @click="confirmInsertTable">Insertar</button>
      </template>
    </AppModal>
  </article>
</template>

<style scoped>
.section-card { background: #fff; border: 1px solid var(--border); border-radius: var(--radius); margin-bottom: 14px; transition: box-shadow .15s, border-color .15s, transform .15s; }
.section-card:hover { border-color: #9ccce5; box-shadow: 0 8px 20px rgba(0, 124, 184, .08); }
.section-head { display: flex; align-items: center; gap: 8px; padding: 11px 12px; border-bottom: 1px solid var(--border); background: #f8fbfe; }
.collapse, .icon-btn { border: 0; background: transparent; color: var(--muted-foreground); padding: 4px; }
.danger:hover { color: var(--dikoin-red); }
.section-num { min-width: 34px; padding: 3px 6px; background: var(--dikoin-blue); color: #fff; text-align: center; font-weight: 600; border-radius: var(--radius); }
.section-title { flex: 1; border: 0; background: transparent; outline: 0; font-weight: 600; color: var(--dikoin-blue-dark); }
.section-status { font-size: 11px; color: var(--muted-foreground); padding: 3px 7px; background: #edf2f7; border-radius: 999px; }
.drag-hint { display: inline-flex; align-items: center; gap: 4px; color: var(--muted-foreground); font-size: 11px; border: 1px dashed var(--border); padding: 3px 7px; border-radius: var(--radius); }
.section-body { padding: 10px 14px 14px 42px; }
.section-toolbar { position: sticky; top: 0; z-index: 4; display: flex; flex-wrap: wrap; gap: 6px; padding: 8px; margin: -2px 0 12px; background: #fff; border: 1px solid var(--border); border-radius: var(--radius); box-shadow: 0 2px 8px rgba(0,0,0,.04); }
.section-toolbar button { border: 1px solid var(--border); background: var(--input-background); padding: 6px 8px; display: inline-flex; align-items: center; gap: 5px; border-radius: var(--radius); color: var(--foreground); }
.section-toolbar button:hover { border-color: var(--dikoin-blue); color: var(--dikoin-blue); background: var(--dikoin-blue-lighter); }
.toolbar-select { border: 1px solid var(--border); background: #fff; padding: 6px 8px; border-radius: var(--radius); color: var(--foreground); max-width: 220px; }
.add-block { border: 1px dashed var(--border); background: #fff; color: var(--dikoin-blue); padding: 8px 10px; border-radius: var(--radius); display: inline-flex; gap: 6px; align-items: center; }
.table-dialog-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.table-dialog-grid label { display: grid; gap: 6px; color: var(--muted-foreground); font-size: 12px; font-weight: 600; }
</style>

