<script setup lang="ts">
import { computed } from 'vue'
import type { Directive } from 'vue'
import {
  BarChart2,
  Calculator,
  FileImage,
  GripVertical,
  Info,
  List,
  ListOrdered,
  Link,
  Table,
  Trash2,
  Type,
} from '@lucide/vue'
import type { EditorBlock, EditorBlockType } from '@/types/editor'

const props = defineProps<{ block: EditorBlock; selected: boolean; subNumber?: string }>()
const emit = defineEmits<{
  select: []
  update: [value: EditorBlock]
  delete: []
  addAfter: []
}>()

const labels: Record<EditorBlockType, string> = {
  titulo: 'Título',
  subtitulo: 'Subtítulo',
  parrafo: 'Párrafo',
  'lista-ul': 'Lista',
  'lista-ol': 'Lista ordenada',
  tabla: 'Tabla',
  advertencia: 'Advertencia',
  nota: 'Nota',
  'nota-ref': 'Nota de biblioteca',
  imagen: 'Imagen',
  enlace: 'Enlace',
  formula: 'Fórmula',
  grafico: 'Gráfico',
  'bloque-ref': 'Bloque común',
}

const selectableTypes = (Object.keys(labels) as EditorBlockType[]).filter((type) => type !== 'advertencia')

const vEditableText: Directive<HTMLElement, string> = {
  mounted(el, binding) {
    setEditableText(el, binding.value)
  },
  updated(el, binding) {
    if (document.activeElement === el) return
    setEditableText(el, binding.value)
  },
}

const icon = computed(() => {
  const map: Record<EditorBlockType, unknown> = {
    titulo: Type,
    subtitulo: Type,
    parrafo: Type,
    'lista-ul': List,
    'lista-ol': ListOrdered,
    tabla: Table,
    advertencia: Info,
    nota: Info,
    'nota-ref': Info,
    imagen: FileImage,
    enlace: Link,
    formula: Calculator,
    grafico: BarChart2,
    'bloque-ref': BarChart2,
  }
  return map[props.block.type]
})

function patch(value: Partial<EditorBlock>) {
  emit('update', { ...props.block, ...value })
}

function setEditableText(el: HTMLElement, value: string) {
  const nextValue = value || ''
  if (el.innerText !== nextValue) {
    el.innerText = nextValue
  }
}

function textFromEvent(event: Event) {
  return (event.target as HTMLElement).innerText
}

function tableRows() {
  return props.block.content.split('\n').filter(Boolean).map((line) => line.split('|'))
}

function patchTableCell(rowIndex: number, cellIndex: number, value: string) {
  const rows = tableRows()
  rows[rowIndex][cellIndex] = value
  patch({ content: rows.map((row) => row.join('|')).join('\n') })
}

function linkParts() {
  const [text = 'Enlace', href = 'https://'] = props.block.content.split('|')
  return { text, href }
}
</script>

<template>
  <div class="block" :class="{ selected }" @click.stop="emit('select')">
    <div class="block-tools">
      <GripVertical :size="13" />
      <button title="Eliminar bloque" @click.stop="emit('delete')"><Trash2 :size="13" /></button>
    </div>
    <div v-if="selected" class="type-select">
      <component :is="icon" :size="12" />
      <select :value="block.type" @change="patch({ type: ($event.target as HTMLSelectElement).value as EditorBlockType })">
        <option v-for="key in selectableTypes" :key="key" :value="key">{{ labels[key] }}</option>
      </select>
    </div>

    <div v-if="block.type === 'titulo' || block.type === 'subtitulo'" class="editable-heading" :class="block.type">
      <span v-if="subNumber" class="mono">{{ subNumber }}</span>
      <span v-editable-text="block.content" contenteditable suppress-contenteditable-warning @input="patch({ content: textFromEvent($event) })" />
    </div>

    <p v-else-if="block.type === 'parrafo'" v-editable-text="block.content" class="editable-p" contenteditable suppress-contenteditable-warning @input="patch({ content: textFromEvent($event) })" />

    <div v-else-if="block.type === 'lista-ul' || block.type === 'lista-ol'" class="list-editor">
      <ul v-if="block.type === 'lista-ul'">
        <li v-for="item in block.content.split('\n').filter(Boolean)" :key="item">{{ item }}</li>
      </ul>
      <ol v-else>
        <li v-for="item in block.content.split('\n').filter(Boolean)" :key="item">{{ item }}</li>
      </ol>
      <textarea class="field" rows="4" :value="block.content" placeholder="Un elemento por línea" @input="patch({ content: ($event.target as HTMLTextAreaElement).value })" />
    </div>

    <div v-else-if="block.type === 'tabla'" class="table-editor">
      <table>
        <tbody>
          <tr v-for="(row, rowIndex) in tableRows()" :key="rowIndex">
            <template v-if="rowIndex === 0">
              <th v-for="(cell, cellIndex) in row" :key="`h-${cellIndex}`">
                <input :value="cell" @input="patchTableCell(rowIndex, cellIndex, ($event.target as HTMLInputElement).value)" />
              </th>
            </template>
            <template v-else>
              <td v-for="(cell, cellIndex) in row" :key="`c-${cellIndex}`">
                <input :value="cell" @input="patchTableCell(rowIndex, cellIndex, ($event.target as HTMLInputElement).value)" />
              </td>
            </template>
          </tr>
        </tbody>
      </table>
      <details>
        <summary>Editar datos como texto</summary>
        <textarea class="field mono" rows="5" :value="block.content" placeholder="Cabecera1|Cabecera2&#10;Dato1|Dato2" @input="patch({ content: ($event.target as HTMLTextAreaElement).value })" />
      </details>
    </div>

    <div v-else-if="block.type === 'advertencia'" v-editable-text="block.content" class="callout warning" contenteditable suppress-contenteditable-warning @input="patch({ content: textFromEvent($event) })" />
    <div v-else-if="block.type === 'nota'" v-editable-text="block.content" class="callout note" contenteditable suppress-contenteditable-warning @input="patch({ content: textFromEvent($event) })" />
    <div v-else-if="block.type === 'nota-ref'" class="library-ref note-ref">
      Nota enlazada #{{ block.content }}
    </div>
    <div v-else-if="block.type === 'bloque-ref'" class="library-ref reusable-ref">
      Bloque común enlazado #{{ block.content }}
    </div>
    <div v-else-if="block.type === 'formula'" v-editable-text="block.content" class="formula" contenteditable suppress-contenteditable-warning @input="patch({ content: textFromEvent($event) })" />
    <div v-else-if="block.type === 'enlace'" class="link-editor">
      <input class="field" :value="linkParts().text" placeholder="Texto del enlace" @input="patch({ content: `${($event.target as HTMLInputElement).value}|${linkParts().href}` })" />
      <input class="field mono" :value="linkParts().href" placeholder="https://..." @input="patch({ content: `${linkParts().text}|${($event.target as HTMLInputElement).value}` })" />
    </div>
    <div v-else class="simple-input">
      <input class="field" :value="block.content" :placeholder="labels[block.type]" @input="patch({ content: ($event.target as HTMLInputElement).value })" />
    </div>

    <button v-if="selected" class="add-after" @click.stop="emit('addAfter')">+ Añadir bloque debajo</button>
  </div>
