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
  AlignCenter,
  AlignLeft,
  AlignRight,
  ChevronUp,
  ClipboardPaste,
  Copy,
  FileImage,
  Image as ImageIcon,
  Eye,
  EyeOff,
  GripVertical,
  Heading,
  List,
  ListChecks,
  ListOrdered,
  Link2,
  Link2Off,
  Library,
  MoveRight,
  PanelTopClose,
  Redo2,
  Save,
  Scissors,
  Sigma,
  TableCellsMerge,
  TableCellsSplit,
  TableColumnsSplit,
  Table as TableIcon,
  TableRowsSplit,
  Trash2,
  Undo2,
  X,
  StickyNote,
  SquareDashedText,
  Group,
} from '@lucide/vue'
import { getAssets, uploadAsset } from '@/api/assets.api'
import { toBackendUrl } from '@/api/http'
import { getNotices } from '@/api/notices.api'
import { getReusableBlocks } from '@/api/reusable-blocks.api'
import { createReusableFragment, getReusableFragments } from '@/api/reusable-fragments.api'
import AppModal from '@/components/shared/AppModal.vue'
import type { EditorBlock, EditorBlockType, EditorSection } from '@/types/editor'
import { backendBlockTypeToEditor, blockContentToJson, editorBlockTypeToBackend, parseContent, randomId } from '@/types/editor'
import type { AssetResponse, LanguageCode, ManualBlockResponse, NoticeTemplateResponse, ReusableBlockResponse, ReusableFragmentResponse } from '@/types/api'

type ReusableFragmentLike = (ReusableBlockResponse | ReusableFragmentResponse) & {
  titleEs?: string
  titleEn?: string
}

type NoteMode = 'new' | 'library'
type EditorRegistryRecord = { editor: Editor; root: () => HTMLElement | null; markDirty: () => void }
type MovableBlockKind = 'note' | 'table' | 'image' | 'formula'
type MovableBlockRange = { from: number; to: number; kind: MovableBlockKind }
type LinkableBoxKind = 'note' | 'fragment'
type LinkableBoxRange = { from: number; to: number; linked: boolean; kind: LinkableBoxKind }
type ResizeHandle = 'nw' | 'ne' | 'sw' | 'se' | 'n' | 's' | 'e' | 'w'
type SectionTarget = { id: string; number?: string; title: string }
type BlockSelectionSync = {
  sectionId: string
  active: boolean
  indexes: number[]
  includeParallel: boolean
  sourceLanguage: LanguageCode
}

let sharedNoteClipboard: JSONContent | null = null
let sharedNoteDrag: { editor: Editor; from: number; to: number; json: JSONContent; markSourceDirty: () => void } | null = null
let sharedBlockClipboard: JSONContent | null = null
let sharedBlockDrag: { editor: Editor; from: number; to: number; json: JSONContent; kind: MovableBlockKind; markSourceDirty: () => void } | null = null
const editorRegistry: EditorRegistryRecord[] = []

const props = defineProps<{
  section: EditorSection
  language: LanguageCode
  selected: boolean
  activeToolbar?: boolean
  manualId?: number
  refreshKey?: number
  sectionTargets?: SectionTarget[]
  selectionOwnerKey?: string
  selectionSync?: BlockSelectionSync | null
}>()

const emit = defineEmits<{
  update: [section: EditorSection]
  select: [id: string]
  activate: [id: string, language: LanguageCode]
  delete: []
  duplicate: []
  saveReusable: []
  addSubsection: []
  moveBlocks: [payload: { sourceSectionId: string; targetSectionId: string; blocks: EditorBlock[] }]
  insertReusableSection: [payload: { sectionItem: ReusableBlockResponse; cloneSpanishToEnglish: boolean; afterSectionId: string }]
  selectionChange: [key: string, active: boolean, indexes: number[]]
  selectionModeChange: [payload: BlockSelectionSync]
  saveSection: []
}>()

const assets = ref<AssetResponse[]>([])
const notices = ref<NoticeTemplateResponse[]>([])
const reusableBlocks = ref<ReusableBlockResponse[]>([])
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
const noteActionsPosition = ref<{ visible: boolean; top: number; left: number; linked: boolean; kind: LinkableBoxKind }>({ visible: false, top: 0, left: 0, linked: false, kind: 'note' })
const noteActionRange = ref<LinkableBoxRange | null>(null)
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
const tableWithHeader = ref(true)
const tablePickerMaxRows = 6
const tablePickerMaxCols = 7
let headingNumberFrame = 0
const editorDirty = ref(false)
const blockSelectionMode = ref(false)
const selectedBlockIds = ref<string[]>([])
const hoveredBlockId = ref('')
const showFragmentModal = ref(false)
const showFragmentInsertModal = ref(false)
const showSectionInsertModal = ref(false)
const showSectionLanguageWarningModal = ref(false)
const showUnlinkSectionModal = ref(false)
const showEquationModal = ref(false)
const showMoveModal = ref(false)
const showSelectionMismatchModal = ref(false)
const showPasteTextModal = ref(false)
const manualPasteText = ref('')
const editorMessage = ref('')
const selectionMismatchCounts = ref({ current: 0, parallel: 0 })
const fragmentName = ref('')
const fragmentDescription = ref('')
const fragmentSaving = ref(false)
const fragmentMessage = ref('')
const fragmentInsertPosition = ref<'END' | 'AFTER_SELECTION'>('END')
const fragmentInsertSearch = ref('')
const sectionInsertSearch = ref('')
const pendingReusableSectionInsert = ref<ReusableBlockResponse | null>(null)
const fragmentPreviewBlocks = computed(() => selectedFragmentBlocks())
const moveTargetSectionId = ref('')
const equationTextarea = ref<HTMLTextAreaElement | null>(null)
const editingEquationPos = ref<number | null>(null)
const equationDraft = ref({
  latex: '',
  caption: '',
  displayMode: 'block',
  numbered: false,
  equationNumber: '',
  align: 'center',
})
const blockSelectionKey = new PluginKey('manualBlockSelection')

const equationTemplates = [
  { label: 'Fracción', latex: '\\frac{a}{b}' },
  { label: 'Superíndice', latex: 'x^{2}' },
  { label: 'Subíndice', latex: 'x_{i}' },
  { label: 'Raíz', latex: '\\sqrt{x}' },
  { label: 'Sumatoria', latex: '\\sum_{i=1}^{n} x_i' },
  { label: 'Integral', latex: '\\int_{a}^{b} f(x)\\,dx' },
  { label: 'Paréntesis', latex: '\\left( x \\right)' },
  { label: 'Matriz 2x2', latex: '\\begin{bmatrix} a & b \\\\ c & d \\end{bmatrix}' },
]

const greekTemplates = [
  ['α', '\\alpha'],
  ['β', '\\beta'],
  ['γ', '\\gamma'],
  ['δ', '\\delta'],
  ['θ', '\\theta'],
  ['λ', '\\lambda'],
  ['μ', '\\mu'],
  ['π', '\\pi'],
  ['ρ', '\\rho'],
  ['ω', '\\omega'],
] as const

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

const ReusableSectionBox = Node.create({
  name: 'reusableSectionBox',
  group: 'block',
  atom: true,
  selectable: true,
  draggable: true,
  addAttributes() {
    return {
      refId: { default: null },
      title: { default: 'Sección reutilizable' },
      code: { default: '' },
    }
  },
  parseHTML() {
    return [{ tag: 'div[data-reusable-section]' }]
  },
  renderHTML({ HTMLAttributes }) {
    return ['div', mergeAttributes(HTMLAttributes, {
      'data-reusable-section': 'true',
      class: 'reusable-section-box',
      draggable: 'true',
      contenteditable: 'false',
    }), ['strong', {}, `${String(HTMLAttributes.code || '')} · ${String(HTMLAttributes.title || 'Sección reutilizable')}`]]
  },
})

const ReusableFragmentBox = Node.create({
  name: 'reusableFragmentBox',
  group: 'block',
  content: 'block+',
  defining: true,
  isolating: true,
  selectable: true,
  draggable: true,
  addAttributes() {
    return {
      refId: { default: null },
      title: { default: 'Fragmento reutilizable' },
      code: { default: '' },
    }
  },
  parseHTML() {
    return [{ tag: 'div[data-reusable-fragment]' }]
  },
  renderHTML({ HTMLAttributes }) {
    return ['div', mergeAttributes(HTMLAttributes, {
      'data-reusable-fragment': 'true',
      ...(HTMLAttributes.refId ? { 'data-reusable-fragment-ref': String(HTMLAttributes.refId) } : {}),
      class: 'reusable-fragment-box linked-reusable-fragment-box',
      draggable: 'true',
      contenteditable: 'false',
    }), ['div', { class: 'reusable-fragment-content' }, 0]]
  },
})

const FormulaBlock = Node.create({
  name: 'formulaBlock',
  group: 'block',
  atom: true,
  selectable: true,
  draggable: true,
  addAttributes() {
    return {
      latex: { default: '' },
      mathml: { default: null },
      omml: { default: null },
      displayMode: { default: 'block' },
      numbered: { default: false },
      equationNumber: { default: null },
      caption: { default: '' },
      align: { default: 'center' },
      width: { default: null },
      offsetX: { default: 0 },
      offsetY: { default: 0 },
    }
  },
  parseHTML() {
    return [{ tag: 'div[data-formula-block]' }]
  },
  renderHTML({ HTMLAttributes }) {
    const latex = String(HTMLAttributes.latex || '')
    const caption = String(HTMLAttributes.caption || '')
    return ['div', mergeAttributes(HTMLAttributes, {
      'data-formula-block': 'true',
      'data-align': String(HTMLAttributes.align || 'center'),
      class: 'formula-block',
      contenteditable: 'false',
      style: HTMLAttributes.width ? `width: ${cssSize(HTMLAttributes.width)}; max-width: 100%;` : undefined,
    }), [
      'span',
      { class: 'formula-latex' },
      latex,
    ], ...(caption ? [['small', { class: 'formula-caption' }, caption]] : [])]
  },
})

const BlockIdentity = Extension.create({
  name: 'blockIdentity',
  addGlobalAttributes() {
    return [{
      types: ['paragraph', 'heading', 'bulletList', 'orderedList', 'table', 'image', 'noteBox', 'formulaBlock', 'reusableSectionBox', 'reusableFragmentBox'],
      attributes: {
        blockId: {
          default: null,
          parseHTML: (element) => element.getAttribute('data-block-id'),
          renderHTML: (attributes) => attributes.blockId ? { 'data-block-id': attributes.blockId } : {},
        },
        backendId: {
          default: null,
          parseHTML: (element) => {
            const value = element.getAttribute('data-backend-id')
            return value ? Number(value) : null
          },
          renderHTML: (attributes) => attributes.backendId ? { 'data-backend-id': String(attributes.backendId) } : {},
        },
      },
    }]
  },
})

const BlockSelection = Extension.create({
  name: 'blockSelection',
  addProseMirrorPlugins() {
    return [new Plugin({
      key: blockSelectionKey,
      appendTransaction: (_transactions, _oldState, newState) => {
        let transaction = newState.tr
        let changed = false
        newState.doc.forEach((node, offset) => {
          if (node.attrs.blockId) return
          transaction = transaction.setNodeMarkup(offset, undefined, {
            ...node.attrs,
            blockId: randomId('block'),
            backendId: node.attrs.backendId || null,
          })
          changed = true
        })
        return changed ? transaction : null
      },
      props: {
        decorations: (state) => {
          const decorations: Decoration[] = []
          if (!blockSelectionMode.value) {
            return DecorationSet.empty
          }
          state.doc.forEach((node, offset) => {
            const blockId = String(node.attrs.blockId || '')
            if (!blockId) return
            if (node.type.name === 'paragraph' && node.content.size === 0) return
            const selected = selectedBlockIds.value.includes(blockId)
            const selectable = canToggleBlockSelection(blockId, state)
            const wrapper = document.createElement('span')
            const hovered = hoveredBlockId.value === blockId
            wrapper.className = `block-checkbox-widget${selected ? ' selected' : ''}${hovered ? ' hovered' : ''}`
            wrapper.contentEditable = 'false'
            const input = document.createElement('input')
            input.type = 'checkbox'
            input.checked = selected
            input.disabled = !selectable
            input.setAttribute('aria-label', selected ? 'Deseleccionar bloque' : 'Seleccionar bloque')
            if (!selectable) {
              input.title = selected
                ? 'Para mantener el rango contiguo, desmarca primero uno de los extremos'
                : 'Solo puedes añadir bloques contiguos a la selección'
            }
            input.addEventListener('mousedown', (event) => event.preventDefault())
            input.addEventListener('click', (event) => {
              event.preventDefault()
              event.stopPropagation()
              toggleBlockSelection(blockId)
            })
            wrapper.appendChild(input)
            decorations.push(Decoration.widget(offset, wrapper, { side: -1, key: `check-${blockId}` }))
            decorations.push(Decoration.node(offset, offset + node.nodeSize, {
              class: [
                'manual-selectable-block',
                selected ? 'selected-manual-block' : '',
                hovered ? 'selection-hovered' : '',
              ].filter(Boolean).join(' '),
            }))
          })
          return DecorationSet.create(state.doc, decorations)
        },
      },
    })]
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
          const style = [
            attributes.width ? `width: ${cssSize(attributes.width)}` : '',
            attributes.height ? `height: ${cssSize(attributes.height)}` : '',
            'max-width: 100%',
          ].filter(Boolean).join('; ')
          return style ? { style } : {}
        },
      },
      height: {
        default: null,
        parseHTML: (element) => element.getAttribute('height') || element.style.height || null,
      },
      align: {
        default: 'inline',
        parseHTML: (element) => element.getAttribute('data-align') || 'inline',
        renderHTML: (attributes) => ({ 'data-align': attributes.align || 'inline' }),
      },
      assetId: {
        default: null,
        parseHTML: (element) => {
          const value = element.getAttribute('data-asset-id')
          return value ? Number(value) : null
        },
        renderHTML: (attributes) => attributes.assetId ? { 'data-asset-id': String(attributes.assetId) } : {},
      },
      offsetX: { default: 0 },
      offsetY: { default: 0 },
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
      hasHeader: {
        default: true,
        parseHTML: (element) => element.getAttribute('data-has-header') !== 'false',
        renderHTML: (attributes) => ({ 'data-has-header': String(attributes.hasHeader !== false) }),
      },
      align: {
        default: 'left',
        parseHTML: (element) => element.getAttribute('data-align') || 'left',
        renderHTML: (attributes) => ({ 'data-align': attributes.align || 'left' }),
      },
    }
  },
})

const isLinkedSection = computed(() => Boolean(props.section.linkedReusableSectionId))

const editor = useEditor({
  editable: !isLinkedSection.value,
  extensions: [
    StarterKit.configure({
      heading: { levels: [1, 2, 3] },
    }),
    Placeholder.configure({ placeholder: 'Escriba el texto...' }),
    ResizableImage.configure({ inline: false, allowBase64: true }),
    ResizableTable.configure({
      resizable: true,
      handleWidth: 8,
      cellMinWidth: 42,
      lastColumnResizable: true,
    }),
    TableRow,
    TableHeader,
    TableCell,
    NoteBox,
    ReusableSectionBox,
    ReusableFragmentBox,
    FormulaBlock,
    BlockIdentity,
    BlockSelection,
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
        if (isInsideLinkedNote()) {
          event.preventDefault()
          return true
        }
        const imageFile = clipboardImageFile(event)
        if (!imageFile) return false
        event.preventDefault()
        void insertClipboardImage(imageFile)
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
  onFocus: () => activateEditor(),
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
    activateEditor()
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
    return [notice.code, notice.title, notice.visibleTitleEs, notice.contentEs, notice.visibleTitleEn, notice.contentEn]
      .filter(Boolean)
      .some((value) => String(value).toLowerCase().includes(search))
  })
})

const reusableSections = computed(() => reusableBlocks.value.filter((item) => item.reusableType === 'SINGLE_BLOCK'))
const reusableFragments = computed(() => reusableBlocks.value.filter((item) => item.reusableType === 'FRAGMENT'))

