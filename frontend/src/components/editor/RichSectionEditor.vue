<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { EditorContent, useEditor } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Image from '@tiptap/extension-image'
import Placeholder from '@tiptap/extension-placeholder'
import { Table } from '@tiptap/extension-table'
import TableCell from '@tiptap/extension-table-cell'
import TableHeader from '@tiptap/extension-table-header'
import TableRow from '@tiptap/extension-table-row'
import { Extension, mergeAttributes, Node, type JSONContent } from '@tiptap/core'
import {
  ChevronUp,
  ClipboardPaste,
  Copy,
  FileImage,
  GripVertical,
  Heading1,
  Heading2,
  Heading3,
  List,
  ListOrdered,
  NotebookPen,
  PanelTopClose,
  Redo2,
  Save,
  Scissors,
  Table as TableIcon,
  Trash2,
  Undo2,
} from '@lucide/vue'
import { getAssets, uploadAsset } from '@/api/assets.api'
import { toBackendUrl } from '@/api/http'
import { getNotices } from '@/api/notices.api'
import type { EditorBlock, EditorBlockType, EditorSection } from '@/types/editor'
import { randomId } from '@/types/editor'
import type { AssetResponse, LanguageCode, NoticeTemplateResponse } from '@/types/api'

type NoteMode = 'new' | 'library'

const props = defineProps<{
  section: EditorSection
  language: LanguageCode
  selected: boolean
  manualId?: number
}>()

const emit = defineEmits<{
  update: [section: EditorSection]
  select: [id: string]
  delete: []
  duplicate: []
  saveReusable: []
}>()

const assets = ref<AssetResponse[]>([])
const notices = ref<NoticeTemplateResponse[]>([])
const showImageModal = ref(false)
const showNoticeModal = ref(false)
const noteMode = ref<NoteMode>('new')
const noticeSearch = ref('')
const uploadingImage = ref(false)
const syncingFromProps = ref(false)

const NoteBox = Node.create({
  name: 'noteBox',
  group: 'block',
  content: 'inline*',
  defining: true,
  addAttributes() {
    return {
      refId: { default: null },
      title: { default: 'Nota' },
    }
  },
  parseHTML() {
    return [{ tag: 'div[data-note-box]' }]
  },
  renderHTML({ HTMLAttributes }) {
    return ['div', mergeAttributes(HTMLAttributes, { 'data-note-box': 'true', class: 'note-box' }), 0]
  },
})

const ParagraphEnter = Extension.create({
  name: 'paragraphEnter',
  priority: 1000,
  addKeyboardShortcuts() {
    return {
      Enter: () => {
        if (this.editor.isActive('table') || this.editor.isActive('bulletList') || this.editor.isActive('orderedList')) {
          return false
        }
        return this.editor.commands.splitBlock()
      },
    }
  },
})

const editor = useEditor({
  extensions: [
    StarterKit.configure({
      heading: { levels: [1, 2, 3] },
    }),
    Placeholder.configure({ placeholder: 'Escriba el texto...' }),
    Image.configure({ inline: false, allowBase64: true }),
    Table.configure({ resizable: true }),
    TableRow,
    TableHeader,
    TableCell,
    NoteBox,
    ParagraphEnter,
  ],
  content: docFromBlocks(visibleBlocks()),
  editorProps: {
    attributes: {
      class: 'rich-editor-surface',
    },
  },
  onFocus: () => emit('select', props.section.id),
  onUpdate: ({ editor }) => {
    if (syncingFromProps.value) return
    patch({ blocks: mergeLanguageBlocks(blocksFromDoc(editor.getJSON())) })
  },
})

const sectionTitle = computed(() => props.language === 'EN'
  ? props.section.titleEn || ''
  : props.section.titleEs)

const filteredNotices = computed(() => {
  const search = noticeSearch.value.trim().toLowerCase()
  if (!search) return notices.value
  return notices.value.filter((notice) => {
    return [notice.code, notice.titleEs, notice.visibleTitleEs, notice.contentEs, notice.titleEn, notice.contentEn]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(search))
  })
})

onMounted(async () => {
  await loadModalData()
})

onBeforeUnmount(() => {
  editor.value?.destroy()
})

