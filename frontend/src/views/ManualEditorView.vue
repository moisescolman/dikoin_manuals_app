<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Eye, Plus, Save, Send } from '@lucide/vue'
import { createReusableBlock } from '@/api/reusable-blocks.api'
import RichSectionEditor from '@/components/editor/RichSectionEditor.vue'
import BackendError from '@/components/shared/BackendError.vue'
import { useManualsStore } from '@/stores/manuals.store'
import type { EditorSection } from '@/types/editor'
import { randomId, sectionsFromBackend, versionRequestFromEditor } from '@/types/editor'
import type { LanguageCode, ManualStatus } from '@/types/api'

const props = defineProps<{ id: string }>()
const route = useRoute()
const router = useRouter()
const store = useManualsStore()
const sections = ref<EditorSection[]>([])
const selectedSectionId = ref('')
const selectedLanguage = ref<LanguageCode>(route.query.lang === 'EN' ? 'EN' : 'ES')
const saved = ref(false)
const saving = ref(false)
const draggingIndex = ref<number | null>(null)
const dropTargetIndex = ref<number | null>(null)

const manual = computed(() => store.current)
const version = computed(() => manual.value?.activeVersion)

onMounted(async () => {
  const loaded = await store.fetchManual(Number(props.id))
  sections.value = sectionsFromBackend(loaded.activeVersion?.sections || [])
})

watch(() => route.query.lang, (lang) => {
  selectedLanguage.value = lang === 'EN' ? 'EN' : 'ES'
})

function addSection() {
  const next = sections.value.length + 1
  const isEnglish = selectedLanguage.value === 'EN'
  sections.value.push({
    id: randomId('section'),
    sortOrder: next,
    sectionNumber: String(next),
    titleEs: 'Nueva sección',
    titleEn: isEnglish ? 'New section' : undefined,
    status: 'PENDING',
    collapsed: false,
    blocks: [],
  })
}

function updateSection(section: EditorSection) {
  sections.value = sections.value.map((item) => item.id === section.id ? section : item)
}

function deleteSection(id: string) {
  sections.value = sections.value.filter((section) => section.id !== id)
}

function duplicateSection(index: number) {
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
  selectedSectionId.value = duplicated.id
}

async function saveReusable(section: EditorSection) {
  const title = window.prompt('Título del bloque reutilizable', section.titleEs || section.titleEn || 'Bloque reutilizable')
  if (!title) return
  const code = window.prompt('Código del bloque reutilizable', `BLQ-${String(Date.now()).slice(-6)}`)
  if (!code) return
  const request = versionRequestFromEditor({
    versionNumber: '1',
    status: 'DRAFT',
    active: true,
    esReady: true,
    enReady: Boolean(section.titleEn || section.blocks.some((block) => block.languageCode === 'EN')),
    sections: [section],
  })
  await createReusableBlock({
    code,
    title,
    productCategory: manual.value?.category || '',
    productCodes: manual.value?.productCode || '',
    contentJson: JSON.stringify({ blocks: request.sections[0].blocks }),
    active: true,
  })
  saved.value = true
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
  return value.map((section, idx) => ({ ...section, sortOrder: idx + 1, sectionNumber: String(idx + 1) }))
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
    esReady: version.value.esReady,
    enReady: version.value.enReady,
    changeNotes,
    sections: sections.value,
  })
}

async function saveDraft(changeNotes = 'Borrador autoguardado desde editor') {
  if (!manual.value) return false
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
  if (lang === selectedLanguage.value || saving.value) return
  saving.value = true
  try {
    const savedDraft = await saveDraft(`Borrador autoguardado al cambiar a ${lang}`)
    if (!savedDraft) return
    selectedSectionId.value = ''
    selectedLanguage.value = lang
    saved.value = true
    router.replace({ name: 'manual-editor', params: { id: props.id }, query: { lang } })
    setTimeout(() => { saved.value = false }, 2000)
  } finally {
    saving.value = false
  }
}

async function sendReview() {
  if (!manual.value || !version.value) return
  const request = buildVersionRequest('REVIEW', 'Enviado a revisión desde frontend')
  if (!request) return
  await store.saveVersion(manual.value.id, request)
  router.push({ name: 'manual-detail', params: { id: manual.value.id }, query: { lang: selectedLanguage.value } })
}

