<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, toRaw, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ChevronDown, ChevronRight, Copy, Eye, GripVertical, Languages, Link2, PanelLeftClose, PanelLeftOpen, Plus, Save } from '@lucide/vue'
import { createReusableBlock, getReusableBlocks } from '@/api/reusable-blocks.api'
import { getReusableFragments } from '@/api/reusable-fragments.api'
import RichSectionEditor from '@/components/editor/RichSectionEditor.vue'
import AppModal from '@/components/shared/AppModal.vue'
import BackendError from '@/components/shared/BackendError.vue'
import { useManualsStore } from '@/stores/manuals.store'
import type { EditorBlock, EditorSection } from '@/types/editor'
import { randomId, sectionsFromBackend, versionRequestFromEditor } from '@/types/editor'
import type { LanguageCode, ManualBlockResponse, ManualDetailResponse, ManualStatus, ReusableBlockResponse } from '@/types/api'

type BlockSelectionSync = {
  sectionId: string
  active: boolean
  indexes: number[]
  includeParallel: boolean
  sourceLanguage: LanguageCode
}

type IndexHeading = {
  blockId: string
  number: string
  title: string
  level: number
}

type IndexItem = IndexHeading & {
  kind: 'heading' | 'fragment'
}

type IndexDropPosition = 'before' | 'after'

const props = defineProps<{ id: string }>()
const route = useRoute()
const router = useRouter()
const store = useManualsStore()
const sections = ref<EditorSection[]>([])
const selectedSectionId = ref('')
const activeEditorKey = ref('')
const selectedLanguage = ref<LanguageCode>(route.query.lang === 'EN' ? 'EN' : 'ES')
const languageMode = ref<LanguageCode | 'BOTH'>(selectedLanguage.value)
const saved = ref(false)
const saving = ref(false)
const draggingIndex = ref<number | null>(null)
const dropTargetIndex = ref<number | null>(null)
const indexDraggingSectionId = ref('')
const indexDropSectionId = ref('')
const indexDraggingHeading = ref<{ sectionId: string; blockId: string } | null>(null)
const indexDropHeading = ref<{ sectionId: string; blockId: string; position: IndexDropPosition } | null>(null)
const indexLastPreviewKey = ref('')
const indexPanelWidth = ref(280)
const resizingIndexPanel = ref(false)
const sectionsPanelCollapsed = ref(false)
const expandedIndexSectionIds = ref<string[]>([])
const sectionEditorRefs = ref<Array<{ flushEditorSync: () => void }>>([])
const selectionOwnerKey = ref('')
const blockSelectionSync = ref<BlockSelectionSync | null>(null)
const infoMessage = ref('')
const reusableSectionCandidate = ref<EditorSection | null>(null)
const reusableTitle = ref('')
const cloneConfirmOpen = ref(false)
const editorContentVersion = ref(0)
const reusableLibrary = ref<ReusableBlockResponse[]>([])
const reusableLibraryLoading = ref(true)
const editorReady = ref(false)
const hasUnsavedChanges = ref(false)
const localDraftSavedAt = ref('')
const showLeaveEditorModal = ref(false)
const pendingNavigation = ref<any>(null)
const navigationAllowed = ref(false)
let localAutosaveTimer = 0

const manual = computed(() => store.current)
const version = computed(() => manual.value?.activeVersion)
const editorLanguages = computed<LanguageCode[]>(() => languageMode.value === 'BOTH' ? ['ES', 'EN'] : [languageMode.value])
const activeLanguageLabel = computed(() => languageMode.value === 'BOTH' ? 'ES + EN' : languageMode.value)
const indexLanguage = computed<LanguageCode>(() => languageMode.value === 'BOTH' ? selectedLanguage.value : languageMode.value)
const sectionTargets = computed(() => sections.value.map((section) => ({
  id: section.id,
  number: section.sectionNumber,
  title: sectionTitle(section),
})))

onMounted(async () => {
  const [loaded] = await Promise.all([
    store.fetchManual(Number(props.id)),
    loadReusableLibrary(),
  ])
  const localDraft = readLocalDraft()
  sections.value = sectionsFromBackend(localDraft?.activeVersion?.sections || loaded.activeVersion?.sections || [])
  editorReady.value = true
  if (localDraft) {
    hasUnsavedChanges.value = true
    localDraftSavedAt.value = 'Cambios locales recuperados'
  }
})

async function loadReusableLibrary() {
  reusableLibraryLoading.value = true
  try {
    const [sectionItems, fragmentItems] = await Promise.all([
      getReusableBlocks(false, 'SINGLE_BLOCK'),
      getReusableFragments(),
    ])
    reusableLibrary.value = [
      ...sectionItems,
      ...fragmentItems.map((fragment) => ({
        ...fragment,
        reusableType: 'FRAGMENT' as const,
      })),
    ] as ReusableBlockResponse[]
  } finally {
    reusableLibraryLoading.value = false
  }
}