watch(
  () => [props.section.id, props.language],
  () => {
    if (!editor.value) return
    syncingFromProps.value = true
    editor.value.commands.setContent(docFromBlocks(visibleBlocks()))
    queueMicrotask(() => { syncingFromProps.value = false })
  },
)

function visibleBlocks() {
  return props.section.blocks.filter((block) => block.languageCode === props.language)
}

function patch(value: Partial<EditorSection>) {
  emit('update', { ...props.section, ...value })
}

function patchTitle(value: string) {
  patch(props.language === 'EN' ? { titleEn: value } : { titleEs: value })
}

function mergeLanguageBlocks(languageBlocks: EditorBlock[]) {
  return [
    ...props.section.blocks.filter((block) => block.languageCode !== props.language),
    ...languageBlocks,
  ]
}

function focusEditor() {
  emit('select', props.section.id)
  editor.value?.commands.focus()
}

function setHeading(level: 1 | 2 | 3) {
  editor.value?.chain().focus().toggleHeading({ level }).run()
}

function undo() {
  editor.value?.chain().focus().undo().run()
}

function redo() {
  editor.value?.chain().focus().redo().run()
}

async function copySelection() {
  const text = selectedText()
  if (!text) return
  await writeClipboard(text)
  editor.value?.commands.focus()
}

async function cutSelection() {
  const text = selectedText()
  if (!text || !editor.value) return
  await writeClipboard(text)
  editor.value.chain().focus().deleteSelection().run()
}

async function pasteClipboard() {
  if (!editor.value) return
  let text = ''
  try {
    text = await navigator.clipboard.readText()
  } catch {
    text = window.prompt('Texto a pegar', '') || ''
  }
  if (!text) return
  editor.value.chain().focus().insertContent(textToParagraphs(text)).run()
}

function selectedText() {
  const current = editor.value
  if (!current || current.state.selection.empty) return ''
  const { from, to } = current.state.selection
  return current.state.doc.textBetween(from, to, '\n')
}

async function writeClipboard(text: string) {
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    const textarea = document.createElement('textarea')
    textarea.value = text
    textarea.setAttribute('readonly', 'true')
    textarea.style.position = 'fixed'
    textarea.style.left = '-9999px'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    textarea.remove()
  }
}

function textToParagraphs(text: string): JSONContent[] {
  return text.split(/\r?\n/).map((line) => ({
    type: 'paragraph',
    content: textContent(line),
  }))
}

function insertTable() {
  const rows = Number(window.prompt('Número de filas', '3') || 3)
  const cols = Number(window.prompt('Número de columnas', '3') || 3)
  editor.value?.chain().focus().insertTable({ rows: Math.max(rows, 1), cols: Math.max(cols, 1), withHeaderRow: true }).run()
}

function openImageModal() {
  showImageModal.value = true
  loadModalData()
}

function openNoticeMenu(mode: NoteMode) {
  noteMode.value = mode
  if (mode === 'new') {
    insertGenericNote()
    return
  }
  showNoticeModal.value = true
  loadModalData()
}

function insertGenericNote() {
  editor.value?.chain().focus().insertContent({
    type: 'noteBox',
    attrs: { title: 'Nota' },
    content: [{ type: 'text', text: 'Nueva nota.' }],
  }).run()
}

function insertNotice(notice: NoticeTemplateResponse) {
  editor.value?.chain().focus().insertContent({
    type: 'noteBox',
    attrs: { refId: notice.id, title: notice.visibleTitleEs || notice.titleEs || 'Nota' },
    content: [{ type: 'text', text: notice.contentEs || '' }],
  }).run()
  showNoticeModal.value = false
  noticeSearch.value = ''
}

function insertImage(src: string, assetId?: number) {
  editor.value?.chain().focus().setImage({ src, alt: assetId ? `asset-${assetId}` : 'imagen' }).run()
  showImageModal.value = false
}

async function uploadImage(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploadingImage.value = true
  try {
    const asset = await uploadAsset({ file, assetType: 'IMAGE', manualId: props.manualId })
    const src = asset.fileUrl ? toBackendUrl(asset.fileUrl) : toBackendUrl(`/api/v1/assets/${asset.id}/file`)
    assets.value = [asset, ...assets.value]
    insertImage(src, asset.id)
  } finally {
    uploadingImage.value = false
    input.value = ''
  }
}

