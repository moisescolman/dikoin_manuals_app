<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Ban, Download, Edit, Eye, History, Plus, Search, Trash2, FileText } from '@lucide/vue'
import { downloadExportPdf, exportManualPdf } from '@/api/exports.api'
import { getApiError } from '@/api/http'
import BackendError from '@/components/shared/BackendError.vue'
import AppModal from '@/components/shared/AppModal.vue'
import StatusBadge from '@/components/shared/StatusBadge.vue'
import { useManualsStore } from '@/stores/manuals.store'
import type { ManualStatus } from '@/types/api'
import { formatDate } from '@/utils/formatters'

type StatusFilter = 'ALL' | ManualStatus
type LangFilter = 'ALL' | 'ES_READY' | 'EN_READY' | 'EN_PENDING'

const router = useRouter()
const route = useRoute()
const store = useManualsStore()
const search = ref(typeof route.query.search === 'string' ? route.query.search : '')
const statusFilter = ref<StatusFilter>(statusFromQuery(route.query.status))
const langFilter = ref<LangFilter>(langFromQuery(route.query.lang))
const selected = ref<number[]>([])
const exportMessage = ref('')
const exportingIds = ref<number[]>([])
const searchFocused = ref(false)
const disabledMessage = ref('')
const bulkNoticeOpen = ref(false)
const bulkNotice = ref({ type: 'WARNING', title: '', content: '' })
const bulkStatus = ref<ManualStatus>('REVIEW')
const statusMessage = ref('')
const deleteCandidate = ref<number | null>(null)

onMounted(() => store.fetchManuals(search.value))
watch(search, (value) => {
  if (!value) store.fetchManuals()
})
watch(() => route.query, (query) => {
  search.value = typeof query.search === 'string' ? query.search : ''
  statusFilter.value = statusFromQuery(query.status)
  langFilter.value = langFromQuery(query.lang)
  store.fetchManuals(search.value)
})
watch([statusFilter, langFilter], syncFiltersToRoute)

const filtered = computed(() => store.manuals.filter((manual) => {
  if (statusFilter.value !== 'ALL' && manual.activeStatus !== statusFilter.value) return false
  if (langFilter.value === 'ES_READY' && !manual.esReady) return false
  if (langFilter.value === 'EN_READY' && !manual.enReady) return false
  if (langFilter.value === 'EN_PENDING' && manual.enReady) return false
  return true
}))

async function performSearch() {
  await store.fetchManuals(search.value)
  syncFiltersToRoute()
}

async function exportPdf(id: number) {
  exportMessage.value = 'Generando PDF...'
  exportingIds.value = [...exportingIds.value, id]
  try {
    const result = await exportManualPdf(id)
    if (result.status !== 'COMPLETED') {
      exportMessage.value = result.logMessage || 'Exportación solicitada'
      return
    }
    const pdf = await downloadExportPdf(result.id)
    const url = URL.createObjectURL(pdf)
    const link = document.createElement('a')
    link.href = url
    link.download = pdfFilename(result.pdfPath, id)
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
    exportMessage.value = 'PDF generado y descargado.'
  } catch (error) {
    exportMessage.value = getApiError(error)
  } finally {
    exportingIds.value = exportingIds.value.filter((item) => item !== id)
  }
}

function pdfFilename(path: string | undefined, id: number) {
  const filename = path?.split('/').pop()
  return filename || `manual-${id}.pdf`
}

async function deleteManual(id: number | null) {
  if (!id) return
  await store.removeManual(id)
  deleteCandidate.value = null
  statusMessage.value = 'Manual movido a la papelera.'
}

async function changeRowStatus(id: number, status: ManualStatus) {
  statusMessage.value = ''
  await store.updateStatus(id, status, `Cambio rapido de estado a ${status}`)
  statusMessage.value = `Estado actualizado a ${status}`
}

async function disableManualById(id: number) {
  await store.updateEnabled(id, false)
  disabledMessage.value = 'Manual deshabilitado.'
}

