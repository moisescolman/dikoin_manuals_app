<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowUp, Columns2, Download, Edit, GitBranch, History } from '@lucide/vue'
import { downloadExportPdf, exportManualPdf } from '@/api/exports.api'
import { getApiError } from '@/api/http'
import AppModal from '@/components/shared/AppModal.vue'
import BackendError from '@/components/shared/BackendError.vue'
import LangBadge from '@/components/shared/LangBadge.vue'
import ManualLanguageCompare from '@/components/shared/ManualLanguageCompare.vue'
import ManualRenderer from '@/components/shared/ManualRenderer.vue'
import StatusBadge from '@/components/shared/StatusBadge.vue'
import { useManualsStore } from '@/stores/manuals.store'
import type { ManualDetailResponse, ManualStatus } from '@/types/api'
import { formatDate } from '@/utils/formatters'

const props = defineProps<{ id: string }>()
const route = useRoute()
const router = useRouter()
const store = useManualsStore()
const tab = ref('Contenido')
const exportMessage = ref('')
const selectedLanguage = ref<'ES' | 'EN'>((route.query.lang === 'EN' ? 'EN' : 'ES'))
const compareMode = ref(false)
const selectedStatus = ref<ManualStatus>('DRAFT')
const statusNotes = ref('')
const statusMessage = ref('')
const changingStatus = ref(false)
const exporting = ref(false)
const publishPdfModalOpen = ref(false)
const detailPage = ref<HTMLElement | null>(null)
const previewStart = ref<HTMLElement | null>(null)
const showBackToTop = ref(false)
const localPreviewManual = ref<ManualDetailResponse | null>(null)
let scrollContainer: HTMLElement | null = null

onMounted(async () => {
  await store.fetchManual(Number(props.id))
  loadLocalPreview()
  await nextTick()
  scrollContainer = detailPage.value?.closest('.main-content') as HTMLElement | null
  scrollContainer?.addEventListener('scroll', updateBackToTop, { passive: true })
  updateBackToTop()
})

onBeforeUnmount(() => {
  scrollContainer?.removeEventListener('scroll', updateBackToTop)
})
watch(() => store.current?.activeVersion?.status, (status) => {
  selectedStatus.value = status || 'DRAFT'
}, { immediate: true })
watch(() => route.query.lang, (lang) => {
  selectedLanguage.value = lang === 'EN' ? 'EN' : 'ES'
})
watch(() => route.query.preview, () => {
  loadLocalPreview()
})

const displayedManual = computed(() => localPreviewManual.value || store.current)

const languageReady = computed(() => selectedLanguage.value === 'ES'
  ? store.current?.activeVersion?.esReady
  : store.current?.activeVersion?.enReady)

function changeLanguage(lang: 'ES' | 'EN') {
  compareMode.value = false
  selectedLanguage.value = lang
  router.replace({ name: 'manual-detail', params: { id: props.id }, query: { lang } })
}

function openEditor() {
  if (!store.current) return
  router.push({ name: 'manual-editor', params: { id: store.current.id }, query: { lang: selectedLanguage.value } })
}

function localDraftKey() {
  return `dikoin-manual-editor-draft:${props.id}`
}

function loadLocalPreview() {
  if (route.query.preview !== 'local') {
    localPreviewManual.value = null
    return
  }
  try {
    const parsed = JSON.parse(localStorage.getItem(localDraftKey()) || 'null')
    localPreviewManual.value = parsed?.manual?.id === Number(props.id) ? parsed.manual : null
  } catch {
    localPreviewManual.value = null
  }
}

function showLanguageCompare() {
  tab.value = 'Contenido'
  compareMode.value = true
}

async function exportPdf() {
  exporting.value = true
  exportMessage.value = 'Generando PDF...'
  try {
    const result = await exportManualPdf(Number(props.id), selectedLanguage.value)
    if (result.status !== 'COMPLETED') {
      exportMessage.value = result.logMessage || 'Exportación solicitada'
      return
    }
    const pdf = await downloadExportPdf(result.id)
    const url = URL.createObjectURL(pdf)
    const link = document.createElement('a')
    link.href = url
    link.download = pdfFilename(result.pdfPath)
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
    exportMessage.value = 'PDF generado y descargado.'
  } catch (error) {
    exportMessage.value = getApiError(error)
  } finally {
    exporting.value = false
  }
}

function pdfFilename(path: string | undefined) {
  return path?.split('/').pop() || `manual-${props.id}.pdf`
}

async function changeStatus() {
  if (!store.current || !store.current.activeVersion) return
  if (selectedStatus.value === 'PUBLISHED') {
    publishPdfModalOpen.value = true
    return
  }
  await applyStatusChange(selectedStatus.value)
}

