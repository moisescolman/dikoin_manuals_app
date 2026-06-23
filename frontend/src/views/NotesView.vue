<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { AlertTriangle, Plus, Save, Trash2, X } from '@lucide/vue'
import { createNotice, deleteNotice, getNoticeUsages, getNotices, updateNotice } from '@/api/notices.api'
import BackendError from '@/components/shared/BackendError.vue'
import type { NoticeTemplateRequest, NoticeTemplateResponse, NoticeType, NoticeUsageResponse } from '@/types/api'

const notes = ref<NoticeTemplateResponse[]>([])
const selectedId = ref<number | null>(null)
const loading = ref(false)
const deleting = ref(false)
const error = ref('')
const saved = ref('')
const deleteCandidateId = ref<number | null>(null)
const deleteUsages = ref<NoticeUsageResponse[]>([])
const previousSelectedId = ref<number | null>(null)

const form = reactive({
  code: '',
  type: 'NOTE' as NoticeType,
  titleEs: '',
  titleEn: '',
  visibleTitleEs: 'Nota',
  visibleTitleEn: '',
  productCategory: '',
  productCodes: '',
  contentEs: '',
  contentEn: '',
  active: true,
})

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    notes.value = await getNotices('NOTE')
    if (selectedId.value && !notes.value.some((note) => note.id === selectedId.value)) {
      selectedId.value = null
    }
    if (!selectedId.value && notes.value.length) {
      select(notes.value[0])
    } else if (!notes.value.length) {
      createNew()
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'No se pudieron cargar las notas'
  } finally {
    loading.value = false
  }
}

function select(note: NoticeTemplateResponse) {
  cancelDelete()
  selectedId.value = note.id
  form.code = note.code
  form.type = 'NOTE'
  form.titleEs = note.titleEs || ''
  form.titleEn = note.titleEn || ''
  form.visibleTitleEs = note.visibleTitleEs || 'Nota'
  form.visibleTitleEn = note.visibleTitleEn || ''
  form.productCategory = note.productCategory || ''
  form.productCodes = note.productCodes || ''
  form.contentEs = note.contentEs || ''
  form.contentEn = note.contentEn || ''
  form.active = note.active
}

function createNew() {
  cancelDelete()
  if (selectedId.value) {
    previousSelectedId.value = selectedId.value
  }
  selectedId.value = null
  form.code = ''
  form.type = 'NOTE'
  form.titleEs = ''
  form.titleEn = ''
  form.visibleTitleEs = 'Nota'
  form.visibleTitleEn = ''
  form.productCategory = ''
  form.productCodes = ''
  form.contentEs = ''
  form.contentEn = ''
  form.active = true
}

function cancelCreate() {
  cancelDelete()
  error.value = ''
  saved.value = ''
  const fallback = notes.value.find((note) => note.id === previousSelectedId.value) || notes.value[0]
  previousSelectedId.value = null
  if (fallback) {
    select(fallback)
    return
  }
  createNew()
}

function payload(): NoticeTemplateRequest {
  const request: NoticeTemplateRequest = {
    type: 'NOTE',
    titleEs: form.titleEs,
    titleEn: form.titleEn || undefined,
    visibleTitleEs: form.visibleTitleEs || 'Nota',
    visibleTitleEn: form.visibleTitleEn || undefined,
    productCategory: form.productCategory || undefined,
    productCodes: form.productCodes || undefined,
    contentEs: form.contentEs,
    contentEn: form.contentEn || undefined,
    active: form.active,
  }
  if (selectedId.value && form.code) {
    request.code = form.code
  }
  return request
}

async function save() {
  loading.value = true
  error.value = ''
  saved.value = ''
  try {
    const result = selectedId.value
      ? await updateNotice(selectedId.value, payload())
      : await createNotice(payload())
    selectedId.value = result.id
    await load()
    select(result)
    saved.value = 'Nota guardada. El codigo se asigna automaticamente al crearla.'
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'No se pudo guardar la nota'
  } finally {
    loading.value = false
  }
}

function usageLabel(usage: NoticeUsageResponse) {
  const section = [usage.sectionNumber, usage.sectionTitle].filter(Boolean).join(' ')
  return section
    ? `${usage.manualCode} - ${usage.manualTitle} (${section})`
    : `${usage.manualCode} - ${usage.manualTitle}`
}

function cancelDelete() {
  deleteCandidateId.value = null
  deleteUsages.value = []
}