function sectionTitle(section: EditorSection) {
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
        <button :class="{ active: selectedLanguage === 'ES' }" :disabled="saving" @click="changeLanguage('ES')">ES</button>
        <button :class="{ active: selectedLanguage === 'EN' }" :disabled="saving" @click="changeLanguage('EN')">EN</button>
      </div>
      <span v-if="saved" class="saved">Guardado</span>
      <button class="btn btn-outline" :disabled="saving" @click="saveDraftForPreview"><Eye :size="14" /> Vista previa</button>
      <button class="btn btn-primary" :disabled="saving" @click="save"><Save :size="14" /> {{ saving ? 'Guardando...' : 'Guardar' }}</button>
      <button class="btn btn-outline" :disabled="saving" @click="sendReview"><Send :size="14" /> Enviar a revisión</button>
    </div>

    <div class="editor-grid">
      <aside class="index-panel card">
        <h2>Secciones</h2>
        <button class="btn btn-primary" @click="addSection"><Plus :size="14" /> Añadir sección</button>
        <ol>
          <li v-for="(section, index) in sections" :key="section.id">
            <button @click="section.collapsed = false">{{ section.sectionNumber || index + 1 }}. {{ sectionTitle(section) }}</button>
            <div>
              <button @click="moveSection(index, -1)">↑</button>
              <button @click="moveSection(index, 1)">↓</button>
            </div>
          </li>
        </ol>
      </aside>

      <main class="cards-panel">
        <div v-if="store.loading">Cargando contenido...</div>
        <RichSectionEditor
          v-for="(section, index) in sections"
          :key="section.id"
          :section="section"
          :selected="selectedSectionId === section.id"
          :language="selectedLanguage"
          :manual-id="manual?.id"
          :class="{ dragging: draggingIndex === index, 'drop-target': dropTargetIndex === index && draggingIndex !== index }"
          @dragstart="draggingIndex = index"
          @dragend="draggingIndex = null; dropTargetIndex = null"
          @dragover.prevent="dropTargetIndex = index"
          @drop="dropSection(index)"
          @update="updateSection"
          @delete="deleteSection(section.id)"
          @duplicate="duplicateSection(index)"
          @save-reusable="saveReusable(section)"
          @select="selectedSectionId = $event"
        />
        <button class="add-section" @click="addSection"><Plus :size="15" /> Añadir sección al final</button>
      </main>

      <aside class="props-panel card">
        <h2>Propiedades</h2>
        <p class="text-muted">Selecciona un bloque para editar su tipo y contenido. Los datos se convierten al JSON que espera Spring.</p>
        <dl>
          <dt>Versión</dt><dd>v{{ version?.versionNumber }}</dd>
          <dt>Estado</dt><dd>{{ version?.status }}</dd>
          <dt>Idioma</dt><dd>{{ selectedLanguage }}</dd>
          <dt>Secciones</dt><dd>{{ sections.length }}</dd>
        </dl>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.editor-page { height: 100%; display: flex; flex-direction: column; }
.editor-top { min-height: 58px; display: flex; align-items: center; gap: 10px; padding: 10px 16px; background: #fff; border-bottom: 1px solid var(--border); flex-wrap: wrap; }
.editor-title { flex: 1; min-width: 180px; }
.editor-title h1 { margin: 0; font-size: 16px; }
.editor-title span { display: block; color: var(--muted-foreground); font-size: 12px; }
.lang-switch { display: inline-flex; border: 1px solid var(--border); border-radius: var(--radius); overflow: hidden; background: #fff; }
.lang-switch button { border: 0; background: #fff; padding: 8px 11px; color: var(--muted-foreground); font-weight: 800; }
.lang-switch button.active { background: var(--dikoin-blue); color: #fff; }
.lang-switch button:disabled { cursor: wait; opacity: .7; }
.saved { color: #065f46; font-weight: 700; font-size: 12px; }
.editor-grid { flex: 1; min-height: 0; display: grid; grid-template-columns: 250px minmax(0, 1fr) 260px; gap: 14px; padding: 14px; overflow: hidden; }
.index-panel, .props-panel { padding: 14px; overflow: auto; }
.index-panel h2, .props-panel h2 { margin: 0 0 12px; font-size: 15px; }
.index-panel ol { padding-left: 18px; }
.index-panel li { margin: 9px 0; display: grid; gap: 5px; }
.index-panel button { border: 0; background: transparent; color: var(--dikoin-blue); text-align: left; }
.cards-panel { overflow: auto; padding: 0 34px 40px; }
.cards-panel :deep(.rich-section.dragging) { opacity: .55; border-color: var(--dikoin-blue); transform: scale(.995); box-shadow: 0 12px 28px rgba(0, 124, 184, .18); }
.cards-panel :deep(.rich-section.drop-target) { border-top: 4px solid var(--dikoin-blue); box-shadow: 0 0 0 3px rgba(0,124,184,.12); }
.add-section { width: 100%; border: 1px dashed var(--border); background: #fff; padding: 12px; color: var(--dikoin-blue); display: flex; justify-content: center; gap: 7px; }
dl { display: grid; grid-template-columns: 90px 1fr; gap: 8px; font-size: 13px; }
dt { color: var(--muted-foreground); }
@media (max-width: 1100px) { .editor-grid { grid-template-columns: 1fr; overflow: auto; } .index-panel, .props-panel, .cards-panel { overflow: visible; } }
</style>