const filteredReusableFragments = computed(() => {
  const search = fragmentInsertSearch.value.trim().toLowerCase()
  if (!search) return reusableFragments.value
  return reusableFragments.value.filter((fragment) => reusableItemMatches(fragment, search))
})

const filteredReusableSections = computed(() => {
  const search = sectionInsertSearch.value.trim().toLowerCase()
  if (!search) return reusableSections.value
  return reusableSections.value.filter((sectionItem) => reusableItemMatches(sectionItem, search))
})

onMounted(async () => {
  registerEditorInstance()
  await loadModalData()
  refreshLinkedNotesFromLibrary()
  scheduleHeadingNumbers()
})

onBeforeUnmount(() => {
  if (headingNumberFrame) {
    cancelAnimationFrame(headingNumberFrame)
    headingNumberFrame = 0
  }
  unregisterEditorInstance()
  flushEditorSync()
  editor.value?.destroy()
})

watch(isLinkedSection, (linked) => {
  editor.value?.setEditable(!linked)
  if (linked) {
    hideBlockActions()
    hideNoteActions()
    clearBlockSelection(false)
  }
})

watch(
  () => [props.section.id, props.language, props.refreshKey],
  () => {
    if (!editor.value || editor.value.isDestroyed) return
    selectedBlockIds.value = []
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

function reusableItemMatches(item: ReusableBlockResponse, search: string) {
  return [item.code, item.title, item.titleEs, item.titleEn, item.description]
    .filter(Boolean)
    .some((value) => String(value).toLowerCase().includes(search))
}

function updateBlockSelectionHover(event?: MouseEvent) {
  if (!blockSelectionMode.value) {
    if (hoveredBlockId.value) {
      hoveredBlockId.value = ''
      refreshBlockSelections()
    }
    return
  }
  const root = editorDom()
  if (!root) return
  let activeBlockId = ''

  if (event) {
    const blocks = Array.from(root.querySelectorAll<HTMLElement>('.manual-selectable-block'))
    for (const block of blocks) {
      const host = block?.closest('.tableWrapper') as HTMLElement | null || block
      const rect = host.getBoundingClientRect()
      const insideExpandedRow = event.clientX >= rect.left - 48
        && event.clientX <= rect.right + 9
        && event.clientY >= rect.top - 5
        && event.clientY <= rect.bottom + 5
      if (insideExpandedRow) {
        activeBlockId = String(block.dataset.blockId || '')
        break
      }
    }
  }

  if (hoveredBlockId.value === activeBlockId) return
  hoveredBlockId.value = activeBlockId
  refreshBlockSelections()
}

const selectedCount = computed(() => selectedBlockIds.value.length)
const hasSelection = computed(() => selectedCount.value > 0)
const availableMoveTargets = computed(() => (props.sectionTargets || []).filter((section) => section.id !== props.section.id))
const selectionKey = computed(() => `${props.section.id}:${props.language}`)

watch(
  () => props.selectionOwnerKey,
  (ownerKey) => {
    if (ownerKey && ownerKey !== selectionKey.value && selectedBlockIds.value.length) {
      selectedBlockIds.value = []
      refreshBlockSelections()
    }
  },
)

watch(
  () => props.selectionSync,
  (sync) => {
    const belongsHere = Boolean(sync?.active && sync.sectionId === props.section.id)
    if (!belongsHere) {
      if (blockSelectionMode.value || selectedBlockIds.value.length) {
        blockSelectionMode.value = false
        selectedBlockIds.value = []
        hoveredBlockId.value = ''
        refreshBlockSelections()
      }
      return
    }
    blockSelectionMode.value = true
    const ids = documentBlockIds()
    selectedBlockIds.value = (sync?.indexes || [])
      .map((index) => ids[index])
      .filter((id): id is string => Boolean(id))
    refreshBlockSelections()
  },
  { deep: true, immediate: true },
)

function refreshBlockSelections() {
  const current = editor.value
  if (!current) return
  current.view.dispatch(current.state.tr.setMeta(blockSelectionKey, Date.now()))
}

function documentBlockIds(state = editor.value?.state) {
  if (!state) return []
  const ids: string[] = []
  state.doc.forEach((node) => {
    if (node.type.name === 'paragraph' && node.content.size === 0) return
    const blockId = String(node.attrs.blockId || '')
    if (blockId) ids.push(blockId)
  })
  return ids
}

function canToggleBlockSelection(blockId: string, state = editor.value?.state) {
  return blockSelectionMode.value && documentBlockIds(state).includes(blockId)
}

function toggleBlockSelection(blockId: string) {
  if (!canToggleBlockSelection(blockId)) return
  const ids = documentBlockIds()
  selectedBlockIds.value = selectedBlockIds.value.includes(blockId)
    ? selectedBlockIds.value.filter((id) => id !== blockId)
    : [...selectedBlockIds.value, blockId].sort((left, right) => ids.indexOf(left) - ids.indexOf(right))
  const indexes = selectedBlockIds.value
    .map((id) => ids.indexOf(id))
    .filter((index) => index >= 0)
  emit('selectionChange', selectionKey.value, selectedBlockIds.value.length > 0, indexes)
  refreshBlockSelections()
  activateEditor()
}

function clearBlockSelection(disableMode = false) {
  selectedBlockIds.value = []
  hoveredBlockId.value = ''
  if (disableMode) blockSelectionMode.value = false
  emit('selectionChange', selectionKey.value, false, [])
  if (disableMode) {
    emit('selectionModeChange', {
      sectionId: props.section.id,
      active: false,
      indexes: [],
      includeParallel: false,
      sourceLanguage: props.language,
    })
  }
  refreshBlockSelections()
}

function toggleBlockSelectionMode() {
  if (blockSelectionMode.value) {
    clearBlockSelection(true)
    return
  }
  const currentCount = blocksFromDoc(editor.value?.getJSON() || { type: 'doc', content: [] }).length
  const parallelLanguage: LanguageCode = props.language === 'ES' ? 'EN' : 'ES'
  const parallelCount = props.section.blocks.filter((block) => block.languageCode === parallelLanguage).length
  if (currentCount !== parallelCount) {
    selectionMismatchCounts.value = { current: currentCount, parallel: parallelCount }
    showSelectionMismatchModal.value = true
    return
  }
  enableBlockSelection(true)
}

function enableBlockSelection(includeParallel: boolean) {
  showSelectionMismatchModal.value = false
  blockSelectionMode.value = true
  emit('selectionModeChange', {
    sectionId: props.section.id,
    active: true,
    indexes: [],
    includeParallel,
    sourceLanguage: props.language,
  })
  refreshBlockSelections()
  activateEditor()
}

function selectedBlocksFromDocument() {
  if (!editor.value) return []
  return blocksFromDoc(editor.value.getJSON()).filter((block) => selectedBlockIds.value.includes(block.id))
}

function selectedFragmentBlocks() {
  const current = selectedBlocksFromDocument()
  if (!props.selectionSync?.includeParallel) return current
  const parallelLanguage: LanguageCode = props.language === 'ES' ? 'EN' : 'ES'
  const parallel = props.section.blocks.filter((block) => block.languageCode === parallelLanguage)
  return [
    ...current,
    ...props.selectionSync.indexes
      .map((index) => parallel[index])
      .filter((block): block is EditorBlock => Boolean(block)),
  ]
}

function duplicateSelectedBlocks() {
  if (!editor.value || !hasSelection.value) return
  const doc = structuredClone(editor.value.getJSON())
  doc.content = (doc.content || []).flatMap((node) => {
    const blockId = String(node.attrs?.blockId || '')
    if (!selectedBlockIds.value.includes(blockId)) return [node]
    const copy = structuredClone(node)
    copy.attrs = { ...(copy.attrs || {}), blockId: randomId('block'), backendId: null }
    return [node, copy]
  })
  editor.value.commands.setContent(doc)
  markEditorDirty()
  clearBlockSelection()
}

function deleteSelectedBlocks() {
  if (!editor.value || !hasSelection.value) return
  const doc = structuredClone(editor.value.getJSON())
  doc.content = (doc.content || []).filter((node) => !selectedBlockIds.value.includes(String(node.attrs?.blockId || '')))
  if (!doc.content.length) {
    doc.content = [{ type: 'paragraph', attrs: { blockId: randomId('block'), backendId: null } }]
  }
  editor.value.commands.setContent(doc)
  markEditorDirty()
  clearBlockSelection()
}

function openFragmentModal() {
  if (!hasSelection.value) return
  fragmentName.value = ''
  fragmentDescription.value = ''
  fragmentMessage.value = ''
  showFragmentModal.value = true
}

async function saveSelectedFragment() {
  const blocks = selectedFragmentBlocks()
  if (!blocks.length || !fragmentName.value.trim()) return
  fragmentSaving.value = true
  fragmentMessage.value = ''
  try {
    const fragment = await createReusableFragment({
      name: fragmentName.value.trim(),
      description: fragmentDescription.value.trim() || undefined,
      sourceSectionId: props.section.backendId,
      blockIds: blocks.every((block) => block.backendId) ? blocks.map((block) => block.backendId as number) : undefined,
      blocks: blocks.map((block, index) => ({
        blockType: editorBlockTypeToBackend(block.type),
        languageCode: block.languageCode,
        contentJson: blockContentToJson(block),
        plainText: block.content,
        assetId: typeof block.data?.assetId === 'number' ? block.data.assetId : undefined,
        sortOrder: index + 1,
      })),
      isReusable: true,
    })
    fragmentMessage.value = 'Fragmento guardado'
    reusableBlocks.value = await loadReusableLibrary()
    replaceSelectedBlocksWithFragment(fragment)
    clearBlockSelection()
    setTimeout(() => {
      showFragmentModal.value = false
      fragmentMessage.value = ''
    }, 650)
  } catch {
    fragmentMessage.value = 'No se pudo guardar el fragmento'
  } finally {
    fragmentSaving.value = false
  }
}

function openMoveModal() {
  if (!hasSelection.value || !availableMoveTargets.value.length) return
  moveTargetSectionId.value = availableMoveTargets.value[0]?.id || ''
  showMoveModal.value = true
}

function confirmMoveSelection() {
  if (!moveTargetSectionId.value) return
  const blocks = selectedBlocksFromDocument().map((block) => ({
    ...structuredClone(block),
    id: randomId('block'),
    backendId: undefined,
  }))
  if (!blocks.length) return
  emit('moveBlocks', {
    sourceSectionId: props.section.id,
    targetSectionId: moveTargetSectionId.value,
    blocks,
  })
  deleteSelectedBlocks()
  syncEditorToSection()
  showMoveModal.value = false
}

function patch(value: Partial<EditorSection>) {
  emit('update', { ...props.section, ...value })
}

function confirmUnlinkSection() {
  patch({ linkedReusableSectionId: undefined })
  showUnlinkSectionModal.value = false
  editor.value?.setEditable(true)
}

function patchTitle(value: string) {
  patch(props.language === 'EN' ? { titleEn: value } : { titleEs: value })
}

function syncEditorToSection() {
  if (!editor.value || syncingFromProps.value) return
  if (isLinkedSection.value) {
    hasPendingTableSync.value = false
    editorDirty.value = false
    return
  }
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

function activateEditor() {
  emit('select', props.section.id)
  emit('activate', props.section.id, props.language)
}

function selectSection() {
  activateEditor()
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
  const root = editorDom()
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

function editorDom() {
  const current = editor.value
  if (!current || current.isDestroyed) return null
  try {
    return current.view?.dom || null
  } catch {
    return null
  }
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
    manualPasteText.value = ''
    showPasteTextModal.value = true
    return
  }
  if (!text) return
  editor.value.chain().focus().insertContent(textToParagraphs(text)).run()
}

function confirmManualPaste() {
  if (!editor.value || !manualPasteText.value.trim()) return
  editor.value.chain().focus().insertContent(textToParagraphs(manualPasteText.value)).run()
  manualPasteText.value = ''
  showPasteTextModal.value = false
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
  editor.value?.chain().focus().insertTable({
    rows: Math.max(rows, 1),
    cols: Math.max(cols, 1),
    withHeaderRow: tableWithHeader.value,
  }).run()
  if (editor.value?.isActive('table')) {
    editor.value.commands.updateAttributes('table', { hasHeader: tableWithHeader.value })
  }
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

function runTableEdit(action: 'row-before' | 'row-after' | 'column-before' | 'column-after' | 'delete-row' | 'delete-column' | 'merge' | 'split') {
  const current = editor.value
  if (!current || !current.isActive('table')) return
  const chain = current.chain().focus()
  if (action === 'row-before') chain.addRowBefore().run()
  if (action === 'row-after') chain.addRowAfter().run()
  if (action === 'column-before') chain.addColumnBefore().run()
  if (action === 'column-after') chain.addColumnAfter().run()
  if (action === 'delete-row') chain.deleteRow().run()
  if (action === 'delete-column') chain.deleteColumn().run()
  if (action === 'merge') chain.mergeCells().run()
  if (action === 'split') chain.splitCell().run()
  hasPendingTableSync.value = true
  markEditorDirty()
  refreshToolbarState()
  requestAnimationFrame(updateBlockActionsPosition)
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
  activateEditor()
  const target = event.target instanceof Element ? event.target : null
  const linkableBox = target?.closest('[data-note-box],[data-reusable-fragment]') as HTMLElement | null
  if (!linkableBox) {
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
  activateNoteElement(linkableBox)
  showNoteActionsForElement(linkableBox, noteInfoFromElement(linkableBox))
}

function handleSectionMouseDown(event: MouseEvent) {
  activateEditor()
  const target = event.target instanceof Element ? event.target : null
  const image = target?.tagName === 'IMG' ? target as HTMLElement : target?.closest('img') as HTMLElement | null
  if (image && image.closest('.rich-editor-surface')) {
    const info = blockInfoFromElement(image, 'image')
    const imageJson = activeBlockJson(info)
    if (!info || !imageJson || !editor.value) return

    event.preventDefault()
    activateBlockElement(image)
    showBlockActionsForElement(image, 'image', info)
    editor.value.commands.setNodeSelection(info.from)
    sharedBlockDrag = {
      editor: editor.value,
      from: info.from,
      to: info.to,
      json: structuredClone(imageJson),
      kind: 'image',
      markSourceDirty: markEditorDirty,
    }
    startBlockDragFeedback(event, imageJson, 'image')
    document.body.classList.add('note-dragging')
    document.addEventListener('mousemove', updateBlockDragFeedback)
    document.addEventListener('mouseup', finishBlockMouseDrag, { once: true })
    return
  }

  const handle = target?.closest('[data-note-drag-handle]')
  const note = target?.closest('[data-note-box],[data-reusable-fragment]') as HTMLElement | null
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
  updateBlockSelectionHover(event)
  const target = event.target instanceof Element ? event.target : null
  if (!target || target.closest('.note-actions-float') || target.closest('.block-actions-float')) return
  const note = target.closest('[data-note-box],[data-reusable-fragment]')
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
  updateBlockSelectionHover()
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
    kind: info.kind,
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
  const formula = target.closest('[data-formula-block]') as HTMLElement | null
  if (formula) return { element: formula, kind: 'formula' as MovableBlockKind }
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
  if (!node || !isLinkableBoxNode(node.type.name)) return

  if (node.type.name === 'reusableFragmentBox') {
    const json = node.toJSON() as JSONContent
    const nodes = cloneFragmentContentNodes(json.content || [])
    if (!nodes.length) {
      editorMessage.value = 'No se pudo desvincular el fragmento.'
      return
    }
    current.chain().focus().insertContentAt({ from: activeNote.from, to: activeNote.to }, nodes).run()
    hideNoteActions()
    editorDirty.value = true
    return
  }

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
  const startY = event.clientY
  const startWidth = blockResizeOverlay.value.width
  const startHeight = blockResizeOverlay.value.height
  let latestWidth = startWidth
  let latestHeight = startHeight
  const onMove = (moveEvent: MouseEvent) => {
    const deltaX = moveEvent.clientX - startX
    const deltaY = moveEvent.clientY - startY
    const signedDeltaX = handle.includes('w') ? -deltaX : deltaX
    const signedDeltaY = handle.includes('n') ? -deltaY : deltaY
    const maxWidth = Math.max(180, current.view.dom.getBoundingClientRect().width)
    const nextWidth = handle.includes('e') || handle.includes('w')
      ? Math.min(maxWidth, Math.max(range.kind === 'table' ? 180 : 40, startWidth + signedDeltaX))
      : startWidth
    const nextHeight = range.kind === 'image' && (handle.includes('n') || handle.includes('s'))
      ? Math.max(32, startHeight + signedDeltaY)
      : startHeight
    latestWidth = nextWidth
    latestHeight = nextHeight
    if (blockElement) {
      blockElement.style.setProperty('width', `${Math.round(nextWidth)}px`, 'important')
      blockElement.setAttribute('data-width', String(Math.round(nextWidth)))
      if (range.kind === 'image') {
        blockElement.style.maxWidth = '100%'
        blockElement.style.setProperty('height', `${Math.round(nextHeight)}px`, 'important')
        ;(blockElement as HTMLImageElement).style.objectFit = 'contain'
      } else if (range.kind === 'table') {
        blockElement.style.tableLayout = 'fixed'
      }
    }
    blockResizeOverlay.value.width = nextWidth
    blockResizeOverlay.value.height = nextHeight
  }
  const onUp = () => {
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    updateBlockSize(range, latestWidth, latestHeight)
    markEditorDirty()
    requestAnimationFrame(updateBlockActionsPosition)
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp, { once: true })
}

function updateBlockSize(range: MovableBlockRange, width: number, height: number) {
  const current = editor.value
  if (!current) return
  const node = current.state.doc.nodeAt(range.from)
  if (!node || !['table', 'image', 'formulaBlock'].includes(node.type.name)) return
  const nextAttrs = {
    ...node.attrs,
    width: Math.round(width),
    ...(range.kind === 'image' ? { height: Math.round(height) } : {}),
  }
  current.view.dispatch(current.state.tr.setNodeMarkup(range.from, undefined, nextAttrs).scrollIntoView())
  markEditorDirty()
}

function alignActiveBlock(align: 'left' | 'center' | 'right') {
  const range = blockActionRange.value
  const current = editor.value
  if (!range || !current) return
  const node = current.state.doc.nodeAt(range.from)
  if (!node || !['image', 'table', 'formulaBlock'].includes(node.type.name)) return
  current.view.dispatch(current.state.tr.setNodeMarkup(range.from, undefined, {
    ...node.attrs,
    align,
    offsetX: 0,
    offsetY: 0,
  }))
  markEditorDirty()
  requestAnimationFrame(updateBlockActionsPosition)
}

function activeNoteJson(range = noteActionRange.value || activeNoteInfo()) {
  const current = editor.value
  if (!current || !range) return null
  const node = current.state.doc.nodeAt(range.from)
  return node && isLinkableBoxNode(node.type.name) ? node.toJSON() as JSONContent : null
}

function activeBlockJson(range = blockActionRange.value) {
  const current = editor.value
  if (!current || !range) return null
  const node = current.state.doc.nodeAt(range.from)
  const expectedType = range.kind === 'table' ? 'table' : range.kind === 'formula' ? 'formulaBlock' : 'image'
  return node?.type.name === expectedType ? node.toJSON() as JSONContent : null
}

function noteJsonText(noteJson: JSONContent) {
  if (noteJson.type === 'reusableFragmentBox') {
    return plainText(noteJson) || String(noteJson.attrs?.title || noteJson.attrs?.code || 'Fragmento reutilizable')
  }
  return plainText(noteJson) || String(noteJson.attrs?.title || 'Nota')
}

function blockPreviewText(blockJson: JSONContent, kind: MovableBlockKind) {
  if (kind === 'image') return String(blockJson.attrs?.alt || blockJson.attrs?.src || 'Imagen')
  if (kind === 'formula') return String(blockJson.attrs?.latex || 'Ecuación')
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
    if (isLinkableBoxNode(node.type.name)) {
      return {
        from: $from.before(depth),
        to: $from.after(depth),
        linked: Boolean(node.attrs.refId),
        kind: linkableBoxKind(node.type.name),
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
    if (isLinkableBoxNode(node.type.name)) {
      return {
        from: $pos.before(depth),
        to: $pos.after(depth),
        linked: Boolean(node.attrs.refId),
        kind: linkableBoxKind(node.type.name),
      }
    }
  }
  const nodeAtPos = current.state.doc.nodeAt(safePos)
  if (nodeAtPos && isLinkableBoxNode(nodeAtPos.type.name)) {
    return {
      from: safePos,
      to: safePos + nodeAtPos.nodeSize,
      linked: Boolean(nodeAtPos.attrs.refId),
      kind: linkableBoxKind(nodeAtPos.type.name),
    }
  }
  return null
}

function isLinkableBoxNode(typeName: string) {
  return typeName === 'noteBox' || typeName === 'reusableFragmentBox'
}

function linkableBoxKind(typeName: string): LinkableBoxKind {
  return typeName === 'reusableFragmentBox' ? 'fragment' : 'note'
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
  const typeName = kind === 'table' ? 'table' : kind === 'formula' ? 'formulaBlock' : 'image'
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
  const typeName = kind === 'table' ? 'table' : kind === 'formula' ? 'formulaBlock' : 'image'
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

function insertEquation() {
  const current = editor.value
  const selectedNode = (current?.state.selection as unknown as { node?: { type: { name: string }; attrs: Record<string, unknown> } })?.node
  if (selectedNode?.type.name === 'formulaBlock' && current) {
    editingEquationPos.value = current.state.selection.from
    equationDraft.value = {
      latex: String(selectedNode.attrs.latex || ''),
      caption: String(selectedNode.attrs.caption || ''),
      displayMode: String(selectedNode.attrs.displayMode || 'block'),
      numbered: Boolean(selectedNode.attrs.numbered),
      equationNumber: String(selectedNode.attrs.equationNumber || ''),
      align: String(selectedNode.attrs.align || 'center'),
    }
  } else {
    editingEquationPos.value = null
    equationDraft.value = {
      latex: '',
      caption: '',
      displayMode: 'block',
      numbered: false,
      equationNumber: '',
      align: 'center',
    }
  }
  showEquationModal.value = true
  queueMicrotask(() => equationTextarea.value?.focus())
}

function insertEquationSnippet(snippet: string) {
  const textarea = equationTextarea.value
  const value = equationDraft.value.latex
  if (!textarea) {
    equationDraft.value.latex = `${value}${value ? ' ' : ''}${snippet}`
    return
  }
  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  equationDraft.value.latex = `${value.slice(0, start)}${snippet}${value.slice(end)}`
  queueMicrotask(() => {
    textarea.focus()
    textarea.setSelectionRange(start + snippet.length, start + snippet.length)
  })
}

function confirmEquation() {
  const current = editor.value
  const latex = equationDraft.value.latex.trim()
  if (!current || !latex) return
  const attrs = {
    latex,
    mathml: null,
    omml: null,
    displayMode: equationDraft.value.displayMode,
    numbered: equationDraft.value.numbered,
    equationNumber: equationDraft.value.numbered ? equationDraft.value.equationNumber.trim() || null : null,
    caption: equationDraft.value.caption.trim(),
    align: equationDraft.value.align,
  }
  if (editingEquationPos.value !== null) {
    const node = current.state.doc.nodeAt(editingEquationPos.value)
    if (node?.type.name === 'formulaBlock') {
      current.view.dispatch(current.state.tr.setNodeMarkup(editingEquationPos.value, undefined, { ...node.attrs, ...attrs }))
    }
  } else {
    current.chain().focus().insertContent({
      type: 'formulaBlock',
      attrs: {
        ...attrs,
        blockId: randomId('block'),
        backendId: null,
      },
    }).run()
  }
  markEditorDirty()
  showEquationModal.value = false
}

function openFragmentInsertModal() {
  fragmentInsertPosition.value = hasSelection.value ? 'AFTER_SELECTION' : 'END'
  fragmentMessage.value = ''
  fragmentInsertSearch.value = ''
  showFragmentInsertModal.value = true
  showSectionInsertModal.value = false
  loadModalData()
}

function openSectionInsertModal() {
  fragmentInsertPosition.value = hasSelection.value ? 'AFTER_SELECTION' : 'END'
  fragmentMessage.value = ''
  sectionInsertSearch.value = ''
  showSectionInsertModal.value = true
  showFragmentInsertModal.value = false
  loadModalData()
}

function insertReusableFragment(fragment: ReusableBlockResponse) {
  insertReusableFragmentReference(fragment)
}

function insertReusableFragmentReference(fragment: ReusableFragmentLike) {
  const current = editor.value
  if (!current) return
  const fragmentNode = reusableFragmentNode(fragment)
  const doc: JSONContent = structuredClone(current.getJSON())
  const content: JSONContent[] = [...(doc.content || [])]
  if (fragmentInsertPosition.value === 'AFTER_SELECTION' && hasSelection.value) {
    const lastSelectedIndex = content.reduce(
      (last, node, index) => selectedBlockIds.value.includes(String(node.attrs?.blockId || '')) ? index : last,
      -1,
    )
    content.splice(lastSelectedIndex + 1, 0, fragmentNode)
  } else {
    content.push(fragmentNode)
  }
  doc.content = content
  current.commands.setContent(doc)
  markEditorDirty()
  clearBlockSelection()
  showFragmentInsertModal.value = false
  fragmentInsertSearch.value = ''
  fragmentMessage.value = ''
}

function replaceSelectedBlocksWithFragment(fragment: ReusableFragmentLike) {
  const current = editor.value
  if (!current || !hasSelection.value) return
  const fragmentNode = reusableFragmentNode(fragment)
  const doc: JSONContent = structuredClone(current.getJSON())
  const content = doc.content || []
  const firstSelectedIndex = content.findIndex((node) => selectedBlockIds.value.includes(String(node.attrs?.blockId || '')))
  if (firstSelectedIndex < 0) return
  doc.content = content.flatMap((node, index) => {
    const selected = selectedBlockIds.value.includes(String(node.attrs?.blockId || ''))
    if (!selected) return [node]
    return index === firstSelectedIndex ? [fragmentNode] : []
  })
  current.commands.setContent(doc)
  markEditorDirty()
}

function reusableFragmentNode(fragment: ReusableFragmentLike): JSONContent {
  const content = reusableFragmentContent(fragment)
  return {
    type: 'reusableFragmentBox',
    attrs: {
      blockId: randomId('block'),
      backendId: null,
      refId: fragment.id,
      code: fragment.code || '',
      title: fragment.titleEs || fragment.title || 'Fragmento reutilizable',
    },
    content: content.length ? content : [{
      type: 'paragraph',
      attrs: { blockId: randomId('block'), backendId: null },
      content: textContent('Fragmento no disponible.'),
    }],
  }
}

function reusableFragmentNodes(fragmentId: number): JSONContent[] {
  const fragment = reusableBlocks.value.find((item) => item.reusableType === 'FRAGMENT' && item.id === fragmentId)
  return reusableFragmentContent(fragment)
}

function reusableFragmentContent(fragment?: ReusableFragmentLike): JSONContent[] {
  if (!fragment) return []
  return reusableFragmentContentFromJson(fragment.contentJson)
}

function reusableFragmentContentFromJson(contentJson?: string): JSONContent[] {
  if (!contentJson) return []
  try {
    const parsed = JSON.parse(contentJson)
    const snapshots = Array.isArray(parsed.blocks) ? parsed.blocks : []
    return snapshots
      .filter((snapshot: Record<string, unknown>) => !snapshot.languageCode || snapshot.languageCode === props.language)
      .map((snapshot: Record<string, unknown>, index: number) => reusableSnapshotBlock(snapshot, index))
      .flatMap((block: EditorBlock) => nodeFromBlock(block))
  } catch {
    return []
  }
}

function cloneFragmentContentNodes(nodes: JSONContent[]): JSONContent[] {
  return nodes.map((node) => cloneFragmentContentNode(node, true))
}

function cloneFragmentContentNode(node: JSONContent, root = false): JSONContent {
  const cloned = structuredClone(node)
  cloned.attrs = {
    ...(cloned.attrs || {}),
    ...(root ? { blockId: randomId('block'), backendId: null } : {}),
  }
  if (Array.isArray(cloned.content)) {
    cloned.content = cloned.content.map((child) => cloneFragmentContentNode(child))
  }
  return cloned
}

function reusableSnapshotBlock(snapshot: Record<string, unknown>, index: number): EditorBlock {
  const contentJson = typeof snapshot.contentJson === 'string'
    ? snapshot.contentJson
    : JSON.stringify(snapshot.contentJson || {})
  const response: ManualBlockResponse = {
    id: 0,
    sortOrder: Number(snapshot.sortOrder || index + 1),
    blockType: snapshot.blockType as ManualBlockResponse['blockType'],
    languageCode: (snapshot.languageCode || props.language) as LanguageCode,
    contentJson,
    plainText: typeof snapshot.plainText === 'string' ? snapshot.plainText : undefined,
    assetId: typeof snapshot.assetId === 'number' ? snapshot.assetId : undefined,
  }
  let data: Record<string, unknown> = {}
  try {
    data = JSON.parse(contentJson)
  } catch {
    data = {}
  }
  return {
    id: randomId('block'),
    type: backendBlockTypeToEditor(response.blockType),
    content: parseContent(response),
    languageCode: response.languageCode,
    data: {
      ...data,
      assetId: response.assetId,
    },
  }
}

function blockKindLabel(kind: MovableBlockKind | null) {
  if (kind === 'table') return 'tabla'
  if (kind === 'formula') return 'ecuación'
  return 'imagen'
}

function insertReusableSection(sectionItem: ReusableBlockResponse) {
  if (reusableSectionNeedsEnglishFallback(sectionItem)) {
    pendingReusableSectionInsert.value = sectionItem
    showSectionLanguageWarningModal.value = true
    return
  }
  emitReusableSectionInsert(sectionItem, false)
}

function confirmReusableSectionWithSpanishClone() {
  const sectionItem = pendingReusableSectionInsert.value
  if (!sectionItem) return
  emitReusableSectionInsert(sectionItem, true)
  pendingReusableSectionInsert.value = null
  showSectionLanguageWarningModal.value = false
}

function cancelReusableSectionInsertWarning() {
  pendingReusableSectionInsert.value = null
  showSectionLanguageWarningModal.value = false
}

function emitReusableSectionInsert(sectionItem: ReusableBlockResponse, cloneSpanishToEnglish: boolean) {
  flushEditorSync()
  emit('insertReusableSection', {
    sectionItem,
    cloneSpanishToEnglish,
    afterSectionId: props.section.id,
  })
  showSectionInsertModal.value = false
  sectionInsertSearch.value = ''
}

function reusableSectionNeedsEnglishFallback(sectionItem: ReusableBlockResponse) {
  const counts = reusableSectionLanguageCounts(sectionItem)
  return counts.es > 0 && (counts.en === 0 || counts.en !== counts.es)
}

function reusableSectionLanguageCounts(sectionItem: ReusableBlockResponse) {
  const blocks = reusableSectionContentSnapshots(sectionItem)
  return {
    es: blocks.filter((block) => block.languageCode === 'ES').length,
    en: blocks.filter((block) => block.languageCode === 'EN').length,
  }
}

function reusableSectionContentSnapshots(sectionItem: ReusableBlockResponse): Record<string, unknown>[] {
  try {
    const parsed = JSON.parse(sectionItem.contentJson || '{}')
    return Array.isArray(parsed.blocks) ? parsed.blocks : []
  } catch {
    return []
  }
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
  editor.value?.chain().focus().insertContent({
    type: 'image',
    attrs: {
      src,
      alt: assetId ? `asset-${assetId}` : 'imagen',
      assetId: assetId || null,
      width: 240,
      align: 'inline',
      offsetX: 0,
      offsetY: 0,
    },
  }).run()
  showImageModal.value = false
}

function clipboardImageFile(event: ClipboardEvent) {
  const items = Array.from(event.clipboardData?.items || [])
  const imageItem = items.find((item) => item.kind === 'file' && item.type.startsWith('image/'))
  const source = imageItem?.getAsFile()
  if (!source) return null
  const extension = source.type.split('/')[1]?.replace('jpeg', 'jpg') || 'png'
  return new File([source], source.name || `imagen-portapapeles-${Date.now()}.${extension}`, { type: source.type })
}

async function insertClipboardImage(file: File) {
  uploadingImage.value = true
  try {
    const asset = await uploadAsset({ file, assetType: 'IMAGE', manualId: props.manualId })
    const src = asset.fileUrl ? toBackendUrl(asset.fileUrl) : toBackendUrl(`/api/v1/assets/${asset.id}/file`)
    assets.value = [asset, ...assets.value.filter((item) => item.id !== asset.id)]
    insertImage(src, asset.id)
    markEditorDirty()
  } catch {
    editorMessage.value = 'No se pudo guardar la imagen pegada desde el portapapeles.'
  } finally {
    uploadingImage.value = false
  }
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
    const [loadedAssets, loadedNotices, loadedReusableBlocks] = await Promise.all([
      getAssets(props.manualId ? { manualId: props.manualId } : undefined),
      getNotices('NOTE'),
      loadReusableLibrary(),
    ])
    assets.value = loadedAssets.filter((asset) => ['IMAGE', 'EXTRACTED_IMAGE', 'PRODUCT_IMAGE'].includes(asset.assetType))
    notices.value = loadedNotices
    reusableBlocks.value = loadedReusableBlocks
  } catch {
    assets.value = []
    notices.value = []
    reusableBlocks.value = []
  }
}

async function loadReusableLibrary() {
  const [sections, fragments] = await Promise.all([
    getReusableBlocks(false, 'SINGLE_BLOCK'),
    getReusableFragments(),
  ])
  return [
    ...sections,
    ...fragments.map((fragment) => ({
      ...fragment,
      reusableType: 'FRAGMENT' as const,
    })),
  ] as ReusableBlockResponse[]
}

function noticeById(id: number) {
  return notices.value.find((notice) => notice.id === id)
}

function noticeTitle(notice: NoticeTemplateResponse) {
  return props.language === 'EN'
    ? notice.visibleTitleEn || notice.visibleTitleEs || notice.title || 'Nota'
    : notice.visibleTitleEs || notice.title || 'Nota'
}

function noticeContent(notice: NoticeTemplateResponse) {
  return props.language === 'EN'
    ? notice.contentEn || notice.contentEs || ''
    : notice.contentEs || ''
}

function refreshLinkedNotesFromLibrary() {
  if (!editor.value || editorDirty.value || !visibleBlocks().some((block) => block.type === 'nota-ref' || block.type === 'fragmento-ref')) return
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
  const identity = { blockId: block.id, backendId: block.backendId || null }
  if (block.type === 'nota-ref') {
    const refId = Number(block.content || data.noticeTemplateId || savedJson?.attrs?.refId || 0)
    const notice = noticeById(refId)
    return [{
      type: 'noteBox',
      attrs: {
        ...identity,
        refId,
        title: notice ? noticeTitle(notice) : String(data.title || savedJson?.attrs?.title || 'Nota'),
      },
      content: textContent(notice ? noticeContent(notice) : String(data.text || block.content)),
    }]
  }
  if (block.type === 'bloque-ref') {
    const refId = Number(block.content || data.reusableBlockId || savedJson?.attrs?.refId || 0)
    const reusable = reusableBlocks.value.find((item) => item.id === refId)
    return [{
      type: 'reusableSectionBox',
      attrs: {
        ...identity,
        refId,
        code: reusable?.code || savedJson?.attrs?.code || '',
        title: reusable
          ? (props.language === 'EN' ? reusable.titleEn || reusable.title : reusable.titleEs || reusable.title)
          : String(savedJson?.attrs?.title || 'Sección reutilizable'),
      },
    }]
  }
  if (block.type === 'fragmento-ref') {
    const refId = Number(block.content || data.reusableFragmentId || savedJson?.attrs?.refId || 0)
    const fragment = reusableBlocks.value.find((item) => item.reusableType === 'FRAGMENT' && item.id === refId)
    return [reusableFragmentNode({
      id: refId,
      code: fragment?.code || String(savedJson?.attrs?.code || ''),
      title: fragment?.title || String(savedJson?.attrs?.title || 'Fragmento reutilizable'),
      titleEs: fragment?.titleEs,
      titleEn: fragment?.titleEn,
      contentJson: fragment?.contentJson || '',
      active: fragment?.active ?? true,
      reusableType: 'FRAGMENT',
    } as ReusableBlockResponse)]
  }
  if (savedJson?.type) {
    const clonedJson = JSON.parse(JSON.stringify(savedJson)) as JSONContent
    return [{ ...clonedJson, attrs: { ...(clonedJson.attrs || {}), ...identity } }]
  }
  if (block.type === 'titulo') {
    return [{ type: 'heading', attrs: { ...identity, level: Number(data.level || 1) }, content: textContent(block.content) }]
  }
  if (block.type === 'lista-ul' || block.type === 'lista-ol') {
    const listType = block.type === 'lista-ul' ? 'bulletList' : 'orderedList'
    return [{
      type: listType,
      attrs: identity,
      content: block.content.split('\n').filter(Boolean).map((item) => ({
        type: 'listItem',
        content: [{ type: 'paragraph', content: textContent(item) }],
      })),
    }]
  }
  if (block.type === 'tabla') {
    const rows = block.content.split('\n').filter(Boolean).map((line) => line.split('|'))
    const table = tableNode(rows, data.width, data.hasHeader !== false)
    table.attrs = { ...(table.attrs || {}), ...identity }
    table.attrs = {
      ...(table.attrs || {}),
      hasHeader: data.hasHeader !== false,
      align: data.align || 'left',
    }
    return [table]
  }
  if (block.type === 'imagen') {
    const attrs = savedJson?.attrs as Record<string, unknown> | undefined
    return [{
      type: 'image',
      attrs: {
        ...identity,
        src: block.content,
        alt: String(data.assetId || ''),
        width: data.width || attrs?.width || null,
        height: data.height || attrs?.height || null,
        align: data.align || attrs?.align || 'inline',
        offsetX: data.offsetX || attrs?.offsetX || 0,
        offsetY: data.offsetY || attrs?.offsetY || 0,
      },
    }]
  }
  if (block.type === 'nota') {
    return [{
      type: 'noteBox',
      attrs: {
        ...identity,
        refId: null,
        title: String(data.title || 'Nota'),
      },
      content: textContent(String(data.text || block.content)),
    }]
  }
  if (block.type === 'formula') {
    return [{
      type: 'formulaBlock',
      attrs: {
        ...identity,
        latex: block.content,
        mathml: data.mathml || null,
        omml: data.omml || null,
        displayMode: data.displayMode || 'block',
        numbered: Boolean(data.numbered),
        equationNumber: data.equationNumber || null,
        caption: data.caption || '',
        align: data.align || 'center',
        width: data.width || null,
        offsetX: data.offsetX || 0,
        offsetY: data.offsetY || 0,
      },
    }]
  }
  return [{ type: 'paragraph', attrs: identity, content: textContent(block.content) }]
}

function tableNode(rows: string[][], width?: unknown, hasHeader = true): JSONContent {
  const normalized = rows.length ? rows : [['Cabecera 1', 'Cabecera 2'], ['', '']]
  return {
    type: 'table',
    attrs: { width: width || null, hasHeader },
    content: normalized.map((row, rowIndex) => ({
      type: 'tableRow',
      content: row.map((cell) => ({
        type: hasHeader && rowIndex === 0 ? 'tableHeader' : 'tableCell',
        content: [{ type: 'paragraph', content: textContent(cell) }],
      })),
    })),
  }
}

function blocksFromDoc(doc: JSONContent): EditorBlock[] {
  return (doc.content || [])
    .filter((node) => node.type !== 'paragraph' || plainText(node).trim())
    .flatMap((node) => blockFromNode(node))
    .filter(Boolean) as EditorBlock[]
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
    return [editorBlock('tabla', rows.map((row) => row.join('|')).join('\n'), {
      type: 'table',
      json: node,
      rows: rows.slice(1),
      columns: rows[0] || [],
      width: node.attrs?.width || null,
      hasHeader: node.attrs?.hasHeader !== false,
      align: node.attrs?.align || 'left',
    })]
  }
  if (node.type === 'image') {
    return [editorBlock('imagen', String(node.attrs?.src || ''), {
      type: 'image',
      json: node,
      assetId: imageAssetId(node),
      caption: '',
      width: node.attrs?.width || null,
      height: node.attrs?.height || null,
      align: node.attrs?.align || 'inline',
      offsetX: node.attrs?.offsetX || 0,
      offsetY: node.attrs?.offsetY || 0,
    })]
  }
  if (node.type === 'noteBox') {
    const refId = Number(node.attrs?.refId || 0)
    if (refId) {
      return [editorBlock('nota-ref', String(refId), { type: 'notice_ref', json: node, noticeTemplateId: refId, title: node.attrs?.title, text: plainText(node) })]
    }
    return [editorBlock('nota', plainText(node), { type: 'note', json: node, title: node.attrs?.title || 'Nota', text: plainText(node) })]
  }
  if (node.type === 'reusableSectionBox') {
    const refId = Number(node.attrs?.refId || 0)
    return [editorBlock('bloque-ref', String(refId), {
      type: 'reusable_block_ref',
      json: node,
      reusableBlockId: refId,
      title: node.attrs?.title,
      code: node.attrs?.code,
    }, node)]
  }
  if (node.type === 'reusableFragmentBox') {
    const refId = Number(node.attrs?.refId || 0)
    return [editorBlock('fragmento-ref', String(refId), {
      type: 'reusable_fragment_ref',
      json: node,
      reusableFragmentId: refId,
      title: node.attrs?.title,
      code: node.attrs?.code,
    }, node)]
  }
  if (node.type === 'formulaBlock') {
    return [editorBlock('formula', String(node.attrs?.latex || ''), {
      type: 'formula',
      json: node,
      latex: node.attrs?.latex || '',
      mathml: node.attrs?.mathml || null,
      omml: node.attrs?.omml || null,
      displayMode: node.attrs?.displayMode || 'block',
      numbered: Boolean(node.attrs?.numbered),
      equationNumber: node.attrs?.equationNumber || null,
      caption: node.attrs?.caption || '',
      align: node.attrs?.align || 'center',
      width: node.attrs?.width || null,
      offsetX: node.attrs?.offsetX || 0,
      offsetY: node.attrs?.offsetY || 0,
    }, node)]
  }
  return []
}

function editorBlock(type: EditorBlockType, content: string, data?: Record<string, unknown>, node?: JSONContent): EditorBlock {
  const sourceNode = node || data?.json as JSONContent | undefined
  return {
    id: String(sourceNode?.attrs?.blockId || randomId('block')),
    backendId: sourceNode?.attrs?.backendId ? Number(sourceNode.attrs.backendId) : undefined,
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
  const assetId = Number(node.attrs?.assetId || 0)
  if (assetId) return assetId
  const alt = String(node.attrs?.alt || '')
  return alt.startsWith('asset-') ? Number(alt.replace('asset-', '')) : undefined
}
</script>

<template>
  <article class="rich-section" :class="{ selected, hidden: !section.visible, linked: isLinkedSection }" @click="selectSection">
    <header class="section-bar">
      <button class="drag-handle" draggable="true" title="Arrastrar sección" @click.stop @mousedown.stop>
        <GripVertical class="drag-dots" :size="17" />
      </button>
      <span class="section-number">{{ section.sectionNumber || section.sortOrder }}.</span>
      <button
        v-if="isLinkedSection"
        class="section-link-button"
        title="Seccion vinculada. Pulsar para desvincular"
        aria-label="Desvincular seccion reutilizable"
        @click.stop="showUnlinkSectionModal = true"
      >
        <Link2 :size="15" />
      </button>
      <input class="section-title-input" :value="sectionTitle" placeholder="Título de sección" @input="patchTitle(($event.target as HTMLInputElement).value)" />
      <select
        class="section-control status-control"
        :class="section.status === 'REVIEW' ? 'review' : 'approved'"
        :value="section.status"
        title="Estado de la sección"
        @click.stop
        @change="patch({ status: ($event.target as HTMLSelectElement).value as EditorSection['status'] })"
      >
        <option value="REVIEW">Revisión</option>
        <option value="APPROVED">Aprobado</option>
      </select>
      <button
        class="visibility-toggle"
        :class="{ hidden: !section.visible }"
        :title="section.visible ? 'Visible' : 'Oculto'"
        :aria-label="section.visible ? 'Ocultar seccion' : 'Mostrar seccion'"
        @click.stop="patch({ visible: !section.visible })"
        @mousedown.stop
      >
        <Eye v-if="section.visible" :size="16" />
        <EyeOff v-else :size="16" />
      </button>
      <button class="bar-save" title="Guardar sección" @click.stop="emit('saveSection')"><Save :size="14" /> Guardar</button>
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
      <Teleport v-if="activeToolbar && !isLinkedSection" to="#manual-editor-toolbar">
      <div class="toolbar" @mousedown.prevent.stop @click.stop>
        <div class="ribbon-group compact-group">
          <div class="ribbon-row">
            <button class="tool-btn icon-only" title="Deshacer" aria-label="Deshacer" @click="undo"><Undo2 :size="16" /></button>
            <button class="tool-btn icon-only" title="Rehacer" aria-label="Rehacer" @click="redo"><Redo2 :size="16" /></button>
            <button class="tool-btn icon-only" title="Copiar" aria-label="Copiar" @click="copySelection"><Copy :size="16" /></button>
            <button class="tool-btn icon-only" title="Cortar" aria-label="Cortar" @click="cutSelection"><Scissors :size="16" /></button>
            <button class="tool-btn icon-only" title="Pegar" aria-label="Pegar" @click="pasteClipboard"><ClipboardPaste :size="16" /></button>
          </div>
          <span class="group-label">Portapapeles</span>
        </div>

        <div class="ribbon-group">
          <div class="ribbon-row">
            <button class="tool-btn" :class="{ active: currentHeadingLevel > 0 }" title="Título" @click="toggleDefaultHeading"><Heading :size="17" /><span>Título</span></button>
            <div class="level-controls" aria-label="Nivel de titulo">
              <button class="step-btn" title="Bajar nivel de titulo" :disabled="!currentHeadingLevel || currentHeadingLevel >= 3" @click="increaseHeadingLevel"><PanelTopClose :size="12" /></button>
              <button class="step-btn" title="Subir nivel de titulo" :disabled="!currentHeadingLevel" @click="decreaseHeadingLevel"><PanelTopClose class="flip" :size="12" /></button>
            </div>
            <button class="tool-btn" :class="{ active: currentHeadingLevel === 0 }" title="Párrafo" @click="setParagraph"><span class="paragraph-icon">¶</span><span>Párrafo</span></button>
          </div>
          <span class="group-label">{{ headingGroupLabel }}</span>
        </div>

        <div class="ribbon-group">
          <div class="ribbon-row">
            <button class="tool-btn" :class="{ active: editor?.isActive('bulletList') }" title="Lista" @click="editor?.chain().focus().toggleBulletList().run()"><List :size="16" /><span>Lista</span></button>
            <button class="tool-btn" :class="{ active: editor?.isActive('orderedList') }" title="Lista ordenada" @click="editor?.chain().focus().toggleOrderedList().run()"><ListOrdered :size="16" /><span>Lista ord.</span></button>
            <div class="level-controls" aria-label="Sangria de lista">
              <button class="step-btn" title="Sangrar lista" @click="editor?.chain().focus().sinkListItem('listItem').run()"><PanelTopClose :size="12" /></button>
              <button class="step-btn" title="Reducir sangria" @click="editor?.chain().focus().liftListItem('listItem').run()"><PanelTopClose class="flip" :size="12" /></button>
            </div>
          </div>
          <span class="group-label">Listas</span>
        </div>

        <div class="ribbon-group">
          <div class="ribbon-row">
            <div class="table-picker-menu">
              <button class="tool-btn" title="Tabla" @click="toggleTablePicker"><TableIcon :size="16" /><span>Tabla</span></button>
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
                <label class="table-header-choice">
                  <input v-model="tableWithHeader" type="checkbox" />
                  Primera fila como encabezado
                </label>
                <button class="custom-table" @click="openCustomTableModal"><TableIcon :size="13" /> Insertar tabla...</button>
              </div>
            </div>
            <button class="tool-btn" title="Imagen" @click="openImageModal"><ImageIcon :size="16" /><span>Imagen</span></button>
            <div class="toolbar-menu">
              <button class="tool-btn" title="Nota" @click="toggleNoteMenu"><StickyNote :size="16" /><span>Nota</span></button>
              <div v-if="showNoteMenu" class="submenu">
                <button @click="openNoticeMenu('new')">Nota nueva</button>
                <button @click="openNoticeMenu('library')">Elegir nota</button>
              </div>
            </div>
            <button class="tool-btn" title="Ecuación" @click="insertEquation"><Sigma :size="16" /><span>Ecuación</span></button>
            <button class="tool-btn" title="Insertar fragmento" @click="openFragmentInsertModal"><Group :size="16" /><span>Fragmento</span></button>
            <button class="tool-btn save-content" title="Insertar sección" @click="openSectionInsertModal"><SquareDashedText :size="16" /><span>Sección</span></button>
          </div>
          <span class="group-label">Insertar</span>
        </div>

        <div class="ribbon-group table-tools">
          <div class="ribbon-row">
            <button class="tool-btn" title="Insertar fila arriba" :disabled="!editor?.isActive('table')" @click="runTableEdit('row-before')"><TableRowsSplit :size="16" /><span>Fila arriba</span></button>
            <button class="tool-btn" title="Insertar fila abajo" :disabled="!editor?.isActive('table')" @click="runTableEdit('row-after')"><TableRowsSplit :size="16" /><span>Fila abajo</span></button>
            <button class="tool-btn" title="Insertar columna izquierda" :disabled="!editor?.isActive('table')" @click="runTableEdit('column-before')"><TableColumnsSplit :size="16" /><span>Col. izq.</span></button>
            <button class="tool-btn" title="Insertar columna derecha" :disabled="!editor?.isActive('table')" @click="runTableEdit('column-after')"><TableColumnsSplit :size="16" /><span>Col. der.</span></button>
          </div>
          <div class="ribbon-row">
            <button class="tool-btn" title="Combinar celdas seleccionadas" :disabled="!editor?.isActive('table')" @click="runTableEdit('merge')"><TableCellsMerge :size="16" /><span>Combinar</span></button>
            <button class="tool-btn" title="Separar celda combinada" :disabled="!editor?.isActive('table')" @click="runTableEdit('split')"><TableCellsSplit :size="16" /><span>Separar</span></button>
            <button class="tool-btn danger" title="Eliminar fila" :disabled="!editor?.isActive('table')" @click="runTableEdit('delete-row')"><Trash2 :size="16" /><span>Fila</span></button>
            <button class="tool-btn danger" title="Eliminar columna" :disabled="!editor?.isActive('table')" @click="runTableEdit('delete-column')"><Trash2 :size="16" /><span>Columna</span></button>
          </div>
          <span class="group-label">Tabla</span>
        </div>

        <div class="ribbon-group section-actions">
          <div class="ribbon-row">
            <button
              class="tool-btn select-blocks-tool"
              :class="{ active: blockSelectionMode }"
              :aria-pressed="blockSelectionMode"
              title="Seleccionar bloques para guardarlos como fragmento"
              @click="toggleBlockSelectionMode"
            >
              <ListChecks :size="16" /><span>Seleccionar bloques</span>
            </button>
            <button class="tool-btn" title="Duplicar sección" @click="emit('duplicate')"><Copy :size="16" /><span>Duplicar</span></button>
            <button class="tool-btn" title="Guardar como reutilizable" @click="emit('saveReusable')"><Save :size="16" /><span>Reutilizable</span></button>
            <button class="tool-btn danger" title="Eliminar sección" @click="emit('delete')"><Trash2 :size="16" /><span>Eliminar</span></button>
          </div>
          <span class="group-label">Sección</span>
        </div>
      </div>
      </Teleport>

      <div v-if="hasSelection && !isLinkedSection" class="selection-actions" @mousedown.stop>
        <strong>{{ selectedCount }} {{ selectedCount === 1 ? 'bloque seleccionado' : 'bloques seleccionados' }}</strong>
        <button @click="duplicateSelectedBlocks"><Copy :size="15" /> Duplicar</button>
        <button :disabled="!availableMoveTargets.length" @click="openMoveModal"><MoveRight :size="15" /> Mover</button>
        <button class="danger" @click="deleteSelectedBlocks"><Trash2 :size="15" /> Eliminar</button>
        <button class="primary" @click="openFragmentModal"><Library :size="15" /> Guardar como fragmento</button>
        <button class="clear-selection" title="Salir de selección de bloques" @click="clearBlockSelection(true)"><X :size="16" /></button>
      </div>

      <EditorContent
        :editor="editor"
        class="editor-shell"
        :class="{ 'block-selection-mode': blockSelectionMode, linked: isLinkedSection }"
        :style="{ '--section-prefix': `${section.sectionNumber || section.sortOrder}.` }"
        @mousedown.stop
        @click.stop="handleSectionClick"
      />
      <div
        v-if="blockActionsPosition.visible && !isLinkedSection"
        class="block-actions-float"
        :style="{ top: `${blockActionsPosition.top}px`, left: `${blockActionsPosition.left}px` }"
      >
        <button
          class="block-action-drag"
          :title="`Arrastrar ${blockKindLabel(blockActionsPosition.kind)}`"
          :aria-label="`Arrastrar ${blockKindLabel(blockActionsPosition.kind)}`"
          @mousedown.stop.prevent="startActiveBlockDrag"
          @click.stop
        >
          <GripVertical :size="16" />
        </button>
        <button
          class="block-action-copy"
          :title="`Copiar ${blockKindLabel(blockActionsPosition.kind)}`"
          :aria-label="`Copiar ${blockKindLabel(blockActionsPosition.kind)}`"
          @mousedown.prevent
          @click.stop="copyActiveBlock"
        >
          <Copy :size="16" />
        </button>
        <button
          class="block-action-cut"
          :title="`Cortar ${blockKindLabel(blockActionsPosition.kind)}`"
          :aria-label="`Cortar ${blockKindLabel(blockActionsPosition.kind)}`"
          @mousedown.prevent
          @click.stop="cutActiveBlock"
        >
          <Scissors :size="16" />
        </button>
        <button
          title="Alinear a la izquierda"
          aria-label="Alinear a la izquierda"
          @mousedown.prevent
          @click.stop="alignActiveBlock('left')"
        >
          <AlignLeft :size="16" />
        </button>
        <button
          title="Centrar elemento"
          aria-label="Centrar elemento"
          @mousedown.prevent
          @click.stop="alignActiveBlock('center')"
        >
          <AlignCenter :size="16" />
        </button>
        <button
          title="Alinear a la derecha"
          aria-label="Alinear a la derecha"
          @mousedown.prevent
          @click.stop="alignActiveBlock('right')"
        >
          <AlignRight :size="16" />
        </button>
        <button
          class="block-action-delete"
          :title="`Eliminar ${blockKindLabel(blockActionsPosition.kind)}`"
          :aria-label="`Eliminar ${blockKindLabel(blockActionsPosition.kind)}`"
          @mousedown.prevent
          @click.stop="deleteActiveBlock"
        >
          <Trash2 :size="16" />
        </button>
      </div>
      <div
        v-if="blockResizeOverlay.visible && !isLinkedSection"
        class="block-resize-overlay"
        :class="blockResizeOverlay.kind"
        :style="{
          top: `${blockResizeOverlay.top}px`,
          left: `${blockResizeOverlay.left}px`,
          width: `${blockResizeOverlay.width}px`,
          height: `${blockResizeOverlay.height}px`,
        }"
      >
        <button v-if="blockResizeOverlay.kind === 'image'" class="resize-handle nw" title="Redimensionar" @mousedown="startBlockResize($event, 'nw')" />
        <button v-if="blockResizeOverlay.kind === 'image'" class="resize-handle ne" title="Redimensionar" @mousedown="startBlockResize($event, 'ne')" />
        <button v-if="blockResizeOverlay.kind === 'image'" class="resize-handle sw" title="Redimensionar" @mousedown="startBlockResize($event, 'sw')" />
        <button v-if="blockResizeOverlay.kind === 'image'" class="resize-handle se" title="Redimensionar" @mousedown="startBlockResize($event, 'se')" />
        <button v-if="blockResizeOverlay.kind === 'image'" class="resize-handle n" title="Redimensionar alto" @mousedown="startBlockResize($event, 'n')" />
        <button v-if="blockResizeOverlay.kind === 'image'" class="resize-handle s" title="Redimensionar alto" @mousedown="startBlockResize($event, 's')" />
        <button class="resize-handle e" title="Redimensionar" @mousedown="startBlockResize($event, 'e')" />
        <button class="resize-handle w" title="Redimensionar" @mousedown="startBlockResize($event, 'w')" />
      </div>
      <div
        v-if="noteActionsPosition.visible && !isLinkedSection"
        class="note-actions-float"
        :style="{ top: `${noteActionsPosition.top}px`, left: `${noteActionsPosition.left}px` }"
      >
        <button
          class="note-action-drag"
          :title="noteActionsPosition.kind === 'fragment' ? 'Arrastrar fragmento' : 'Arrastrar nota'"
          :aria-label="noteActionsPosition.kind === 'fragment' ? 'Arrastrar fragmento' : 'Arrastrar nota'"
          @mousedown.stop.prevent="startActiveNoteDrag"
          @click.stop
        >
          <GripVertical :size="16" />
        </button>
        <button
          v-if="noteActionsPosition.linked"
          class="note-action-unlink"
          :title="noteActionsPosition.kind === 'fragment' ? 'Desvincular fragmento' : 'Desvincular nota'"
          :aria-label="noteActionsPosition.kind === 'fragment' ? 'Desvincular fragmento' : 'Desvincular nota'"
          @mousedown.prevent
          @click.stop="unlinkActiveNote"
        >
          <Link2Off :size="16" />
        </button>
        <button
          class="note-action-copy"
          :title="noteActionsPosition.kind === 'fragment' ? 'Copiar fragmento' : 'Copiar nota'"
          :aria-label="noteActionsPosition.kind === 'fragment' ? 'Copiar fragmento' : 'Copiar nota'"
          @mousedown.prevent
          @click.stop="copyActiveNote"
        >
          <Copy :size="16" />
        </button>
        <button
          class="note-action-cut"
          :title="noteActionsPosition.kind === 'fragment' ? 'Cortar fragmento' : 'Cortar nota'"
          :aria-label="noteActionsPosition.kind === 'fragment' ? 'Cortar fragmento' : 'Cortar nota'"
          @mousedown.prevent
          @click.stop="cutActiveNote"
        >
          <Scissors :size="16" />
        </button>
        <button
          class="note-action-delete"
          :title="noteActionsPosition.kind === 'fragment' ? 'Eliminar fragmento' : 'Eliminar nota'"
          :aria-label="noteActionsPosition.kind === 'fragment' ? 'Eliminar fragmento' : 'Eliminar nota'"
          @mousedown.prevent
          @click.stop="deleteActiveNote"
        >
          <Trash2 :size="16" />
        </button>
      </div>
      <div
        v-if="noteDragPreview.visible && !isLinkedSection"
        class="note-drag-preview"
        :class="{ linked: noteDragPreview.linked }"
        :style="{ top: `${noteDragPreview.top}px`, left: `${noteDragPreview.left}px` }"
      >
        <strong>{{ noteDragPreview.title }}</strong>
        <p>{{ noteDragPreview.text }}</p>
      </div>
      <div
        v-if="blockDragPreview.visible && !isLinkedSection"
        class="block-drag-preview"
        :class="blockDragPreview.kind"
        :style="{ top: `${blockDragPreview.top}px`, left: `${blockDragPreview.left}px` }"
      >
        <strong>{{ blockDragPreview.title }}</strong>
        <p>{{ blockDragPreview.text }}</p>
      </div>
    </div>

    <Teleport to="body">
    <AppModal
      v-if="showUnlinkSectionModal"
      title="Desvincular seccion"
      description="La seccion dejara de estar vinculada al contenido reutilizable y podra editarse de forma independiente."
      size="sm"
      @close="showUnlinkSectionModal = false"
    >
      <template #footer>
        <button type="button" class="btn btn-outline" @click="showUnlinkSectionModal = false">Cancelar</button>
        <button type="button" class="btn btn-primary" @click="confirmUnlinkSection">Desvincular</button>
      </template>
    </AppModal>

    <div v-if="showImageModal" class="modal-backdrop" @click.self="showImageModal = false">
      <div class="modal-card image-modal">
        <header>
          <h3>Insertar imagen</h3>
          <button @click="showImageModal = false">×</button>
        </header>
        <label class="upload-box">
          <FileImage :size="16" />
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
          <label class="remember-size">
            <input v-model="tableWithHeader" type="checkbox" />
            Usar primera fila como encabezado
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
            <strong>{{ notice.code }} · {{ notice.title }}</strong>
            <span>{{ notice.contentEs }}</span>
          </button>
        </div>
      </div>
    </div>

    <div v-if="showSelectionMismatchModal" class="modal-backdrop" @click.self="showSelectionMismatchModal = false">
      <section class="modal-card selection-warning-dialog">
        <header>
          <div>
            <h3>Los bloques ES y EN no coinciden</h3>
            <p>
              {{ language }} tiene {{ selectionMismatchCounts.current }} bloques y la versión paralela tiene
              {{ selectionMismatchCounts.parallel }}.
            </p>
          </div>
          <button type="button" class="modal-close" @click="showSelectionMismatchModal = false">×</button>
        </header>
        <p>
          No es posible seleccionar automáticamente los bloques paralelos. ¿Deseas seleccionar y guardar
          solamente los bloques del idioma {{ language }}?
        </p>
        <footer>
          <button type="button" class="btn btn-outline" @click="showSelectionMismatchModal = false">Cancelar selección</button>
          <button type="button" class="btn btn-primary" @click="enableBlockSelection(false)">Guardar solo {{ language }}</button>
        </footer>
      </section>
    </div>

    <div v-if="showFragmentModal" class="modal-backdrop" @click.self="showFragmentModal = false">
      <form class="modal-card fragment-dialog" @submit.prevent="saveSelectedFragment">
        <header>
          <div>
            <h3>Guardar fragmento reutilizable</h3>
            <p>{{ selectedCount }} {{ selectedCount === 1 ? 'bloque' : 'bloques' }} en el snapshot</p>
          </div>
          <button type="button" @click="showFragmentModal = false">×</button>
        </header>
        <label>
          Nombre
          <input v-model="fragmentName" class="field" required maxlength="220" placeholder="Ej. Conexión inicial" />
        </label>
        <label>
          Descripción opcional
          <textarea v-model="fragmentDescription" class="field" rows="3" maxlength="600" placeholder="Qué incluye y cuándo conviene reutilizarlo" />
        </label>
        <section class="fragment-preview">
          <strong>Vista previa del fragmento ({{ selectionSync?.includeParallel ? 'ES + EN' : language }})</strong>
          <ol>
            <li v-for="block in fragmentPreviewBlocks" :key="block.id">
              <span>{{ block.type }}</span>
              <p>{{ block.content || 'Bloque sin texto' }}</p>
            </li>
          </ol>
        </section>
        <p v-if="fragmentMessage" class="dialog-message">{{ fragmentMessage }}</p>
        <footer>
          <button type="button" class="btn btn-outline" @click="showFragmentModal = false">Cancelar</button>
          <button type="submit" class="btn btn-primary" :disabled="fragmentSaving || !fragmentName.trim()">
            {{ fragmentSaving ? 'Guardando...' : 'Guardar fragmento' }}
          </button>
        </footer>
      </form>
    </div>

    <div v-if="showMoveModal" class="modal-backdrop" @click.self="showMoveModal = false">
      <form class="modal-card move-dialog" @submit.prevent="confirmMoveSelection">
        <header>
          <h3>Mover bloques</h3>
          <button type="button" @click="showMoveModal = false">×</button>
        </header>
        <label>
          Sección destino
          <select v-model="moveTargetSectionId" class="field" required>
            <option v-for="target in availableMoveTargets" :key="target.id" :value="target.id">
              {{ target.number }}. {{ target.title }}
            </option>
          </select>
        </label>
        <footer>
          <button type="button" class="btn btn-outline" @click="showMoveModal = false">Cancelar</button>
          <button type="submit" class="btn btn-primary">Mover {{ selectedCount }} bloques</button>
        </footer>
      </form>
    </div>

    <div v-if="showFragmentInsertModal" class="modal-backdrop" @click.self="showFragmentInsertModal = false">
      <div class="modal-card reusable-dialog">
        <header>
          <div>
            <h3>Insertar fragmento</h3>
            <p>Los fragmentos se insertan vinculados y se pueden desvincular desde el editor.</p>
          </div>
          <button @click="showFragmentInsertModal = false">×</button>
        </header>
        <div class="fragment-insert-options">
          <label>
            Posición
            <select v-model="fragmentInsertPosition" class="field">
              <option value="END">Al final de la sección</option>
              <option value="AFTER_SELECTION" :disabled="!hasSelection">Después del último bloque seleccionado</option>
            </select>
          </label>
        </div>
        <p v-if="fragmentMessage" class="dialog-message">{{ fragmentMessage }}</p>
        <input v-model="fragmentInsertSearch" class="field" placeholder="Buscar fragmento..." />
        <div class="notice-list">
          <button
            v-for="fragment in filteredReusableFragments"
            :key="fragment.id"
            @click="insertReusableFragment(fragment)"
          >
            <strong>{{ fragment.titleEs || fragment.title }}</strong>
            <span>{{ fragment.description || fragment.code }}</span>
            <small>VINCULADO</small>
          </button>
          <p v-if="!filteredReusableFragments.length" class="text-muted">
            No hay fragmentos reutilizables que coincidan.
          </p>
        </div>
      </div>
    </div>

    <div v-if="showSectionInsertModal" class="modal-backdrop" @click.self="showSectionInsertModal = false">
      <div class="modal-card reusable-dialog">
        <header>
          <div>
            <h3>Insertar sección</h3>
            <p>Se creara una nueva seccion del manual con el contenido guardado.</p>
          </div>
          <button @click="showSectionInsertModal = false">×</button>
        </header>
        <p class="dialog-message">Se insertara despues de la seccion actual y aparecera en el indice lateral.</p>
        <p v-if="fragmentMessage" class="dialog-message">{{ fragmentMessage }}</p>
        <input v-model="sectionInsertSearch" class="field" placeholder="Buscar sección..." />
        <div class="notice-list">
          <button
            v-for="sectionItem in filteredReusableSections"
            :key="sectionItem.id"
            @click="insertReusableSection(sectionItem)"
          >
            <strong>{{ sectionItem.titleEs || sectionItem.title }}</strong>
            <span>{{ sectionItem.titleEn || sectionItem.description || sectionItem.code }}</span>
            <small>SECCION</small>
          </button>
          <p v-if="!filteredReusableSections.length" class="text-muted">
            No hay secciones reutilizables que coincidan.
          </p>
        </div>
      </div>
    </div>

    <div v-if="showSectionLanguageWarningModal" class="modal-backdrop" @click.self="cancelReusableSectionInsertWarning">
      <section class="modal-card selection-warning-dialog">
        <header>
          <div>
            <h3>La versi&oacute;n inglesa no coincide</h3>
            <p>La seccion reutilizable no tiene contenido EN o no tiene la misma cantidad de bloques que ES.</p>
          </div>
          <button type="button" class="modal-close" @click="cancelReusableSectionInsertWarning">×</button>
        </header>
        <p>
          Puedes insertar la seccion y crear la version inglesa como clon temporal del contenido espanol.
          El cambio quedara pendiente hasta que guardes.
        </p>
        <footer>
          <button type="button" class="btn btn-outline" @click="cancelReusableSectionInsertWarning">Cancelar</button>
          <button type="button" class="btn btn-primary" @click="confirmReusableSectionWithSpanishClone">Usar clon temporal ES</button>
        </footer>
      </section>
    </div>

    <div v-if="showEquationModal" class="modal-backdrop equation-backdrop" @click.self="showEquationModal = false">
      <form class="equation-dialog" @submit.prevent="confirmEquation">
        <header>
          <div>
            <span class="equation-kicker">Herramientas matemáticas</span>
            <h3>{{ editingEquationPos === null ? 'Insertar ecuación' : 'Editar ecuación' }}</h3>
          </div>
          <button type="button" class="modal-close" @click="showEquationModal = false">×</button>
        </header>
        <section class="equation-gallery">
          <div class="structure-grid">
            <button v-for="template in equationTemplates" :key="template.label" type="button" @click="insertEquationSnippet(template.latex)">
              <span>{{ template.latex }}</span>
              <small>{{ template.label }}</small>
            </button>
          </div>
          <div class="greek-row">
            <span>Símbolos</span>
            <button v-for="[symbol, latex] in greekTemplates" :key="latex" type="button" @click="insertEquationSnippet(latex)">{{ symbol }}</button>
          </div>
        </section>
        <section class="equation-editor-grid">
          <label class="latex-field">
            LaTeX
            <textarea ref="equationTextarea" v-model="equationDraft.latex" class="field mono" rows="6" required placeholder="\frac{P_{salida}}{P_{entrada}} \cdot 100" />
          </label>
          <div class="equation-preview" :style="{ textAlign: equationDraft.align as 'left' | 'center' | 'right' }">
            <span>Vista previa LaTeX</span>
            <strong>{{ equationDraft.latex || 'La ecuación aparecerá aquí' }}</strong>
            <small v-if="equationDraft.caption">{{ equationDraft.caption }}</small>
          </div>
          <label>
            Leyenda
            <input v-model="equationDraft.caption" class="field" placeholder="Cálculo del rendimiento" />
          </label>
          <label>
            Alineación
            <select v-model="equationDraft.align" class="field">
              <option value="left">Izquierda</option>
              <option value="center">Centro</option>
              <option value="right">Derecha</option>
            </select>
          </label>
          <label class="number-equation">
            <input v-model="equationDraft.numbered" type="checkbox" />
            Numerar ecuación
          </label>
          <label v-if="equationDraft.numbered">
            Número
            <input v-model="equationDraft.equationNumber" class="field" placeholder="1" />
          </label>
        </section>
        <footer>
          <span>Formato principal: LaTeX. MathML y OMML quedan preparados en el JSON.</span>
          <div>
            <button type="button" class="btn btn-outline" @click="showEquationModal = false">Cancelar</button>
            <button type="submit" class="btn btn-primary" :disabled="!equationDraft.latex.trim()">
              {{ editingEquationPos === null ? 'Insertar ecuación' : 'Actualizar ecuación' }}
            </button>
          </div>
        </footer>
      </form>
    </div>
    </Teleport>

    <AppModal v-if="showPasteTextModal" title="Pegar texto" description="No se pudo leer el portapapeles automáticamente. Pega el texto aquí." @close="showPasteTextModal = false">
      <textarea v-model="manualPasteText" class="field paste-textarea" rows="6" />
      <template #footer>
        <button type="button" class="btn btn-outline" @click="showPasteTextModal = false">Cancelar</button>
        <button type="button" class="btn btn-primary" :disabled="!manualPasteText.trim()" @click="confirmManualPaste">Pegar</button>
      </template>
    </AppModal>

    <AppModal v-if="editorMessage" title="Aviso" :description="editorMessage" size="sm" @close="editorMessage = ''">
      <template #footer>
        <button type="button" class="btn btn-primary" @click="editorMessage = ''">Entendido</button>
      </template>
    </AppModal>
  </article>
</template>

<style scoped>
.rich-section {
  width: 210mm;
  max-width: none;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: 0;
  margin: 0 auto 14px;
  overflow: visible;
  box-shadow: 0 10px 30px rgba(0, 0, 0, .08);
}

.rich-section.selected {
  border-color: var(--dikoin-blue);
  box-shadow: 0 0 0 2px rgba(14, 127, 187, .1);
}

.rich-section.linked {
  background: #eff8ff;
  border-color: #8fc7ee;
}

.rich-section.linked.selected {
  box-shadow: 0 0 0 2px rgba(47, 128, 192, .18);
}

.section-bar {
  position: sticky;
  top: 0;
  z-index: 55;
  min-height: 38px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 10px;
  background: var(--dikoin-blue);
  color: #fff;
}

.drag-handle {
  border: 0;
  background: transparent;
  color: #fff;
  padding: 3px;
  display: inline-flex;
  align-items: center;
  cursor: grab;
}

.drag-handle:active {
  cursor: grabbing;
}

.drag-dots {
  opacity: .95;
}

.section-number {
  font-weight: 600;
}

.section-link-button,
.visibility-toggle {
  width: 28px;
  height: 26px;
  min-width: 28px;
  border: 1px solid rgba(255, 255, 255, .5);
  border-radius: var(--radius);
  background: rgba(255, 255, 255, .14);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.section-link-button:hover,
.visibility-toggle:hover {
  background: rgba(255, 255, 255, .24);
}

.section-link-button {
  color: #dff2ff;
}

.visibility-toggle.hidden {
  color: rgba(255, 255, 255, .66);
}

.section-title-input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: #fff;
  font-weight: 600;
}

.section-title-input::placeholder {
  color: rgba(255, 255, 255, .78);
}

.bar-icon {
  border: 0;
  background: transparent;
  color: #fff;
  padding: 4px;
  display: inline-flex;
  align-items: center;
}

.bar-icon .collapsed {
  transform: rotate(180deg);
}

.section-content {
  min-height: 332px;
  position: relative;
  overflow: visible;
}

.toolbar {
  position: relative;
  z-index: 210;
  display: flex;
  flex-wrap: nowrap;
  align-items: stretch;
  width: 100%;
  min-width: 0;
  min-height: 74px;
  padding: 8px 10px 6px;
  border: 1px solid #dce7f0;
  border-radius: var(--radius);
  background: linear-gradient(#ffffff, #f5f9fd);
  overflow: visible;
  box-shadow: 0 7px 12px rgba(15, 23, 42, .05);
}

.ribbon-group {
  position: relative;
  display: grid;
  grid-template-rows: 1fr auto;
  align-items: stretch;
  gap: 4px;
  padding: 0 10px;
  border-right: 1px solid #d7e3ed;
  flex: 0 0 auto;
}

.ribbon-group:first-child {
  padding-left: 2px;
}

.ribbon-group:last-child {
  border-right: 0;
}

.ribbon-row {
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  justify-content: center;
  gap: 4px;
  max-width: none;
  white-space: nowrap;
}

.compact-group {
  min-width: 228px;
}

.compact-group .ribbon-row {
  justify-content: flex-start;
}

.group-label {
  align-self: end;
  text-align: center;
  color: #6b7280;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
}

.tool-btn {
  min-width: 58px;
  min-height: 48px;
  border: 1px solid transparent;
  border-radius: 4px;
  background: transparent;
  color: #1f2937;
  padding: 6px 7px;
  display: grid;
  justify-items: center;
  align-content: center;
  gap: 5px;
  font-size: 10px;
  line-height: 1.1;
  font-weight: 600;
}

.tool-btn.mini {
  min-width: 58px;
  min-height: 30px;
  grid-auto-flow: column;
  grid-auto-columns: max-content;
  align-content: center;
  align-items: center;
  gap: 5px;
  padding: 4px 6px;
  font-size: 11px;
}

.tool-btn.icon-only {
  min-width: 40px;
  min-height: 40px;
  padding: 4px;
  display: inline-grid;
  place-items: center;
}

.tool-btn svg {
  width: 20px;
  height: 20px;
}

.tool-btn.icon-only svg {
  width: 22px;
  height: 22px;
}

.level-controls {
  display: inline-grid;
  gap: 1px;
  align-self: center;
  border: 1px solid #c9d8e4;
  border-radius: 2px;
  overflow: hidden;
  background: #fff;
}

.step-btn {
  width: 28px;
  height: 24px;
  min-width: 0;
  min-height: 0;
  border: 0;
  border-bottom: 1px solid #d7e3ed;
  background: #f8fbfe;
  color: #496579;
  padding: 0;
  display: grid;
  place-items: center;
}

.step-btn svg {
  width: 16px;
  height: 16px;
}

.step-btn:last-child {
  border-bottom: 0;
}

.step-btn:hover:not(:disabled) {
  background: var(--dikoin-blue-lighter);
  color: var(--dikoin-blue);
}

.step-btn:disabled {
  color: #aeb8c2;
  cursor: default;
  background: #f3f6f9;
}

.save-content {
  min-width: 82px;
}

.tool-btn:hover,
.tool-btn.active {
  border-color: #b7d7ea;
  color: var(--dikoin-blue);
  background: #eaf4fb;
}

.tool-btn:disabled {
  cursor: default;
  color: #9aa7b4;
  background: transparent;
  border-color: transparent;
  opacity: .58;
}

.tool-btn.danger {
  color: var(--dikoin-red);
}

.paragraph-icon {
  font-size: 20px;
  line-height: 1;
  font-weight: 700;
}

.toolbar .flip {
  transform: rotate(180deg);
}

.toolbar-menu,
.table-picker-menu {
  position: relative;
  z-index: 220;
}

.toolbar-menu>button {
  height: 100%;
}

.submenu {
  display: grid;
  position: absolute;
  z-index: 230;
  left: 0;
  top: calc(100% + 4px);
  min-width: 150px;
  background: #fff;
  border: 1px solid var(--border);
  box-shadow: 0 10px 20px rgba(0, 0, 0, .12);
}

.submenu button {
  width: 100%;
  display: block;
  min-width: 0;
  text-align: left;
  justify-items: start;
  border: 0;
  background: #fff;
  padding: 9px 10px;
  font-size: 12px;
}

.table-picker-popover {
  position: absolute;
  z-index: 230;
  left: 0;
  top: calc(100% + 4px);
  width: 202px;
  padding: 7px;
  background: #fbfbfb;
  border: 1px solid #b7b7b7;
  box-shadow: 2px 4px 10px rgba(0, 0, 0, .16);
  display: grid;
  gap: 7px;
}

.table-picker-popover strong {
  display: block;
  padding: 2px 3px;
  color: #374151;
  font-size: 12px;
  font-weight: 600;
}

.table-picker-grid {
  display: grid;
  grid-template-columns: repeat(7, 24px);
  gap: 3px;
}

.table-picker-cell {
  width: 24px;
  height: 24px;
  min-width: 0;
  min-height: 0;
  border: 1px solid #7f7f7f;
  background: #fff;
  padding: 0;
}

.table-picker-cell.active {
  border-color: #2f80c0;
  background: #d8ecfb;
}

.custom-table {
  border: 0;
  border-top: 1px solid #d8d8d8;
  background: #fbfbfb;
  color: #1f2937;
  padding: 7px 4px 3px;
  display: flex;
  align-items: center;
  gap: 8px;
  text-align: left;
  font-size: 12px;
}

.custom-table:hover {
  color: var(--dikoin-blue);
  background: #edf6fd;
}

.editor-shell {
  --selection-row-width: calc(182mm + 48px);
  min-height: 240mm;
  padding: 14mm;
  background: #fff;
}

.editor-shell.linked {
  background: #eff8ff;
}

.editor-shell :deep(.rich-editor-surface) {
  min-height: 220mm;
  outline: 0;
  font-family: Arial, sans-serif;
  font-size: 12px;
  line-height: 1.42;
  position: relative;
}

.editor-shell :deep(.manual-selectable-block) {
  position: relative;
  isolation: isolate;
  transition: color .12s ease;
}

.editor-shell :deep(.manual-selectable-block::before) {
  content: "";
  position: absolute;
  z-index: -1;
  top: -5px;
  bottom: -5px;
  left: -48px;
  width: var(--selection-row-width);
  box-sizing: border-box;
  border: 1px solid transparent;
  background: transparent;
  pointer-events: none;
  transition: background .12s ease, border-color .12s ease, box-shadow .12s ease;
}

.editor-shell.block-selection-mode :deep(.manual-selectable-block:hover::before) {
  border-color: #c5ddec;
  background: rgba(218, 238, 250, .58);
  box-shadow: 0 3px 10px rgba(14, 127, 187, .07);
}

.editor-shell.block-selection-mode :deep(.manual-selectable-block.selection-hovered::before) {
  border-color: #c5ddec;
  background: rgba(218, 238, 250, .58);
  box-shadow: 0 3px 10px rgba(14, 127, 187, .07);
}

.editor-shell.block-selection-mode :deep(.block-checkbox-widget:hover + .manual-selectable-block::before) {
  border-color: #c5ddec;
  background: rgba(218, 238, 250, .58);
  box-shadow: 0 3px 10px rgba(14, 127, 187, .07);
}

.editor-shell.block-selection-mode :deep(.tableWrapper:has(.manual-selectable-block:hover)) {
  position: relative;
  isolation: isolate;
}

.editor-shell.block-selection-mode :deep(.tableWrapper:has(.manual-selectable-block:hover)::before) {
  content: "";
  position: absolute;
  z-index: -1;
  top: -5px;
  bottom: -5px;
  left: -48px;
  width: var(--selection-row-width);
  box-sizing: border-box;
  border: 1px solid #c5ddec;
  background: rgba(218, 238, 250, .58);
  box-shadow: 0 3px 10px rgba(14, 127, 187, .07);
  pointer-events: auto;
}

.editor-shell.block-selection-mode :deep(.block-checkbox-widget:hover + .tableWrapper) {
  position: relative;
  isolation: isolate;
}

.editor-shell.block-selection-mode :deep(.block-checkbox-widget:hover + .tableWrapper::before) {
  content: "";
  position: absolute;
  z-index: -1;
  top: -5px;
  bottom: -5px;
  left: -48px;
  width: var(--selection-row-width);
  box-sizing: border-box;
  border: 1px solid #c5ddec;
  background: rgba(218, 238, 250, .58);
  box-shadow: 0 3px 10px rgba(14, 127, 187, .07);
  pointer-events: auto;
}

.editor-shell :deep(.block-checkbox-widget) {
  position: absolute;
  left: -40px;
  margin-top: -4px;
  z-index: 5;
  display: inline-grid;
  place-items: center;
  width: 24px;
  height: 24px;
  /* border: 1px solid #a9c8dc; */
  /* background: #fff; */
  /* box-shadow: 0 2px 7px rgba(15, 23, 42, .08); */
  opacity: 0;
  pointer-events: auto;
  transform: translateX(4px);
  transition: opacity .12s ease, transform .12s ease, border-color .12s ease;
}

.editor-shell :deep(.block-checkbox-widget:hover),
.editor-shell :deep(.block-checkbox-widget:focus-within),
.editor-shell :deep(.block-checkbox-widget.selected),
.editor-shell :deep(.block-checkbox-widget.hovered),
.editor-shell :deep(.block-checkbox-widget:has(+ .manual-selectable-block:hover)),
.editor-shell :deep(.block-checkbox-widget:has(+ .tableWrapper:hover)) {
  opacity: 1;
  pointer-events: auto;
  transform: translateX(0);
  /* border-color: var(--dikoin-blue); */
}

.editor-shell.block-selection-mode :deep(.block-checkbox-widget) {
  opacity: 1;
  transform: translateX(0);
}

.editor-shell :deep(.block-checkbox-widget input) {
  width: 15px;
  height: 15px;
  accent-color: var(--dikoin-blue);
  cursor: pointer;
}

.editor-shell :deep(.block-checkbox-widget input:disabled) {
  cursor: not-allowed;
  opacity: .42;
}

.editor-shell :deep(.selected-manual-block) {
  background: transparent;
}

.rich-section.hidden .section-content {
  opacity: .58;
}

.editor-shell :deep(.selected-manual-block:not(img)::before) {
  border-color: rgba(14, 127, 187, .55);
  background: linear-gradient(90deg, rgba(219, 238, 250, .78), rgba(239, 248, 253, .48));
  box-shadow: 0 0 0 1px rgba(14, 127, 187, .18);
}

.editor-shell :deep(img.selected-manual-block) {
  outline: 2px solid var(--dikoin-blue);
  outline-offset: 3px;
}

.selection-actions {
  position: sticky;
  top: 36px;
  z-index: 52;
  min-height: 44px;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 7px 12px;
  background: #eef8fd;
  border-bottom: 1px solid #acd5e9;
  box-shadow: 0 6px 14px rgba(15, 23, 42, .08);
}

.selection-actions strong {
  margin-right: auto;
  color: var(--dikoin-blue-dark);
  font-size: 12px;
}

.selection-actions button {
  border: 1px solid #b9d8e8;
  background: #fff;
  color: var(--dikoin-blue-dark);
  padding: 6px 9px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  font-weight: 600;
}

.selection-actions button:hover:not(:disabled) {
  border-color: var(--dikoin-blue);
  color: var(--dikoin-blue);
}

.selection-actions button.primary {
  background: var(--dikoin-blue);
  border-color: var(--dikoin-blue);
  color: #fff;
}

.selection-actions button.danger {
  color: var(--dikoin-red);
  border-color: #fecaca;
}

.selection-actions button:disabled {
  opacity: .45;
  cursor: default;
}

.selection-actions .clear-selection {
  padding: 6px;
}

.editor-shell :deep(.is-editor-empty:first-child::before) {
  content: attr(data-placeholder);
  float: left;
  color: #9aa7b4;
  pointer-events: none;
  height: 0;
}

.editor-shell :deep(.ProseMirror-focused .is-editor-empty:first-child::before) {
  display: none;
}

.editor-shell :deep(.ProseMirror p:empty::after) {
  content: "\00a0";
}

.editor-shell :deep(p) {
  margin: 0 0 8px;
  line-height: 1.42;
}

.editor-shell :deep(h1),
.editor-shell :deep(h2),
.editor-shell :deep(h3) {
  color: var(--dikoin-blue-dark);
  margin: 0 0 8px;
  line-height: 1.25;
}

.editor-shell :deep(h1) {
  font-size: 14px;
}

.editor-shell :deep(h2) {
  font-size: 13px;
}

.editor-shell :deep(h3) {
  font-size: 12px;
}

.editor-shell :deep(h1),
.editor-shell :deep(h2),
.editor-shell :deep(h3) {
  display: flex;
  align-items: baseline;
}

.editor-shell :deep(h1::after),
.editor-shell :deep(h2::after),
.editor-shell :deep(h3::after) {
  content: attr(data-heading-number);
  display: inline-block;
  order: -1;
  flex: 0 0 auto;
  margin-right: 8px;
  color: var(--dikoin-blue);
  font-weight: 600;
}

.editor-shell :deep(ul),
.editor-shell :deep(ol) {
  margin: 0 0 8px 22px;
  padding-left: 18px;
}

.editor-shell :deep(.tableWrapper) {
  max-width: 100%;
  overflow-x: auto;
  padding: 2px 0 8px;
}

.editor-shell :deep(table) {
  max-width: 100%;
  table-layout: fixed;
  border-collapse: collapse;
  margin: 0 0 9px;
  font-size: 11px;
}

.editor-shell :deep(table:not([data-width])) {
  width: 100%;
}

.editor-shell :deep(table:hover),
.editor-shell :deep(table.movable-block-active) {
  outline: 2px solid rgba(14, 127, 187, .24);
  outline-offset: 3px;
}

.editor-shell :deep(th) {
  background: var(--dikoin-blue);
  color: #fff;
}

.editor-shell :deep(td),
.editor-shell :deep(th) {
  position: relative;
  border: 1px solid #b8cce3;
  padding: 6px;
  min-width: 0;
  vertical-align: top;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.editor-shell :deep(.column-resize-handle) {
  position: absolute;
  top: -1px;
  right: -4px;
  bottom: -1px;
  z-index: 22;
  width: 8px;
  background: rgba(14, 127, 187, .28);
  border-left: 1px solid rgba(14, 127, 187, .42);
  border-right: 1px solid rgba(14, 127, 187, .12);
  cursor: col-resize;
  pointer-events: auto;
}

.editor-shell :deep(.column-resize-handle:hover) {
  background: rgba(14, 127, 187, .46);
}

.editor-shell :deep(.resize-cursor),
.editor-shell :deep(.resize-cursor *) {
  cursor: col-resize !important;
}

.editor-shell :deep(.column-resize-dragging) {
  background: rgba(14, 127, 187, .08);
}

.editor-shell :deep(.selectedCell::after) {
  content: "";
  position: absolute;
  inset: 0;
  z-index: 1;
  background: rgba(14, 127, 187, .14);
  pointer-events: none;
}

.editor-shell :deep(img) {
  max-width: 100%;
  display: block;
  margin: 10px 0;
  object-fit: contain;
  cursor: grab;
}

.editor-shell :deep(img:active) {
  cursor: grabbing;
}

.editor-shell :deep(img:hover),
.editor-shell :deep(img.movable-block-active) {
  outline: 2px solid rgba(14, 127, 187, .28);
  outline-offset: 4px;
}

.editor-shell :deep(.movable-block-drag-source) {
  opacity: .38;
}

.editor-shell :deep(.note-box) {
  position: relative;
  border: 1px solid #fed7aa;
  border-left: 8px solid var(--dikoin-orange);
  border-radius: var(--radius);
  background: #fff7ed;
  color: #78350f;
  padding: 12px 14px 12px 16px;
  margin: 16px 0;
  box-shadow: 0 8px 18px rgba(146, 64, 14, .08);
  white-space: pre-wrap;
  transition: box-shadow .12s ease, border-color .12s ease;
}

.editor-shell :deep(.note-box:hover),
.editor-shell :deep(.note-box.ProseMirror-selectednode),
.editor-shell :deep(.note-box-active) {
  border-color: #fb923c;
  box-shadow: 0 0 0 2px rgba(251, 146, 60, .14), 0 8px 18px rgba(146, 64, 14, .08);
}

.editor-shell :deep(.note-box-drag-source) {
  opacity: .38;
}

.editor-shell :deep(.note-drag-handle) {
  display: none;
}

.editor-shell :deep(.note-content) {
  display: block;
  min-width: 0;
}

.editor-shell :deep(.linked-note-box) {
  cursor: default;
  padding-right: 42px;
}

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

.editor-shell :deep(.linked-reusable-fragment-box) {
  position: relative;
  margin: 10px -22px;
  padding: 12px 62px 12px 18px;
  border: 1px solid #bfdbfe;
  border-left: 4px solid var(--dikoin-blue);
  border-radius: var(--radius);
  background: #eff8ff;
  color: inherit;
  box-shadow: 0 8px 18px rgba(0, 124, 184, .07);
  cursor: default;
  user-select: none;
}

.editor-shell :deep(.linked-reusable-fragment-box:hover),
.editor-shell :deep(.linked-reusable-fragment-box.ProseMirror-selectednode),
.editor-shell :deep(.linked-reusable-fragment-box.note-box-active) {
  border-color: #7dd3fc;
  box-shadow: 0 0 0 2px rgba(0, 124, 184, .16), 0 10px 22px rgba(0, 124, 184, .1);
}

.editor-shell :deep(.linked-reusable-fragment-box::after) {
  content: "";
  position: absolute;
  top: 12px;
  right: 14px;
  width: 16px;
  height: 16px;
  background: var(--dikoin-blue);
  opacity: .58;
  pointer-events: none;
  -webkit-mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='24' height='24' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M9 17H7A5 5 0 0 1 7 7h2'/%3E%3Cpath d='M15 7h2a5 5 0 1 1 0 10h-2'/%3E%3Cline x1='8' x2='16' y1='12' y2='12'/%3E%3C/svg%3E") center / contain no-repeat;
  mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='24' height='24' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M9 17H7A5 5 0 0 1 7 7h2'/%3E%3Cpath d='M15 7h2a5 5 0 1 1 0 10h-2'/%3E%3Cline x1='8' x2='16' y1='12' y2='12'/%3E%3C/svg%3E") center / contain no-repeat;
}

.editor-shell :deep(.reusable-fragment-content > *:first-child) {
  margin-top: 0;
}

.editor-shell :deep(.reusable-fragment-content > *:last-child) {
  margin-bottom: 0;
}

.editor-shell :deep(.note-box::before) {
  content: attr(title);
  display: block;
  font-weight: 600;
  margin-bottom: 4px;
}

.editor-shell :deep(.formula-block) {
  display: grid;
  justify-items: center;
  gap: 5px;
  margin: 18px 0;
  padding: 14px 18px;
  border: 1px solid #c9d8e4;
  border-left: 5px solid var(--dikoin-blue);
  background: linear-gradient(135deg, #f8fbfe, #fff);
  cursor: pointer;
}

.editor-shell :deep(.formula-block.ProseMirror-selectednode) {
  outline: 2px solid var(--dikoin-blue);
  outline-offset: 3px;
}

.editor-shell :deep(.formula-latex) {
  font-family: Cambria, "Times New Roman", serif;
  font-size: 20px;
  color: #172b3a;
  white-space: pre-wrap;
}

.editor-shell :deep(.formula-caption) {
  color: var(--muted-foreground);
  font-size: 11px;
}

.table-delete-float {
  position: absolute;
  z-index: 8;
  width: 28px;
  height: 28px;
  border: 1px solid #fecaca;
  background: #fff;
  color: var(--dikoin-red);
  padding: 0;
  border-radius: var(--radius);
  display: inline-grid;
  place-items: center;
  box-shadow: 0 8px 20px rgba(0, 0, 0, .12);
}

.table-delete-float:hover {
  background: var(--dikoin-red-light);
  border-color: var(--dikoin-red);
}

.block-actions-float {
  position: absolute;
  z-index: 8;
  display: inline-flex;
  gap: 4px;
}

.block-actions-float button {
  width: 28px;
  height: 28px;
  border: 1px solid #bfdbfe;
  background: #fff;
  color: var(--dikoin-blue);
  padding: 0;
  border-radius: var(--radius);
  display: inline-grid;
  place-items: center;
  box-shadow: 0 8px 20px rgba(0, 0, 0, .12);
}

.block-actions-float .block-action-drag {
  cursor: grab;
}

.block-actions-float .block-action-drag:active {
  cursor: grabbing;
}

.block-actions-float .block-action-delete {
  color: var(--dikoin-red);
  border-color: #fecaca;
}

.block-actions-float .block-action-delete:hover {
  background: var(--dikoin-red-light);
  border-color: var(--dikoin-red);
}

.block-actions-float .block-action-drag:hover,
.block-actions-float .block-action-copy:hover,
.block-actions-float .block-action-cut:hover {
  background: var(--dikoin-blue-lighter);
  border-color: var(--dikoin-blue);
}

.block-resize-overlay {
  position: absolute;
  z-index: 7;
  pointer-events: none;
  border: 1px dashed rgba(14, 127, 187, .6);
  border-radius: 2px;
}

.block-resize-overlay .resize-handle {
  position: absolute;
  width: 10px;
  height: 10px;
  min-width: 0;
  min-height: 0;
  padding: 0;
  border: 1px solid var(--dikoin-blue);
  background: #fff;
  border-radius: 50%;
  pointer-events: auto;
  box-shadow: 0 2px 8px rgba(15, 23, 42, .16);
}

.block-resize-overlay .resize-handle.nw {
  top: -5px;
  left: -5px;
  cursor: nwse-resize;
}

.block-resize-overlay .resize-handle.ne {
  top: -5px;
  right: -5px;
  cursor: nesw-resize;
}

.block-resize-overlay .resize-handle.sw {
  bottom: -5px;
  left: -5px;
  cursor: nesw-resize;
}

.block-resize-overlay .resize-handle.se {
  right: -5px;
  bottom: -5px;
  cursor: nwse-resize;
}

.block-resize-overlay .resize-handle.n {
  top: -5px;
  left: calc(50% - 5px);
  cursor: ns-resize;
}

.block-resize-overlay .resize-handle.s {
  bottom: -5px;
  left: calc(50% - 5px);
  cursor: ns-resize;
}

.block-resize-overlay .resize-handle.e {
  top: calc(50% - 5px);
  right: -5px;
  cursor: ew-resize;
}

.block-resize-overlay .resize-handle.w {
  top: calc(50% - 5px);
  left: -5px;
  cursor: ew-resize;
}

.block-resize-overlay.table .resize-handle.e,
.block-resize-overlay.table .resize-handle.w {
  width: 12px;
  height: 34px;
  top: calc(50% - 17px);
  border-radius: 999px;
}

.block-resize-overlay.table .resize-handle.e {
  right: -6px;
}

.block-resize-overlay.table .resize-handle.w {
  left: -6px;
}

.note-actions-float {
  position: absolute;
  z-index: 8;
  display: inline-flex;
  gap: 4px;
}

.note-actions-float button {
  width: 28px;
  height: 28px;
  border: 1px solid #fed7aa;
  background: #fff;
  color: #6b7280;
  padding: 0;
  border-radius: var(--radius);
  display: inline-grid;
  place-items: center;
  box-shadow: 0 8px 20px rgba(0, 0, 0, .12);
}

.note-actions-float .note-action-drag {
  color: #c2410c;
  border-color: #fdba74;
  cursor: grab;
}

.note-actions-float .note-action-drag:active {
  cursor: grabbing;
}

.note-actions-float .note-action-unlink,
.note-actions-float .note-action-copy,
.note-actions-float .note-action-cut {
  color: var(--dikoin-blue);
  border-color: #bfdbfe;
}

.note-actions-float .note-action-delete {
  color: var(--dikoin-red);
  border-color: #fecaca;
}

.note-actions-float .note-action-delete:hover {
  background: var(--dikoin-red-light);
  border-color: var(--dikoin-red);
}

.note-actions-float .note-action-drag:hover {
  background: #fff7ed;
  border-color: var(--dikoin-orange);
}

.note-actions-float .note-action-unlink:hover,
.note-actions-float .note-action-copy:hover,
.note-actions-float .note-action-cut:hover {
  background: var(--dikoin-blue-lighter);
  border-color: var(--dikoin-blue);
}

.note-drag-preview {
  position: fixed;
  z-index: 1000;
  width: min(320px, 42vw);
  max-height: 150px;
  overflow: hidden;
  pointer-events: none;
  opacity: .82;
  transform: rotate(-1deg);
  border: 1px solid #fed7aa;
  border-left: 8px solid var(--dikoin-orange);
  border-radius: var(--radius);
  background: #fff7ed;
  color: #78350f;
  padding: 10px 12px;
  box-shadow: 0 18px 38px rgba(15, 23, 42, .22);
}

.note-drag-preview strong {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
}

.note-drag-preview p {
  margin: 0;
  font-size: 12px;
  line-height: 1.35;
  max-height: 68px;
  overflow: hidden;
}

.note-drag-preview.linked::after {
  content: "";
  position: absolute;
  top: 9px;
  right: 10px;
  width: 14px;
  height: 14px;
  background: #9a3412;
  opacity: .55;
  -webkit-mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='24' height='24' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M9 17H7A5 5 0 0 1 7 7h2'/%3E%3Cpath d='M15 7h2a5 5 0 1 1 0 10h-2'/%3E%3Cline x1='8' x2='16' y1='12' y2='12'/%3E%3C/svg%3E") center / contain no-repeat;
  mask: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='24' height='24' viewBox='0 0 24 24' fill='none' stroke='black' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M9 17H7A5 5 0 0 1 7 7h2'/%3E%3Cpath d='M15 7h2a5 5 0 1 1 0 10h-2'/%3E%3Cline x1='8' x2='16' y1='12' y2='12'/%3E%3C/svg%3E") center / contain no-repeat;
}

.block-drag-preview {
  position: fixed;
  z-index: 1000;
  width: min(300px, 40vw);
  pointer-events: none;
  opacity: .82;
  transform: rotate(-1deg);
  border: 1px solid #bfdbfe;
  border-left: 8px solid var(--dikoin-blue);
  border-radius: var(--radius);
  background: #f8fbfe;
  color: var(--dikoin-blue-dark);
  padding: 10px 12px;
  box-shadow: 0 18px 38px rgba(15, 23, 42, .22);
}

.block-drag-preview strong {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
}

.block-drag-preview p {
  margin: 0;
  color: var(--muted-foreground);
  font-size: 12px;
  line-height: 1.35;
  max-height: 54px;
  overflow: hidden;
}

.block-drag-preview.image {
  border-left-color: #64748b;
}

.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1200;
  background: rgba(15, 23, 42, .35);
  display: grid;
  place-items: center;
  padding: 24px;
}

.modal-card {
  width: min(760px, 100%);
  max-height: min(720px, 90vh);
  overflow: auto;
  background: #fff;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: 0 22px 50px rgba(0, 0, 0, .22);
  padding: 16px;
  display: grid;
  gap: 14px;
}

.modal-card header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.modal-card h3 {
  margin: 0;
  font-size: 16px;
}

.modal-card header button {
  border: 0;
  background: transparent;
  font-size: 24px;
  color: var(--muted-foreground);
}

.table-dialog {
  width: min(380px, 100%);
  background: #fff;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: 0 22px 50px rgba(0, 0, 0, .22);
  display: grid;
  gap: 0;
}

.table-dialog header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
}

.table-dialog h3 {
  margin: 0;
  color: var(--dikoin-blue-dark);
  font-size: 16px;
}

.table-dialog .modal-close {
  border: 0;
  background: transparent;
  color: var(--muted-foreground);
  font-size: 24px;
  line-height: 1;
}

.table-dialog section {
  padding: 16px;
  display: grid;
  gap: 12px;
}

.table-dialog section strong {
  color: var(--foreground);
  font-size: 13px;
}

.table-dialog label {
  display: grid;
  grid-template-columns: 1fr 92px;
  align-items: center;
  gap: 12px;
  color: var(--muted-foreground);
  font-size: 12px;
  font-weight: 600;
}

.table-dialog input[type="number"] {
  width: 92px;
  padding: 7px 8px;
}

.table-dialog .remember-size {
  grid-template-columns: auto 1fr;
  justify-content: start;
  gap: 8px;
  margin-top: 4px;
  color: var(--foreground);
  font-weight: 500;
}

.table-dialog .remember-size input {
  width: 14px;
  height: 14px;
}

.table-dialog footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 12px 16px 16px;
}

.upload-box {
  border: 1px dashed var(--border);
  background: var(--dikoin-blue-lighter);
  color: var(--dikoin-blue);
  padding: 14px;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-weight: 600;
}

.upload-box input {
  display: none;
}

.asset-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 10px;
}

.asset-grid button {
  border: 1px solid var(--border);
  background: #fff;
  padding: 8px;
  display: grid;
  gap: 7px;
  text-align: left;
}

.asset-grid img {
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: contain;
  background: #f8fbfe;
}

.asset-grid span {
  font-size: 11px;
  color: var(--muted-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-list {
  display: grid;
  gap: 8px;
}

.notice-list button {
  border: 1px solid var(--border);
  background: #fff;
  text-align: left;
  padding: 10px;
  display: grid;
  gap: 4px;
}

.notice-list button:hover {
  border-color: var(--dikoin-blue);
  background: var(--dikoin-blue-lighter);
}

.notice-list span {
  color: var(--muted-foreground);
  font-size: 12px;
  line-height: 1.35;
  max-height: 48px;
  overflow: hidden;
}

.notice-list small {
  color: var(--dikoin-blue);
  font-size: 11px;
  font-weight: 700;
}

.fragment-dialog,
.move-dialog {
  width: min(520px, 100%);
}

.selection-warning-dialog {
  width: min(540px, 100%);
}

.selection-warning-dialog header p,
.selection-warning-dialog > p {
  margin: 4px 0 0;
  color: var(--muted-foreground);
  line-height: 1.5;
}

.selection-warning-dialog footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.fragment-dialog header p,
.reusable-dialog header p {
  margin: 4px 0 0;
  color: var(--muted-foreground);
  font-size: 12px;
}

.fragment-dialog label,
.move-dialog label {
  display: grid;
  gap: 6px;
  color: var(--dikoin-blue-dark);
  font-size: 12px;
  font-weight: 600;
}

.editor-shell :deep(img[data-align="inline"]) {
  display: inline-block;
  max-width: 100%;
  margin: 4px 8px 4px 0;
  vertical-align: top;
}

.editor-shell :deep(img[data-align="left"]) {
  display: block;
  margin: 6px auto 6px 0;
}

.editor-shell :deep(img[data-align="center"]) {
  display: block;
  margin: 6px auto;
}

.editor-shell :deep(img[data-align="right"]) {
  display: block;
  margin: 6px 0 6px auto;
}

.editor-shell :deep(table[data-align="center"]),
.editor-shell :deep(.formula-block[data-align="center"]) {
  margin-left: auto;
  margin-right: auto;
}

.editor-shell :deep(table[data-align="right"]),
.editor-shell :deep(.formula-block[data-align="right"]) {
  margin-left: auto;
  margin-right: 0;
}

.editor-shell :deep(table[data-align="left"]),
.editor-shell :deep(.formula-block[data-align="left"]) {
  margin-left: 0;
  margin-right: auto;
}

.table-header-choice {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 4px 4px;
  color: var(--foreground);
  font-size: 11px;
  font-weight: 500;
}

.section-control {
  max-width: 118px;
  height: 26px;
  border: 1px solid rgba(255, 255, 255, .45);
  border-radius: var(--radius);
  background: rgba(255, 255, 255, .12);
  color: #fff;
  padding: 2px 6px;
  font-size: 11px;
}

.status-control {
  min-width: 98px;
  border-color: transparent;
  color: #fff;
  font-weight: 700;
}

.status-control.review {
  background: #f59e0b;
}

.status-control.approved {
  background: #16a34a;
}

.section-control option {
  color: var(--foreground);
  background: #fff;
}

.section-control.visibility {
  max-width: 82px;
}

.bar-save {
  height: 26px;
  border: 1px solid rgba(255, 255, 255, .55);
  border-radius: var(--radius);
  background: #fff;
  color: var(--dikoin-blue-dark);
  padding: 0 8px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  font-weight: 600;
}

.fragment-preview {
  display: grid;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: #f8fbfe;
}

.fragment-preview > strong {
  color: var(--dikoin-blue-dark);
  font-size: 12px;
}

.fragment-preview ol {
  max-height: 240px;
  margin: 0;
  padding-left: 24px;
  overflow: auto;
}

.fragment-preview li {
  padding: 6px 0;
}

.fragment-preview span {
  color: var(--dikoin-blue);
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
}

.fragment-preview p {
  max-height: 44px;
  margin: 3px 0 0;
  overflow: hidden;
  color: var(--muted-foreground);
  font-size: 12px;
  white-space: pre-wrap;
}

.fragment-dialog footer,
.move-dialog footer {
  display: flex;
  justify-content: flex-end;
  gap: 9px;
}

.dialog-message {
  margin: 0;
  padding: 8px 10px;
  background: var(--dikoin-blue-lighter);
  color: var(--dikoin-blue-dark);
  font-size: 12px;
}

.paste-textarea {
  min-height: 150px;
  resize: vertical;
}

.fragment-library {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 10px;
}

.library-subtitle {
  color: var(--dikoin-blue-dark);
  font-size: 12px;
}

.editor-shell :deep(.reusable-section-box) {
  margin: 10px 0;
  padding: 14px 16px;
  border: 1px solid #93c5fd;
  border-left: 5px solid var(--dikoin-blue);
  border-radius: var(--radius);
  background: #eff8ff;
  color: var(--dikoin-blue-dark);
  cursor: grab;
}

.editor-shell.linked :deep(.rich-editor-surface) {
  cursor: not-allowed;
}

.fragment-insert-options {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: end;
  gap: 500px;
}

.fragment-insert-options label {
  display: grid;
  gap: 5px;
  color: var(--dikoin-blue-dark);
  font-size: 11px;
  font-weight: 700;
}

.fragment-insert-options>button {
  height: 35px;
  border: 1px dashed #cbd8e1;
  background: #f8fafc;
  color: #94a3b8;
  padding: 0 10px;
}

.fragment-library>button {
  position: relative;
  min-height: 96px;
  border: 1px solid var(--border);
  background: linear-gradient(145deg, #fff, #f6fafc);
  padding: 12px;
  display: grid;
  align-content: start;
  gap: 6px;
  text-align: left;
}

.fragment-library>button:hover {
  border-color: var(--dikoin-blue);
  box-shadow: 0 8px 18px rgba(14, 127, 187, .1);
}

.fragment-library strong {
  color: var(--dikoin-blue-dark);
}

.fragment-library span {
  color: var(--muted-foreground);
  font-size: 12px;
}

.fragment-library small {
  position: absolute;
  right: 8px;
  bottom: 8px;
  color: var(--dikoin-blue);
  font-weight: 700;
}

.equation-backdrop {
  z-index: 1200;
}

.equation-dialog {
  width: min(980px, 96vw);
  max-height: 92vh;
  overflow: auto;
  background: #fff;
  border: 1px solid #aebdca;
  box-shadow: 0 28px 80px rgba(15, 23, 42, .32);
}

.equation-dialog>header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  border-bottom: 1px solid #d9e3ea;
  background: linear-gradient(110deg, #f7fbfd, #eaf4f9);
}

.equation-dialog h3 {
  margin: 2px 0 0;
  color: #193649;
  font-family: Georgia, serif;
  font-size: 21px;
}

.equation-kicker {
  color: var(--dikoin-blue);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: .12em;
  text-transform: uppercase;
}

.equation-dialog .modal-close {
  border: 0;
  background: transparent;
  color: #607789;
  font-size: 26px;
}

.equation-gallery {
  padding: 14px 18px;
  border-bottom: 1px solid #d9e3ea;
  background: #fbfdfe;
  display: grid;
  gap: 10px;
}

.structure-grid {
  display: grid;
  grid-template-columns: repeat(8, minmax(78px, 1fr));
  gap: 7px;
}

.structure-grid button {
  min-height: 74px;
  border: 1px solid #cbd8e1;
  background: #fff;
  color: #273f50;
  padding: 7px 5px;
  display: grid;
  place-items: center;
  gap: 6px;
}

.structure-grid button:hover {
  border-color: var(--dikoin-blue);
  background: #eef8fd;
}

.structure-grid button span {
  font-family: Cambria, "Times New Roman", serif;
  font-size: 14px;
}

.structure-grid button small {
  color: #607789;
  font-size: 10px;
  font-weight: 600;
}

.greek-row {
  display: flex;
  align-items: center;
  gap: 5px;
}

.greek-row>span {
  margin-right: 6px;
  color: #526b7c;
  font-size: 11px;
  font-weight: 700;
}

.greek-row button {
  width: 32px;
  height: 30px;
  border: 1px solid #cbd8e1;
  background: #fff;
  color: #273f50;
  font-family: Cambria, serif;
  font-size: 17px;
}

.equation-editor-grid {
  padding: 18px;
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(260px, .65fr);
  gap: 14px;
}

.equation-editor-grid label {
  display: grid;
  gap: 6px;
  color: #526b7c;
  font-size: 11px;
  font-weight: 700;
}

.latex-field {
  grid-row: span 2;
}

.latex-field textarea {
  min-height: 144px;
  resize: vertical;
  font-size: 13px;
  line-height: 1.5;
}

.equation-preview {
  min-height: 118px;
  padding: 14px;
  border: 1px solid #cbd8e1;
  background: repeating-linear-gradient(0deg, #fff, #fff 27px, #f2f6f8 28px);
  display: grid;
  align-content: center;
  gap: 9px;
}

.equation-preview>span {
  color: #78909f;
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: .08em;
}

.equation-preview strong {
  font-family: Cambria, "Times New Roman", serif;
  font-size: 21px;
  font-weight: 500;
  overflow-wrap: anywhere;
}

.equation-preview small {
  color: #607789;
}

.number-equation {
  grid-template-columns: auto 1fr !important;
  justify-content: start;
  align-items: center;
}

.equation-dialog>footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  padding: 13px 18px 17px;
  border-top: 1px solid #d9e3ea;
  color: #78909f;
  font-size: 10px;
}

.equation-dialog>footer>div {
  display: flex;
  gap: 9px;
}

@media (max-width: 860px) {
  .structure-grid {
    grid-template-columns: repeat(4, 1fr);
  }

  .equation-editor-grid {
    grid-template-columns: 1fr;
  }

  .selection-actions {
    flex-wrap: wrap;
  }
}
</style>