</template>

<style scoped>
.block { position: relative; border: 1px solid transparent; border-radius: var(--radius); padding: 10px 12px; margin: 12px 0; background: #fff; transition: border-color .15s, box-shadow .15s, transform .15s; }
.block:hover, .block.selected { border-color: #9ccce5; box-shadow: 0 0 0 2px rgba(0,124,184,.06); }
.block-tools { position: absolute; left: -30px; top: 10px; display: grid; gap: 4px; opacity: .45; color: var(--muted-foreground); }
.block:hover .block-tools, .block.selected .block-tools { opacity: 1; color: var(--dikoin-blue); }
.block-tools button { border: 0; background: transparent; color: inherit; padding: 0; }
.type-select { display: inline-flex; align-items: center; gap: 4px; padding: 3px 6px; border: 1px solid #9ccce5; background: #eaf3fa; color: var(--dikoin-blue); border-radius: var(--radius); font-size: 11px; margin-bottom: 8px; }
.type-select select { border: 0; background: transparent; color: inherit; outline: 0; font-size: 11px; }
.editable-heading { display: flex; align-items: center; gap: 8px; color: var(--dikoin-blue); font-weight: 600; }
.editable-heading.titulo { font-size: 16px; }
.editable-heading.subtitulo { font-size: 14px; color: var(--dikoin-blue-dark); }
[contenteditable] { outline: 0; }
.editable-p { margin: 0; line-height: 1.65; }
.list-editor textarea, .table-editor textarea { margin-top: 8px; }
.table-editor table { width: 100%; border-collapse: collapse; font-size: 12px; }
.table-editor th { background: var(--dikoin-blue); color: #fff; padding: 6px; text-align: left; }
.table-editor td { border: 1px solid #b8cce3; padding: 6px; }
.table-editor input { width: 100%; min-width: 80px; border: 0; background: transparent; color: inherit; outline: 0; }
.table-editor td input { color: var(--foreground); }
.table-editor details { margin-top: 8px; color: var(--muted-foreground); font-size: 12px; }
.link-editor { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.callout { padding: 10px; border-left: 4px solid; }
.warning { background: var(--dikoin-orange-light); border-color: var(--dikoin-orange); color: #78350f; }
.note { background: var(--dikoin-blue-light); border-color: var(--dikoin-blue); color: var(--dikoin-blue-dark); }
.library-ref { border-left: 4px solid; padding: 10px; font-weight: 600; }
.note-ref { background: #fff7ed; border-color: var(--dikoin-orange); color: #78350f; }
.reusable-ref { background: var(--dikoin-blue-lighter); border-color: var(--dikoin-blue); color: var(--dikoin-blue-dark); }
.formula { font-family: Georgia, serif; font-size: 22px; padding: 8px; }
.add-after { margin-top: 8px; border: 1px dashed var(--border); background: #fff; color: var(--dikoin-blue); padding: 5px 8px; border-radius: var(--radius); }
</style>