async function requestDelete() {
  if (!selectedId.value || loading.value || deleting.value) return
  error.value = ''
  saved.value = ''
  deleting.value = true
  try {
    const usages = await getNoticeUsages(selectedId.value)
    if (usages.length) {
      deleteCandidateId.value = selectedId.value
      deleteUsages.value = usages
      return
    }
    if (window.confirm('Eliminar esta nota de la biblioteca?')) {
      await confirmDelete()
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'No se pudo comprobar el uso de la nota'
  } finally {
    deleting.value = false
  }
}

async function confirmDelete() {
  const id = deleteCandidateId.value || selectedId.value
  if (!id) return
  loading.value = true
  error.value = ''
  saved.value = ''
  try {
    await deleteNotice(id)
    cancelDelete()
    selectedId.value = null
    await load()
    saved.value = 'Nota eliminada de la biblioteca. Las notas ya insertadas se conservaron en los manuales.'
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'No se pudo eliminar la nota'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="notes-page">
    <div class="head">
      <div>
        <h1 class="page-title">Notas</h1>
        <p class="text-muted">Biblioteca de notas reutilizables.</p>
      </div>
      <button class="btn btn-primary" @click="createNew"><Plus :size="15" /> Nueva nota</button>
    </div>

    <BackendError :message="error" />
    <div v-if="saved" class="success-msg">{{ saved }}</div>

    <div class="notes-grid">
      <form class="card editor" @submit.prevent="save">
        <section class="preview-panel" aria-label="Vista previa de la nota">
          <div class="panel-title">
            <span>Vista previa de la nota</span>
            <small>{{ form.active ? 'Activa' : 'Inactiva' }}</small>
          </div>
          <div class="note-preview">
            <strong>{{ form.visibleTitleEs || 'Nota' }}</strong>
            <p>{{ form.contentEs || 'Texto de la nota' }}</p>
          </div>
        </section>

        <div class="form-header">
          <div>
            <span class="eyebrow">{{ selectedId ? 'Editar nota' : 'Nueva nota' }}</span>
            <h2>{{ form.titleEs || 'Sin título' }}</h2>
          </div>
          <div class="readonly-code">
            <span>Codigo</span>
            <strong class="mono">{{ form.code || 'Autogenerado' }}</strong>
          </div>
        </div>

        <label>Titulo de la nota <input v-model="form.titleEs" class="field" required /></label>
        <label>Titulo visible <input v-model="form.visibleTitleEs" class="field" placeholder="Nota" /></label>
        <label>Categoria producto <input v-model="form.productCategory" class="field" /></label>
        <label>Codigos producto <input v-model="form.productCodes" class="field mono" placeholder="FLB10.1, HY100" /></label>
        <label>Texto <textarea v-model="form.contentEs" class="field note-text" rows="8" required /></label>
        <label class="check"><input v-model="form.active" type="checkbox" /> Activa</label>

        <div class="form-actions">
          <button type="submit" class="btn btn-primary" :disabled="loading"><Save :size="15" /> Guardar nota</button>
          <button v-if="selectedId" type="button" class="btn btn-danger" :disabled="loading || deleting" @click="requestDelete">
            <Trash2 :size="15" /> Eliminar nota
          </button>
          <button v-else type="button" class="btn btn-outline" :disabled="loading" @click="cancelCreate">
            <X :size="15" /> Cancelar
          </button>
        </div>

        <div v-if="deleteCandidateId && deleteUsages.length" class="warning-msg">
          <div class="warning-head">
            <AlertTriangle :size="18" />
            <strong>Esta nota esta incluida en estos manuales</strong>
          </div>
          <div class="usage-list">
            <div v-for="usage in deleteUsages" :key="usage.blockId || `${usage.manualId}-${usage.sectionId}`" class="usage-item">
              {{ usageLabel(usage) }}
            </div>
          </div>
          <p>Si eliminas la nota de la biblioteca, el texto ya insertado se conservara dentro de esos manuales.</p>
          <div class="warning-actions">
            <button type="button" class="btn btn-danger" :disabled="loading" @click="confirmDelete">
              <Trash2 :size="15" /> Eliminar nota
            </button>
            <button type="button" class="btn btn-outline" :disabled="loading" @click="cancelDelete">
              <X :size="15" /> Cancelar
            </button>
          </div>
        </div>
      </form>

      <aside class="card list">
        <div class="list-head">
          <div>
            <h2>Biblioteca de notas</h2>
            <span>{{ notes.length }} notas</span>
          </div>
        </div>
        <button v-for="note in notes" :key="note.id" :class="{ active: note.id === selectedId }" @click="select(note)">
          <div class="note-row-main">
            <span class="mono">{{ note.code }}</span>
            <strong>{{ note.titleEs }}</strong>
            <small>{{ note.active ? 'Activa' : 'Inactiva' }}</small>
          </div>
          <div class="note-row-content">
            <span>{{ note.visibleTitleEs || 'Nota' }}</span>
            <p>{{ note.contentEs }}</p>
          </div>
        </button>
        <p v-if="!notes.length && !loading" class="text-muted empty-list">No hay notas creadas.</p>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.notes-page {
  height: 100%;
  padding: 24px;
  display: grid;
  grid-template-rows: auto auto auto 1fr;
  gap: 16px;
  overflow: hidden;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.notes-grid {
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(360px, 460px) minmax(520px, 1fr);
  gap: 16px;
  align-items: start;
  overflow: hidden;
}

.list {
  min-height: 0;
  max-height: 100%;
  padding: 0;
  align-content: start;
  overflow: auto;
}

.list-head {
  position: sticky;
  top: 0;
  z-index: 2;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
  background: #fff;
}

.list-head h2 {
  margin: 0;
  font-size: 15px;
}

.list-head span {
  color: var(--muted-foreground);
  font-size: 12px;
}

.list button {
  width: 100%;
  border: 0;
  border-bottom: 1px solid var(--border);
  background: #fff;
  padding: 12px 16px;
  text-align: left;
  display: grid;
  grid-template-columns: minmax(170px, 240px) minmax(0, 1fr);
  gap: 18px;
  border-radius: 0;
}

.list button.active,
.list button:hover {
  background: var(--dikoin-blue-lighter);
  box-shadow: inset 3px 0 0 var(--dikoin-blue);
}

.note-row-main {
  display: grid;
  gap: 3px;
  align-content: start;
}

.note-row-main strong {
  color: var(--foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note-row-main small {
  color: var(--muted-foreground);
}

.note-row-content {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.note-row-content span {
  color: #92400e;
  font-size: 12px;
  font-weight: 800;
}

.note-row-content p {
  margin: 0;
  color: var(--muted-foreground);
  font-size: 12px;
  line-height: 1.35;
  max-height: 34px;
  overflow: hidden;
}

.empty-list {
  padding: 16px;
}

.editor {
  max-height: 100%;
  padding: 16px;
  display: grid;
  gap: 12px;
  overflow: auto;
}

.form-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: start;
  padding-top: 2px;
}

.form-header h2 {
  margin: 2px 0 0;
  font-size: 17px;
  line-height: 1.25;
}

.eyebrow {
  color: var(--muted-foreground);
  font-size: 11px;
  font-weight: 800;
  text-transform: uppercase;
  letter-spacing: .04em;
}

.readonly-code {
  min-width: 132px;
  border: 1px solid var(--border);
  background: var(--input-background);
  border-radius: var(--radius);
  padding: 8px 10px;
  display: grid;
  gap: 4px;
}

.readonly-code span {
  color: var(--muted-foreground);
  font-size: 12px;
  font-weight: 700;
}

label {
  display: grid;
  gap: 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--muted-foreground);
}

.check {
  display: flex;
  align-items: center;
  gap: 8px;
}

.preview-panel {
  display: grid;
  gap: 10px;
}

.panel-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  color: var(--foreground);
  font-size: 13px;
  font-weight: 800;
}

.panel-title small {
  color: var(--muted-foreground);
  font-size: 11px;
  font-weight: 700;
}

.note-preview {
  border: 1px solid #fed7aa;
  border-left: 4px solid var(--dikoin-orange);
  border-radius: var(--radius);
  background: #fff7ed;
  color: #78350f;
  padding: 12px 14px;
  box-shadow: 0 8px 18px rgba(146, 64, 14, .08);
}

.note-preview p {
  margin: 6px 0 0;
}

.success-msg {
  background: var(--dikoin-green-light);
  color: #065f46;
  border: 1px solid #86efac;
  padding: 10px;
  border-radius: var(--radius);
}

.warning-msg {
  display: grid;
  gap: 10px;
  background: #fff7ed;
  color: #78350f;
  border: 1px solid #fdba74;
  border-radius: var(--radius);
  padding: 12px;
}

.warning-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.usage-list {
  display: grid;
  gap: 6px;
  max-height: 260px;
  overflow: auto;
}

.usage-item {
  background: #fff;
  border: 1px solid #fed7aa;
  border-radius: var(--radius);
  padding: 8px 10px;
}

.warning-msg p {
  margin: 0;
}

.warning-actions,
.form-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.btn-danger {
  background: var(--dikoin-red);
  color: #fff;
  border-color: var(--dikoin-red);
}

@media (max-width: 1100px) {
  .notes-page {
    height: auto;
    overflow: visible;
  }

  .notes-grid {
    grid-template-columns: 1fr;
    overflow: visible;
  }

  .list,
  .editor {
    max-height: none;
    overflow: visible;
  }

  .list button {
    grid-template-columns: 1fr;
    gap: 8px;
  }
}

@media (max-width: 560px) {
  .form-header {
    grid-template-columns: 1fr;
  }

  .readonly-code {
    width: 100%;
  }
}
</style>
