<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RotateCcw, Search, Trash2 } from '@lucide/vue'
import BackendError from '@/components/shared/BackendError.vue'
import AppModal from '@/components/shared/AppModal.vue'
import LangBadge from '@/components/shared/LangBadge.vue'
import StatusBadge from '@/components/shared/StatusBadge.vue'
import { useManualsStore } from '@/stores/manuals.store'
import type { ManualSummaryResponse } from '@/types/api'
import { formatDate } from '@/utils/formatters'

const store = useManualsStore()
const search = ref('')
const message = ref('')
const restoreCandidate = ref<ManualSummaryResponse | null>(null)
const deleteCandidate = ref<ManualSummaryResponse | null>(null)
const restoreNeedsRename = ref(false)
const renameForm = reactive({
  title: '',
  titleEs: '',
  titleEn: '',
})

onMounted(async () => {
  await Promise.all([store.fetchManuals(), store.fetchDeletedManuals()])
})

const deletedManuals = computed(() => store.deletedManuals)

async function performSearch() {
  await store.fetchDeletedManuals(search.value)
}

function normalized(value?: string) {
  return String(value || '').trim().toLowerCase()
}

function manualNames(manual: ManualSummaryResponse) {
  return [manual.title, manual.titleEs, manual.titleEn].map(normalized).filter(Boolean)
}

function hasActiveNameCollision(manual: ManualSummaryResponse) {
  const names = new Set(manualNames(manual))
  return store.manuals.some((active) => manualNames(active).some((name) => names.has(name)))
}

function openRestore(manual: ManualSummaryResponse) {
  restoreCandidate.value = manual
  restoreNeedsRename.value = hasActiveNameCollision(manual)
  renameForm.title = restoreNeedsRename.value ? `${manual.title} restaurado` : manual.title
  renameForm.titleEs = restoreNeedsRename.value ? `${manual.titleEs || manual.title} restaurado` : manual.titleEs || manual.title
  renameForm.titleEn = manual.titleEn || ''
}

function closeRestore() {
  restoreCandidate.value = null
  restoreNeedsRename.value = false
}

async function confirmRestore() {
  if (!restoreCandidate.value) return
  const request = restoreNeedsRename.value
    ? {
        title: renameForm.title.trim(),
        titleEs: renameForm.titleEs.trim(),
        titleEn: renameForm.titleEn.trim() || undefined,
      }
    : undefined
  await store.restoreDeletedManual(restoreCandidate.value.id, request)
  message.value = 'Manual restaurado.'
  closeRestore()
}

function openPermanentDelete(manual: ManualSummaryResponse) {
  deleteCandidate.value = manual
}

async function confirmPermanentDelete() {
  if (!deleteCandidate.value) return
  await store.permanentlyDeleteManual(deleteCandidate.value.id)
  message.value = 'Manual eliminado definitivamente.'
  deleteCandidate.value = null
}
</script>

