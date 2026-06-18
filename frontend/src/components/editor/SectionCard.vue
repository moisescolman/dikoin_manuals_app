<script setup lang="ts">
import { computed } from 'vue'
import { AlertTriangle, ChevronDown, ChevronUp, FileImage, GripVertical, Link, List, Plus, Table, Trash2, Type } from '@lucide/vue'
import BlockEditor from './BlockEditor.vue'
import type { EditorBlock, EditorBlockType, EditorSection } from '@/types/editor'
import { randomId } from '@/types/editor'

const props = defineProps<{ section: EditorSection; selectedBlockId?: string }>()
const emit = defineEmits<{
  update: [section: EditorSection]
  delete: []
  selectBlock: [id: string]
}>()

const subsections = computed(() => {
  let count = 0
  return props.section.blocks.reduce<Record<string, string>>((map, block) => {
    if (block.type === 'titulo' || block.type === 'subtitulo') {
      count++
      map[block.id] = `${props.section.sectionNumber || props.section.sortOrder}.${count}`
    }
    return map
  }, {})
})

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
    imagen: '/api/v1/assets/{id}/file',
    enlace: 'Texto del enlace|https://',
    formula: 'P = ρ · g · H · Q',
    grafico: 'Gráfico imprimible',
  }
  const block: EditorBlock = { id: randomId('block'), type, content: content ?? defaultContent[type], languageCode: 'ES' }
  const blocks = [...props.section.blocks]
  const index = afterId ? blocks.findIndex((item) => item.id === afterId) + 1 : blocks.length
  blocks.splice(index, 0, block)
  patch({ blocks })
  emit('selectBlock', block.id)
}

function deleteBlock(id: string) {
  patch({ blocks: props.section.blocks.filter((block) => block.id !== id) })
}

function insertTable() {
  const rows = Number(window.prompt('Número de filas', '3') || 3)
  const cols = Number(window.prompt('Número de columnas', '3') || 3)
  addBlock(undefined, 'tabla', tableContent(Math.max(rows, 1), Math.max(cols, 1)))
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
      <input class="section-title" :value="section.titleEs" @input="patch({ titleEs: ($event.target as HTMLInputElement).value })" />
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
        <button @click="addBlock(undefined, 'advertencia')"><AlertTriangle :size="14" /> Advertencia</button>
      </div>
      <BlockEditor
        v-for="block in section.blocks"
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
  </article>
</template>

<style scoped>
.section-card { background: #fff; border: 1px solid var(--border); border-radius: var(--radius); margin-bottom: 14px; transition: box-shadow .15s, border-color .15s, transform .15s; }
.section-card:hover { border-color: #9ccce5; box-shadow: 0 8px 20px rgba(0, 124, 184, .08); }
.section-head { display: flex; align-items: center; gap: 8px; padding: 11px 12px; border-bottom: 1px solid var(--border); background: #f8fbfe; }
.collapse, .icon-btn { border: 0; background: transparent; color: var(--muted-foreground); padding: 4px; }
.danger:hover { color: var(--dikoin-red); }
.section-num { min-width: 34px; padding: 3px 6px; background: var(--dikoin-blue); color: #fff; text-align: center; font-weight: 800; border-radius: var(--radius); }
.section-title { flex: 1; border: 0; background: transparent; outline: 0; font-weight: 800; color: var(--dikoin-blue-dark); }
.section-status { font-size: 11px; color: var(--muted-foreground); padding: 3px 7px; background: #edf2f7; border-radius: 999px; }
.drag-hint { display: inline-flex; align-items: center; gap: 4px; color: var(--muted-foreground); font-size: 11px; border: 1px dashed var(--border); padding: 3px 7px; border-radius: var(--radius); }
.section-body { padding: 10px 14px 14px 42px; }
.section-toolbar { position: sticky; top: 0; z-index: 4; display: flex; flex-wrap: wrap; gap: 6px; padding: 8px; margin: -2px 0 12px; background: #fff; border: 1px solid var(--border); border-radius: var(--radius); box-shadow: 0 2px 8px rgba(0,0,0,.04); }
.section-toolbar button { border: 1px solid var(--border); background: var(--input-background); padding: 6px 8px; display: inline-flex; align-items: center; gap: 5px; border-radius: var(--radius); color: var(--foreground); }
.section-toolbar button:hover { border-color: var(--dikoin-blue); color: var(--dikoin-blue); background: var(--dikoin-blue-lighter); }
.add-block { border: 1px dashed var(--border); background: #fff; color: var(--dikoin-blue); padding: 8px 10px; border-radius: var(--radius); display: inline-flex; gap: 6px; align-items: center; }
</style>