async function applyStatusChange(status: ManualStatus) {
  if (!store.current || !store.current.activeVersion) return
  changingStatus.value = true
  statusMessage.value = ''
  try {
    await store.updateStatus(store.current.id, status, statusNotes.value || `Cambio de estado a ${status}`)
    selectedStatus.value = status
    statusMessage.value = `Estado cambiado a ${status}`
  } finally {
    changingStatus.value = false
  }
}

async function confirmPublish(generatePdf: boolean) {
  publishPdfModalOpen.value = false
  await applyStatusChange('PUBLISHED')
  if (generatePdf) {
    await exportPdf()
  }
}

function updateBackToTop() {
  showBackToTop.value = Boolean(scrollContainer && scrollContainer.scrollTop > 520)
}

function scrollToCover() {
  const target = previewStart.value || detailPage.value
  target?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
</script>

<template>
  <section ref="detailPage" class="detail-page">
    <BackendError :message="store.error" />
    <div v-if="store.loading">Cargando manual...</div>
    <template v-else-if="store.current">
      <div class="detail-head">
        <button class="btn btn-outline" @click="router.push({ name: 'manuals' })"><ArrowLeft :size="14" /> Volver</button>
        <div class="title-area">
          <h1>{{ store.current.code }}</h1>
          <p>{{ store.current.title }} · {{ store.current.productCode }}</p>
        </div>
        <div class="head-actions">
          <div class="lang-switch">
            <button :class="{ active: selectedLanguage === 'ES' }" @click="changeLanguage('ES')">ES</button>
            <button :class="{ active: selectedLanguage === 'EN' }" @click="changeLanguage('EN')">EN</button>
          </div>
          <button class="btn btn-outline compare-btn" :class="{ active: compareMode }" @click="showLanguageCompare"><Columns2 :size="15" /> Comparar idiomas</button>
          <button class="btn btn-primary" @click="openEditor"><Edit :size="15" /> Editar manual</button>
          <button class="btn btn-outline" :disabled="exporting" @click="exportPdf"><Download :size="15" /> {{ exporting ? 'Exportando...' : 'Exportar PDF' }}</button>
          <button class="btn btn-outline" @click="router.push({ name: 'history', params: { id: store.current.id } })"><History :size="15" /> Historial</button>
        </div>
      </div>

      <div v-if="exportMessage" class="success-msg">{{ exportMessage }}</div>
      <div v-if="statusMessage" class="success-msg">{{ statusMessage }}</div>
      <div v-if="!compareMode && !languageReady" class="empty-lang">
        La versión {{ selectedLanguage }} todavía no tiene contenido listo. Se muestra la vista en blanco para completar o revisar.
      </div>

      <div class="meta-grid">
        <article class="card meta-card"><span>Estado</span><StatusBadge :status="store.current.activeVersion?.status" /></article>
        <article class="card meta-card"><span>Versión activa</span><strong>v{{ store.current.activeVersion?.versionNumber }}</strong></article>
        <article class="card meta-card"><span>Tipo</span><strong>{{ store.current.documentTypeCode || '-' }}</strong></article>
        <article class="card meta-card"><span>Idiomas</span><div><LangBadge label="ES" :ready="store.current.activeVersion?.esReady" /> <LangBadge label="EN" :ready="store.current.activeVersion?.enReady" /></div></article>
        <article class="card meta-card"><span>Actualizado</span><strong>{{ formatDate(store.current.updatedAt) }}</strong></article>
      </div>

      <div class="card status-panel">
        <div>
          <strong>Cambiar estado de la versión activa</strong>
          <p class="text-muted">Se conserva todo el contenido actual y solo se actualiza el estado.</p>
        </div>
        <select v-model="selectedStatus" class="field">
          <option value="DRAFT">Borrador</option>
          <option value="REVIEW">En revisión</option>
          <option value="APPROVED">Aprobado</option>
          <option value="PUBLISHED">Publicado</option>
          <option value="ARCHIVED">Archivado</option>
        </select>
        <input v-model="statusNotes" class="field" placeholder="Notas del cambio, opcional" />
        <button class="btn btn-primary" :disabled="changingStatus" @click="changeStatus">
          {{ changingStatus ? 'Cambiando...' : 'Aplicar estado' }}
        </button>
      </div>

      <div class="tabs">
        <button v-for="item in ['Contenido','Metadatos','Versiones','Exportaciones']" :key="item" :class="{ active: tab === item }" @click="tab = item">
          {{ item }}
        </button>
      </div>

      <div v-if="tab === 'Contenido'" ref="previewStart" class="preview-start">
        <ManualLanguageCompare v-if="compareMode && displayedManual" :manual="displayedManual" />
        <ManualRenderer v-else-if="displayedManual" :manual="displayedManual" :language="selectedLanguage" />
      </div>
      <div v-else-if="tab === 'Metadatos'" class="card info-panel">
        <p><strong>Producto:</strong> {{ store.current.productCode }} · {{ store.current.productName }}</p>
        <p><strong>Tipo documental:</strong> {{ store.current.documentTypeCode || '-' }} {{ store.current.documentTypeName || '' }}</p>
        <p><strong>Nomenclatura:</strong> {{ store.current.documentYear || '--' }}{{ store.current.documentVersion || '--' }} [{{ store.current.languageCode || '--' }}]</p>
        <p><strong>Categoría:</strong> {{ store.current.category || '-' }}</p>
        <p><strong>Creado:</strong> {{ formatDate(store.current.createdAt) }}</p>
      </div>
      <div v-else-if="tab === 'Versiones'" class="card info-panel">
        <table class="table">
          <thead><tr><th>Versión</th><th>Estado</th><th>Activa</th><th>Creada</th><th>Notas</th></tr></thead>
          <tbody>
            <tr v-for="version in store.current.versions" :key="version.id">
              <td class="mono">v{{ version.versionNumber }}</td>
              <td><StatusBadge :status="version.status" /></td>
              <td>{{ version.active ? 'Sí' : 'No' }}</td>
              <td>{{ formatDate(version.createdAt) }}</td>
              <td>{{ version.changeNotes }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="card info-panel">
        <GitBranch :size="18" />
        <p>Las exportaciones PDF se generan desde el botón superior y se descargan al terminar.</p>
      </div>
      <button v-if="tab === 'Contenido' && showBackToTop" class="back-to-cover" type="button" @click="scrollToCover">
        <ArrowUp :size="16" /> Subir
      </button>

      <AppModal
        v-if="publishPdfModalOpen"
        title="Publicar manual"
        description="El manual quedara publicado como version vigente. Se recomienda generar un PDF nuevo para dejarlo disponible para clientes y suscriptores."
        size="sm"
        @close="publishPdfModalOpen = false"
      >
        <template #footer>
          <button type="button" class="btn btn-outline" :disabled="changingStatus || exporting" @click="confirmPublish(false)">
            Publicar sin PDF
          </button>
          <button type="button" class="btn btn-primary" :disabled="changingStatus || exporting" @click="confirmPublish(true)">
            Publicar y generar PDF
          </button>
        </template>
      </AppModal>
    </template>
  </section>
</template>

<style scoped>
.detail-page {
  padding: 24px;
  display: grid;
  gap: 16px;
}

.detail-head {
  position: sticky;
  top: 0;
  z-index: 50;
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin: -24px -24px 0;
  padding: 14px 24px;
  background: rgba(255, 255, 255, .96);
  border-bottom: 1px solid var(--border);
  box-shadow: 0 8px 18px rgba(15, 23, 42, .08);
  backdrop-filter: blur(8px);
}

.title-area {
  flex: 1;
}

.title-area h1 {
  margin: 0;
  font-size: 22px;
}

.title-area p {
  margin: 5px 0 0;
  color: var(--muted-foreground);
}

.head-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
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
}

.lang-switch button.active {
  background: var(--dikoin-blue);
  color: #fff;
}

.compare-btn.active {
  border-color: var(--dikoin-blue);
  color: var(--dikoin-blue);
  background: var(--dikoin-blue-lighter);
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
}

.meta-card {
  padding: 12px;
  display: grid;
  gap: 7px;
}

.meta-card span {
  color: var(--muted-foreground);
  font-size: 12px;
}

.status-panel {
  padding: 12px;
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 180px minmax(220px, 1fr) auto;
  gap: 10px;
  align-items: center;
}

.status-panel strong {
  display: block;
  margin-bottom: 3px;
}

.status-panel p {
  margin: 0;
}

.tabs {
  display: flex;
  gap: 2px;
  border-bottom: 1px solid var(--border);
}

.tabs button {
  border: 0;
  background: transparent;
  padding: 10px 14px;
  color: var(--muted-foreground);
}

.tabs button.active {
  color: var(--dikoin-blue);
  border-bottom: 2px solid var(--dikoin-blue);
}

.info-panel {
  padding: 16px;
}

.preview-start {
  scroll-margin-top: 92px;
}

.back-to-cover {
  position: fixed;
  right: 18px;
  bottom: 18px;
  z-index: 70;
  border: 1px solid var(--dikoin-blue);
  border-radius: var(--radius);
  background: var(--dikoin-blue);
  color: #fff;
  padding: 9px 12px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  box-shadow: 0 10px 24px rgba(15, 23, 42, .18);
}

.success-msg {
  background: var(--dikoin-green-light);
  color: #065f46;
  border: 1px solid #86efac;
  padding: 10px;
  border-radius: var(--radius);
}

.empty-lang {
  background: #fff7ed;
  color: #9a3412;
  border: 1px solid #fed7aa;
  padding: 10px;
  border-radius: var(--radius);
}

@media (max-width: 980px) {
  .status-panel {
    grid-template-columns: 1fr;
  }
}
</style>