<template>
  <section class="trash-page">
    <header class="head">
      <div>
        <h1 class="page-title">Papelera</h1>
        <p class="text-muted">{{ deletedManuals.length }} manuales eliminados</p>
      </div>
    </header>

    <BackendError :message="store.error" />
    <div v-if="message" class="success-msg">{{ message }}</div>

    <div class="filters card">
      <div class="search-box">
        <Search :size="14" />
        <input v-model="search" placeholder="Buscar por código, producto o título..." @keydown.enter="performSearch" />
      </div>
      <button class="btn btn-outline" @click="performSearch">Buscar</button>
    </div>

    <div class="card list-card">
      <table class="table">
        <thead>
          <tr>
            <th>Código</th><th>Tipo</th><th>Producto</th><th>Título ES</th><th>Título EN</th><th>Estado</th><th>Idiomas</th><th>Eliminado</th><th>Acciones</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="store.loading"><td colspan="9">Cargando papelera...</td></tr>
          <tr v-else-if="!deletedManuals.length"><td colspan="9">No hay manuales en la papelera.</td></tr>
          <tr v-for="manual in deletedManuals" :key="manual.id">
            <td class="mono">{{ manual.code }}</td>
            <td class="mono">{{ manual.documentTypeCode || '-' }}</td>
            <td>{{ manual.productCode }}</td>
            <td class="manual-title">{{ manual.titleEs || manual.title }}</td>
            <td>{{ manual.titleEn || '-' }}</td>
            <td><StatusBadge :status="manual.activeStatus" /></td>
            <td><div class="langs"><LangBadge label="ES" :ready="manual.esReady" /><LangBadge label="EN" :ready="manual.enReady" /></div></td>
            <td>{{ formatDate(manual.deletedAt) }}</td>
            <td>
              <div class="row-actions">
                <button title="Restaurar" @click="openRestore(manual)"><RotateCcw :size="15" /></button>
                <button class="danger" title="Eliminar definitivamente" @click="openPermanentDelete(manual)"><Trash2 :size="15" /></button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <AppModal
      v-if="restoreCandidate"
      :title="restoreNeedsRename ? 'Nombre duplicado' : 'Restaurar manual'"
      :description="restoreNeedsRename ? 'Ya existe un manual activo con ese nombre. Renombra el manual antes de restaurarlo.' : 'El manual volverá al listado principal.'"
      @close="closeRestore"
    >
      <div class="restore-body">
        <div class="manual-summary">
          <span class="mono">{{ restoreCandidate.code }}</span>
          <strong>{{ restoreCandidate.title }}</strong>
          <small>{{ restoreCandidate.productCode }}</small>
        </div>
        <div v-if="restoreNeedsRename" class="rename-grid">
          <label>Nombre principal <input v-model="renameForm.title" class="field" required /></label>
          <label>Título ES <input v-model="renameForm.titleEs" class="field" required /></label>
          <label>Título EN <input v-model="renameForm.titleEn" class="field" /></label>
        </div>
      </div>
      <template #footer>
        <button type="button" class="btn btn-outline" @click="closeRestore">Cancelar</button>
        <button type="button" class="btn btn-primary" :disabled="store.loading || (restoreNeedsRename && (!renameForm.title.trim() || !renameForm.titleEs.trim()))" @click="confirmRestore">
          Restaurar
        </button>
      </template>
    </AppModal>

    <AppModal
      v-if="deleteCandidate"
      title="Eliminar definitivamente"
      description="Esta acción eliminará el manual de forma permanente."
      size="sm"
      @close="deleteCandidate = null"
    >
      <p class="confirm-text">¿Eliminar definitivamente {{ deleteCandidate.code }}?</p>
      <template #footer>
        <button type="button" class="btn btn-outline" @click="deleteCandidate = null">Cancelar</button>
        <button type="button" class="btn btn-danger" :disabled="store.loading" @click="confirmPermanentDelete">Eliminar</button>
      </template>
    </AppModal>
  </section>
</template>

<style scoped>
.trash-page {
  padding: 24px;
  display: grid;
  gap: 16px;
}

.head,
.filters,
.langs,
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
  min-width: 320px;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--border);
  background: var(--input-background);
  padding: 8px 10px;
}

.search-box input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
}

.list-card {
  overflow: auto;
}

.manual-title {
  color: var(--dikoin-blue);
  font-weight: 600;
}

.row-actions button {
  border: 0;
  background: transparent;
  color: var(--muted-foreground);
  padding: 5px;
}

.row-actions button:hover {
  color: var(--dikoin-blue);
  background: var(--dikoin-blue-lighter);
}

.row-actions .danger:hover {
  color: var(--dikoin-red);
  background: var(--dikoin-red-light);
}

.success-msg {
  background: var(--dikoin-green-light);
  color: #065f46;
  border: 1px solid #86efac;
  padding: 10px;
  border-radius: var(--radius);
}

.restore-body,
.rename-grid {
  display: grid;
  gap: 12px;
}

.manual-summary {
  display: grid;
  gap: 4px;
  padding: 12px;
  border: 1px solid var(--border);
  background: var(--input-background);
}

.manual-summary small {
  color: var(--muted-foreground);
}

label {
  display: grid;
  gap: 6px;
  color: var(--muted-foreground);
  font-size: 12px;
  font-weight: 600;
}

.confirm-text {
  margin: 0;
}

.btn-danger {
  background: var(--dikoin-red);
  border-color: var(--dikoin-red);
  color: #fff;
}
</style>