async function changeSelectedStatus() {
  for (const id of selected.value) {
    await store.updateStatus(id, bulkStatus.value, `Cambio de estado en lote a ${bulkStatus.value}`)
  }
  statusMessage.value = `${selected.value.length} manuales cambiados a ${bulkStatus.value}`
  selected.value = []
}

function applyBulkNotice() {
  disabledMessage.value = `La ${bulkNotice.value.type.toLowerCase()} "${bulkNotice.value.title}" se aplicaría a ${selected.value.length} manuales. Requiere POST /api/v1/manuals/bulk/notices.`
  bulkNoticeOpen.value = false
}

function toggle(id: number) {
  selected.value = selected.value.includes(id) ? selected.value.filter((item) => item !== id) : [...selected.value, id]
}

function closeSuggestionsLater() {
  window.setTimeout(() => { searchFocused.value = false }, 150)
}

function statusFromQuery(value: unknown): StatusFilter {
  return ['DRAFT', 'REVIEW', 'APPROVED', 'PUBLISHED', 'ARCHIVED', 'DEACTIVATED'].includes(String(value))
    ? String(value) as ManualStatus
    : 'ALL'
}

function langFromQuery(value: unknown): LangFilter {
  return ['ES_READY', 'EN_READY', 'EN_PENDING'].includes(String(value))
    ? String(value) as LangFilter
    : 'ALL'
}

function syncFiltersToRoute() {
  const query = {
    search: search.value || undefined,
    status: statusFilter.value === 'ALL' ? undefined : statusFilter.value,
    lang: langFilter.value === 'ALL' ? undefined : langFilter.value,
  }

  if (
    route.query.search === query.search &&
    route.query.status === query.status &&
    route.query.lang === query.lang
  ) {
    return
  }

  router.replace({ name: 'manuals', query })
}
</script>

