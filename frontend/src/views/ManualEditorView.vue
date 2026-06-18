<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Eye, Plus, Save, Send } from '@lucide/vue'
import SectionCard from '@/components/editor/SectionCard.vue'
import BackendError from '@/components/shared/BackendError.vue'
import { useManualsStore } from '@/stores/manuals.store'
import type { EditorSection } from '@/types/editor'
import { randomId, sectionsFromBackend, versionRequestFromEditor } from '@/types/editor'

const props = defineProps<{ id: string }>()
const router = useRouter()
const store = useManualsStore()
const sections = ref<EditorSection[]>([])
const selectedBlockId = ref('')
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

function addSection() {
  const next = sections.value.length + 1
  sections.value.push({
    id: randomId('section'),
    sortOrder: next,
    sectionNumber: String(next),
    titleEs: 'Nueva sección',
    status: 'PENDING',
    collapsed: false,
    blocks: [{ id: randomId('block'), type: 'parrafo', content: 'Nuevo contenido.', languageCode: 'ES' }],
  })
}

function updateSection(section: EditorSection) {
  sections.value = sections.value.map((item) => item.id === section.id ? section : item)
}

function deleteSection(id: string) {
  sections.value = sections.value.filter((section) => section.id !== id)
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

async function save() {
  if (!manual.value || !version.value) return
  saving.value = true
  try {
    await store.saveVersion(manual.value.id, versionRequestFromEditor({
      versionNumber: version.value.versionNumber,
      status: version.value.status,
      active: true,
      esReady: version.value.esReady,
      enReady: version.value.enReady,
      changeNotes: 'Guardado desde editor Vue',
      sections: sections.value,
    }))
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
    await store.saveVersion(manual.value.id, versionRequestFromEditor({
      versionNumber: version.value.versionNumber,
      status: 'DRAFT',
      active: true,
      esReady: version.value.esReady,
      enReady: version.value.enReady,
      changeNotes: 'Borrador autoguardado para vista previa',
      sections: sections.value,
    }))
    router.push({ name: 'manual-detail', params: { id: manual.value.id } })
  } finally {
    saving.value = false
  }
}

async function sendReview() {
  if (!manual.value || !version.value) return
  await store.saveVersion(manual.value.id, versionRequestFromEditor({
    versionNumber: version.value.versionNumber,
    status: 'REVIEW',
    active: true,
    esReady: version.value.esReady,
    enReady: version.value.enReady,
    changeNotes: 'Enviado a revisión desde frontend',
    sections: sections.value,
  }))
  router.push({ name: 'manual-detail', params: { id: manual.value.id } })
}
</script>

<template>
  <section class="editor-page">
    <BackendError :message="store.error" />
    <div class="editor-top">
      <button class="btn btn-outline" @click="router.push({ name: 'manual-detail', params: { id } })"><ArrowLeft :size="14" /> Volver</button>
      <div class="editor-title">
        <h1>{{ manual?.code || 'Editor' }}</h1>
        <span>{{ manual?.title }}</span>
      </div>
      <span v-if="saved" class="saved">Guardado</span>
      <button class="btn btn-outline" :disabled="saving" @click="saveDraftForPreview"><Eye :size="14" /> Vista previa</button>
      <button class="btn btn-primary" :disabled="saving" @click="save"><Save :size="14" /> {{ saving ? 'Guardando...' : 'Guardar' }}</button>
      <button class="btn btn-outline" @click="sendReview"><Send :size="14" /> Enviar a revisión</button>
    </div>

    <div class="editor-grid">
      <aside class="index-panel card">
        <h2>Secciones</h2>
        <button class="btn btn-primary" @click="addSection"><Plus :size="14" /> Añadir sección</button>
        <ol>
          <li v-for="(section, index) in sections" :key="section.id">
            <button @click="section.collapsed = false">{{ section.sectionNumber || index + 1 }}. {{ section.titleEs }}</button>
            <div>
              <button @click="moveSection(index, -1)">↑</button>
              <button @click="moveSection(index, 1)">↓</button>
            </div>
          </li>
        </ol>
      </aside>

      <main class="cards-panel">
        <div v-if="store.loading">Cargando contenido...</div>
        <SectionCard
          v-for="(section, index) in sections"
          :key="section.id"
          :section="section"
          :selected-block-id="selectedBlockId"
          draggable="true"
          :class="{ dragging: draggingIndex === index, 'drop-target': dropTargetIndex === index && draggingIndex !== index }"
          @dragstart="draggingIndex = index"
          @dragend="draggingIndex = null; dropTargetIndex = null"
          @dragover.prevent="dropTargetIndex = index"
          @drop="dropSection(index)"
          @update="updateSection"
          @delete="deleteSection(section.id)"
          @select-block="selectedBlockId = $event"
        />
        <button class="add-section" @click="addSection"><Plus :size="15" /> Añadir sección al final</button>
      </main>

      <aside class="props-panel card">
        <h2>Propiedades</h2>
        <p class="text-muted">Selecciona un bloque para editar su tipo y contenido. Los datos se convierten al JSON que espera Spring.</p>
        <dl>
          <dt>Versión</dt><dd>v{{ version?.versionNumber }}</dd>
          <dt>Estado</dt><dd>{{ version?.status }}</dd>
          <dt>Secciones</dt><dd>{{ sections.length }}</dd>
        </dl>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.editor-page { height: 100%; display: flex; flex-direction: column; }
.editor-top { height: 58px; display: flex; align-items: center; gap: 10px; padding: 10px 16px; background: #fff; border-bottom: 1px solid var(--border); }
.editor-title { flex: 1; min-width: 0; }
.editor-title h1 { margin: 0; font-size: 16px; }
.editor-title span { display: block; color: var(--muted-foreground); font-size: 12px; }
.saved { color: #065f46; font-weight: 700; font-size: 12px; }
.editor-grid { flex: 1; min-height: 0; display: grid; grid-template-columns: 250px minmax(0, 1fr) 260px; gap: 14px; padding: 14px; overflow: hidden; }
.index-panel, .props-panel { padding: 14px; overflow: auto; }
.index-panel h2, .props-panel h2 { margin: 0 0 12px; font-size: 15px; }
.index-panel ol { padding-left: 18px; }
.index-panel li { margin: 9px 0; display: grid; gap: 5px; }
.index-panel button { border: 0; background: transparent; color: var(--dikoin-blue); text-align: left; }
.cards-panel { overflow: auto; padding: 0 34px 40px; }
.cards-panel :deep(.section-card) { cursor: grab; }
.cards-panel :deep(.section-card:active) { cursor: grabbing; }
.cards-panel :deep(.section-card.dragging) { opacity: .55; border-color: var(--dikoin-blue); transform: scale(.995); box-shadow: 0 12px 28px rgba(0, 124, 184, .18); }
.cards-panel :deep(.section-card.drop-target) { border-top: 4px solid var(--dikoin-blue); box-shadow: 0 0 0 3px rgba(0,124,184,.12); }
.add-section { width: 100%; border: 1px dashed var(--border); background: #fff; padding: 12px; color: var(--dikoin-blue); display: flex; justify-content: center; gap: 7px; }
dl { display: grid; grid-template-columns: 90px 1fr; gap: 8px; font-size: 13px; }
dt { color: var(--muted-foreground); }
@media (max-width: 1100px) { .editor-grid { grid-template-columns: 1fr; overflow: auto; } .index-panel, .props-panel, .cards-panel { overflow: visible; } }
</style>
