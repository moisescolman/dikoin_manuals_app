<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Copy, Eye, Languages, PanelLeftClose, PanelLeftOpen, Plus, Save, Send } from '@lucide/vue'
import { createReusableBlock } from '@/api/reusable-blocks.api'
import RichSectionEditor from '@/components/editor/RichSectionEditor.vue'
import AppModal from '@/components/shared/AppModal.vue'
import BackendError from '@/components/shared/BackendError.vue'
import { useManualsStore } from '@/stores/manuals.store'
import type { EditorBlock, EditorSection } from '@/types/editor'
import { randomId, sectionsFromBackend, versionRequestFromEditor } from '@/types/editor'
import type { LanguageCode, ManualStatus } from '@/types/api'

type BlockSelectionSync = {
  sectionId: string
  active: boolean
  indexes: number[]
  includeParallel: boolean
  sourceLanguage: LanguageCode
}

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
const sectionsPanelCollapsed = ref(false)
const sectionEditorRefs = ref<Array<{ flushEditorSync: () => void }>>([])
const selectionOwnerKey = ref('')
const blockSelectionSync = ref<BlockSelectionSync | null>(null)
const infoMessage = ref('')
const reusableSectionCandidate = ref<EditorSection | null>(null)
const reusableTitle = ref('')
const cloneConfirmOpen = ref(false)

const manual = computed(() => store.current)
const version = computed(() => manual.value?.activeVersion)
const editorLanguages = computed<LanguageCode[]>(() => languageMode.value === 'BOTH' ? ['ES', 'EN'] : [languageMode.value])
const activeLanguageLabel = computed(() => languageMode.value === 'BOTH' ? 'ES + EN' : languageMode.value)
const sectionTargets = computed(() => sections.value.map((section) => ({
  id: section.id,
  number: section.sectionNumber,
  title: sectionTitle(section),
})))

onMounted(async () => {
  const loaded = await store.fetchManual(Number(props.id))
  sections.value = sectionsFromBackend(loaded.activeVersion?.sections || [])
})

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
    status: 'DRAFT',
    visible: true,
    collapsed: false,
    blocks: [],
  })
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
    status: 'DRAFT',
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
    ...structuredClone(source),
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

function moveSection(index: number, direction: -1 | 1) {
  const target = index + direction
  if (target < 0 || target >= sections.value.length) return
  const copy = [...sections.value]
  const [item] = copy.splice(index, 1)
  copy.splice(target, 0, item)
  sections.value = renumberSections(copy)
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
  const copy = [...sections.value]
  const [item] = copy.splice(draggingIndex.value, 1)
  copy.splice(targetIndex, 0, item)
  sections.value = renumberSections(copy)
  draggingIndex.value = null
  dropTargetIndex.value = null
}