<template>
  <section class="manuals-page">
    <div class="head">
      <div>
        <h1 class="page-title">Manuales</h1>
        <p class="text-muted">{{ filtered.length }} registros encontrados desde backend</p>
      </div>
      <div class="head-actions">
        <button class="btn btn-primary" @click="router.push({ name: 'manual-create' })"><Plus :size="15" /> Crear manual</button>
        <button class="btn btn-outline" @click="router.push({ name: 'import' })">+ Importar manual</button>
      </div>
    </div>

    <BackendError :message="store.error" />
    <div v-if="exportMessage" class="success-msg">{{ exportMessage }}</div>
    <div v-if="statusMessage" class="success-msg">{{ statusMessage }}</div>
    <div v-if="disabledMessage" class="warning-msg">{{ disabledMessage }}</div>

    <div class="filters card">
      <div class="search-box">
        <Search :size="14" />
        <input v-model="search" placeholder="Código, producto o título..." @focus="searchFocused = true" @blur="closeSuggestionsLater" @keydown.enter="performSearch" />
        <div v-if="searchFocused && search" class="suggestions">
          <button
            v-for="manual in filtered.slice(0, 6)"
            :key="manual.id"
            @mousedown.prevent="router.push({ name: 'manual-detail', params: { id: manual.id } })"
          >
            <span class="mono">{{ manual.code }}</span>
            <small>{{ manual.title }}</small>
          </button>
        </div>
      </div>
      <button class="btn btn-outline" @click="performSearch">Buscar</button>
      <select v-model="statusFilter" class="field">
        <option value="ALL">Todos los estados</option>
        <option value="DRAFT">Borrador</option>
        <option value="REVIEW">Revisión</option>
        <option value="APPROVED">Aprobado</option>
        <option value="PUBLISHED">Publicado</option>
        <option value="ARCHIVED">Archivado</option>
        <option value="DEACTIVATED">Dado de baja</option>
      </select>
      <select v-model="langFilter" class="field">
        <option value="ALL">Todos los idiomas</option>
        <option value="ES_READY">ES listo</option>
        <option value="EN_READY">EN listo</option>
        <option value="EN_PENDING">EN pendiente</option>
      </select>
    </div>

    <div v-if="selected.length" class="bulk">
      <strong>{{ selected.length }} seleccionados</strong>
      <button class="btn btn-outline">Cambiar categoría</button>
      <select v-model="bulkStatus" class="field bulk-status">
        <option value="DRAFT">Borrador</option>
        <option value="REVIEW">Revisión</option>
        <option value="APPROVED">Aprobado</option>
        <option value="PUBLISHED">Publicado</option>
        <option value="ARCHIVED">Archivado</option>
        <option value="DEACTIVATED">Dado de baja</option>
      </select>
      <button class="btn btn-outline" @click="changeSelectedStatus">Aplicar estado</button>
      <button class="btn btn-outline" @click="bulkNoticeOpen = !bulkNoticeOpen">Agregar aviso/nota</button>
      <button class="btn btn-outline">Archivar</button>
      <button class="btn btn-outline" @click="selected = []">Limpiar</button>
    </div>

    <div v-if="bulkNoticeOpen" class="card notice-panel">
      <h2>Aplicar aviso en lote</h2>
      <select v-model="bulkNotice.type" class="field">
        <option value="WARNING">Advertencia</option>
        <option value="ALERT">Alerta</option>
        <option value="NOTE">Nota</option>
        <option value="SUGGESTION">Sugerencia</option>
      </select>
      <input v-model="bulkNotice.title" class="field" placeholder="Título del aviso" />
      <textarea v-model="bulkNotice.content" class="field" rows="3" placeholder="Contenido que se aplicará a los manuales seleccionados" />
      <button class="btn btn-primary" @click="applyBulkNotice">Preparar aplicación</button>
    </div>

    <div class="card list-card">
      <table class="table">
        <thead>
          <tr>
            <th></th><th>Código</th><th>Tipo</th><th>Producto</th><th>Título</th><th>Estado</th><th>Modificado</th><th>Versión</th><th>Acciones</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="store.loading"><td colspan="9">Cargando manuales...</td></tr>
          <tr v-else-if="!filtered.length"><td colspan="9">No hay manuales para mostrar.</td></tr>
          <tr v-for="manual in filtered" :key="manual.id">
            <td><input type="checkbox" :checked="selected.includes(manual.id)" @change="toggle(manual.id)" /></td>
            <td class="mono">{{ manual.code }}</td>
            <td class="mono">{{ manual.documentTypeCode || '-' }}</td>
            <td>{{ manual.productCode }}</td>
            <td class="manual-title" @click="router.push({ name: 'manual-detail', params: { id: manual.id } })">{{ manual.title }}</td>
            <td><StatusBadge :status="manual.activeStatus" /></td>
            <td>{{ formatDate(manual.updatedAt) }}</td>
            <td class="mono">v{{ manual.activeVersionNumber || '-' }}</td>
            <td>
              <div class="row-actions">
                <select class="status-select" :value="manual.activeStatus || 'DRAFT'" @change="changeRowStatus(manual.id, ($event.target as HTMLSelectElement).value as ManualStatus)">
                  <option value="DRAFT">Borrador</option>
                  <option value="REVIEW">Revisión</option>
                  <option value="APPROVED">Aprobado</option>
                  <option value="PUBLISHED">Publicado</option>
                  <option value="ARCHIVED">Archivado</option>
                  <option value="DEACTIVATED">Dado de baja</option>
                </select>
                <button title="Ver" @click="router.push({ name: 'manual-detail', params: { id: manual.id } })"><Eye :size="14" /></button>
                <button class="lang-action" title="Ver español" @click="router.push({ name: 'manual-detail', params: { id: manual.id }, query: { lang: 'ES' } })">ES</button>
                <button class="lang-action" title="Ver inglés" @click="router.push({ name: 'manual-detail', params: { id: manual.id }, query: { lang: 'EN' } })">EN</button>
                <button title="Editar" @click="router.push({ name: 'manual-editor', params: { id: manual.id } })"><Edit :size="14" /></button>
                <button title="Exportar PDF" :disabled="exportingIds.includes(manual.id)" @click="exportPdf(manual.id)"><FileText :size="14" /></button>
                <button title="Historial" @click="router.push({ name: 'history', params: { id: manual.id } })"><History :size="14" /></button>
                <button title="Deshabilitar" @click="disableManualById(manual.id)"><Ban :size="14" /></button>
                <button title="Eliminar" @click="deleteCandidate = manual.id"><Trash2 :size="14" /></button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <AppModal
      v-if="deleteCandidate"
      title="Mover a papelera"
      description="El manual dejará de aparecer en el listado principal y podrás restaurarlo desde Papelera."
      size="sm"
      @close="deleteCandidate = null"
    >
      <p class="confirm-text">¿Mover este manual a la papelera?</p>
      <template #footer>
        <button type="button" class="btn btn-outline" @click="deleteCandidate = null">Cancelar</button>
        <button type="button" class="btn btn-danger" :disabled="store.loading" @click="deleteManual(deleteCandidate)">Mover</button>
      </template>
    </AppModal>
  </section>
