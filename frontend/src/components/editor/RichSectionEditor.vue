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
import { Extension, mergeAttributes, Node, type Editor, type JSONContent } from '@tiptap/core'
import { Plugin, PluginKey } from '@tiptap/pm/state'
import { Decoration, DecorationSet } from '@tiptap/pm/view'
import {
  ChevronUp,
  ClipboardPaste,
  Copy,
  FileImage,
  GripVertical,
  Heading,
  List,
  ListOrdered,
  Link2Off,
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
type EditorRegistryRecord = { editor: Editor; root: () => HTMLElement | null; markDirty: () => void }
type MovableBlockKind = 'note' | 'table' | 'image'
type MovableBlockRange = { from: number; to: number; kind: MovableBlockKind }
type ResizeHandle = 'nw' | 'ne' | 'sw' | 'se' | 'e' | 'w'

let sharedNoteClipboard: JSONContent | null = null
let sharedNoteDrag: { editor: Editor; from: number; to: number; json: JSONContent; markSourceDirty: () => void } | null = null
let sharedBlockClipboard: JSONContent | null = null
let sharedBlockDrag: { editor: Editor; from: number; to: number; json: JSONContent; kind: MovableBlockKind; markSourceDirty: () => void } | null = null
const editorRegistry: EditorRegistryRecord[] = []

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
const hasPendingTableSync = ref(false)
const showNoteMenu = ref(false)
const sectionContentRef = ref<HTMLElement | null>(null)
const tableDeletePosition = ref<{ visible: boolean; top: number; left: number }>({ visible: false, top: 0, left: 0 })
const blockActionsPosition = ref<{ visible: boolean; top: number; left: number; kind: MovableBlockKind | null }>({ visible: false, top: 0, left: 0, kind: null })
const blockResizeOverlay = ref<{ visible: boolean; top: number; left: number; width: number; height: number; kind: MovableBlockKind | null }>({ visible: false, top: 0, left: 0, width: 0, height: 0, kind: null })
const blockActionRange = ref<MovableBlockRange | null>(null)
const noteActionsPosition = ref<{ visible: boolean; top: number; left: number; linked: boolean }>({ visible: false, top: 0, left: 0, linked: false })
const noteActionRange = ref<{ from: number; to: number; linked: boolean } | null>(null)
const noteDragPreview = ref<{ visible: boolean; top: number; left: number; title: string; text: string; linked: boolean }>({ visible: false, top: 0, left: 0, title: '', text: '', linked: false })
const blockDragPreview = ref<{ visible: boolean; top: number; left: number; title: string; text: string; kind: MovableBlockKind | null }>({ visible: false, top: 0, left: 0, title: '', text: '', kind: null })
const toolbarStateTick = ref(0)
let activeNoteElement: HTMLElement | null = null
let activeBlockElement: HTMLElement | null = null
const showTablePicker = ref(false)
const tablePickerRows = ref(0)
const tablePickerCols = ref(0)
const showCustomTableModal = ref(false)
const customTableRows = ref(2)
const customTableCols = ref(5)
const rememberTableSize = ref(false)
const tablePickerMaxRows = 6
const tablePickerMaxCols = 7
let headingNumberFrame = 0
const editorDirty = ref(false)

const NoteBox = Node.create({
  name: 'noteBox',
  group: 'block',
  content: 'inline*',
  defining: true,
  draggable: true,
  selectable: true,
  isolating: true,
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
    const refId = HTMLAttributes.refId
    const linked = Boolean(refId)
    return ['div', mergeAttributes(HTMLAttributes, {
      'data-note-box': 'true',
      ...(linked ? { 'data-note-ref': String(refId) } : {}),
      draggable: 'true',
      class: linked ? 'note-box linked-note-box' : 'note-box',
    }), [
      'span',
      {
        'data-note-drag-handle': 'true',
        contenteditable: 'false',
        draggable: 'true',
        title: 'Arrastrar nota',
        class: 'note-drag-handle',
      },
    ], ['span', { class: 'note-content' }, 0]]
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

const HeadingNumbering = Extension.create({
  name: 'headingNumbering',
  addProseMirrorPlugins() {
    return [
      new Plugin({
        key: new PluginKey('headingNumbering'),
        props: {
          decorations: (state) => {
            const decorations: Decoration[] = []
            let h1 = 0
            let h2 = 0
            let h3 = 0
            const sectionPrefix = props.section.sectionNumber || String(props.section.sortOrder)

            state.doc.descendants((node, position) => {
              if (node.type.name !== 'heading') return

              const level = Number(node.attrs.level || 1)
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

              decorations.push(Decoration.node(position, position + node.nodeSize, {
                'data-heading-number': number,
                class: 'numbered-heading',
              }))
            })

            return DecorationSet.create(state.doc, decorations)
          },
        },
      }),
    ]
  },
})

const ResizableImage = Image.extend({
  addAttributes() {
    return {
      ...(this.parent?.() || {}),
      width: {
        default: null,
        parseHTML: (element) => element.getAttribute('width') || element.style.width || null,
        renderHTML: (attributes) => {
          if (!attributes.width) return {}
          return { style: `width: ${cssSize(attributes.width)}; max-width: 100%; height: auto;` }
        },
      },
    }
  },
})

const ResizableTable = Table.extend({
  addAttributes() {
    return {
      ...(this.parent?.() || {}),
      width: {
        default: null,
        parseHTML: (element) => element.getAttribute('data-width') || element.style.width || null,
        renderHTML: (attributes) => {
          if (!attributes.width) return {}
          return {
            'data-width': String(attributes.width),
            style: `width: ${cssSize(attributes.width)};`,
          }
        },
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
    ResizableImage.configure({ inline: false, allowBase64: true }),
    ResizableTable.configure({ resizable: true }),
    TableRow,
    TableHeader,
    TableCell,
    NoteBox,
    ParagraphEnter,
    HeadingNumbering,
  ],
  content: docFromBlocks(visibleBlocks()),
  editorProps: {
    attributes: {
      class: 'rich-editor-surface',
    },
    handleDOMEvents: {
      beforeinput: (_view, event) => {
        if (!isInsideLinkedNote()) return false
        event.preventDefault()
        return true
      },
      paste: (_view, event) => {
        if (!isInsideLinkedNote()) return false
        event.preventDefault()
        return true
      },
      keydown: (_view, event) => {
        if (!isInsideLinkedNote()) return false
        if (event.ctrlKey || event.metaKey || event.altKey) return false
        const allowedKeys = ['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End', 'PageUp', 'PageDown', 'Tab', 'Escape']
        const shouldBlock = !allowedKeys.includes(event.key)
        if (shouldBlock) event.preventDefault()
        return shouldBlock
      },
      mousedown: (_view, event) => {
        const target = event.target instanceof Element ? event.target : null
        const note = target?.closest('[data-note-box]') as HTMLElement | null
        if (!note) return false
        const isHandle = Boolean(target?.closest('[data-note-drag-handle]'))
        const noteRect = note.getBoundingClientRect()
        if (!isHandle && event.clientX - noteRect.left > 46) return false
        const info = noteInfoFromElement(note)
        if (!info) return false
        activateNoteElement(note)
        showNoteActionsForElement(note, info)
        editor.value?.commands.setNodeSelection(info.from)
        return false
      },
      dragstart: (_view, event) => {
        const target = event.target instanceof Element ? event.target : null
        const note = target?.closest('[data-note-box]') as HTMLElement | null
        if (!note || !target?.closest('[data-note-drag-handle]')) return false
        const info = noteInfoFromElement(note)
        const noteJson = activeNoteJson(info)
        if (!info || !noteJson || !editor.value) return false
        activateNoteElement(note)
        showNoteActionsForElement(note, info)
        editor.value?.commands.setNodeSelection(info.from)
        sharedNoteDrag = { editor: editor.value, from: info.from, to: info.to, json: structuredClone(noteJson), markSourceDirty: markEditorDirty }
        if (event.dataTransfer) {
          event.dataTransfer.effectAllowed = 'move'
          event.dataTransfer.setData('text/plain', noteJsonText(noteJson))
        }
        return false
      },
      dragend: () => {
        stopNoteDragFeedback()
        sharedNoteDrag = null
        return false
      },
      drop: (_view, event) => {
        if (!sharedNoteDrag || !editor.value) return false
        const position = editor.value.view.posAtCoords({ left: event.clientX, top: event.clientY })
        if (!position) return false
        event.preventDefault()
        moveDraggedNoteTo(editorRegistry.find((record) => record.editor === editor.value) || currentEditorRegistryRecord(), position.pos)
        return true
      },
    },
  },
  onFocus: () => emit('select', props.section.id),
  onUpdate: ({ editor }) => {
    if (syncingFromProps.value) return
    editorDirty.value = true
    refreshToolbarState()
    scheduleHeadingNumbers()
    updateBlockActionsPosition()
    updateNoteDeletePosition()
    if (editor.isActive('table')) {
      hasPendingTableSync.value = true
    }
  },
  onSelectionUpdate: ({ editor }) => {
    refreshToolbarState()
    scheduleHeadingNumbers()
    updateBlockActionsPosition()
    updateNoteDeletePosition()
  },
  onBlur: () => {
    tableDeletePosition.value.visible = false
    hideBlockActions()
    hideNoteActions()
    flushEditorSync()
  },
})

const sectionTitle = computed(() => props.language === 'EN'
  ? props.section.titleEn || ''
  : props.section.titleEs)

const currentHeadingLevel = computed(() => {
  toolbarStateTick.value
  return headingLevel()
})

const headingGroupLabel = computed(() => currentHeadingLevel.value ? `Títulos · nivel ${currentHeadingLevel.value}` : 'Títulos')

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
  registerEditorInstance()
  await loadModalData()
  refreshLinkedNotesFromLibrary()
  scheduleHeadingNumbers()
})

onBeforeUnmount(() => {
  unregisterEditorInstance()
  flushEditorSync()
  editor.value?.destroy()
})

watch(
  () => [props.section.id, props.language],
  () => {
    if (!editor.value) return
    syncingFromProps.value = true
    editor.value.commands.setContent(docFromBlocks(visibleBlocks()))
    queueMicrotask(() => {
      syncingFromProps.value = false
      scheduleHeadingNumbers()
    })
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

function syncEditorToSection() {
  if (!editor.value || syncingFromProps.value) return
  hasPendingTableSync.value = false
  editorDirty.value = false
  patch({ blocks: mergeLanguageBlocks(blocksFromDoc(editor.value.getJSON())) })
}

function flushEditorSync() {
  if (editorDirty.value || hasPendingTableSync.value) {
    syncEditorToSection()
  }
}

defineExpose({ flushEditorSync })

function markEditorDirty() {
  editorDirty.value = true
}

function currentEditorRegistryRecord() {
  const current = editor.value
  let record = editorRegistry.find((item) => item.editor === current)
  if (!record && current) {
    record = {
      editor: current,
      root: () => sectionContentRef.value,
      markDirty: markEditorDirty,
    }
  }
  return record
}

function registerEditorInstance() {
  const current = editor.value
  if (!current || editorRegistry.some((item) => item.editor === current)) return
  editorRegistry.push({
    editor: current,
    root: () => sectionContentRef.value,
    markDirty: markEditorDirty,
  })
}

function unregisterEditorInstance() {
  const current = editor.value
  const index = editorRegistry.findIndex((item) => item.editor === current)
  if (index >= 0) editorRegistry.splice(index, 1)
}

function mergeLanguageBlocks(languageBlocks: EditorBlock[]) {
  return [
    ...props.section.blocks.filter((block) => block.languageCode !== props.language),
    ...languageBlocks,
  ]
}

function selectSection() {
  emit('select', props.section.id)
}

function setHeading(level: 1 | 2 | 3) {
  editor.value?.chain().focus().toggleHeading({ level }).run()
  refreshToolbarState()
  scheduleHeadingNumbers()
}

function toggleDefaultHeading() {
  const current = editor.value
  if (!current) return
  if (current.isActive('heading')) {
    current.chain().focus().setParagraph().run()
  } else {
    current.chain().focus().setHeading({ level: 1 }).run()
  }
  refreshToolbarState()
  scheduleHeadingNumbers()
}

function headingLevel() {
  const current = editor.value
  if (!current) return 0
  if (current.isActive('heading', { level: 1 })) return 1
  if (current.isActive('heading', { level: 2 })) return 2
  if (current.isActive('heading', { level: 3 })) return 3
  return 0
}

function increaseHeadingLevel() {
  const level = headingLevel()
  if (!level || level >= 3) return
  editor.value?.chain().focus().setHeading({ level: (level + 1) as 1 | 2 | 3 }).run()
  refreshToolbarState()
  scheduleHeadingNumbers()
}

function decreaseHeadingLevel() {
  const level = headingLevel()
  if (!level) return
  if (level === 1) {
    editor.value?.chain().focus().setParagraph().run()
  } else {
    editor.value?.chain().focus().setHeading({ level: (level - 1) as 1 | 2 | 3 }).run()
  }
  refreshToolbarState()
  scheduleHeadingNumbers()
}

function setParagraph() {
  editor.value?.chain().focus().setParagraph().run()
  refreshToolbarState()
  scheduleHeadingNumbers()
}

function refreshToolbarState() {
  toolbarStateTick.value += 1
}

function scheduleHeadingNumbers() {
  if (headingNumberFrame) cancelAnimationFrame(headingNumberFrame)
  headingNumberFrame = requestAnimationFrame(() => {
    headingNumberFrame = 0
    updateHeadingNumbers()
  })
}

function updateHeadingNumbers() {
  const root = editor.value?.view.dom
  if (!root) return
  let h1 = 0
  let h2 = 0
  let h3 = 0
  const sectionPrefix = props.section.sectionNumber || String(props.section.sortOrder)
  root.querySelectorAll<HTMLElement>('h1,h2,h3').forEach((heading) => {
    if (heading.tagName === 'H1') {
      h1++
      h2 = 0
      h3 = 0
      heading.dataset.headingNumber = `${sectionPrefix}.${h1}`
      return
    }
    if (heading.tagName === 'H2') {
      if (!h1) h1 = 1
      h2++
      h3 = 0
      heading.dataset.headingNumber = `${sectionPrefix}.${h1}.${h2}`
      return
    }
    if (!h1) h1 = 1
    if (!h2) h2 = 1
    h3++
    heading.dataset.headingNumber = `${sectionPrefix}.${h1}.${h2}.${h3}`
  })
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
  sharedNoteClipboard = null
  sharedBlockClipboard = null
  await writeClipboard(text)
  editor.value?.commands.focus()
}

async function cutSelection() {
  const text = selectedText()
  if (!text || !editor.value) return
  sharedNoteClipboard = null
  sharedBlockClipboard = null
  await writeClipboard(text)
  editor.value.chain().focus().deleteSelection().run()
}

async function pasteClipboard() {
  if (!editor.value) return
  if (sharedNoteClipboard) {
    editor.value.chain().focus().insertContent(structuredClone(sharedNoteClipboard)).run()
    return
  }
  if (sharedBlockClipboard) {
    editor.value.chain().focus().insertContent(structuredClone(sharedBlockClipboard)).run()
    return
  }
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

function insertTable(rows = 3, cols = 3) {
  editor.value?.chain().focus().insertTable({ rows: Math.max(rows, 1), cols: Math.max(cols, 1), withHeaderRow: true }).run()
  closeTablePicker()
}

function openCustomTableModal() {
  closeTablePicker()
  showCustomTableModal.value = true
}

function confirmCustomTable() {
  const rows = Math.max(Number(customTableRows.value) || 1, 1)
  const cols = Math.max(Number(customTableCols.value) || 1, 1)
  if (rememberTableSize.value) {
    customTableRows.value = rows
    customTableCols.value = cols
  }
  showCustomTableModal.value = false
  insertTable(rows, cols)
}

function toggleTablePicker() {
  showTablePicker.value = !showTablePicker.value
  if (showTablePicker.value) {
    tablePickerRows.value = 0
    tablePickerCols.value = 0
  }
}

function closeTablePicker() {
  showTablePicker.value = false
  tablePickerRows.value = 0
  tablePickerCols.value = 0
}

function hoverTableSize(rows: number, cols: number) {
  tablePickerRows.value = rows
  tablePickerCols.value = cols
}

function tablePickerLabel() {
  if (!tablePickerRows.value || !tablePickerCols.value) return 'Insertar tabla'
  return `${tablePickerCols.value} x ${tablePickerRows.value}`
}

function updateTableDeletePosition() {
  const current = editor.value
  const wrapper = sectionContentRef.value
  if (!current || !wrapper || !current.isActive('table')) {
    tableDeletePosition.value.visible = false
    return
  }

  const domAtSelection = current.view.domAtPos(current.state.selection.from).node
  const element = domAtSelection instanceof Element ? domAtSelection : domAtSelection.parentElement
  const table = element?.closest('table')
  if (!table) {
    tableDeletePosition.value.visible = false
    return
  }

  const tableRect = table.getBoundingClientRect()
  const wrapperRect = wrapper.getBoundingClientRect()
  tableDeletePosition.value = {
    visible: true,
    top: tableRect.top - wrapperRect.top + wrapper.scrollTop - 32,
    left: tableRect.right - wrapperRect.left + wrapper.scrollLeft - 30,
  }
}

function updateBlockActionsPosition() {
  const current = editor.value
  if (!current) {
    hideBlockActions()
    return
  }

  const domAtSelection = current.view.domAtPos(current.state.selection.from).node
  const element = domAtSelection instanceof Element ? domAtSelection : domAtSelection.parentElement
  const blockElement = movableBlockElementFromTarget(element)
  if (!blockElement) {
    hideBlockActions()
    return
  }

  showBlockActionsForElement(blockElement.element, blockElement.kind, blockInfoFromElement(blockElement.element, blockElement.kind))
}

function updateNoteDeletePosition() {
  const current = editor.value
  const wrapper = sectionContentRef.value
  if (!current || !wrapper) {
    hideNoteActions()
    return
  }

  const domAtSelection = current.view.domAtPos(current.state.selection.from).node
  const element = domAtSelection instanceof Element ? domAtSelection : domAtSelection.parentElement
  const note = element?.closest('[data-note-box]')
  if (!note) {
    hideNoteActions()
    return
  }

  showNoteActionsForElement(note as HTMLElement, activeNoteInfo())
}

function handleSectionClick(event: MouseEvent) {
  const target = event.target instanceof Element ? event.target : null
  const note = target?.closest('[data-note-box]') as HTMLElement | null
  if (!note) {
    clearActiveNoteElement()
    const blockElement = movableBlockElementFromTarget(target)
    if (!blockElement) {
      clearActiveBlockElement()
      hideBlockActions()
      return
    }
    activateBlockElement(blockElement.element)
    showBlockActionsForElement(blockElement.element, blockElement.kind, blockInfoFromElement(blockElement.element, blockElement.kind))
    return
  }
  clearActiveBlockElement()
  hideBlockActions()
  activateNoteElement(note)
  showNoteActionsForElement(note, noteInfoFromElement(note))
}

function handleSectionMouseDown(event: MouseEvent) {
  const target = event.target instanceof Element ? event.target : null
  const handle = target?.closest('[data-note-drag-handle]')
  const note = target?.closest('[data-note-box]') as HTMLElement | null
  if (!handle || !note) return
  const info = noteInfoFromElement(note)
  const noteJson = activeNoteJson(info)
  if (!info || !noteJson || !editor.value) return

  event.preventDefault()
  activateNoteElement(note)
  showNoteActionsForElement(note, info)
  editor.value.commands.setNodeSelection(info.from)
  sharedNoteDrag = {
    editor: editor.value,
    from: info.from,
    to: info.to,
    json: structuredClone(noteJson),
    markSourceDirty: markEditorDirty,
  }
  startNoteDragFeedback(event, noteJson, info.linked)
  document.body.classList.add('note-dragging')
  document.addEventListener('mousemove', updateNoteDragFeedback)
  document.addEventListener('mouseup', finishNoteMouseDrag, { once: true })
}

function startActiveNoteDrag(event: MouseEvent) {
  const info = noteActionRange.value || activeNoteInfo()
  const noteJson = activeNoteJson(info)
  if (!info || !noteJson || !editor.value) return

  event.preventDefault()
  editor.value.commands.setNodeSelection(info.from)
  sharedNoteDrag = {
    editor: editor.value,
    from: info.from,
    to: info.to,
    json: structuredClone(noteJson),
    markSourceDirty: markEditorDirty,
  }
  startNoteDragFeedback(event, noteJson, info.linked)
  document.body.classList.add('note-dragging')
  document.addEventListener('mousemove', updateNoteDragFeedback)
  document.addEventListener('mouseup', finishNoteMouseDrag, { once: true })
}

function handleSectionMouseMove(event: MouseEvent) {
  const target = event.target instanceof Element ? event.target : null
  if (!target || target.closest('.note-actions-float') || target.closest('.block-actions-float')) return
  const note = target.closest('[data-note-box]')
  if (!note) {
    const blockElement = movableBlockElementFromTarget(target)
    if (blockElement) {
      clearActiveNoteElement()
      hideNoteActions()
      showBlockActionsForElement(blockElement.element, blockElement.kind, blockInfoFromElement(blockElement.element, blockElement.kind))
      return
    }
    if (!activeNoteInfo()) {
      hideNoteActions()
      clearActiveNoteElement()
    }
    if (!blockActionRange.value) {
      hideBlockActions()
      clearActiveBlockElement()
    }
    return
  }
  hideBlockActions()
  clearActiveBlockElement()
  showNoteActionsForElement(note as HTMLElement, noteInfoFromElement(note as HTMLElement))
}

function handleSectionMouseLeave() {
  hideNoteActions()
  hideBlockActions()
  if (!activeNoteInfo()) clearActiveNoteElement()
  if (!blockActionRange.value) clearActiveBlockElement()
}

function finishNoteMouseDrag(event: MouseEvent) {
  stopNoteDragFeedback()
  document.body.classList.remove('note-dragging')
  if (!sharedNoteDrag) return
  const target = editorRegistry.find((record) => {
    const rect = record.editor.view.dom.getBoundingClientRect()
    return event.clientX >= rect.left && event.clientX <= rect.right && event.clientY >= rect.top && event.clientY <= rect.bottom
  })
  if (!target) {
    sharedNoteDrag = null
    clearActiveNoteElement()
    return
  }
  const position = target.editor.view.posAtCoords({ left: event.clientX, top: event.clientY })
  if (!position) {
    sharedNoteDrag = null
    clearActiveNoteElement()
    return
  }
  moveDraggedNoteTo(target, position.pos)
}

function startActiveBlockDrag(event: MouseEvent) {
  const range = blockActionRange.value
  const blockJson = activeBlockJson()
  if (!range || !blockJson || !editor.value) return

  event.preventDefault()
  sharedBlockDrag = {
    editor: editor.value,
    from: range.from,
    to: range.to,
    json: structuredClone(blockJson),
    kind: range.kind,
    markSourceDirty: markEditorDirty,
  }
  startBlockDragFeedback(event, blockJson, range.kind)
  document.body.classList.add('note-dragging')
  document.addEventListener('mousemove', updateBlockDragFeedback)
  document.addEventListener('mouseup', finishBlockMouseDrag, { once: true })
}

function finishBlockMouseDrag(event: MouseEvent) {
  stopBlockDragFeedback()
  document.body.classList.remove('note-dragging')
  if (!sharedBlockDrag) return
  const target = editorRegistry.find((record) => {
    const rect = record.editor.view.dom.getBoundingClientRect()
    return event.clientX >= rect.left && event.clientX <= rect.right && event.clientY >= rect.top && event.clientY <= rect.bottom
  })
  if (!target) {
    sharedBlockDrag = null
    clearActiveBlockElement()
    return
  }
  const position = target.editor.view.posAtCoords({ left: event.clientX, top: event.clientY })
  if (!position) {
    sharedBlockDrag = null
    clearActiveBlockElement()
    return
  }
  moveDraggedBlockTo(target, position.pos)
}

function startBlockDragFeedback(event: MouseEvent, blockJson: JSONContent, kind: MovableBlockKind) {
  updateBlockDragPreview(event.clientX, event.clientY, blockJson, kind)
  activeBlockElement?.classList.add('movable-block-drag-source')
}

function updateBlockDragFeedback(event: MouseEvent) {
  if (!sharedBlockDrag) return
  updateBlockDragPreview(event.clientX, event.clientY, sharedBlockDrag.json, sharedBlockDrag.kind)
}

function updateBlockDragPreview(clientX: number, clientY: number, blockJson: JSONContent, kind: MovableBlockKind) {
  blockDragPreview.value = {
    visible: true,
    top: clientY + 14,
    left: clientX + 14,
    title: kind === 'table' ? 'Tabla' : 'Imagen',
    text: blockPreviewText(blockJson, kind),
    kind,
  }
}

function stopBlockDragFeedback() {
  document.removeEventListener('mousemove', updateBlockDragFeedback)
  activeBlockElement?.classList.remove('movable-block-drag-source')
  blockDragPreview.value.visible = false
}

function startNoteDragFeedback(event: MouseEvent, noteJson: JSONContent, linked: boolean) {
  updateNoteDragPreview(event.clientX, event.clientY, noteJson, linked)
  activeNoteElement?.classList.add('note-box-drag-source')
}

function updateNoteDragFeedback(event: MouseEvent) {
  if (!sharedNoteDrag) return
  updateNoteDragPreview(event.clientX, event.clientY, sharedNoteDrag.json, Boolean(sharedNoteDrag.json.attrs?.refId))
}

function updateNoteDragPreview(clientX: number, clientY: number, noteJson: JSONContent, linked: boolean) {
  noteDragPreview.value = {
    visible: true,
    top: clientY + 14,
    left: clientX + 14,
    title: String(noteJson.attrs?.title || 'Nota'),
    text: noteJsonText(noteJson),
    linked,
  }
}

function stopNoteDragFeedback() {
  document.removeEventListener('mousemove', updateNoteDragFeedback)
  activeNoteElement?.classList.remove('note-box-drag-source')
  noteDragPreview.value.visible = false
}

function showNoteActionsForElement(note: HTMLElement, info = noteInfoFromElement(note)) {
  const wrapper = sectionContentRef.value
  if (!wrapper || !info) {
    hideNoteActions()
    return
  }

  const noteRect = note.getBoundingClientRect()
  const wrapperRect = wrapper.getBoundingClientRect()
  const actionsWidth = info.linked ? 156 : 124
  noteActionsPosition.value = {
    visible: true,
    top: noteRect.top - wrapperRect.top + wrapper.scrollTop - 14,
    left: noteRect.right - wrapperRect.left + wrapper.scrollLeft - actionsWidth - 8,
    linked: info.linked,
  }
  noteActionRange.value = info
}

function showBlockActionsForElement(element: HTMLElement, kind: MovableBlockKind, info = blockInfoFromElement(element, kind)) {
  const wrapper = sectionContentRef.value
  if (!wrapper || !info) {
    hideBlockActions()
    return
  }

  const blockRect = element.getBoundingClientRect()
  const wrapperRect = wrapper.getBoundingClientRect()
  const actionsWidth = 124
  blockActionsPosition.value = {
    visible: true,
    top: blockRect.top - wrapperRect.top + wrapper.scrollTop - 14,
    left: blockRect.right - wrapperRect.left + wrapper.scrollLeft - actionsWidth - 8,
    kind,
  }
  blockResizeOverlay.value = {
    visible: true,
    top: blockRect.top - wrapperRect.top + wrapper.scrollTop,
    left: blockRect.left - wrapperRect.left + wrapper.scrollLeft,
    width: blockRect.width,
    height: blockRect.height,
    kind,
  }
  blockActionRange.value = info
}

function hideNoteActions() {
  noteActionsPosition.value.visible = false
  noteActionRange.value = null
}

function hideBlockActions() {
  blockActionsPosition.value.visible = false
  blockActionsPosition.value.kind = null
  blockResizeOverlay.value.visible = false
  blockResizeOverlay.value.kind = null
  blockActionRange.value = null
}

function activateNoteElement(note: HTMLElement) {
  if (activeNoteElement && activeNoteElement !== note) {
    activeNoteElement.classList.remove('note-box-active')
  }
  activeNoteElement = note
  activeNoteElement.classList.add('note-box-active')
}

function clearActiveNoteElement() {
  activeNoteElement?.classList.remove('note-box-active')
  activeNoteElement = null
}

function activateBlockElement(element: HTMLElement) {
  if (activeBlockElement && activeBlockElement !== element) {
    activeBlockElement.classList.remove('movable-block-active')
  }
  activeBlockElement = element
  activeBlockElement.classList.add('movable-block-active')
}

function clearActiveBlockElement() {
  activeBlockElement?.classList.remove('movable-block-active')
  activeBlockElement = null
}

function movableBlockElementFromTarget(target: Element | null) {
  if (!target) return null
  const table = target.closest('table') as HTMLElement | null
  if (table) return { element: table, kind: 'table' as MovableBlockKind }
  const image = target.tagName === 'IMG' ? target as HTMLElement : target.closest('img') as HTMLElement | null
  if (image && image.closest('.rich-editor-surface')) return { element: image, kind: 'image' as MovableBlockKind }
  return null
}

function deleteActiveNote() {
  const activeNote = noteActionRange.value || activeNoteInfo()
  if (!editor.value || !activeNote) return
  editor.value.view.dispatch(editor.value.state.tr.delete(activeNote.from, activeNote.to).scrollIntoView())
  editor.value.commands.focus()
  hideNoteActions()
}

function unlinkActiveNote() {
  const current = editor.value
  const activeNote = noteActionRange.value || activeNoteInfo()
  if (!current || !activeNote || !activeNote.linked) return

  const node = current.state.doc.nodeAt(activeNote.from)
  if (!node || node.type.name !== 'noteBox') return

  const title = String(node.attrs.title || 'Nota')
  const text = node.textContent || ''
  current.chain().focus().insertContentAt(
    { from: activeNote.from, to: activeNote.to },
    {
      type: 'noteBox',
      attrs: { refId: null, title },
      content: textContent(text),
    },
  ).run()
  hideNoteActions()
  editorDirty.value = true
}

async function copyActiveNote() {
  const noteJson = activeNoteJson()
  if (!noteJson) return
  sharedNoteClipboard = structuredClone(noteJson)
  await writeClipboard(noteJsonText(noteJson))
  editor.value?.commands.focus()
}

async function cutActiveNote() {
  const noteJson = activeNoteJson()
  const activeNote = noteActionRange.value || activeNoteInfo()
  if (!editor.value || !noteJson || !activeNote) return
  sharedNoteClipboard = structuredClone(noteJson)
  await writeClipboard(noteJsonText(noteJson))
  editor.value.view.dispatch(editor.value.state.tr.delete(activeNote.from, activeNote.to).scrollIntoView())
  editor.value.commands.focus()
  hideNoteActions()
}

async function copyActiveBlock() {
  const blockJson = activeBlockJson()
  if (!blockJson) return
  sharedNoteClipboard = null
  sharedBlockClipboard = structuredClone(blockJson)
  await writeClipboard(blockPreviewText(blockJson, blockActionRange.value?.kind || 'table'))
  editor.value?.commands.focus()
}

async function cutActiveBlock() {
  const blockJson = activeBlockJson()
  const range = blockActionRange.value
  if (!editor.value || !blockJson || !range) return
  sharedNoteClipboard = null
  sharedBlockClipboard = structuredClone(blockJson)
  await writeClipboard(blockPreviewText(blockJson, range.kind))
  editor.value.view.dispatch(editor.value.state.tr.delete(range.from, range.to).scrollIntoView())
  editor.value.commands.focus()
  markEditorDirty()
  hideBlockActions()
  clearActiveBlockElement()
}

function deleteActiveBlock() {
  const range = blockActionRange.value
  if (!editor.value || !range) return
  editor.value.view.dispatch(editor.value.state.tr.delete(range.from, range.to).scrollIntoView())
  editor.value.commands.focus()
  markEditorDirty()
  hideBlockActions()
  clearActiveBlockElement()
}

function startBlockResize(event: MouseEvent, handle: ResizeHandle) {
  const range = blockActionRange.value
  const current = editor.value
  if (!range || !current) return

  event.preventDefault()
  event.stopPropagation()
  const blockElement = activeBlockElement
  const startX = event.clientX
  const startWidth = blockResizeOverlay.value.width
  const onMove = (moveEvent: MouseEvent) => {
    const delta = moveEvent.clientX - startX
    const signedDelta = handle.includes('w') ? -delta : delta
    const maxWidth = Math.max(180, current.view.dom.getBoundingClientRect().width - 36)
    const nextWidth = Math.min(maxWidth, Math.max(80, startWidth + signedDelta))
    if (blockElement) {
      blockElement.style.width = `${Math.round(nextWidth)}px`
      if (range.kind === 'image') {
        blockElement.style.maxWidth = '100%'
        blockElement.style.height = 'auto'
      }
    }
    updateBlockWidth(range, nextWidth)
    blockResizeOverlay.value.width = nextWidth
  }
  const onUp = () => {
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    markEditorDirty()
    requestAnimationFrame(updateBlockActionsPosition)
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp, { once: true })
}

function updateBlockWidth(range: MovableBlockRange, width: number) {
  const current = editor.value
  if (!current) return
  const node = current.state.doc.nodeAt(range.from)
  if (!node || !['table', 'image'].includes(node.type.name)) return
  const nextAttrs = { ...node.attrs, width: Math.round(width) }
  current.view.dispatch(current.state.tr.setNodeMarkup(range.from, undefined, nextAttrs).scrollIntoView())
  markEditorDirty()
}

function activeNoteJson(range = noteActionRange.value || activeNoteInfo()) {
  const current = editor.value
  if (!current || !range) return null
  const node = current.state.doc.nodeAt(range.from)
  return node?.type.name === 'noteBox' ? node.toJSON() as JSONContent : null
}

function activeBlockJson(range = blockActionRange.value) {
  const current = editor.value
  if (!current || !range) return null
  const node = current.state.doc.nodeAt(range.from)
  const expectedType = range.kind === 'table' ? 'table' : 'image'
  return node?.type.name === expectedType ? node.toJSON() as JSONContent : null
}

function noteJsonText(noteJson: JSONContent) {
  return plainText(noteJson) || String(noteJson.attrs?.title || 'Nota')
}

function blockPreviewText(blockJson: JSONContent, kind: MovableBlockKind) {
  if (kind === 'image') return String(blockJson.attrs?.alt || blockJson.attrs?.src || 'Imagen')
  const rows = tableRowsFromNode(blockJson)
  const cols = rows[0]?.length || 0
  return `${rows.length} filas x ${cols} columnas`
}

function moveDraggedBlockTo(target: EditorRegistryRecord | undefined, position: number) {
  const drag = sharedBlockDrag
  const targetEditor = target?.editor
  if (!drag || !targetEditor) return

  const sourceSize = drag.to - drag.from
  if (drag.editor === targetEditor) {
    if (position >= drag.from && position <= drag.to) {
      sharedBlockDrag = null
      return
    }
    const insertPosition = Math.max(0, position > drag.to ? position - sourceSize : position)
    drag.editor.view.dispatch(drag.editor.state.tr.delete(drag.from, drag.to))
    targetEditor.commands.insertContentAt(insertPosition, structuredClone(drag.json))
  } else {
    targetEditor.commands.insertContentAt(position, structuredClone(drag.json))
    drag.editor.view.dispatch(drag.editor.state.tr.delete(drag.from, drag.to))
  }

  drag.markSourceDirty()
  target.markDirty()
  sharedBlockDrag = null
  hideBlockActions()
  clearActiveBlockElement()
}

function moveDraggedNoteTo(target: EditorRegistryRecord | undefined, position: number) {
  const drag = sharedNoteDrag
  const targetEditor = target?.editor
  if (!drag || !targetEditor) return

  const sourceSize = drag.to - drag.from
  if (drag.editor === targetEditor) {
    if (position >= drag.from && position <= drag.to) {
      sharedNoteDrag = null
      return
    }
    const insertPosition = Math.max(0, position > drag.to ? position - sourceSize : position)
    drag.editor.view.dispatch(drag.editor.state.tr.delete(drag.from, drag.to))
    targetEditor.commands.insertContentAt(insertPosition, structuredClone(drag.json))
  } else {
    targetEditor.commands.insertContentAt(position, structuredClone(drag.json))
    drag.editor.view.dispatch(drag.editor.state.tr.delete(drag.from, drag.to))
  }

  drag.markSourceDirty()
  target.markDirty()
  sharedNoteDrag = null
  hideNoteActions()
  clearActiveNoteElement()
}

function activeNoteInfo() {
  const current = editor.value
  if (!current) return null
  const { $from } = current.state.selection
  for (let depth = $from.depth; depth > 0; depth--) {
    const node = $from.node(depth)
    if (node.type.name === 'noteBox') {
      return {
        from: $from.before(depth),
        to: $from.after(depth),
        linked: Boolean(node.attrs.refId),
      }
    }
  }
  return null
}

function noteInfoFromElement(note: HTMLElement) {
  const current = editor.value
  if (!current) return null
  const pos = current.view.posAtDOM(note, 0)
  return noteInfoAtPos(pos) || noteInfoAtPos(pos + 1)
}

function noteInfoAtPos(pos: number) {
  const current = editor.value
  if (!current) return null
  const safePos = Math.min(Math.max(pos, 0), current.state.doc.content.size)
  const $pos = current.state.doc.resolve(safePos)
  for (let depth = $pos.depth; depth > 0; depth--) {
    const node = $pos.node(depth)
    if (node.type.name === 'noteBox') {
      return {
        from: $pos.before(depth),
        to: $pos.after(depth),
        linked: Boolean(node.attrs.refId),
      }
    }
  }
  const nodeAtPos = current.state.doc.nodeAt(safePos)
  if (nodeAtPos?.type.name === 'noteBox') {
    return {
      from: safePos,
      to: safePos + nodeAtPos.nodeSize,
      linked: Boolean(nodeAtPos.attrs.refId),
    }
  }
  return null
}

function blockInfoFromElement(element: HTMLElement, kind: MovableBlockKind): MovableBlockRange | null {
  const current = editor.value
  if (!current) return null
  const pos = current.view.posAtDOM(element, 0)
  const domMatched = blockInfoByDom(element, kind)
  return domMatched || blockInfoAtPos(pos, kind) || blockInfoAtPos(pos - 1, kind) || blockInfoAtPos(pos + 1, kind)
}

function blockInfoAtPos(pos: number, kind: MovableBlockKind): MovableBlockRange | null {
  const current = editor.value
  if (!current) return null
  const typeName = kind === 'table' ? 'table' : 'image'
  const safePos = Math.min(Math.max(pos, 0), current.state.doc.content.size)
  const nodeAtPos = current.state.doc.nodeAt(safePos)
  if (nodeAtPos?.type.name === typeName) {
    return { from: safePos, to: safePos + nodeAtPos.nodeSize, kind }
  }

  const $pos = current.state.doc.resolve(safePos)
  for (let depth = $pos.depth; depth > 0; depth--) {
    const node = $pos.node(depth)
    if (node.type.name === typeName) {
      return { from: $pos.before(depth), to: $pos.after(depth), kind }
    }
  }

  const nodeAfter = $pos.nodeAfter
  if (nodeAfter?.type.name === typeName) {
    return { from: safePos, to: safePos + nodeAfter.nodeSize, kind }
  }
  const nodeBefore = $pos.nodeBefore
  if (nodeBefore?.type.name === typeName) {
    return { from: safePos - nodeBefore.nodeSize, to: safePos, kind }
  }
  return null
}

function blockInfoByDom(element: HTMLElement, kind: MovableBlockKind): MovableBlockRange | null {
  const current = editor.value
  if (!current) return null
  const typeName = kind === 'table' ? 'table' : 'image'
  let found: MovableBlockRange | null = null
  current.state.doc.descendants((node, pos) => {
    if (found || node.type.name !== typeName) return false
    const dom = current.view.nodeDOM(pos)
    if (dom === element || (dom instanceof Element && dom.contains(element))) {
      found = { from: pos, to: pos + node.nodeSize, kind }
      return false
    }
    return true
  })
  return found
}

function isInsideLinkedNote() {
  return Boolean(activeNoteInfo()?.linked)
}

function openImageModal() {
  showImageModal.value = true
  loadModalData()
}

function openNoticeMenu(mode: NoteMode) {
  showNoteMenu.value = false
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
    attrs: { refId: notice.id, title: noticeTitle(notice) },
    content: [{ type: 'text', text: noticeContent(notice) }],
  }).run()
  showNoticeModal.value = false
  showNoteMenu.value = false
  noticeSearch.value = ''
}

function toggleNoteMenu() {
  showNoteMenu.value = !showNoteMenu.value
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

function noticeById(id: number) {
  return notices.value.find((notice) => notice.id === id)
}

function noticeTitle(notice: NoticeTemplateResponse) {
  return props.language === 'EN'
    ? notice.visibleTitleEn || notice.titleEn || notice.visibleTitleEs || notice.titleEs || 'Nota'
    : notice.visibleTitleEs || notice.titleEs || 'Nota'
}

function noticeContent(notice: NoticeTemplateResponse) {
  return props.language === 'EN'
    ? notice.contentEn || notice.contentEs || ''
    : notice.contentEs || ''
}

function refreshLinkedNotesFromLibrary() {
  if (!editor.value || editorDirty.value || !visibleBlocks().some((block) => block.type === 'nota-ref')) return
  syncingFromProps.value = true
  editor.value.commands.setContent(docFromBlocks(visibleBlocks()))
  queueMicrotask(() => {
    syncingFromProps.value = false
    scheduleHeadingNumbers()
  })
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
  if (block.type === 'nota-ref') {
    const refId = Number(block.content || data.noticeTemplateId || savedJson?.attrs?.refId || 0)
    const notice = noticeById(refId)
    return [{
      type: 'noteBox',
      attrs: {
        refId,
        title: notice ? noticeTitle(notice) : String(data.title || savedJson?.attrs?.title || 'Nota'),
      },
      content: textContent(notice ? noticeContent(notice) : String(data.text || block.content)),
    }]
  }
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
    return [tableNode(rows, data.width)]
  }
  if (block.type === 'imagen') {
    return [{ type: 'image', attrs: { src: block.content, alt: String(data.assetId || ''), width: data.width || (savedJson?.attrs as Record<string, unknown> | undefined)?.width || null } }]
  }
  if (block.type === 'nota') {
    return [{
      type: 'noteBox',
      attrs: {
        refId: null,
        title: String(data.title || 'Nota'),
      },
      content: textContent(String(data.text || block.content)),
    }]
  }
  return [{ type: 'paragraph', content: textContent(block.content) }]
}

function tableNode(rows: string[][], width?: unknown): JSONContent {
  const normalized = rows.length ? rows : [['Cabecera 1', 'Cabecera 2'], ['', '']]
  return {
    type: 'table',
    attrs: { width: width || null },
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
    return [editorBlock('tabla', rows.map((row) => row.join('|')).join('\n'), { type: 'table', json: node, rows: rows.slice(1), columns: rows[0] || [], width: node.attrs?.width || null })]
  }
  if (node.type === 'image') {
    return [editorBlock('imagen', String(node.attrs?.src || ''), { type: 'image', json: node, assetId: imageAssetId(node), caption: '', width: node.attrs?.width || null })]
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

function cssSize(value: unknown) {
  if (typeof value === 'number') return `${value}px`
  const text = String(value || '').trim()
  if (!text) return ''
  return /^\d+(\.\d+)?$/.test(text) ? `${text}px` : text
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
  <article class="rich-section" :class="{ selected }" @click="selectSection">
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

    <div
      v-if="!section.collapsed"
      ref="sectionContentRef"
      class="section-content"
      @mousedown="handleSectionMouseDown"
      @mousemove="handleSectionMouseMove"
      @click="handleSectionClick"
      @mouseleave="handleSectionMouseLeave"
    >
      <div class="toolbar" @mousedown.prevent.stop @click.stop>
        <div class="ribbon-group compact-group">
          <div class="ribbon-row">
            <button class="tool-btn icon-only" title="Deshacer" aria-label="Deshacer" @click="undo"><Undo2 :size="16" /></button>
            <button class="tool-btn icon-only" title="Rehacer" aria-label="Rehacer" @click="redo"><Redo2 :size="16" /></button>
          </div>
          <div class="ribbon-row">
            <button class="tool-btn icon-only" title="Copiar" aria-label="Copiar" @click="copySelection"><Copy :size="16" /></button>
            <button class="tool-btn icon-only" title="Cortar" aria-label="Cortar" @click="cutSelection"><Scissors :size="16" /></button>
            <button class="tool-btn icon-only" title="Pegar" aria-label="Pegar" @click="pasteClipboard"><ClipboardPaste :size="16" /></button>
          </div>
          <span class="group-label">Portapapeles</span>
        </div>

        <div class="ribbon-group">
          <div class="ribbon-row">
            <button class="tool-btn" :class="{ active: currentHeadingLevel > 0 }" title="Título" @click="toggleDefaultHeading"><Heading :size="24" /><span>Título</span></button>
            <button class="tool-btn" title="Bajar nivel de título" :disabled="!currentHeadingLevel || currentHeadingLevel >= 3" @click="increaseHeadingLevel"><PanelTopClose :size="20" /><span>Nivel +</span></button>
            <button class="tool-btn" title="Subir nivel de título" :disabled="!currentHeadingLevel" @click="decreaseHeadingLevel"><PanelTopClose class="flip" :size="20" /><span>Nivel -</span></button>
            <button class="tool-btn" :class="{ active: currentHeadingLevel === 0 }" title="Párrafo" @click="setParagraph"><span class="paragraph-icon">¶</span><span>Párrafo</span></button>
          </div>
          <span class="group-label">{{ headingGroupLabel }}</span>
        </div>

        <div class="ribbon-group">
          <div class="ribbon-row">
            <button class="tool-btn" :class="{ active: editor?.isActive('bulletList') }" title="Lista" @click="editor?.chain().focus().toggleBulletList().run()"><List :size="22" /><span>Lista</span></button>
            <button class="tool-btn" :class="{ active: editor?.isActive('orderedList') }" title="Lista ordenada" @click="editor?.chain().focus().toggleOrderedList().run()"><ListOrdered :size="22" /><span>Lista ord.</span></button>
            <button class="tool-btn" title="Sangrar lista" @click="editor?.chain().focus().sinkListItem('listItem').run()"><PanelTopClose :size="20" /><span>Nivel +</span></button>
            <button class="tool-btn" title="Reducir sangría" @click="editor?.chain().focus().liftListItem('listItem').run()"><PanelTopClose class="flip" :size="20" /><span>Nivel -</span></button>
          </div>
          <span class="group-label">Listas</span>
        </div>

        <div class="ribbon-group">
          <div class="ribbon-row">
            <div class="table-picker-menu">
              <button class="tool-btn" title="Tabla" @click="toggleTablePicker"><TableIcon :size="22" /><span>Tabla</span></button>
              <div v-if="showTablePicker" class="table-picker-popover" @mouseleave="hoverTableSize(0, 0)">
                <strong>{{ tablePickerLabel() }}</strong>
                <div class="table-picker-grid">
                  <button
                    v-for="cell in tablePickerMaxRows * tablePickerMaxCols"
                    :key="cell"
                    class="table-picker-cell"
                    :class="{
                      active: Math.ceil(cell / tablePickerMaxCols) <= tablePickerRows && ((cell - 1) % tablePickerMaxCols) + 1 <= tablePickerCols,
                    }"
                    @mouseenter="hoverTableSize(Math.ceil(cell / tablePickerMaxCols), ((cell - 1) % tablePickerMaxCols) + 1)"
                    @click="insertTable(Math.ceil(cell / tablePickerMaxCols), ((cell - 1) % tablePickerMaxCols) + 1)"
                  />
                </div>
                <button class="custom-table" @click="openCustomTableModal"><TableIcon :size="13" /> Insertar tabla...</button>
              </div>
            </div>
            <button class="tool-btn" title="Imagen" @click="openImageModal"><FileImage :size="22" /><span>Imagen</span></button>
            <div class="toolbar-menu">
              <button class="tool-btn" title="Nota" @click="toggleNoteMenu"><NotebookPen :size="22" /><span>Nota</span></button>
              <div v-if="showNoteMenu" class="submenu">
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
        @mousedown.stop
        @click.stop="handleSectionClick"
      />
      <div
        v-if="blockActionsPosition.visible"
        class="block-actions-float"
        :style="{ top: `${blockActionsPosition.top}px`, left: `${blockActionsPosition.left}px` }"
      >
        <button
          class="block-action-drag"
          :title="`Arrastrar ${blockActionsPosition.kind === 'table' ? 'tabla' : 'imagen'}`"
          :aria-label="`Arrastrar ${blockActionsPosition.kind === 'table' ? 'tabla' : 'imagen'}`"
          @mousedown.stop.prevent="startActiveBlockDrag"
          @click.stop
        >
          <GripVertical :size="16" />
        </button>
        <button
          class="block-action-copy"
          :title="`Copiar ${blockActionsPosition.kind === 'table' ? 'tabla' : 'imagen'}`"
          :aria-label="`Copiar ${blockActionsPosition.kind === 'table' ? 'tabla' : 'imagen'}`"
          @mousedown.prevent
          @click.stop="copyActiveBlock"
        >
          <Copy :size="16" />
        </button>
        <button
          class="block-action-cut"
          :title="`Cortar ${blockActionsPosition.kind === 'table' ? 'tabla' : 'imagen'}`"
          :aria-label="`Cortar ${blockActionsPosition.kind === 'table' ? 'tabla' : 'imagen'}`"
          @mousedown.prevent
          @click.stop="cutActiveBlock"
        >
          <Scissors :size="16" />
        </button>
        <button
          class="block-action-delete"
          :title="`Eliminar ${blockActionsPosition.kind === 'table' ? 'tabla' : 'imagen'}`"
          :aria-label="`Eliminar ${blockActionsPosition.kind === 'table' ? 'tabla' : 'imagen'}`"
          @mousedown.prevent
          @click.stop="deleteActiveBlock"
        >
          <Trash2 :size="16" />
        </button>
      </div>
      <div
        v-if="blockResizeOverlay.visible"
        class="block-resize-overlay"
        :class="blockResizeOverlay.kind"
        :style="{
          top: `${blockResizeOverlay.top}px`,
          left: `${blockResizeOverlay.left}px`,
          width: `${blockResizeOverlay.width}px`,
          height: `${blockResizeOverlay.height}px`,
        }"
      >
        <button class="resize-handle nw" title="Redimensionar" @mousedown="startBlockResize($event, 'nw')" />
        <button class="resize-handle ne" title="Redimensionar" @mousedown="startBlockResize($event, 'ne')" />
        <button class="resize-handle sw" title="Redimensionar" @mousedown="startBlockResize($event, 'sw')" />
        <button class="resize-handle se" title="Redimensionar" @mousedown="startBlockResize($event, 'se')" />
        <button class="resize-handle e" title="Redimensionar" @mousedown="startBlockResize($event, 'e')" />
        <button class="resize-handle w" title="Redimensionar" @mousedown="startBlockResize($event, 'w')" />
      </div>
      <div
        v-if="noteActionsPosition.visible"
        class="note-actions-float"
        :style="{ top: `${noteActionsPosition.top}px`, left: `${noteActionsPosition.left}px` }"
      >
        <button
          class="note-action-drag"
          title="Arrastrar nota"
          aria-label="Arrastrar nota"
          @mousedown.stop.prevent="startActiveNoteDrag"
          @click.stop
        >
          <GripVertical :size="16" />
        </button>
        <button
          v-if="noteActionsPosition.linked"
          class="note-action-unlink"
          title="Desvincular nota"
          aria-label="Desvincular nota"
          @mousedown.prevent
          @click.stop="unlinkActiveNote"
        >
          <Link2Off :size="16" />
        </button>
        <button
          class="note-action-copy"
          title="Copiar nota"
          aria-label="Copiar nota"
          @mousedown.prevent
          @click.stop="copyActiveNote"
        >
          <Copy :size="16" />
        </button>
        <button
          class="note-action-cut"
          title="Cortar nota"
          aria-label="Cortar nota"
          @mousedown.prevent
          @click.stop="cutActiveNote"
        >
          <Scissors :size="16" />
        </button>
        <button
          class="note-action-delete"
          title="Eliminar nota"
          aria-label="Eliminar nota"
          @mousedown.prevent
          @click.stop="deleteActiveNote"
        >
          <Trash2 :size="16" />
        </button>
      </div>
      <div
        v-if="noteDragPreview.visible"
        class="note-drag-preview"
        :class="{ linked: noteDragPreview.linked }"
        :style="{ top: `${noteDragPreview.top}px`, left: `${noteDragPreview.left}px` }"
      >
        <strong>{{ noteDragPreview.title }}</strong>
        <p>{{ noteDragPreview.text }}</p>
      </div>
      <div
        v-if="blockDragPreview.visible"
        class="block-drag-preview"
        :class="blockDragPreview.kind"
        :style="{ top: `${blockDragPreview.top}px`, left: `${blockDragPreview.left}px` }"
      >
        <strong>{{ blockDragPreview.title }}</strong>
        <p>{{ blockDragPreview.text }}</p>
      </div>
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

    <div v-if="showCustomTableModal" class="modal-backdrop" @click.self="showCustomTableModal = false">
      <form class="table-dialog" @submit.prevent="confirmCustomTable">
        <header>
          <h3>Insertar tabla</h3>
          <button type="button" class="modal-close" title="Cancelar" @click="showCustomTableModal = false">×</button>
        </header>
        <section>
          <strong>Tamaño de la tabla</strong>
          <label>
            Número de columnas:
            <input v-model.number="customTableCols" class="field" type="number" min="1" max="30" />
          </label>
          <label>
            Número de filas:
            <input v-model.number="customTableRows" class="field" type="number" min="1" max="100" />
          </label>
          <label class="remember-size">
            <input v-model="rememberTableSize" type="checkbox" />
            Recordar dimensiones para tablas nuevas
          </label>
        </section>
        <footer>
          <button type="submit" class="btn btn-primary">Aceptar</button>
          <button type="button" class="btn btn-outline" @click="showCustomTableModal = false">Cancelar</button>
        </footer>
      </form>
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
.rich-section { background: #fff; border: 1px solid var(--border); border-radius: 0; margin-bottom: 14px; overflow: visible; }
.rich-section.selected { border-color: var(--dikoin-blue); box-shadow: 0 0 0 2px rgba(14, 127, 187, .1); }
.section-bar { position: sticky; top: 0; z-index: 55; height: 34px; display: flex; align-items: center; gap: 10px; padding: 0 10px; background: var(--dikoin-blue); color: #fff; }
.drag-handle { border: 0; background: transparent; color: #fff; padding: 3px; display: inline-flex; align-items: center; cursor: grab; }
.drag-handle:active { cursor: grabbing; }
.drag-dots { opacity: .95; }
.section-number { font-weight: 800; }
.section-title-input { flex: 1; min-width: 0; border: 0; outline: 0; background: transparent; color: #fff; font-weight: 800; }
.section-title-input::placeholder { color: rgba(255,255,255,.78); }
.bar-icon { border: 0; background: transparent; color: #fff; padding: 4px; display: inline-flex; align-items: center; }
.bar-icon .collapsed { transform: rotate(180deg); }
.section-content { min-height: 332px; position: relative; overflow: visible; }
.toolbar { position: sticky; top: 34px; z-index: 54; display: flex; align-items: stretch; min-height: 86px; padding: 8px 10px 6px; border-bottom: 1px solid #dce7f0; background: linear-gradient(#ffffff, #f5f9fd); overflow: visible; box-shadow: 0 7px 12px rgba(15, 23, 42, .05); }
.ribbon-group { position: relative; display: grid; grid-template-rows: 1fr auto; align-items: stretch; gap: 4px; padding: 0 12px; border-right: 1px solid #d7e3ed; }
.ribbon-group:first-child { padding-left: 2px; }
.ribbon-group:last-child { border-right: 0; }
.ribbon-row { display: flex; align-items: center; justify-content: center; gap: 2px; }
.compact-group { min-width: 112px; }
.compact-group .ribbon-row { justify-content: flex-start; }
.group-label { align-self: end; text-align: center; color: #6b7280; font-size: 10px; line-height: 1; }
.tool-btn { min-width: 58px; min-height: 50px; border: 1px solid transparent; border-radius: 2px; background: transparent; color: #1f2937; padding: 5px 7px; display: grid; justify-items: center; align-content: center; gap: 4px; font-size: 10px; line-height: 1.05; }
.tool-btn.mini { min-width: 48px; min-height: 24px; grid-auto-flow: column; grid-auto-columns: max-content; align-content: center; align-items: center; gap: 4px; padding: 3px 5px; font-size: 10px; }
.tool-btn.icon-only { min-width: 30px; min-height: 24px; padding: 3px; display: inline-grid; place-items: center; }
.tool-btn:hover, .tool-btn.active { border-color: #b7d7ea; color: var(--dikoin-blue); background: #eaf4fb; }
.tool-btn:disabled { cursor: default; color: #9aa7b4; background: transparent; border-color: transparent; opacity: .58; }
.tool-btn.danger { color: var(--dikoin-red); }
.paragraph-icon { font-size: 22px; line-height: 1; font-weight: 800; }
.toolbar .flip { transform: rotate(180deg); }
.toolbar-menu, .table-picker-menu { position: relative; z-index: 45; }
.toolbar-menu > button { height: 100%; }
.submenu { display: grid; position: absolute; z-index: 60; left: 0; top: calc(100% + 4px); min-width: 150px; background: #fff; border: 1px solid var(--border); box-shadow: 0 10px 20px rgba(0,0,0,.12); }
.submenu button { width: 100%; display: block; min-width: 0; text-align: left; justify-items: start; border: 0; background: #fff; padding: 9px 10px; font-size: 12px; }
.table-picker-popover { position: absolute; z-index: 60; left: 0; top: calc(100% + 4px); width: 202px; padding: 7px; background: #fbfbfb; border: 1px solid #b7b7b7; box-shadow: 2px 4px 10px rgba(0,0,0,.16); display: grid; gap: 7px; }
.table-picker-popover strong { display: block; padding: 2px 3px; color: #374151; font-size: 12px; font-weight: 700; }
.table-picker-grid { display: grid; grid-template-columns: repeat(7, 24px); gap: 3px; }
.table-picker-cell { width: 24px; height: 24px; min-width: 0; min-height: 0; border: 1px solid #7f7f7f; background: #fff; padding: 0; }
.table-picker-cell.active { border-color: #2f80c0; background: #d8ecfb; }
.custom-table { border: 0; border-top: 1px solid #d8d8d8; background: #fbfbfb; color: #1f2937; padding: 7px 4px 3px; display: flex; align-items: center; gap: 8px; text-align: left; font-size: 12px; }
.custom-table:hover { color: var(--dikoin-blue); background: #edf6fd; }
.editor-shell { min-height: 260px; padding: 22px 18px 40px; }
.editor-shell :deep(.rich-editor-surface) { min-height: 250px; outline: 0; }
.editor-shell :deep(.is-editor-empty:first-child::before) { content: attr(data-placeholder); float: left; color: #9aa7b4; pointer-events: none; height: 0; }
.editor-shell :deep(.ProseMirror-focused .is-editor-empty:first-child::before) { display: none; }
.editor-shell :deep(.ProseMirror p:empty::after) { content: "\00a0"; }
.editor-shell :deep(p) { margin: 0 0 10px; line-height: 1.55; }
.editor-shell :deep(h1), .editor-shell :deep(h2), .editor-shell :deep(h3) { color: var(--dikoin-blue-dark); margin: 16px 0 8px; line-height: 1.25; }
.editor-shell :deep(h1) { font-size: 20px; }
.editor-shell :deep(h2) { font-size: 17px; }
.editor-shell :deep(h3) { font-size: 15px; }
.editor-shell :deep(h1::before),
.editor-shell :deep(h2::before),
.editor-shell :deep(h3::before) { content: attr(data-heading-number); display: inline-block; margin-right: 8px; color: var(--dikoin-blue); font-weight: 800; }
.editor-shell :deep(ul), .editor-shell :deep(ol) { margin: 0 0 10px 22px; padding-left: 18px; }
.editor-shell :deep(table) { width: 100%; max-width: 100%; table-layout: fixed; border-collapse: collapse; margin: 12px 0; }
.editor-shell :deep(table:hover), .editor-shell :deep(table.movable-block-active) { outline: 2px solid rgba(14, 127, 187, .24); outline-offset: 3px; }
.editor-shell :deep(th) { background: var(--dikoin-blue); color: #fff; }
.editor-shell :deep(td), .editor-shell :deep(th) { border: 1px solid #b8cce3; padding: 7px; min-width: 0; vertical-align: top; overflow-wrap: anywhere; word-break: break-word; }
.editor-shell :deep(img) { max-width: 100%; height: auto; display: block; margin: 12px 0; }
.editor-shell :deep(img:hover), .editor-shell :deep(img.movable-block-active) { outline: 2px solid rgba(14, 127, 187, .28); outline-offset: 4px; }
.editor-shell :deep(.movable-block-drag-source) { opacity: .38; }
.editor-shell :deep(.note-box) { position: relative; border: 1px solid #fed7aa; border-left: 8px solid var(--dikoin-orange); border-radius: var(--radius); background: #fff7ed; color: #78350f; padding: 12px 14px 12px 16px; margin: 16px 0; box-shadow: 0 8px 18px rgba(146, 64, 14, .08); white-space: pre-wrap; transition: box-shadow .12s ease, border-color .12s ease; }
.editor-shell :deep(.note-box:hover), .editor-shell :deep(.note-box.ProseMirror-selectednode), .editor-shell :deep(.note-box-active) { border-color: #fb923c; box-shadow: 0 0 0 2px rgba(251, 146, 60, .14), 0 8px 18px rgba(146, 64, 14, .08); }
.editor-shell :deep(.note-box-drag-source) { opacity: .38; }
.editor-shell :deep(.note-drag-handle) { display: none; }
.editor-shell :deep(.note-content) { display: block; min-width: 0; }
.editor-shell :deep(.linked-note-box) { cursor: default; padding-right: 42px; }
.editor-shell :deep(.linked-note-box::after) {
  content: "";
  position: absolute;
  top: 10px;
  right: 12px;
  width: 16px;
  height: 16px;
  background: #9a3412;
  opacity: .58;
  pointer-events: none;
  -webkit-mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='24' height='24' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M9 17H7A5 5 0 0 1 7 7h2'/%3E%3Cpath d='M15 7h2a5 5 0 1 1 0 10h-2'/%3E%3Cline x1='8' x2='16' y1='12' y2='12'/%3E%3C/svg%3E") center / contain no-repeat;
  mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='24' height='24' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M9 17H7A5 5 0 0 1 7 7h2'/%3E%3Cpath d='M15 7h2a5 5 0 1 1 0 10h-2'/%3E%3Cline x1='8' x2='16' y1='12' y2='12'/%3E%3C/svg%3E") center / contain no-repeat;
}
.editor-shell :deep(.note-box::before) { content: attr(title); display: block; font-weight: 800; margin-bottom: 4px; }
.table-delete-float { position: absolute; z-index: 8; width: 28px; height: 28px; border: 1px solid #fecaca; background: #fff; color: var(--dikoin-red); padding: 0; border-radius: var(--radius); display: inline-grid; place-items: center; box-shadow: 0 8px 20px rgba(0,0,0,.12); }
.table-delete-float:hover { background: var(--dikoin-red-light); border-color: var(--dikoin-red); }
.block-actions-float { position: absolute; z-index: 8; display: inline-flex; gap: 4px; }
.block-actions-float button { width: 28px; height: 28px; border: 1px solid #bfdbfe; background: #fff; color: var(--dikoin-blue); padding: 0; border-radius: var(--radius); display: inline-grid; place-items: center; box-shadow: 0 8px 20px rgba(0,0,0,.12); }
.block-actions-float .block-action-drag { cursor: grab; }
.block-actions-float .block-action-drag:active { cursor: grabbing; }
.block-actions-float .block-action-delete { color: var(--dikoin-red); border-color: #fecaca; }
.block-actions-float .block-action-delete:hover { background: var(--dikoin-red-light); border-color: var(--dikoin-red); }
.block-actions-float .block-action-drag:hover,
.block-actions-float .block-action-copy:hover,
.block-actions-float .block-action-cut:hover { background: var(--dikoin-blue-lighter); border-color: var(--dikoin-blue); }
.block-resize-overlay { position: absolute; z-index: 7; pointer-events: none; border: 1px dashed rgba(14, 127, 187, .6); border-radius: 2px; }
.block-resize-overlay .resize-handle { position: absolute; width: 10px; height: 10px; min-width: 0; min-height: 0; padding: 0; border: 1px solid var(--dikoin-blue); background: #fff; border-radius: 50%; pointer-events: auto; box-shadow: 0 2px 8px rgba(15, 23, 42, .16); }
.block-resize-overlay .resize-handle.nw { top: -5px; left: -5px; cursor: nwse-resize; }
.block-resize-overlay .resize-handle.ne { top: -5px; right: -5px; cursor: nesw-resize; }
.block-resize-overlay .resize-handle.sw { bottom: -5px; left: -5px; cursor: nesw-resize; }
.block-resize-overlay .resize-handle.se { right: -5px; bottom: -5px; cursor: nwse-resize; }
.block-resize-overlay .resize-handle.e { top: calc(50% - 5px); right: -5px; cursor: ew-resize; }
.block-resize-overlay .resize-handle.w { top: calc(50% - 5px); left: -5px; cursor: ew-resize; }
.note-actions-float { position: absolute; z-index: 8; display: inline-flex; gap: 4px; }
.note-actions-float button { width: 28px; height: 28px; border: 1px solid #fed7aa; background: #fff; color: #6b7280; padding: 0; border-radius: var(--radius); display: inline-grid; place-items: center; box-shadow: 0 8px 20px rgba(0,0,0,.12); }
.note-actions-float .note-action-drag { color: #c2410c; border-color: #fdba74; cursor: grab; }
.note-actions-float .note-action-drag:active { cursor: grabbing; }
.note-actions-float .note-action-unlink,
.note-actions-float .note-action-copy,
.note-actions-float .note-action-cut { color: var(--dikoin-blue); border-color: #bfdbfe; }
.note-actions-float .note-action-delete { color: var(--dikoin-red); border-color: #fecaca; }
.note-actions-float .note-action-delete:hover { background: var(--dikoin-red-light); border-color: var(--dikoin-red); }
.note-actions-float .note-action-drag:hover { background: #fff7ed; border-color: var(--dikoin-orange); }
.note-actions-float .note-action-unlink:hover,
.note-actions-float .note-action-copy:hover,
.note-actions-float .note-action-cut:hover { background: var(--dikoin-blue-lighter); border-color: var(--dikoin-blue); }
.note-drag-preview { position: fixed; z-index: 1000; width: min(320px, 42vw); max-height: 150px; overflow: hidden; pointer-events: none; opacity: .82; transform: rotate(-1deg); border: 1px solid #fed7aa; border-left: 8px solid var(--dikoin-orange); border-radius: var(--radius); background: #fff7ed; color: #78350f; padding: 10px 12px; box-shadow: 0 18px 38px rgba(15, 23, 42, .22); }
.note-drag-preview strong { display: block; margin-bottom: 4px; font-size: 12px; }
.note-drag-preview p { margin: 0; font-size: 12px; line-height: 1.35; max-height: 68px; overflow: hidden; }
.note-drag-preview.linked::after { content: ""; position: absolute; top: 9px; right: 10px; width: 14px; height: 14px; background: #9a3412; opacity: .55; -webkit-mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='24' height='24' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M9 17H7A5 5 0 0 1 7 7h2'/%3E%3Cpath d='M15 7h2a5 5 0 1 1 0 10h-2'/%3E%3Cline x1='8' x2='16' y1='12' y2='12'/%3E%3C/svg%3E") center / contain no-repeat; mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='24' height='24' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M9 17H7A5 5 0 0 1 7 7h2'/%3E%3Cpath d='M15 7h2a5 5 0 1 1 0 10h-2'/%3E%3Cline x1='8' x2='16' y1='12' y2='12'/%3E%3C/svg%3E") center / contain no-repeat; }
.block-drag-preview { position: fixed; z-index: 1000; width: min(300px, 40vw); pointer-events: none; opacity: .82; transform: rotate(-1deg); border: 1px solid #bfdbfe; border-left: 8px solid var(--dikoin-blue); border-radius: var(--radius); background: #f8fbfe; color: var(--dikoin-blue-dark); padding: 10px 12px; box-shadow: 0 18px 38px rgba(15, 23, 42, .22); }
.block-drag-preview strong { display: block; margin-bottom: 4px; font-size: 12px; }
.block-drag-preview p { margin: 0; color: var(--muted-foreground); font-size: 12px; line-height: 1.35; max-height: 54px; overflow: hidden; }
.block-drag-preview.image { border-left-color: #64748b; }
.modal-backdrop { position: fixed; inset: 0; z-index: 100; background: rgba(15, 23, 42, .35); display: grid; place-items: center; padding: 24px; }
.modal-card { width: min(760px, 100%); max-height: min(720px, 90vh); overflow: auto; background: #fff; border: 1px solid var(--border); border-radius: var(--radius); box-shadow: 0 22px 50px rgba(0,0,0,.22); padding: 16px; display: grid; gap: 14px; }
.modal-card header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.modal-card h3 { margin: 0; font-size: 16px; }
.modal-card header button { border: 0; background: transparent; font-size: 24px; color: var(--muted-foreground); }
.table-dialog { width: min(380px, 100%); background: #fff; border: 1px solid var(--border); border-radius: var(--radius); box-shadow: 0 22px 50px rgba(0,0,0,.22); display: grid; gap: 0; }
.table-dialog header { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 16px; border-bottom: 1px solid var(--border); }
.table-dialog h3 { margin: 0; color: var(--dikoin-blue-dark); font-size: 16px; }
.table-dialog .modal-close { border: 0; background: transparent; color: var(--muted-foreground); font-size: 24px; line-height: 1; }
.table-dialog section { padding: 16px; display: grid; gap: 12px; }
.table-dialog section strong { color: var(--foreground); font-size: 13px; }
.table-dialog label { display: grid; grid-template-columns: 1fr 92px; align-items: center; gap: 12px; color: var(--muted-foreground); font-size: 12px; font-weight: 700; }
.table-dialog input[type="number"] { width: 92px; padding: 7px 8px; }
.table-dialog .remember-size { grid-template-columns: auto 1fr; justify-content: start; gap: 8px; margin-top: 4px; color: var(--foreground); font-weight: 500; }
.table-dialog .remember-size input { width: 14px; height: 14px; }
.table-dialog footer { display: flex; justify-content: flex-end; gap: 10px; padding: 12px 16px 16px; }
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