onBeforeUnmount(() => {
  stopIndexPanelResize()
  window.clearTimeout(localAutosaveTimer)
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

onBeforeRouteLeave((to) => {
  if (navigationAllowed.value || !hasUnsavedChanges.value) return true
  flushSectionEditors()
  saveLocalDraft(false)
  pendingNavigation.value = to
  showLeaveEditorModal.value = true
  return false
})

window.addEventListener('beforeunload', handleBeforeUnload)

watch(() => route.query.lang, (lang) => {
  selectedLanguage.value = lang === 'EN' ? 'EN' : 'ES'
  if (languageMode.value !== 'BOTH') {
    languageMode.value = selectedLanguage.value
  }
})

function addSection() {
  const next = sections.value.length + 1
  const isEnglish = languageMode.value === 'EN' || languageMode.value === 'BOTH'
  sections.value.push({
    id: randomId('section'),
    sortOrder: next,
    sectionNumber: String(next),
    level: 1,
    titleEs: 'Nueva sección',
    titleEn: isEnglish ? 'New section' : undefined,
    status: 'APPROVED',
    visible: true,
    collapsed: false,
    blocks: [],
  })
}

function insertReusableSection(payload: { sectionItem: ReusableBlockResponse; cloneSpanishToEnglish: boolean; afterSectionId: string }) {
  flushSectionEditors()
  const anchorIndex = sections.value.findIndex((section) => section.id === payload.afterSectionId)
  const anchor = sections.value[anchorIndex]
  const reusableSection = editorSectionFromReusable(payload.sectionItem, payload.cloneSpanishToEnglish, anchor)
  const insertAt = insertionIndexAfterSection(anchorIndex)
  const copy = [...sections.value]
  copy.splice(insertAt, 0, reusableSection)
  sections.value = renumberSections(copy)
  activateEditor(reusableSection.id, selectedLanguage.value)
  editorContentVersion.value += 1
}

function editorSectionFromReusable(sectionItem: ReusableBlockResponse, cloneSpanishToEnglish: boolean, anchor?: EditorSection): EditorSection {
  const parsed = parseReusableSectionContent(sectionItem)
  const snapshots = Array.isArray(parsed.blocks) ? parsed.blocks : []
  const normalizedBlocks = normalizeReusableBlocks(snapshots)
  const esBlocks = normalizedBlocks.filter((block) => block.languageCode === 'ES')
  const enBlocks = normalizedBlocks.filter((block) => block.languageCode === 'EN')
  const blocks = cloneSpanishToEnglish
    ? [...esBlocks, ...cloneReusableSnapshotsToEnglish(esBlocks)]
    : [...esBlocks, ...enBlocks]
  const loaded = sectionsFromBackend([{
    id: 0,
    sortOrder: 1,
    sectionNumber: '1',
    parentSectionId: anchor?.parentSectionId,
    linkedReusableSectionId: sectionItem.id,
    level: anchor?.level || 1,
    titleEs: String(parsed.titleEs || sectionItem.titleEs || sectionItem.title || 'Seccion reutilizable'),
    titleEn: String(parsed.titleEn || sectionItem.titleEn || (cloneSpanishToEnglish ? parsed.titleEs || sectionItem.titleEs || sectionItem.title : '') || ''),
    completionStatus: 'APPROVED',
    visible: true,
    blocks,
  }])[0]
  return {
    ...loaded,
    id: randomId('section'),
    backendId: undefined,
    parentSectionId: anchor?.parentSectionId,
    linkedReusableSectionId: sectionItem.id,
    level: anchor?.level || 1,
    status: 'APPROVED',
    collapsed: false,
    blocks: loaded.blocks.map((block) => ({
      ...block,
      id: randomId('block'),
      backendId: undefined,
    })),
  }
}

function parseReusableSectionContent(sectionItem: ReusableBlockResponse): Record<string, any> {
  try {
    return JSON.parse(sectionItem.contentJson || '{}')
  } catch {
    return {}
  }
}

function normalizeReusableBlocks(blocks: any[]): ManualBlockResponse[] {
  return blocks.map((block, index) => ({
    id: 0,
    sortOrder: Number(block.sortOrder || index + 1),
    blockType: block.blockType || 'PARAGRAPH',
    languageCode: block.languageCode === 'EN' ? 'EN' : 'ES',
    contentJson: typeof block.contentJson === 'string' ? block.contentJson : JSON.stringify(block.contentJson || {}),
    plainText: block.plainText,
    assetId: typeof block.assetId === 'number' ? block.assetId : undefined,
    reusableBlockId: typeof block.reusableBlockId === 'number' ? block.reusableBlockId : undefined,
    reusableFragmentId: typeof block.reusableFragmentId === 'number' ? block.reusableFragmentId : undefined,
  } as ManualBlockResponse))
}

function cloneReusableSnapshotsToEnglish(blocks: ManualBlockResponse[]): ManualBlockResponse[] {
  return blocks.map((block) => ({
    ...block,
    id: 0,
    languageCode: 'EN',
  }))
}

function insertionIndexAfterSection(anchorIndex: number) {
  if (anchorIndex < 0) return sections.value.length
  const anchor = sections.value[anchorIndex]
  const prefix = `${anchor.sectionNumber || anchor.sortOrder}.`
  let insertAt = anchorIndex + 1
  while (insertAt < sections.value.length && String(sections.value[insertAt].sectionNumber || '').startsWith(prefix)) {
    insertAt++
  }
  return insertAt
}

function editorKey(sectionId: string, language: LanguageCode) {
  return `${sectionId}:${language}`
}

function activateEditor(sectionId: string, language: LanguageCode) {
  selectedSectionId.value = sectionId
  selectedLanguage.value = language
  activeEditorKey.value = editorKey(sectionId, language)
}

function updateSelectionOwner(key: string, active: boolean, indexes: number[] = []) {
  if (active) {
    selectionOwnerKey.value = key
    if (blockSelectionSync.value) {
      blockSelectionSync.value = { ...blockSelectionSync.value, indexes: [...indexes] }
    }
  } else if (selectionOwnerKey.value === key) {
    selectionOwnerKey.value = ''
    if (blockSelectionSync.value) {
      blockSelectionSync.value = { ...blockSelectionSync.value, indexes: [] }
    }
  }
}

function updateBlockSelectionMode(payload: BlockSelectionSync) {
  blockSelectionSync.value = payload.active ? { ...payload, indexes: [...payload.indexes] } : null
  selectionOwnerKey.value = payload.active ? editorKey(payload.sectionId, payload.sourceLanguage) : ''
}

function updateSection(section: EditorSection) {
  sections.value = sections.value.map((item) => item.id === section.id ? section : item)
}

function updateSectionAllLanguages(section: EditorSection) {
  updateSection(section)
  editorContentVersion.value += 1
}

function updateSectionForLanguage(section: EditorSection, language: LanguageCode) {
  sections.value = sections.value.map((item) => {
    if (item.id !== section.id) return item
    return {
      ...item,
      ...section,
      titleEs: language === 'ES' ? section.titleEs : item.titleEs,
      titleEn: language === 'EN' ? section.titleEn : item.titleEn,
      blocks: [
        ...item.blocks.filter((block) => block.languageCode !== language),
        ...section.blocks.filter((block) => block.languageCode === language),
      ],
    }
  })
}

function flushSectionEditors() {
  sectionEditorRefs.value.forEach((editorRef) => editorRef?.flushEditorSync?.())
}

function deleteSection(id: string) {
  const section = sections.value.find((item) => item.id === id)
  const removedBackendIds = new Set<number>()
  if (section?.backendId) removedBackendIds.add(section.backendId)
  let changed = true
  while (changed) {
    changed = false
    sections.value.forEach((item) => {
      if (item.backendId && item.parentSectionId && removedBackendIds.has(item.parentSectionId) && !removedBackendIds.has(item.backendId)) {
        removedBackendIds.add(item.backendId)
        changed = true
      }
    })
  }
  sections.value = renumberSections(sections.value.filter((item) => item.id !== id && (!item.backendId || !removedBackendIds.has(item.backendId))))
  if (selectedSectionId.value === id) {
    selectedSectionId.value = ''
    activeEditorKey.value = ''
  }
}

function addSubsection(parent: EditorSection) {
  if (!parent.backendId) {
    infoMessage.value = 'Guarda primero la sección para poder añadirle subsecciones.'
    return
  }
  const siblings = sections.value.filter((section) => section.parentSectionId === parent.backendId)
  const prefix = `${parent.sectionNumber || parent.sortOrder}.`
  let insertAt = sections.value.findIndex((section) => section.id === parent.id) + 1
  while (insertAt < sections.value.length && String(sections.value[insertAt].sectionNumber || '').startsWith(prefix)) {
    insertAt++
  }
  const subsection: EditorSection = {
    id: randomId('section'),
    sortOrder: siblings.length + 1,
    sectionNumber: `${parent.sectionNumber || parent.sortOrder}.${siblings.length + 1}`,
    parentSectionId: parent.backendId,
    level: (parent.level || 1) + 1,
    titleEs: 'Nueva subsección',
    titleEn: languageMode.value === 'EN' || languageMode.value === 'BOTH' ? 'New subsection' : undefined,
    status: 'APPROVED',
    visible: true,
    collapsed: false,
    blocks: [],
  }
  const copy = [...sections.value]
  copy.splice(insertAt, 0, subsection)
  sections.value = renumberSections(copy)
  activateEditor(subsection.id, selectedLanguage.value)
}

function duplicateSection(index: number) {
  flushSectionEditors()
  const source = sections.value[index]
  if (!source) return
  const duplicated: EditorSection = {
    ...clonePlain(source),
    id: randomId('section'),
    backendId: undefined,
    titleEs: `${source.titleEs} copia`,
    titleEn: source.titleEn ? `${source.titleEn} copy` : undefined,
    collapsed: false,
    blocks: source.blocks.map((block) => ({
      ...block,
      id: randomId('block'),
      backendId: undefined,
    })),
  }
  const copy = [...sections.value]
  copy.splice(index + 1, 0, duplicated)
  sections.value = renumberSections(copy)
  activateEditor(duplicated.id, selectedLanguage.value)
}

async function saveReusable(section: EditorSection) {
  flushSectionEditors()
  const currentSection = sections.value.find((item) => item.id === section.id) || section
  reusableSectionCandidate.value = currentSection
  reusableTitle.value = currentSection.titleEs || currentSection.titleEn || 'Sección reutilizable'
}

async function confirmSaveReusable() {
  const currentSection = reusableSectionCandidate.value
  const title = reusableTitle.value.trim()
  if (!currentSection || !title) return
  const request = versionRequestFromEditor({
    versionNumber: '1',
    status: 'DRAFT',
    active: true,
    esReady: true,
    enReady: Boolean(currentSection.titleEn || currentSection.blocks.some((block) => block.languageCode === 'EN')),
    sections: [currentSection],
  })
  await createReusableBlock({
    title,
    titleEs: title,
    titleEn: currentSection.titleEn,
    reusableType: 'SINGLE_BLOCK',
    productCategory: manual.value?.category || '',
    productCodes: manual.value?.productCode || '',
    contentJson: JSON.stringify({
      type: 'SECTION',
      titleEs: currentSection.titleEs,
      titleEn: currentSection.titleEn,
      blocks: request.sections[0].blocks,
    }),
    active: true,
  })
  saved.value = true
  reusableSectionCandidate.value = null
  setTimeout(() => { saved.value = false }, 2000)
}

function renumberSections(value: EditorSection[]) {
  const byParent = new Map<number | null, EditorSection[]>()
  value.forEach((section) => {
    const key = section.parentSectionId || null
    byParent.set(key, [...(byParent.get(key) || []), section])
  })
  const result: EditorSection[] = []
  const visit = (parentId: number | null, prefix = '', level = 1) => {
    const siblings = byParent.get(parentId) || []
    siblings.forEach((section, index) => {
      const number = prefix ? `${prefix}.${index + 1}` : String(index + 1)
      result.push({ ...section, sortOrder: index + 1, sectionNumber: number, level })
      if (section.backendId) visit(section.backendId, number, level + 1)
    })
  }
  visit(null)
  const included = new Set(result.map((section) => section.id))
  value.filter((section) => !included.has(section.id)).forEach((section) => {
    result.push({
      ...section,
      parentSectionId: undefined,
      level: 1,
      sortOrder: result.length + 1,
      sectionNumber: String(result.length + 1),
    })
  })
  return result
}

function moveSelectedBlocks(payload: { sourceSectionId: string; targetSectionId: string; blocks: EditorBlock[] }) {
  sections.value = sections.value.map((section) => {
    if (section.id !== payload.targetSectionId) return section
    return {
      ...section,
      blocks: [...section.blocks, ...payload.blocks],
    }
  })
}

function dropSection(targetIndex: number) {
  if (draggingIndex.value === null || draggingIndex.value === targetIndex) {
    draggingIndex.value = null
    dropTargetIndex.value = null
    return
  }
  flushSectionEditors()
  const copy = [...sections.value]
  const [item] = copy.splice(draggingIndex.value, 1)
  copy.splice(targetIndex, 0, item)
  sections.value = renumberSections(copy)
  draggingIndex.value = null
  dropTargetIndex.value = null
}

function openSectionFromIndex(section: EditorSection) {
  section.collapsed = false
  selectedSectionId.value = section.id
  activateEditor(section.id, indexLanguage.value)
}

function isIndexSectionExpanded(sectionId: string) {
  return expandedIndexSectionIds.value.includes(sectionId)
}

function toggleIndexSection(sectionId: string) {
  expandedIndexSectionIds.value = isIndexSectionExpanded(sectionId)
    ? expandedIndexSectionIds.value.filter((id) => id !== sectionId)
    : [...expandedIndexSectionIds.value, sectionId]
}

function indexHeadings(section: EditorSection): IndexHeading[] {
  const counters = [0, 0, 0]
  const prefix = section.sectionNumber || String(section.sortOrder)
  return section.blocks
    .filter((block) => block.languageCode === indexLanguage.value && block.type === 'titulo')
    .map((block) => {
      const level = headingBlockLevel(block)
      if (level === 1) {
        counters[0] += 1
        counters[1] = 0
        counters[2] = 0
      } else if (level === 2) {
        if (!counters[0]) counters[0] = 1
        counters[1] += 1
        counters[2] = 0
      } else {
        if (!counters[0]) counters[0] = 1
        if (!counters[1]) counters[1] = 1
        counters[2] += 1
      }
      return {
        blockId: block.id,
        number: `${prefix}.${counters.slice(0, level).join('.')}`,
        title: block.content || 'Título sin texto',
        level,
      }
    })
}

function indexItems(section: EditorSection): IndexItem[] {
  return indexItemsForLanguage(section, indexLanguage.value)
}

function indexItemsForLanguage(section: EditorSection, language: LanguageCode): IndexItem[] {
  const counters = [0, 0, 0]
  const prefix = section.sectionNumber || String(section.sortOrder)
  let lastHeadingLevel = 0
  return section.blocks
    .filter((block) => block.languageCode === language)
    .flatMap((block): IndexItem[] => {
      if (block.type === 'titulo') {
        const level = headingBlockLevel(block)
        lastHeadingLevel = level
        if (level === 1) {
          counters[0] += 1
          counters[1] = 0
          counters[2] = 0
        } else if (level === 2) {
          if (!counters[0]) counters[0] = 1
          counters[1] += 1
          counters[2] = 0
        } else {
          if (!counters[0]) counters[0] = 1
          if (!counters[1]) counters[1] = 1
          counters[2] += 1
        }
        return [{
          kind: 'heading' as const,
          blockId: block.id,
          number: `${prefix}.${counters.slice(0, level).join('.')}`,
          title: block.content || 'Titulo sin texto',
          level,
        }]
      }
      if (block.type !== 'fragmento-ref' || !Number(block.data?.reusableFragmentId || block.content || 0)) {
        return []
      }
      const fragmentHeadings = reusableFragmentHeadings(block, language)
      const fragmentNumberedHeadings: Array<{ level: number; counters: number[] }> = []
      fragmentHeadings.forEach((heading) => {
        const level = heading.level
        lastHeadingLevel = level
        incrementIndexCounters(counters, level)
        fragmentNumberedHeadings.push({ level, counters: [...counters] })
      })
      const firstHeading = fragmentNumberedHeadings[0]
      const level = firstHeading?.level || Math.min(3, Math.max(1, lastHeadingLevel + 1))
      const number = firstHeading
        ? `${prefix}.${firstHeading.counters.slice(0, level).join('.')}`
        : 'Frag.'
      return [{
        kind: 'fragment' as const,
        blockId: block.id,
        number,
        title: String(block.data?.title || block.data?.code || 'Fragmento vinculado'),
        level,
      }]
    })
}

function incrementIndexCounters(counters: number[], level: number) {
  if (level === 1) {
    counters[0] += 1
    counters[1] = 0
    counters[2] = 0
  } else if (level === 2) {
    if (!counters[0]) counters[0] = 1
    counters[1] += 1
    counters[2] = 0
  } else {
    if (!counters[0]) counters[0] = 1
    if (!counters[1]) counters[1] = 1
    counters[2] += 1
  }
}

function reusableFragmentHeadings(block: EditorBlock, language: LanguageCode): Array<{ level: number }> {
  const fragmentId = Number(block.data?.reusableFragmentId || block.content || 0)
  const fragment = reusableLibrary.value.find((item) => item.reusableType === 'FRAGMENT' && item.id === fragmentId)
  if (!fragment?.contentJson) return []
  try {
    const parsed = JSON.parse(fragment.contentJson)
    const snapshots = Array.isArray(parsed.blocks) ? parsed.blocks : []
    return snapshots
      .filter((snapshot: Record<string, unknown>) => (!snapshot.languageCode || snapshot.languageCode === language) && snapshot.blockType === 'HEADING')
      .map((snapshot: Record<string, unknown>) => {
        const contentJson = typeof snapshot.contentJson === 'string'
          ? snapshot.contentJson
          : JSON.stringify(snapshot.contentJson || {})
        const parsedContent = JSON.parse(contentJson)
        return {
          level: Math.min(3, Math.max(1, Number(parsedContent.level || 1))),
        }
      })
  } catch {
    return []
  }
}

function headingBlockLevel(block: EditorBlock) {
  return Math.min(3, Math.max(1, Number(block.data?.level || 1)))
}

function startIndexHeadingDrag(sectionId: string, blockId: string) {
  flushSectionEditors()
  indexDraggingHeading.value = { sectionId, blockId }
  indexDropHeading.value = null
}

function dropIndexHeading(sectionId: string, targetBlockId: string) {
  const source = indexDraggingHeading.value
  if (!source) {
    clearIndexDragState()
    return
  }
  if (source.sectionId !== sectionId) {
    infoMessage.value = 'Los títulos solo se pueden reordenar dentro de su sección.'
    clearIndexDragState()
    return
  }
  const targetSection = sections.value.find((section) => section.id === sectionId)
  if (!targetSection) {
    clearIndexDragState()
    return
  }
  const headings = indexHeadings(targetSection)
  const sourceHeading = headings.find((heading) => heading.blockId === source.blockId)
  const targetHeading = headings.find((heading) => heading.blockId === targetBlockId)
  if (sourceHeading && targetHeading && sourceHeading.level !== targetHeading.level) {
    infoMessage.value = 'Solo se pueden reordenar títulos del mismo nivel.'
    clearIndexDragState()
    return
  }
  sections.value = sections.value.map((section) => {
    if (section.id !== sectionId) return section
    return reorderHeadingSegments(section, source.blockId, targetBlockId)
  })
  editorContentVersion.value += 1
  clearIndexDragState()
}

function startIndexItemDrag(sectionId: string, item: IndexItem) {
  flushSectionEditors()
  indexDraggingHeading.value = { sectionId, blockId: item.blockId }
  indexDropHeading.value = null
  indexLastPreviewKey.value = ''
}

function updateIndexItemDrop(sectionId: string, targetItem: IndexItem, event: DragEvent) {
  const source = indexDraggingHeading.value
  if (!source || source.sectionId !== sectionId || source.blockId === targetItem.blockId) return
  const position = indexDropPosition(event)
  indexDropHeading.value = { sectionId, blockId: targetItem.blockId, position }
  const previewKey = `${sectionId}:${source.blockId}:${targetItem.blockId}:${position}`
  if (indexLastPreviewKey.value === previewKey) return
  const section = sections.value.find((item) => item.id === sectionId)
  if (!section || !canReorderIndexItems(section, source.blockId, targetItem.blockId)) return
  indexLastPreviewKey.value = previewKey
  sections.value = sections.value.map((section) => {
    if (section.id !== sectionId) return section
    return reorderIndexItems(section, source.blockId, targetItem.blockId, position)
  })
  editorContentVersion.value += 1
}

function indexDropPosition(event: DragEvent): IndexDropPosition {
  const target = event.currentTarget instanceof HTMLElement ? event.currentTarget : null
  if (!target) return 'before'
  const rect = target.getBoundingClientRect()
  return event.clientY > rect.top + rect.height / 2 ? 'after' : 'before'
}

function dropIndexItem() {
  clearIndexDragState()
}

function canReorderIndexItems(section: EditorSection, sourceBlockId: string, targetBlockId: string) {
  const items = indexItemsForLanguage(section, indexLanguage.value)
  const sourceItem = items.find((item) => item.blockId === sourceBlockId)
  const targetItem = items.find((item) => item.blockId === targetBlockId)
  if (!sourceItem || !targetItem) return false
  if (sourceItem.kind === 'heading' && targetItem.kind === 'heading' && sourceItem.level !== targetItem.level) {
    infoMessage.value = 'Solo se pueden reordenar titulos del mismo nivel.'
    return false
  }
  return true
}

function reorderHeadingSegments(section: EditorSection, sourceBlockId: string, targetBlockId: string): EditorSection {
  const sourceHeadings = indexHeadings(section)
  const sourceOrdinal = sourceHeadings.findIndex((heading) => heading.blockId === sourceBlockId)
  const targetOrdinal = sourceHeadings.findIndex((heading) => heading.blockId === targetBlockId)
  if (sourceOrdinal < 0 || targetOrdinal < 0 || sourceOrdinal === targetOrdinal) return section

  const nextBlocks = reorderHeadingSegmentForLanguage(section.blocks, indexLanguage.value, sourceOrdinal, targetOrdinal)
  const parallelLanguage: LanguageCode = indexLanguage.value === 'ES' ? 'EN' : 'ES'
  const sourceLanguageHeadings = headingsForLanguage(section, indexLanguage.value)
  const parallelHeadings = headingsForLanguage(section, parallelLanguage)
  const canMoveParallel = parallelHeadings.length === sourceLanguageHeadings.length
    && parallelHeadings[sourceOrdinal]?.level === sourceLanguageHeadings[sourceOrdinal]?.level
    && parallelHeadings[targetOrdinal]?.level === sourceLanguageHeadings[targetOrdinal]?.level
  const withParallel = canMoveParallel
    ? reorderHeadingSegmentForLanguage(nextBlocks, parallelLanguage, sourceOrdinal, targetOrdinal)
    : nextBlocks
  if (!canMoveParallel && parallelHeadings.length) {
    infoMessage.value = 'El idioma paralelo no coincide en número/nivel de títulos; se reordenó solo el idioma activo.'
  }
  return { ...section, blocks: withParallel }
}

function reorderIndexItems(section: EditorSection, sourceBlockId: string, targetBlockId: string, position: IndexDropPosition): EditorSection {
  const sourceItems = indexItemsForLanguage(section, indexLanguage.value)
  const sourceOrdinal = sourceItems.findIndex((item) => item.blockId === sourceBlockId)
  const targetOrdinal = sourceItems.findIndex((item) => item.blockId === targetBlockId)
  if (sourceOrdinal < 0 || targetOrdinal < 0 || sourceOrdinal === targetOrdinal) return section
  const activeBlocks = reorderIndexItemSegmentForLanguage(section.blocks, indexLanguage.value, sourceOrdinal, targetOrdinal, position)
  const parallelLanguage: LanguageCode = indexLanguage.value === 'ES' ? 'EN' : 'ES'
  const parallelItems = indexItemsForLanguage({ ...section, blocks: activeBlocks }, parallelLanguage)
  const canMoveParallel = Boolean(
    parallelItems[sourceOrdinal]
    && parallelItems[targetOrdinal]
    && parallelItems[sourceOrdinal].kind === sourceItems[sourceOrdinal].kind
    && parallelItems[targetOrdinal].kind === sourceItems[targetOrdinal].kind,
  )
  return {
    ...section,
    blocks: canMoveParallel
      ? reorderIndexItemSegmentForLanguage(activeBlocks, parallelLanguage, sourceOrdinal, targetOrdinal, position)
      : activeBlocks,
  }
}

function reorderIndexItemSegmentForLanguage(blocks: EditorBlock[], language: LanguageCode, sourceOrdinal: number, targetOrdinal: number, position: IndexDropPosition) {
  const languageBlocks = blocks.filter((block) => block.languageCode === language)
  const reorderedLanguageBlocks = reorderLanguageIndexItemSegment(languageBlocks, sourceOrdinal, targetOrdinal, position)
  let nextIndex = 0
  return blocks.map((block) => {
    if (block.languageCode !== language) return block
    return reorderedLanguageBlocks[nextIndex++] || block
  })
}

function reorderLanguageIndexItemSegment(blocks: EditorBlock[], sourceOrdinal: number, targetOrdinal: number, position: IndexDropPosition) {
  const ranges = indexItemRanges(blocks)
  const source = ranges[sourceOrdinal]
  const target = ranges[targetOrdinal]
  if (!source || !target) return blocks
  if (target.start >= source.start && target.start < source.end) return blocks
  const segment = blocks.slice(source.start, source.end)
  const withoutSegment = [...blocks.slice(0, source.start), ...blocks.slice(source.end)]
  let insertIndex = position === 'after' ? target.end : target.start
  if (source.start < insertIndex) {
    insertIndex -= segment.length
  }
  return [
    ...withoutSegment.slice(0, insertIndex),
    ...segment,
    ...withoutSegment.slice(insertIndex),
  ]
}

function indexItemRanges(blocks: EditorBlock[]) {
  const items = blocks
    .map((block, index) => ({ block, index }))
    .filter(({ block }) => block.type === 'titulo' || (block.type === 'fragmento-ref' && Number(block.data?.reusableFragmentId || block.content || 0)))
  return items.map(({ block, index }) => {
    if (block.type === 'fragmento-ref') {
      return { kind: 'fragment' as const, start: index, end: index + 1 }
    }
    const sourceLevel = headingBlockLevel(block)
    const end = items.find(({ block: nextBlock, index: nextIndex }) => {
      return nextIndex > index && nextBlock.type === 'titulo' && headingBlockLevel(nextBlock) <= sourceLevel
    })?.index ?? blocks.length
    return { kind: 'heading' as const, start: index, end }
  })
}

function headingsForLanguage(section: EditorSection, language: LanguageCode) {
  return section.blocks
    .filter((block) => block.languageCode === language && block.type === 'titulo')
    .map((block) => ({ blockId: block.id, level: headingBlockLevel(block) }))
}

function reorderHeadingSegmentForLanguage(blocks: EditorBlock[], language: LanguageCode, sourceOrdinal: number, targetOrdinal: number) {
  const languageBlocks = blocks.filter((block) => block.languageCode === language)
  const reorderedLanguageBlocks = reorderLanguageHeadingSegment(languageBlocks, sourceOrdinal, targetOrdinal)
  let nextIndex = 0
  const merged = blocks.map((block) => {
    if (block.languageCode !== language) return block
    return reorderedLanguageBlocks[nextIndex++] || block
  })
  return merged
}

function reorderLanguageHeadingSegment(blocks: EditorBlock[], sourceOrdinal: number, targetOrdinal: number) {
  const headings = blocks
    .map((block, index) => ({ block, index }))
    .filter(({ block }) => block.type === 'titulo')
  const source = headings[sourceOrdinal]
  const target = headings[targetOrdinal]
  if (!source || !target) return blocks
  const sourceLevel = headingBlockLevel(source.block)
  const segmentEnd = headings.find(({ block, index }) => index > source.index && headingBlockLevel(block) <= sourceLevel)?.index ?? blocks.length
  if (target.index > source.index && target.index < segmentEnd) return blocks
  const segment = blocks.slice(source.index, segmentEnd)
  const withoutSegment = [...blocks.slice(0, source.index), ...blocks.slice(segmentEnd)]
  const targetIndex = source.index < target.index ? target.index - segment.length : target.index
  return [
    ...withoutSegment.slice(0, targetIndex),
    ...segment,
    ...withoutSegment.slice(targetIndex),
  ]
}

function startIndexSectionDrag(section: EditorSection) {
  flushSectionEditors()
  indexDraggingSectionId.value = section.id
  indexDropSectionId.value = ''
}

function dropIndexSection(targetSectionId: string) {
  const sourceSectionId = indexDraggingSectionId.value
  if (!sourceSectionId || sourceSectionId === targetSectionId) {
    clearIndexDragState()
    return
  }
  const sourceIndex = sections.value.findIndex((section) => section.id === sourceSectionId)
  const targetIndex = sections.value.findIndex((section) => section.id === targetSectionId)
  if (sourceIndex < 0 || targetIndex < 0) {
    clearIndexDragState()
    return
  }
  if ((sections.value[sourceIndex].parentSectionId || null) !== (sections.value[targetIndex].parentSectionId || null)) {
    infoMessage.value = 'Solo se pueden reordenar títulos dentro del mismo nivel.'
    clearIndexDragState()
    return
  }
  const copy = [...sections.value]
  const [item] = copy.splice(sourceIndex, 1)
  copy.splice(targetIndex, 0, item)
  sections.value = renumberSections(copy)
  clearIndexDragState()
}

function clearIndexDragState() {
  indexDraggingSectionId.value = ''
  indexDropSectionId.value = ''
  indexDraggingHeading.value = null
  indexDropHeading.value = null
  indexLastPreviewKey.value = ''
}

function startIndexPanelResize(event: PointerEvent) {
  resizingIndexPanel.value = true
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
  onIndexPanelResize(event)
  window.addEventListener('pointermove', onIndexPanelResize)
  window.addEventListener('pointerup', stopIndexPanelResize, { once: true })
}

function onIndexPanelResize(event: PointerEvent) {
  if (!resizingIndexPanel.value) return
  const minWidth = 240
  const maxWidth = 520
  const nextWidth = Math.min(maxWidth, Math.max(minWidth, event.clientX - 14))
  indexPanelWidth.value = nextWidth
}

function stopIndexPanelResize() {
  if (!resizingIndexPanel.value) return
  resizingIndexPanel.value = false
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
  window.removeEventListener('pointermove', onIndexPanelResize)
}

function buildVersionRequest(status: ManualStatus, changeNotes: string) {
  if (!version.value) return null
  const manualStatus = sections.value.some((section) => section.status === 'REVIEW') ? 'REVIEW' : status
  return versionRequestFromEditor({
    versionNumber: version.value.versionNumber,
    status: manualStatus,
    active: true,
    esReady: version.value.esReady || hasLanguageBlocks('ES'),
    enReady: hasLanguageBlocks('EN'),
    changeNotes,
    sections: sections.value,
  })
}

function hasLanguageBlocks(language: LanguageCode) {
  return sections.value.some((section) => section.blocks.some((block) => block.languageCode === language))
}

function localDraftKey() {
  return `dikoin-manual-editor-draft:${props.id}`
}

function createLocalDraftManual(): ManualDetailResponse | null {
  if (!manual.value || !version.value) return null
  const request = buildVersionRequest(version.value.status, 'Autoguardado local')
  if (!request) return null
  const activeVersion = {
    ...version.value,
    status: request.status,
    active: request.active,
    esReady: request.esReady,
    enReady: request.enReady,
    changeNotes: request.changeNotes,
    sections: request.sections.map((section, sectionIndex) => ({
      id: section.id || 0,
      sortOrder: section.sortOrder,
      sectionNumber: section.sectionNumber,
      parentSectionId: section.parentSectionId,
      linkedReusableSectionId: section.linkedReusableSectionId,
      level: section.level || 1,
      titleEs: section.titleEs,
      titleEn: section.titleEn,
      completionStatus: section.completionStatus,
      visible: section.visible !== false,
      blocks: section.blocks.map((block, blockIndex) => ({
        id: block.id || 0,
        sortOrder: block.sortOrder || blockIndex + 1,
        blockType: block.blockType,
        languageCode: block.languageCode,
        contentJson: block.contentJson,
        plainText: block.plainText,
        assetId: block.assetId,
        reusableBlockId: block.reusableBlockId,
        reusableFragmentId: block.reusableFragmentId,
      })),
    })),
  }
  return {
    ...manual.value,
    activeVersion,
    versions: manual.value.versions.map((item) => item.id === activeVersion.id ? activeVersion : item),
  }
}

function saveLocalDraft(flushEditors = true) {
  if (flushEditors) flushSectionEditors()
  const draft = createLocalDraftManual()
  if (!draft) return false
  localStorage.setItem(localDraftKey(), JSON.stringify({
    manualId: draft.id,
    savedAt: new Date().toISOString(),
    manual: draft,
  }))
  localDraftSavedAt.value = 'Autoguardado local'
  return true
}

function readLocalDraft(): ManualDetailResponse | null {
  try {
    const parsed = JSON.parse(localStorage.getItem(localDraftKey()) || 'null')
    return parsed?.manual?.id === Number(props.id) ? parsed.manual : null
  } catch {
    return null
  }
}

function clearLocalDraft() {
  localStorage.removeItem(localDraftKey())
  localDraftSavedAt.value = ''
}

function markLocalChange() {
  if (!editorReady.value) return
  hasUnsavedChanges.value = true
  window.clearTimeout(localAutosaveTimer)
  localAutosaveTimer = window.setTimeout(() => {
    saveLocalDraft(false)
  }, 600)
}

function handleBeforeUnload(event: BeforeUnloadEvent) {
  if (!hasUnsavedChanges.value) return
  flushSectionEditors()
  saveLocalDraft(false)
  event.preventDefault()
  event.returnValue = ''
}

watch(sections, () => {
  markLocalChange()
}, { deep: true })

async function saveDraft(changeNotes = 'Borrador autoguardado desde editor', flushEditors = true) {
  if (!manual.value) return false
  if (flushEditors) {
    flushSectionEditors()
  }
  return saveLocalDraft(false)
}

async function save() {
  if (!manual.value || !version.value) return
  saving.value = true
  try {
    flushSectionEditors()
    const request = buildVersionRequest(version.value.status, 'Guardado desde editor Vue')
    if (!request) return
    await store.saveVersion(manual.value.id, request)
    hasUnsavedChanges.value = false
    clearLocalDraft()
    saved.value = true
    setTimeout(() => { saved.value = false }, 2000)
  } finally {
    saving.value = false
  }
}

async function saveDraftForPreview() {
  if (!manual.value || !version.value) return
  flushSectionEditors()
  saveLocalDraft(false)
  navigationAllowed.value = true
  router.push({ name: 'manual-detail', params: { id: manual.value.id }, query: { lang: selectedLanguage.value, preview: 'local' } })
}

async function changeLanguage(lang: LanguageCode) {
  if ((lang === selectedLanguage.value && languageMode.value === lang) || saving.value) return
  saving.value = true
  try {
    const savedDraft = await saveDraft(`Autoguardado local al cambiar a ${lang}`)
    if (!savedDraft) return
    selectedSectionId.value = ''
    activeEditorKey.value = ''
    selectedLanguage.value = lang
    languageMode.value = lang
    saved.value = true
    router.replace({ name: 'manual-editor', params: { id: props.id }, query: { lang } })
    setTimeout(() => { saved.value = false }, 2000)
  } finally {
    saving.value = false
  }
}

async function showBothLanguages() {
  if (languageMode.value === 'BOTH' || saving.value) return
  saving.value = true
  try {
    const savedDraft = await saveDraft('Autoguardado local al mostrar ambos idiomas')
    if (!savedDraft) return
    selectedSectionId.value = ''
    activeEditorKey.value = ''
    languageMode.value = 'BOTH'
    saved.value = true
    setTimeout(() => { saved.value = false }, 2000)
  } finally {
    saving.value = false
  }
}

async function cloneSpanishToEnglish(force = false) {
  if (saving.value) return
  flushSectionEditors()
  const hasSpanish = sections.value.some((section) => section.blocks.some((block) => block.languageCode === 'ES'))
  if (!hasSpanish) {
    infoMessage.value = 'No hay contenido en español para clonar.'
    return
  }
  const hasEnglish = sections.value.some((section) => section.blocks.some((block) => block.languageCode === 'EN'))
  if (hasEnglish && !force) {
    cloneConfirmOpen.value = true
    return
  }
  cloneConfirmOpen.value = false
  sections.value = sections.value.map((section) => ({
    ...section,
    titleEn: section.titleEs,
    blocks: [
      ...section.blocks.filter((block) => block.languageCode !== 'EN'),
      ...section.blocks
        .filter((block) => block.languageCode === 'ES')
        .map(cloneSpanishBlockToEnglish),
    ],
  }))
  editorContentVersion.value += 1
  saving.value = true
  try {
    await saveDraft('Version inglesa clonada desde el contenido espanol', false)
    editorContentVersion.value += 1
    selectedLanguage.value = 'EN'
    languageMode.value = 'EN'
    saved.value = true
    window.setTimeout(() => { saved.value = false }, 2000)
  } finally {
    saving.value = false
  }
}

function cloneSpanishBlockToEnglish(block: EditorBlock): EditorBlock {
  const id = randomId('block')
  const cloned = clonePlain(block)
  return {
    ...cloned,
    id,
    backendId: undefined,
    languageCode: 'EN',
    data: cloneBlockDataForNewBlock(cloned.data, id),
  }
}

function cloneBlockDataForNewBlock(data: EditorBlock['data'], blockId: string): EditorBlock['data'] {
  if (!data) return undefined
  const cloned = clonePlain(data)
  if (cloned.json && typeof cloned.json === 'object') {
    cloned.json = cloneJsonNodeForNewBlock(cloned.json as Record<string, unknown>, blockId, true)
  }
  return cloned
}

function clonePlain<T>(value: T): T {
  return JSON.parse(JSON.stringify(toRaw(value))) as T
}

function cloneJsonNodeForNewBlock(node: Record<string, unknown>, blockId: string, root = false): Record<string, unknown> {
  const cloned: Record<string, unknown> = { ...node }
  if (cloned.attrs && typeof cloned.attrs === 'object' && !Array.isArray(cloned.attrs)) {
    const attrs = { ...(cloned.attrs as Record<string, unknown>) }
    if (root || 'blockId' in attrs) attrs.blockId = root ? blockId : randomId('block')
    if (root || 'backendId' in attrs) attrs.backendId = null
    cloned.attrs = attrs
  }
  if (Array.isArray(cloned.content)) {
    cloned.content = cloned.content.map((child) => {
      if (!child || typeof child !== 'object' || Array.isArray(child)) return child
      return cloneJsonNodeForNewBlock(child as Record<string, unknown>, blockId)
    })
  }
  return cloned
}

async function saveAndContinueNavigation() {
  await save()
  if (!pendingNavigation.value || hasUnsavedChanges.value) return
  navigationAllowed.value = true
  showLeaveEditorModal.value = false
  router.push(pendingNavigation.value)
  pendingNavigation.value = null
}

function discardChangesAndContinueNavigation() {
  clearLocalDraft()
  hasUnsavedChanges.value = false
  navigationAllowed.value = true
  showLeaveEditorModal.value = false
  const target = pendingNavigation.value
  pendingNavigation.value = null
  if (target) {
    router.push(target)
  }
}
function sectionTitle(section: EditorSection) {
  if (languageMode.value === 'BOTH') {
    return [section.titleEs, section.titleEn].filter(Boolean).join(' / ') || 'Sin titulo'
  }
  const activeTitle = selectedLanguage.value === 'EN' ? section.titleEn : section.titleEs
  return activeTitle || section.titleEs || section.titleEn || 'Sin título'
}
</script>

<template>
  <section class="editor-page">
    <BackendError :message="store.error" />
    <div class="editor-top">
      <button class="btn btn-outline" @click="router.push({ name: 'manual-detail', params: { id }, query: { lang: selectedLanguage } })"><ArrowLeft :size="14" /> Volver</button>
      <div class="editor-title">
        <h1>{{ manual?.code || 'Editor' }}</h1>
        <span>{{ manual?.title }}</span>
      </div>
      <div class="lang-switch">
        <button :class="{ active: languageMode === 'ES' }" :disabled="saving" @click="changeLanguage('ES')">ES</button>
        <button :class="{ active: languageMode === 'EN' }" :disabled="saving" @click="changeLanguage('EN')">EN</button>
        <button :class="{ active: languageMode === 'BOTH' }" :disabled="saving" @click="showBothLanguages"><Languages :size="13" /> Ambos idiomas</button>
      </div>
      <button v-if="languageMode === 'EN'" class="btn btn-outline" :disabled="saving" @click="cloneSpanishToEnglish()">
        <Copy :size="14" /> Clonar del Español
      </button>
      <dl class="top-properties">
        <div><dt>Versión</dt><dd>v{{ version?.versionNumber }}</dd></div>
        <div><dt>Estado</dt><dd>{{ version?.status }}</dd></div>
        <div><dt>Idioma</dt><dd>{{ activeLanguageLabel }}</dd></div>
        <div><dt>Secciones</dt><dd>{{ sections.length }}</dd></div>
      </dl>
      <span v-if="saved" class="saved">Guardado</span>
      <span v-else-if="localDraftSavedAt" class="saved local">{{ localDraftSavedAt }}</span>
      <button class="btn btn-outline" :disabled="saving" @click="saveDraftForPreview"><Eye :size="14" /> Vista previa</button>
      <button class="btn btn-primary" :disabled="saving" @click="save"><Save :size="14" /> {{ saving ? 'Guardando...' : 'Guardar' }}</button>
      <div id="manual-editor-toolbar" class="editor-toolbar-dock">
        <span v-if="!activeEditorKey" class="toolbar-empty">Selecciona una seccion para mostrar la cinta de herramientas.</span>
      </div>
    </div>

    <div
      class="editor-grid"
      :class="{ 'sections-collapsed': sectionsPanelCollapsed, 'both-languages': languageMode === 'BOTH', 'resizing-index': resizingIndexPanel }"
      :style="{ '--index-panel-width': `${indexPanelWidth}px` }"
    >
      <button
        type="button"
        class="sections-toggle"
        :aria-label="sectionsPanelCollapsed ? 'Mostrar secciones' : 'Ocultar secciones'"
        :title="sectionsPanelCollapsed ? 'Mostrar secciones' : 'Ocultar secciones'"
        @click="sectionsPanelCollapsed = !sectionsPanelCollapsed"
      >
        <PanelLeftOpen v-if="sectionsPanelCollapsed" :size="16" />
        <PanelLeftClose v-else :size="16" />
      </button>
      <aside class="index-panel card" :aria-hidden="sectionsPanelCollapsed">
        <div class="index-resize-handle" title="Redimensionar panel" @pointerdown.prevent="startIndexPanelResize"></div>
        <div class="index-head">
          <div>
            <h2>Contenido</h2>
            <span>{{ indexLanguage }}</span>
          </div>
        </div>
        <button class="btn btn-primary" @click="addSection"><Plus :size="14" /> Añadir sección</button>
        <div class="index-card-list">
          <article
            v-for="section in sections"
            :key="section.id"
            class="index-section-card"
            :class="{
              selected: selectedSectionId === section.id,
              collapsed: !isIndexSectionExpanded(section.id),
              dragging: indexDraggingSectionId === section.id,
              'drop-target': indexDropSectionId === section.id && indexDraggingSectionId !== section.id,
            }"
            :style="{ '--level-indent': `${Math.max(0, (section.level || 1) - 1) * 12}px` }"
            draggable="true"
            @dragstart="startIndexSectionDrag(section)"
            @dragend="clearIndexDragState"
            @dragover.prevent="indexDropSectionId = section.id"
            @drop.prevent="dropIndexSection(section.id)"
          >
            <header class="index-section-header" @click="openSectionFromIndex(section)">
              <button
                type="button"
                class="index-section-collapse"
                :aria-label="isIndexSectionExpanded(section.id) ? 'Contraer seccion' : 'Expandir seccion'"
                :title="isIndexSectionExpanded(section.id) ? 'Contraer' : 'Expandir'"
                @click.stop="toggleIndexSection(section.id)"
              >
                <ChevronDown v-if="isIndexSectionExpanded(section.id)" :size="15" />
                <ChevronRight v-else :size="15" />
              </button>
              <GripVertical class="index-drag-icon" :size="15" />
              <span class="index-section-number">{{ section.sectionNumber || section.sortOrder }}</span>
              <strong>{{ sectionTitle(section) }}</strong>
              <small>Nivel {{ section.level || 1 }}</small>
            </header>
            <div v-if="isIndexSectionExpanded(section.id) && indexItems(section).length" class="index-heading-list">
              <button
                v-for="heading in indexItems(section)"
                :key="`${heading.kind}-${heading.blockId}`"
                type="button"
                class="index-heading-card"
                :class="{
                  fragment: heading.kind === 'fragment',
                  dragging: indexDraggingHeading?.blockId === heading.blockId,
                  'drop-target': indexDropHeading?.blockId === heading.blockId,
                  'drop-before': indexDropHeading?.blockId === heading.blockId && indexDropHeading?.position === 'before',
                  'drop-after': indexDropHeading?.blockId === heading.blockId && indexDropHeading?.position === 'after',
                }"
                :style="{ '--heading-indent': `${Math.max(0, heading.level - 1) * 16}px` }"
                draggable="true"
                @click="openSectionFromIndex(section)"
                @dragstart.stop="startIndexItemDrag(section.id, heading)"
                @dragend="clearIndexDragState"
                @dragover.prevent.stop="updateIndexItemDrop(section.id, heading, $event)"
                @drop.stop.prevent="dropIndexItem"
              >
                <GripVertical v-if="heading.kind === 'heading'" :size="12" />
                <Link2 v-else :size="12" />
                <span>{{ heading.number }}</span>
                <strong>{{ heading.title }}</strong>
              </button>
            </div>
          </article>
        </div>
      </aside>

      <main class="cards-panel">
        <div v-if="store.loading">Cargando contenido...</div>
        <div
          v-for="(section, index) in sections"
          :key="section.id"
          class="section-editor-row"
          :class="{ dragging: draggingIndex === index, 'drop-target': dropTargetIndex === index && draggingIndex !== index }"
          @dragstart="draggingIndex = index"
          @dragend="draggingIndex = null; dropTargetIndex = null"
          @dragover.prevent="dropTargetIndex = index"
          @drop="dropSection(index)"
        >
          <div v-for="lang in editorLanguages" :key="`${section.id}-${lang}`" class="language-editor-column">
            <span v-if="languageMode === 'BOTH'" class="language-column-label">{{ lang }}</span>
            <RichSectionEditor
              ref="sectionEditorRefs"
              :section="section"
              :selected="selectedSectionId === section.id"
              :active-toolbar="activeEditorKey === editorKey(section.id, lang)"
              :language="lang"
              :refresh-key="editorContentVersion"
              :manual-id="manual?.id"
              :section-targets="sectionTargets"
              :selection-sync="blockSelectionSync"
              :reusable-library="reusableLibrary"
              :reusable-library-loading="reusableLibraryLoading"
              @update="updateSectionForLanguage($event, lang)"
              @update-all-languages="updateSectionAllLanguages"
              @refresh-reusable-library="loadReusableLibrary"
              @insert-reusable-section="insertReusableSection"
              @delete="deleteSection(section.id)"
              @duplicate="duplicateSection(index)"
              @add-subsection="addSubsection(section)"
              @save-reusable="saveReusable(section)"
              @move-blocks="moveSelectedBlocks"
              @selection-change="updateSelectionOwner"
              @selection-mode-change="updateBlockSelectionMode"
              @save-section="save"
              @select="selectedSectionId = $event"
              @activate="activateEditor"
            />
          </div>
        </div>
        <button class="add-section" @click="addSection"><Plus :size="15" /> Añadir sección al final</button>
      </main>

      <aside v-if="false" class="props-panel card">
        <h2>Propiedades</h2>
        <p class="text-muted">Selecciona un bloque o sección para editar su tipo y contenido. Puedes arrastrar las secciones para reordenarlas. Los cambios se guardan automáticamente al cambiar de sección o idioma.</p>
        <dl>
          <dt>Versión</dt><dd>v{{ version?.versionNumber }}</dd>
          <dt>Estado</dt><dd>{{ version?.status }}</dd>
          <dt>Idioma</dt><dd>{{ selectedLanguage }}</dd>
          <dt>Secciones</dt><dd>{{ sections.length }}</dd>
        </dl>
      </aside>
    </div>

    <AppModal v-if="infoMessage" title="Acción no disponible" :description="infoMessage" size="sm" @close="infoMessage = ''">
      <template #footer>
        <button type="button" class="btn btn-primary" @click="infoMessage = ''">Entendido</button>
      </template>
    </AppModal>

    <AppModal
      v-if="reusableSectionCandidate"
      title="Guardar como reutilizable"
      description="Indica el título con el que se guardará en la biblioteca."
      @close="reusableSectionCandidate = null"
    >
      <label class="modal-field">Título <input v-model="reusableTitle" class="field" /></label>
      <template #footer>
        <button type="button" class="btn btn-outline" @click="reusableSectionCandidate = null">Cancelar</button>
        <button type="button" class="btn btn-primary" :disabled="!reusableTitle.trim()" @click="confirmSaveReusable">Guardar</button>
      </template>
    </AppModal>

    <AppModal
      v-if="cloneConfirmOpen"
      title="Clonar del Español"
      description="La versión inglesa actual se reemplazará con una copia del español."
      size="sm"
      @close="cloneConfirmOpen = false"
    >
      <p class="confirm-text">¿Continuar?</p>
      <template #footer>
        <button type="button" class="btn btn-outline" @click="cloneConfirmOpen = false">Cancelar</button>
        <button type="button" class="btn btn-primary" @click="cloneSpanishToEnglish(true)">Clonar</button>
      </template>
    </AppModal>
    <AppModal
      v-if="showLeaveEditorModal"
      title="Cambios sin guardar"
      description="Estás saliendo del editor. Si no guardas, los cambios se perderán."
      size="sm"
      @close="showLeaveEditorModal = false"
    >
      <template #footer>
        <button type="button" class="btn btn-outline" @click="showLeaveEditorModal = false">Permanecer</button>
        <button type="button" class="btn btn-outline danger-action" @click="discardChangesAndContinueNavigation">Descartar cambios</button>
        <button type="button" class="btn btn-primary" :disabled="saving" @click="saveAndContinueNavigation">
          <Save :size="14" /> {{ saving ? 'Guardando...' : 'Guardar y salir' }}
        </button>
      </template>
    </AppModal>
  </section>
