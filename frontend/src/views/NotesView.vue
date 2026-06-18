<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Plus, Save } from '@lucide/vue'
import { createNotice, getNotices, updateNotice } from '@/api/notices.api'
import BackendError from '@/components/shared/BackendError.vue'
import type { NoticeTemplateResponse, NoticeType } from '@/types/api'

const notes = ref<NoticeTemplateResponse[]>([])
const selectedId = ref<number | null>(null)
const loading = ref(false)
const error = ref('')
const saved = ref('')
const form = reactive({
  code: '',
  type: 'NOTE' as NoticeType,
  titleEs: 'Nota',
  titleEn: '',
  productCategory: '',
  productCodes: '',
  contentEs: '',
  contentEn: '',
  active: true,
})

const selected = computed(() => notes.value.find((note) => note.id === selectedId.value))

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    notes.value = await getNotices()
    if (!selectedId.value && notes.value.length) select(notes.value[0])
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'No se pudieron cargar las notas'
  } finally {
    loading.value = false
  }
}

function select(note: NoticeTemplateResponse) {
  selectedId.value = note.id
  form.code = note.code
  form.type = note.type
  form.titleEs = note.titleEs || 'Nota'
  form.titleEn = note.titleEn || ''
  form.productCategory = note.productCategory || ''
  form.productCodes = note.productCodes || ''
  form.contentEs = note.contentEs || ''
  form.contentEn = note.contentEn || ''
  form.active = note.active
}

function createNew() {
  selectedId.value = null
  form.code = `NOTA-${String(notes.value.length + 1).padStart(3, '0')}`
  form.type = 'NOTE'
  form.titleEs = 'Nota'
  form.titleEn = ''
  form.productCategory = ''
  form.productCodes = ''
  form.contentEs = ''
  form.contentEn = ''
  form.active = true
}

async function save() {
  loading.value = true
  error.value = ''
  saved.value = ''
  try {
    const payload = { ...form }
    const result = selectedId.value
      ? await updateNotice(selectedId.value, payload)
      : await createNotice(payload)
    await load()
    select(result)
    saved.value = 'Nota guardada. Los manuales enlazados usarán esta versión.'
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'No se pudo guardar la nota'
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
        <p class="text-muted">Biblioteca de notas y advertencias reutilizables.</p>
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
          <small>{{ note.type }}</small>
        </button>
        <p v-if="!notes.length && !loading" class="text-muted">No hay notas creadas.</p>
      </aside>

      <form class="card editor" @submit.prevent="save">
        <div class="form-row">
          <label>Código <input v-model="form.code" class="field mono" required /></label>
          <label>Tipo
            <select v-model="form.type" class="field">
              <option value="NOTE">Nota</option>
              <option value="WARNING">Advertencia</option>
              <option value="ALERT">Alerta</option>
              <option value="SUGGESTION">Sugerencia</option>
            </select>
          </label>
        </div>
        <label>Título <input v-model="form.titleEs" class="field" required /></label>
        <div class="form-row">
          <label>Categoría producto <input v-model="form.productCategory" class="field" /></label>
          <label>Códigos producto <input v-model="form.productCodes" class="field mono" placeholder="FLB10.1, HY100" /></label>
        </div>
        <label>Texto <textarea v-model="form.contentEs" class="field note-text" rows="8" required /></label>
        <label class="check"><input v-model="form.active" type="checkbox" /> Activa</label>

        <div class="note-preview">
          <strong>{{ form.titleEs || 'Nota' }}</strong>
          <p>{{ form.contentEs || 'Texto de la nota' }}</p>
        </div>

        <button class="btn btn-primary" :disabled="loading"><Save :size="15" /> Guardar nota</button>
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
.form-row { display: grid; grid-template-columns: 1fr 180px; gap: 12px; }
label { display: grid; gap: 6px; font-size: 12px; font-weight: 700; color: var(--muted-foreground); }
.check { display: flex; align-items: center; gap: 8px; }
.note-preview { border-left: 4px solid var(--dikoin-orange); background: #fff7ed; color: #78350f; padding: 12px; }
.note-preview p { margin: 6px 0 0; }
.success-msg { background: var(--dikoin-green-light); color: #065f46; border: 1px solid #86efac; padding: 10px; border-radius: var(--radius); }
@media (max-width: 920px) { .notes-grid, .form-row { grid-template-columns: 1fr; } }
</style>