async function loadModalData() {
  try {
    const [loadedAssets, loadedNotices] = await Promise.all([
      getAssets(props.manualId ? { manualId: props.manualId } : undefined),
      getNotices('NOTE'),
    ])
    assets.value = loadedAssets.filter((asset) => ['IMAGE', 'EXTRACTED_IMAGE', 'PRODUCT_IMAGE'].includes(asset.assetType))
    notices.value = loadedNotices
  } catch {
    assets.value = []
    notices.value = []
  }
}

function assetSrc(asset: AssetResponse) {
  if (asset.fileUrl) return toBackendUrl(asset.fileUrl)
  return toBackendUrl(`/api/v1/assets/${asset.id}/file`)
}

function docFromBlocks(blocks: EditorBlock[]): JSONContent {
  const content = blocks.flatMap((block) => nodeFromBlock(block))
  return { type: 'doc', content: content.length ? content : [{ type: 'paragraph' }] }
}

function nodeFromBlock(block: EditorBlock): JSONContent[] {
  const data = block.data || {}
  const savedJson = data.json as JSONContent | undefined
  if (savedJson?.type) return [savedJson]
  if (block.type === 'titulo') {
    return [{ type: 'heading', attrs: { level: Number(data.level || 1) }, content: textContent(block.content) }]
  }
  if (block.type === 'lista-ul' || block.type === 'lista-ol') {
    const listType = block.type === 'lista-ul' ? 'bulletList' : 'orderedList'
    return [{
      type: listType,
      content: block.content.split('\n').filter(Boolean).map((item) => ({
        type: 'listItem',
        content: [{ type: 'paragraph', content: textContent(item) }],
      })),
    }]
  }
  if (block.type === 'tabla') {
    const rows = block.content.split('\n').filter(Boolean).map((line) => line.split('|'))
    return [tableNode(rows)]
  }
  if (block.type === 'imagen') {
    return [{ type: 'image', attrs: { src: block.content, alt: String(data.assetId || '') } }]
  }
  if (block.type === 'nota' || block.type === 'nota-ref') {
    return [{
      type: 'noteBox',
      attrs: {
        refId: block.type === 'nota-ref' ? Number(block.content) : null,
        title: String(data.title || 'Nota'),
      },
      content: textContent(String(data.text || block.content)),
    }]
  }
  return [{ type: 'paragraph', content: textContent(block.content) }]
}

function tableNode(rows: string[][]): JSONContent {
  const normalized = rows.length ? rows : [['Cabecera 1', 'Cabecera 2'], ['', '']]
  return {
    type: 'table',
    content: normalized.map((row, rowIndex) => ({
      type: 'tableRow',
      content: row.map((cell) => ({
        type: rowIndex === 0 ? 'tableHeader' : 'tableCell',
        content: [{ type: 'paragraph', content: textContent(cell) }],
      })),
    })),
  }
}

function blocksFromDoc(doc: JSONContent): EditorBlock[] {
  return (doc.content || []).flatMap((node) => blockFromNode(node)).filter(Boolean) as EditorBlock[]
}

function blockFromNode(node: JSONContent): EditorBlock[] {
  if (node.type === 'heading') {
    return [editorBlock('titulo', plainText(node), { type: 'heading', level: node.attrs?.level || 1, json: node })]
  }
  if (node.type === 'paragraph') {
    const text = plainText(node)
    return [editorBlock('parrafo', text, { type: 'paragraph', json: node })]
  }
  if (node.type === 'bulletList' || node.type === 'orderedList') {
    const type: EditorBlockType = node.type === 'bulletList' ? 'lista-ul' : 'lista-ol'
    return [editorBlock(type, listLines(node).join('\n'), { type: node.type, json: node, items: listLines(node) })]
  }
  if (node.type === 'table') {
    const rows = tableRowsFromNode(node)
    return [editorBlock('tabla', rows.map((row) => row.join('|')).join('\n'), { type: 'table', json: node, rows: rows.slice(1), columns: rows[0] || [] })]
  }
  if (node.type === 'image') {
    return [editorBlock('imagen', String(node.attrs?.src || ''), { type: 'image', json: node, assetId: imageAssetId(node), caption: '' })]
  }
  if (node.type === 'noteBox') {
    const refId = Number(node.attrs?.refId || 0)
    if (refId) {
      return [editorBlock('nota-ref', String(refId), { type: 'notice_ref', json: node, noticeTemplateId: refId, title: node.attrs?.title, text: plainText(node) })]
    }
    return [editorBlock('nota', plainText(node), { type: 'note', json: node, title: node.attrs?.title || 'Nota', text: plainText(node) })]
  }
  return []
}

