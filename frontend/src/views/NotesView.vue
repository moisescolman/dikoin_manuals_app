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
      <aside class="card list">
        <button v-for="note in notes" :key="note.id" :class="{ active: note.id === selectedId }" @click="select(note)">
          <span class="mono">{{ note.code }}</span>
          <strong>{{ note.titleEs }}</strong>
          <small>{{ note.visibleTitleEs || 'Nota' }}</small>
        </button>
        <p v-if="!notes.length && !loading" class="text-muted">No hay notas creadas.</p>
      </aside>

      <form class="card editor" @submit.prevent="save">
        <div class="readonly-code">
          <span>Codigo</span>
          <strong class="mono">{{ form.code || 'Automatico al guardar' }}</strong>
        </div>
        <label>Titulo de la nota <input v-model="form.titleEs" class="field" required /></label>
        <label>Titulo visible <input v-model="form.visibleTitleEs" class="field" placeholder="Nota" /></label>
        <div class="form-row">
          <label>Categoria producto <input v-model="form.productCategory" class="field" /></label>
          <label>Codigos producto <input v-model="form.productCodes" class="field mono" placeholder="FLB10.1, HY100" /></label>
        </div>
        <label>Texto <textarea v-model="form.contentEs" class="field note-text" rows="8" required /></label>
        <label class="check"><input v-model="form.active" type="checkbox" /> Activa</label>

        <div class="note-preview">
          <strong>{{ form.visibleTitleEs || 'Nota' }}</strong>
          <p>{{ form.contentEs || 'Texto de la nota' }}</p>
        </div>

        <div class="form-actions">
          <button type="submit" class="btn btn-primary" :disabled="loading"><Save :size="15" /> Guardar nota</button>
          <button type="button" class="btn btn-danger" :disabled="!selectedId || loading || deleting" @click="requestDelete">
            <Trash2 :size="15" /> Eliminar nota
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
    </div>
  </section>
</template>

<style scoped>
.notes-page { padding: 24px; display: grid; gap: 16px; }
.head { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.notes-grid { display: grid; grid-template-columns: 340px minmax(0, 1fr); gap: 16px; }
.list { padding: 8px; display: grid; gap: 6px; align-content: start; }
.list button { border: 1px solid transparent; background: #fff; padding: 10px; text-align: left; display: grid; gap: 3px; border-radius: var(--radius); }
.list button.active, .list button:hover { border-color: var(--dikoin-blue); background: var(--dikoin-blue-lighter); }
.list small { color: var(--muted-foreground); }
.editor { padding: 16px; display: grid; gap: 12px; }
.readonly-code { border: 1px solid var(--border); background: var(--input-background); border-radius: var(--radius); padding: 10px; display: grid; gap: 4px; }
.readonly-code span { color: var(--muted-foreground); font-size: 12px; font-weight: 700; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
label { display: grid; gap: 6px; font-size: 12px; font-weight: 700; color: var(--muted-foreground); }
.check { display: flex; align-items: center; gap: 8px; }
.note-preview { border-left: 4px solid var(--dikoin-orange); background: #fff7ed; color: #78350f; padding: 12px; }
.note-preview p { margin: 6px 0 0; }
.success-msg { background: var(--dikoin-green-light); color: #065f46; border: 1px solid #86efac; padding: 10px; border-radius: var(--radius); }
.warning-msg { display: grid; gap: 10px; background: #fff7ed; color: #78350f; border: 1px solid #fdba74; border-radius: var(--radius); padding: 12px; }
.warning-head { display: flex; align-items: center; gap: 8px; }
.usage-list { display: grid; gap: 6px; max-height: 260px; overflow: auto; }
.usage-item { background: #fff; border: 1px solid #fed7aa; border-radius: var(--radius); padding: 8px 10px; }
.warning-msg p { margin: 0; }
.warning-actions, .form-actions { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.btn-danger { background: var(--dikoin-red); color: #fff; border-color: var(--dikoin-red); }
@media (max-width: 920px) { .notes-grid, .form-row { grid-template-columns: 1fr; } }
</style>