function buildVersionRequest(status: ManualStatus, changeNotes: string) {
  if (!version.value) return null
  return versionRequestFromEditor({
    versionNumber: version.value.versionNumber,
    status,
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

async function saveDraft(changeNotes = 'Borrador autoguardado desde editor') {
  if (!manual.value) return false
  flushSectionEditors()
  const request = buildVersionRequest('DRAFT', changeNotes)
  if (!request) return false
  await store.saveVersion(manual.value.id, request)
  sections.value = sectionsFromBackend(store.current?.activeVersion?.sections || [])
  return true
}

async function save() {
  if (!manual.value || !version.value) return
  saving.value = true
  try {
    flushSectionEditors()
    const request = buildVersionRequest(version.value.status, 'Guardado desde editor Vue')
    if (!request) return
    await store.saveVersion(manual.value.id, request)
    saved.value = true
    setTimeout(() => { saved.value = false }, 2000)
  } finally {
    saving.value = false
  }
}

async function saveDraftForPreview() {
  if (!manual.value || !version.value) return
  saving.value = true
  try {
    await saveDraft('Borrador autoguardado para vista previa')
    router.push({ name: 'manual-detail', params: { id: manual.value.id }, query: { lang: selectedLanguage.value } })
  } finally {
    saving.value = false
  }
}

async function changeLanguage(lang: LanguageCode) {
  if ((lang === selectedLanguage.value && languageMode.value === lang) || saving.value) return
  saving.value = true
  try {
    const savedDraft = await saveDraft(`Borrador autoguardado al cambiar a ${lang}`)
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
    const savedDraft = await saveDraft('Borrador autoguardado al mostrar ambos idiomas')
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
  const hasEnglish = sections.value.some((section) => section.blocks.some((block) => block.languageCode === 'EN'))
  if (hasEnglish && !force) {
    cloneConfirmOpen.value = true
    return
  }
  cloneConfirmOpen.value = false
  flushSectionEditors()
  sections.value = sections.value.map((section) => ({
    ...section,
    titleEn: section.titleEs,
    blocks: [
      ...section.blocks.filter((block) => block.languageCode === 'ES'),
      ...section.blocks
        .filter((block) => block.languageCode === 'ES')
        .map((block) => ({
          ...structuredClone(block),
          id: randomId('block'),
          backendId: undefined,
          languageCode: 'EN' as const,
        })),
    ],
  }))
  saving.value = true
  try {
    await saveDraft('Versión inglesa clonada desde el contenido español')
    selectedLanguage.value = 'EN'
    languageMode.value = 'EN'
    saved.value = true
    window.setTimeout(() => { saved.value = false }, 2000)
  } finally {
    saving.value = false
  }
}

async function sendReview() {
  if (!manual.value || !version.value) return
  flushSectionEditors()
  const request = buildVersionRequest('REVIEW', 'Enviado a revisión desde frontend')
  if (!request) return
  await store.saveVersion(manual.value.id, request)
  router.push({ name: 'manual-detail', params: { id: manual.value.id }, query: { lang: selectedLanguage.value } })
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
      <button class="btn btn-outline" :disabled="saving" @click="saveDraftForPreview"><Eye :size="14" /> Vista previa</button>
      <button class="btn btn-primary" :disabled="saving" @click="save"><Save :size="14" /> {{ saving ? 'Guardando...' : 'Guardar' }}</button>
      <button class="btn btn-outline" :disabled="saving" @click="sendReview"><Send :size="14" /> Enviar a revisión</button>
      <div id="manual-editor-toolbar" class="editor-toolbar-dock">
        <span v-if="!activeEditorKey" class="toolbar-empty">Selecciona una seccion para mostrar la cinta de herramientas.</span>
      </div>
    </div>

    <div class="editor-grid" :class="{ 'sections-collapsed': sectionsPanelCollapsed, 'both-languages': languageMode === 'BOTH' }">
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
        <div class="index-head">
          <h2>Secciones</h2>
        </div>
        <button class="btn btn-primary" @click="addSection"><Plus :size="14" /> Añadir sección</button>
        <ol>
          <li v-for="(section, index) in sections" :key="section.id">
            <button :style="{ paddingLeft: `${Math.max(0, (section.level || 1) - 1) * 14}px` }" @click="section.collapsed = false">{{ section.sectionNumber || index + 1 }}. {{ sectionTitle(section) }}</button>
            <div>
              <button @click="moveSection(index, -1)">↑</button>
              <button @click="moveSection(index, 1)">↓</button>
            </div>
          </li>
        </ol>
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
              :manual-id="manual?.id"
              :section-targets="sectionTargets"
              :selection-sync="blockSelectionSync"
              @update="updateSectionForLanguage($event, lang)"
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
  grid-template-columns: 250px minmax(0, 1fr);
  gap: 14px;
  padding: 14px;
  overflow: hidden;
  transition: grid-template-columns .18s ease;
}

.editor-grid.sections-collapsed {
  grid-template-columns: 0 minmax(0, 1fr);
}

.sections-toggle {
  position: absolute;
  top: 50px;
  left: 264px;
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

.index-panel ol {
  padding-left: 18px;
}

.index-panel li {
  margin: 9px 0;
  display: grid;
  gap: 5px;
}

.index-panel button {
  border: 0;
  background: transparent;
  color: var(--dikoin-blue);
  text-align: left;
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