function editorBlock(type: EditorBlockType, content: string, data?: Record<string, unknown>): EditorBlock {
  return {
    id: randomId('block'),
    type,
    content,
    languageCode: props.language,
    data,
  }
}

function textContent(text: string): JSONContent[] | undefined {
  return text ? [{ type: 'text', text }] : undefined
}

function plainText(node: JSONContent): string {
  if (node.text) return node.text
  return (node.content || []).map((child) => plainText(child)).join(node.type === 'paragraph' ? '' : '\n').trim()
}

function listLines(node: JSONContent, depth = 0): string[] {
  return (node.content || []).flatMap((item) => {
    const paragraph = (item.content || []).find((child) => child.type === 'paragraph')
    const nested = (item.content || []).filter((child) => child.type === 'bulletList' || child.type === 'orderedList')
    return [
      `${'  '.repeat(depth)}${plainText(paragraph || {})}`.trimEnd(),
      ...nested.flatMap((child) => listLines(child, depth + 1)),
    ].filter(Boolean)
  })
}

function tableRowsFromNode(node: JSONContent): string[][] {
  return (node.content || []).map((row) => {
    return (row.content || []).map((cell) => plainText(cell).replace(/\n+/g, ' ').trim())
  })
}

function imageAssetId(node: JSONContent) {
  const alt = String(node.attrs?.alt || '')
  return alt.startsWith('asset-') ? Number(alt.replace('asset-', '')) : undefined
}
</script>