</template>

<style scoped>
.manuals-page {
  padding: 24px;
  display: grid;
  gap: 16px;
}

.head,
.head-actions,
.filters,
.bulk,
.row-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.head {
  justify-content: space-between;
}

.filters {
  padding: 12px;
}

.search-box {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--border);
  background: var(--input-background);
  padding: 8px 10px;
  min-width: 300px;
}

.search-box input {
  border: 0;
  outline: 0;
  background: transparent;
  width: 100%;
}

.filters .field {
  width: 190px;
}

.bulk {
  padding: 10px 12px;
  background: #eaf3fa;
  border: 1px solid #b8cce3;
}

.bulk-status {
  width: 150px;
  background: #fff;
}

.notice-panel {
  padding: 14px;
  display: grid;
  gap: 10px;
  max-width: 760px;
}

.notice-panel h2 {
  margin: 0;
  font-size: 15px;
}

.list-card {
  overflow: auto;
}

.manual-title {
  color: var(--dikoin-blue);
  font-weight: 600;
  cursor: pointer;
}

.row-actions button {
  border: 0;
  background: transparent;
  padding: 4px;
  color: var(--muted-foreground);
}

.status-select {
  width: 112px;
  border: 1px solid var(--border);
  background: #fff;
  padding: 4px 5px;
  font-size: 11px;
  color: var(--foreground);
}

.row-actions button:hover {
  color: var(--dikoin-blue);
  background: var(--dikoin-blue-lighter);
}

.lang-action {
  font-size: 10px;
  font-weight: 500;
  border: 1px solid var(--border) !important;
  border-radius: 3px;
  background: #fff !important;
}

.success-msg {
  background: var(--dikoin-green-light);
  color: #065f46;
  border: 1px solid #86efac;
  padding: 10px;
  border-radius: var(--radius);
}

.warning-msg {
  background: #fff7ed;
  color: #9a3412;
  border: 1px solid #fed7aa;
  padding: 10px;
  border-radius: var(--radius);
}

.confirm-text {
  margin: 0;
}

.suggestions {
  position: absolute;
  top: calc(100% + 5px);
  left: 0;
  width: min(560px, 90vw);
  background: #fff;
  border: 1px solid var(--border);
  box-shadow: 0 10px 24px rgba(0, 0, 0, .12);
  z-index: 20;
}

.suggestions button {
  width: 100%;
  border: 0;
  background: #fff;
  padding: 9px 10px;
  display: grid;
  gap: 2px;
  text-align: left;
}

.suggestions button:hover {
  background: var(--dikoin-blue-lighter);
}

.suggestions small {
  color: var(--muted-foreground);
}
</style>