</template>

<style scoped>
.editor-page {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.editor-top {
  position: relative;
  z-index: 200;
  min-height: 58px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px 8px;
  background: #fff;
  border-bottom: 1px solid var(--border);
  flex-wrap: wrap;
  overflow: visible;
}

.editor-title {
  flex: 1;
  min-width: 180px;
}

.editor-title h1 {
  margin: 0;
  font-size: 16px;
}

.editor-title span {
  display: block;
  color: var(--muted-foreground);
  font-size: 12px;
}

.lang-switch {
  display: inline-flex;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
  background: #fff;
}

.lang-switch button {
  border: 0;
  background: #fff;
  padding: 8px 11px;
  color: var(--muted-foreground);
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.lang-switch button.active {
  background: var(--dikoin-blue);
  color: #fff;
}

.lang-switch button:disabled {
  cursor: wait;
  opacity: .7;
}

.top-properties {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0;
}

.top-properties div {
  display: grid;
  grid-template-columns: auto auto;
  gap: 4px;
  align-items: center;
  padding: 4px 7px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--input-background);
  font-size: 11px;
  line-height: 1;
}

.top-properties dt {
  color: var(--muted-foreground);
  font-weight: 600;
}

.top-properties dd {
  margin: 0;
  color: var(--foreground);
  font-weight: 600;
}

.saved {
  color: #065f46;
  font-weight: 600;
  font-size: 12px;
}

.saved.local {
  color: var(--dikoin-blue);
}

.danger-action {
  border-color: #fecaca;
  color: #b91c1c;
}

.danger-action:hover {
  background: #fef2f2;
}

.editor-toolbar-dock {
  position: relative;
  z-index: 205;
  flex: 1 0 100%;
  min-height: 58px;
  display: flex;
  align-items: stretch;
  overflow: visible;
  isolation: isolate;
  padding-top: 7px;
  border-top: 1px solid #edf3f8;
}

.toolbar-empty {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  color: var(--muted-foreground);
  font-size: 12px;
}

.editor-grid {
  position: relative;
  z-index: 1;
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: var(--index-panel-width, 280px) minmax(0, 1fr);
  gap: 14px;
  padding: 14px;
  overflow: hidden;
  transition: grid-template-columns .18s ease;
}

.editor-grid.sections-collapsed {
  grid-template-columns: 0 minmax(0, 1fr);
}

.editor-grid.resizing-index {
  cursor: col-resize;
  user-select: none;
}

.sections-toggle {
  position: absolute;
  top: 50px;
  left: calc(var(--index-panel-width, 280px) + 14px);
  z-index: 80;
  width: 28px;
  height: 44px;
  transform: translateY(-50%);
  border: 1px solid var(--border);
  border-left: 0;
  border-radius: 0 var(--radius) var(--radius) 0;
  background: #fff;
  color: var(--dikoin-blue);
  display: grid;
  place-items: center;
  padding: 0;
  pointer-events: auto;
  /* box-shadow: 0 8px 18px rgba(15, 23, 42, .12); */
  transition: left .18s ease, background .12s ease;
}

.sections-toggle:hover {
  background: var(--dikoin-blue-lighter);
}

.editor-grid.sections-collapsed .sections-toggle {
  left: 0px;
}

.index-panel,
.props-panel {
  padding: 14px;
  overflow: auto;
}

.index-panel {
  position: relative;
  z-index: 2;
  min-width: 0;
  transition: opacity .16s ease, visibility .16s ease;
}

.index-resize-handle {
  position: absolute;
  top: 0;
  right: -8px;
  z-index: 4;
  width: 12px;
  height: 100%;
  cursor: col-resize;
}

.index-resize-handle::after {
  content: "";
  position: absolute;
  top: 12px;
  bottom: 12px;
  left: 5px;
  width: 2px;
  border-radius: 999px;
  background: transparent;
  transition: background .12s ease;
}

.index-resize-handle:hover::after,
.editor-grid.resizing-index .index-resize-handle::after {
  background: var(--dikoin-blue);
}

.editor-grid.sections-collapsed .index-panel {
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  overflow: hidden;
  padding-left: 0;
  padding-right: 0;
}

.index-panel h2,
.props-panel h2 {
  margin: 0 0 12px;
  font-size: 15px;
}

.index-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.index-head h2 {
  margin-bottom: 2px;
}

.index-head span {
  display: inline-flex;
  color: var(--muted-foreground);
  font-size: 11px;
  font-weight: 700;
}

.index-panel > .btn {
  width: 100%;
  justify-content: center;
  margin-bottom: 12px;
}

.index-card-list {
  display: grid;
  gap: 10px;
}

.index-section-card {
  display: grid;
  gap: 8px;
  margin-left: var(--level-indent);
  padding: 9px;
  border: 1px solid #d8e6f0;
  border-left: 4px solid var(--dikoin-blue);
  border-radius: var(--radius);
  background: #fff;
  box-shadow: 0 5px 12px rgba(15, 23, 42, .05);
  cursor: grab;
  transition: border-color .12s ease, box-shadow .12s ease, transform .12s ease, opacity .12s ease;
}

.index-section-card.selected {
  border-color: var(--dikoin-blue);
  box-shadow: 0 0 0 2px rgba(0, 124, 184, .12), 0 8px 16px rgba(15, 23, 42, .07);
}

.index-section-card.dragging {
  opacity: .48;
}

.index-section-card.drop-target {
  border-color: var(--dikoin-blue);
  box-shadow: 0 0 0 2px rgba(0, 124, 184, .18), 0 10px 20px rgba(15, 23, 42, .1);
  transform: translateY(-1px);
}

.index-section-card.collapsed {
  gap: 0;
}

.index-section-header {
  display: grid;
  grid-template-columns: auto auto auto minmax(0, 1fr);
  gap: 7px;
  align-items: center;
  cursor: pointer;
}

.index-section-collapse {
  width: 24px;
  height: 24px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: #fff;
  color: var(--dikoin-blue);
  display: grid;
  place-items: center;
  padding: 0;
}

.index-section-collapse:hover {
  background: var(--dikoin-blue-lighter);
  border-color: #9ecde8;
}

.index-drag-icon {
  color: #8aa4b7;
}

.index-section-number {
  min-width: 28px;
  padding: 2px 6px;
  border-radius: 3px;
  background: var(--dikoin-blue);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  text-align: center;
}

.index-section-header strong {
  min-width: 0;
  overflow: hidden;
  color: var(--dikoin-blue-dark);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.index-section-header small {
  grid-column: 4;
  color: var(--muted-foreground);
  font-size: 10px;
  font-weight: 700;
}

.index-heading-list {
  display: grid;
  gap: 6px;
  padding-left: 24px;
}

.index-heading-card {
  position: relative;
  display: grid;
  grid-template-columns: auto auto minmax(0, 1fr);
  align-items: center;
  gap: 6px;
  margin-left: var(--heading-indent);
  min-height: 32px;
  border: 1px solid #dce9f2;
  border-radius: 4px;
  background: #f8fbfe;
  padding: 6px 7px;
  color: var(--foreground);
  text-align: left;
  cursor: grab;
  transition: border-color .12s ease, background .12s ease, opacity .12s ease;
}

.index-heading-card::before,
.index-heading-card::after {
  content: "";
  position: absolute;
  left: 4px;
  right: 4px;
  height: 3px;
  border-radius: 999px;
  background: transparent;
  pointer-events: none;
}

.index-heading-card::before {
  top: -6px;
}

.index-heading-card::after {
  bottom: -6px;
}

.index-heading-card.drop-before::before,
.index-heading-card.drop-after::after {
  background: var(--dikoin-blue);
  box-shadow: 0 0 0 3px rgba(14, 127, 187, .13);
}

.index-heading-card:hover,
.index-heading-card.drop-target {
  border-color: #9ecde8;
  background: var(--dikoin-blue-lighter);
}

.index-heading-card.fragment {
  border-color: #9ecde8;
  background: #edf8ff;
  color: var(--dikoin-blue-dark);
  cursor: pointer;
}

.index-heading-card.fragment:hover {
  background: #dff2ff;
  border-color: #73b9df;
}

.index-heading-card.dragging {
  opacity: .45;
}

.index-heading-card span {
  padding: 1px 5px;
  border-radius: 3px;
  background: #e5f3fb;
  color: var(--dikoin-blue);
  font-size: 10px;
  font-weight: 800;
}

.index-heading-card.fragment span {
  background: #cfeeff;
  color: var(--dikoin-blue-dark);
}

.index-heading-card strong {
  min-width: 0;
  overflow: hidden;
  font-size: 11px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cards-panel {
  overflow: auto;
  padding: 0 34px 40px;
}

.section-editor-row {
  display: grid;
  grid-template-columns: 1fr;
  gap: 14px;
  margin-bottom: 14px;
}

.editor-grid.both-languages .cards-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.editor-grid.both-languages .section-editor-row {
  grid-template-columns: repeat(2, 210mm);
  align-items: start;
  justify-content: center;
  width: max-content;
  max-width: 100%;
  margin-left: auto;
  margin-right: auto;
}

.editor-grid.both-languages .add-section {
  width: min(100%, calc(420mm + 14px));
  margin-left: auto;
  margin-right: auto;
}

.language-editor-column {
  min-width: 0;
}

.language-column-label {
  position: sticky;
  top: 0;
  z-index: 60;
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 8px;
  margin-bottom: 6px;
  border-radius: var(--radius);
  background: var(--dikoin-blue-dark);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
}

.section-editor-row.dragging {
  opacity: .55;
}

.section-editor-row.drop-target {
  border-top: 4px solid var(--dikoin-blue);
  box-shadow: 0 0 0 3px rgba(0, 124, 184, .12);
}

.add-section {
  width: 100%;
  border: 1px dashed var(--border);
  background: #fff;
  padding: 12px;
  color: var(--dikoin-blue);
  display: flex;
  justify-content: center;
  gap: 7px;
}

dl {
  display: grid;
  grid-template-columns: 90px 1fr;
  gap: 8px;
  font-size: 13px;
}

dt {
  color: var(--muted-foreground);
}

.modal-field {
  display: grid;
  gap: 6px;
  color: var(--muted-foreground);
  font-size: 12px;
  font-weight: 600;
}

.confirm-text {
  margin: 0;
}

@media (max-width: 1100px) {

  .editor-grid,
  .editor-grid.sections-collapsed {
    grid-template-columns: 1fr;
    overflow: auto;
  }

  .sections-toggle {
    display: none;
  }

  .index-panel,
  .props-panel,
  .cards-panel {
    overflow: visible;
  }

  .editor-grid.sections-collapsed .index-panel {
    display: none;
  }

  .editor-grid.both-languages .section-editor-row {
    grid-template-columns: 1fr;
  }
}
</style>