<template>
  <article class="rich-section" :class="{ selected }" @click="focusEditor">
    <header class="section-bar">
      <button class="drag-handle" draggable="true" title="Arrastrar sección" @click.stop @mousedown.stop>
        <GripVertical class="drag-dots" :size="17" />
      </button>
      <span class="section-number">{{ section.sectionNumber || section.sortOrder }}.</span>
      <input class="section-title-input" :value="sectionTitle" placeholder="Título de sección" @input="patchTitle(($event.target as HTMLInputElement).value)" />
      <button class="bar-icon" title="Comprimir editor" @click.stop="patch({ collapsed: !section.collapsed })">
        <ChevronUp :class="{ collapsed: section.collapsed }" :size="18" />
      </button>
    </header>

    <div v-if="!section.collapsed" class="section-content">
      <div class="toolbar" @mousedown.prevent.stop @click.stop>
        <div class="ribbon-group compact-group">
          <div class="ribbon-row">
            <button class="tool-btn mini" title="Deshacer" @click="undo"><Undo2 :size="16" /><span>Deshacer</span></button>
            <button class="tool-btn mini" title="Rehacer" @click="redo"><Redo2 :size="16" /><span>Rehacer</span></button>
          </div>
          <div class="ribbon-row">
            <button class="tool-btn mini" title="Copiar" @click="copySelection"><Copy :size="16" /><span>Copiar</span></button>
            <button class="tool-btn mini" title="Cortar" @click="cutSelection"><Scissors :size="16" /><span>Cortar</span></button>
            <button class="tool-btn mini" title="Pegar" @click="pasteClipboard"><ClipboardPaste :size="16" /><span>Pegar</span></button>
          </div>
          <span class="group-label">Portapapeles</span>
        </div>

        <div class="ribbon-group">
          <div class="ribbon-row">
            <button class="tool-btn" :class="{ active: editor?.isActive('heading', { level: 1 }) }" title="Título 1" @click="setHeading(1)"><Heading1 :size="24" /><span>Título 1</span></button>
            <button class="tool-btn" :class="{ active: editor?.isActive('heading', { level: 2 }) }" title="Título 2" @click="setHeading(2)"><Heading2 :size="24" /><span>Título 2</span></button>
            <button class="tool-btn" :class="{ active: editor?.isActive('heading', { level: 3 }) }" title="Título 3" @click="setHeading(3)"><Heading3 :size="24" /><span>Título 3</span></button>
          </div>
          <span class="group-label">Estilos</span>
        </div>

        <div class="ribbon-group">
          <div class="ribbon-row">
            <button class="tool-btn" :class="{ active: editor?.isActive('bulletList') }" title="Lista" @click="editor?.chain().focus().toggleBulletList().run()"><List :size="22" /><span>Lista</span></button>
            <button class="tool-btn" :class="{ active: editor?.isActive('orderedList') }" title="Lista ordenada" @click="editor?.chain().focus().toggleOrderedList().run()"><ListOrdered :size="22" /><span>Lista ord.</span></button>
            <button class="tool-btn" title="Sangrar lista" @click="editor?.chain().focus().sinkListItem('listItem').run()"><PanelTopClose :size="20" /><span>Nivel +</span></button>
            <button class="tool-btn" title="Reducir sangría" @click="editor?.chain().focus().liftListItem('listItem').run()"><PanelTopClose class="flip" :size="20" /><span>Nivel -</span></button>
          </div>
          <span class="group-label">Párrafo</span>
        </div>

        <div class="ribbon-group">
          <div class="ribbon-row">
            <button class="tool-btn" title="Tabla" @click="insertTable"><TableIcon :size="22" /><span>Tabla</span></button>
            <button class="tool-btn" title="Imagen" @click="openImageModal"><FileImage :size="22" /><span>Imagen</span></button>
            <div class="toolbar-menu">
              <button class="tool-btn" title="Nota"><NotebookPen :size="22" /><span>Nota</span></button>
              <div class="submenu">
                <button @click="openNoticeMenu('new')">Nota nueva</button>
                <button @click="openNoticeMenu('library')">Elegir nota</button>
              </div>
            </div>
          </div>
          <span class="group-label">Insertar</span>
        </div>

        <div class="ribbon-group section-actions">
          <div class="ribbon-row">
            <button class="tool-btn" title="Duplicar sección" @click="emit('duplicate')"><Copy :size="21" /><span>Duplicar</span></button>
            <button class="tool-btn" title="Guardar como reutilizable" @click="emit('saveReusable')"><Save :size="21" /><span>Reutilizable</span></button>
            <button class="tool-btn danger" title="Eliminar sección" @click="emit('delete')"><Trash2 :size="21" /><span>Eliminar</span></button>
          </div>
          <span class="group-label">Sección</span>
        </div>
      </div>

      <EditorContent
        :editor="editor"
        class="editor-shell"
        :style="{ '--section-prefix': `${section.sectionNumber || section.sortOrder}.` }"
      />
      <button
        v-if="editor?.isActive('table')"
        class="table-delete-float"
        title="Eliminar tabla"
        @mousedown.prevent
        @click.stop="editor?.chain().focus().deleteTable().run()"
      >
        <Trash2 :size="15" />
        Eliminar tabla
      </button>
    </div>

    <div v-if="showImageModal" class="modal-backdrop" @click.self="showImageModal = false">
      <div class="modal-card image-modal">
        <header>
          <h3>Insertar imagen</h3>
          <button @click="showImageModal = false">×</button>
        </header>
        <label class="upload-box">
          <FileImage :size="22" />
          <span>{{ uploadingImage ? 'Subiendo...' : 'Subir imagen nueva' }}</span>
          <input type="file" accept="image/*" :disabled="uploadingImage" @change="uploadImage" />
        </label>
        <div class="asset-grid">
          <button v-for="asset in assets" :key="asset.id" @click="insertImage(assetSrc(asset), asset.id)">
            <img :src="assetSrc(asset)" :alt="asset.originalFilename" />
            <span>{{ asset.originalFilename }}</span>
          </button>
        </div>
      </div>
    </div>

    <div v-if="showNoticeModal" class="modal-backdrop" @click.self="showNoticeModal = false">
      <div class="modal-card notice-modal">
        <header>
          <h3>Elegir nota</h3>
          <button @click="showNoticeModal = false">×</button>
        </header>
        <input v-model="noticeSearch" class="field" placeholder="Buscar nota..." />
        <div class="notice-list">
          <button v-for="notice in filteredNotices" :key="notice.id" @click="insertNotice(notice)">
            <strong>{{ notice.code }} · {{ notice.titleEs }}</strong>
            <span>{{ notice.contentEs }}</span>
          </button>
        </div>
      </div>
    </div>
  </article>
</template>

<style scoped>
.rich-section { background: #fff; border: 1px solid var(--border); border-radius: 0; margin-bottom: 14px; }
.rich-section.selected { border-color: var(--dikoin-blue); box-shadow: 0 0 0 2px rgba(14, 127, 187, .1); }
.section-bar { height: 34px; display: flex; align-items: center; gap: 10px; padding: 0 10px; background: var(--dikoin-blue); color: #fff; }
.drag-handle { border: 0; background: transparent; color: #fff; padding: 3px; display: inline-flex; align-items: center; cursor: grab; }
.drag-handle:active { cursor: grabbing; }
.drag-dots { opacity: .95; }
.section-number { font-weight: 800; }
.section-title-input { flex: 1; min-width: 0; border: 0; outline: 0; background: transparent; color: #fff; font-weight: 800; }
.section-title-input::placeholder { color: rgba(255,255,255,.78); }
.bar-icon { border: 0; background: transparent; color: #fff; padding: 4px; display: inline-flex; align-items: center; }
.bar-icon .collapsed { transform: rotate(180deg); }
.section-content { min-height: 332px; position: relative; }
.toolbar { display: flex; align-items: stretch; min-height: 86px; padding: 8px 10px 6px; border-bottom: 1px solid #dce7f0; background: linear-gradient(#ffffff, #f5f9fd); overflow-x: auto; }
.ribbon-group { position: relative; display: grid; grid-template-rows: 1fr auto; align-items: stretch; gap: 4px; padding: 0 12px; border-right: 1px solid #d7e3ed; }
.ribbon-group:first-child { padding-left: 2px; }
.ribbon-group:last-child { border-right: 0; }
.ribbon-row { display: flex; align-items: center; justify-content: center; gap: 2px; }
.compact-group { min-width: 168px; }
.compact-group .ribbon-row { justify-content: flex-start; }
.group-label { align-self: end; text-align: center; color: #6b7280; font-size: 10px; line-height: 1; }
.tool-btn { min-width: 58px; min-height: 50px; border: 1px solid transparent; border-radius: 2px; background: transparent; color: #1f2937; padding: 5px 7px; display: grid; justify-items: center; align-content: center; gap: 4px; font-size: 10px; line-height: 1.05; }
.tool-btn.mini { min-width: 48px; min-height: 24px; grid-auto-flow: column; grid-auto-columns: max-content; align-content: center; align-items: center; gap: 4px; padding: 3px 5px; font-size: 10px; }
.tool-btn:hover, .tool-btn.active { border-color: #b7d7ea; color: var(--dikoin-blue); background: #eaf4fb; }
.tool-btn.danger { color: var(--dikoin-red); }
.toolbar .flip { transform: rotate(180deg); }
.toolbar-menu { position: relative; }
.toolbar-menu > button { height: 100%; }
.toolbar-menu:hover .submenu { display: grid; }
.submenu { display: none; position: absolute; z-index: 20; left: 0; top: 100%; min-width: 150px; background: #fff; border: 1px solid var(--border); box-shadow: 0 10px 20px rgba(0,0,0,.12); }
.submenu button { width: 100%; display: block; min-width: 0; text-align: left; justify-items: start; border: 0; background: #fff; padding: 9px 10px; font-size: 12px; }
.editor-shell { min-height: 260px; padding: 22px 18px 40px; counter-reset: h1 h2 h3; }
.editor-shell :deep(.rich-editor-surface) { min-height: 250px; outline: 0; }
.editor-shell :deep(.is-editor-empty:first-child::before) { content: attr(data-placeholder); float: left; color: #9aa7b4; pointer-events: none; height: 0; }
.editor-shell :deep(.ProseMirror-focused .is-editor-empty:first-child::before) { display: none; }
.editor-shell :deep(.ProseMirror p:empty::after) { content: "\00a0"; }
.editor-shell :deep(p) { margin: 0 0 10px; line-height: 1.55; }
.editor-shell :deep(h1), .editor-shell :deep(h2), .editor-shell :deep(h3) { color: var(--dikoin-blue-dark); margin: 16px 0 8px; line-height: 1.25; }
.editor-shell :deep(h1) { font-size: 20px; }
.editor-shell :deep(h2) { font-size: 17px; }
.editor-shell :deep(h3) { font-size: 15px; }
.editor-shell :deep(h1) { counter-increment: h1; counter-reset: h2 h3; }
.editor-shell :deep(h2) { counter-increment: h2; counter-reset: h3; }
.editor-shell :deep(h3) { counter-increment: h3; }
.editor-shell :deep(h1::before) { content: var(--section-prefix) counter(h1) " "; color: var(--dikoin-blue); }
.editor-shell :deep(h2::before) { content: var(--section-prefix) counter(h1) "." counter(h2) " "; color: var(--dikoin-blue); }
.editor-shell :deep(h3::before) { content: var(--section-prefix) counter(h1) "." counter(h2) "." counter(h3) " "; color: var(--dikoin-blue); }
.editor-shell :deep(ul), .editor-shell :deep(ol) { margin: 0 0 10px 22px; padding-left: 18px; }
.editor-shell :deep(table) { width: 100%; border-collapse: collapse; margin: 12px 0; }
.editor-shell :deep(th) { background: var(--dikoin-blue); color: #fff; }
.editor-shell :deep(td), .editor-shell :deep(th) { border: 1px solid #b8cce3; padding: 7px; min-width: 80px; vertical-align: top; }
.editor-shell :deep(img) { max-width: 100%; height: auto; display: block; margin: 12px 0; }
.editor-shell :deep(.note-box) { border-left: 4px solid var(--dikoin-orange); background: #fff7ed; color: #78350f; padding: 10px 12px; margin: 12px 0; }
.editor-shell :deep(.note-box::before) { content: attr(title); display: block; font-weight: 800; margin-bottom: 4px; }
.table-delete-float { position: absolute; right: 18px; bottom: 16px; z-index: 8; border: 1px solid var(--dikoin-red); background: #fff; color: var(--dikoin-red); padding: 7px 9px; border-radius: var(--radius); display: inline-flex; align-items: center; gap: 6px; font-size: 12px; box-shadow: 0 8px 20px rgba(0,0,0,.12); }
.modal-backdrop { position: fixed; inset: 0; z-index: 100; background: rgba(15, 23, 42, .35); display: grid; place-items: center; padding: 24px; }
.modal-card { width: min(760px, 100%); max-height: min(720px, 90vh); overflow: auto; background: #fff; border: 1px solid var(--border); border-radius: var(--radius); box-shadow: 0 22px 50px rgba(0,0,0,.22); padding: 16px; display: grid; gap: 14px; }
.modal-card header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.modal-card h3 { margin: 0; font-size: 16px; }
.modal-card header button { border: 0; background: transparent; font-size: 24px; color: var(--muted-foreground); }
.upload-box { border: 1px dashed var(--border); background: var(--dikoin-blue-lighter); color: var(--dikoin-blue); padding: 14px; display: flex; justify-content: center; align-items: center; gap: 8px; cursor: pointer; font-weight: 800; }
.upload-box input { display: none; }
.asset-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 10px; }
.asset-grid button { border: 1px solid var(--border); background: #fff; padding: 8px; display: grid; gap: 7px; text-align: left; }
.asset-grid img { width: 100%; aspect-ratio: 4 / 3; object-fit: contain; background: #f8fbfe; }
.asset-grid span { font-size: 11px; color: var(--muted-foreground); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.notice-list { display: grid; gap: 8px; }
.notice-list button { border: 1px solid var(--border); background: #fff; text-align: left; padding: 10px; display: grid; gap: 4px; }
.notice-list button:hover { border-color: var(--dikoin-blue); background: var(--dikoin-blue-lighter); }
.notice-list span { color: var(--muted-foreground); font-size: 12px; line-height: 1.35; max-height: 48px; overflow: hidden; }
</style>
