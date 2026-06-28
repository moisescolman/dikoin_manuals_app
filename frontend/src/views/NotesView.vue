<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { BookOpenText, Pencil, Plus, Save, Search, Trash2, X } from '@lucide/vue'
import { createNotice, deleteNotice, getNoticeUsages, getNotices, updateNotice } from '@/api/notices.api'
import { getProductCategories, getProducts } from '@/api/products.api'
import AppModal from '@/components/shared/AppModal.vue'
import BackendError from '@/components/shared/BackendError.vue'
import ProductCategoryMultiSelect from '@/components/shared/ProductCategoryMultiSelect.vue'
import type { NoticeTemplateRequest, NoticeTemplateResponse, NoticeType, NoticeUsageResponse, ProductCategoryResponse, ProductResponse } from '@/types/api'

const notes = ref<NoticeTemplateResponse[]>([])
const selectedId = ref<number | null>(null)
const loading = ref(false)
const saving = ref(false)
const deleting = ref(false)
const loadingUsages = ref(false)
const error = ref('')
const saved = ref('')
const categories = ref<ProductCategoryResponse[]>([])
const products = ref<ProductResponse[]>([])
const selectedCategoryCodes = ref<string[]>([])
const searchQuery = ref('')
const searchFocused = ref(false)
const searchUsageCache = ref<Record<number, NoticeUsageResponse[]>>({})
const loadingSearchUsages = ref(false)
const searchUsagesLoaded = ref(false)
let searchUsagesPromise: Promise<void> | null = null

const editOpen = ref(false)
const usagesOpen = ref(false)
const deleteOpen = ref(false)
const usagesNote = ref<NoticeTemplateResponse | null>(null)
const deleteCandidate = ref<NoticeTemplateResponse | null>(null)
const usages = ref<NoticeUsageResponse[]>([])
const deleteUsages = ref<NoticeUsageResponse[]>([])

const form = reactive({
  code: '',
  type: 'NOTE' as NoticeType,
  title: '',
  visibleTitleEs: 'Nota',
  visibleTitleEn: 'Note',
  productCategory: '',
  productCodes: '',
  contentEs: '',
  contentEn: '',
  active: true,
})

const selectedNote = computed(() => notes.value.find((note) => note.id === selectedId.value) || notes.value[0] || null)
const uniqueUsages = computed(() => uniqueManuals(usages.value))
const uniqueDeleteUsages = computed(() => uniqueManuals(deleteUsages.value))
const editTitle = computed(() => form.code ? `Editar ${form.code}` : 'Nueva nota')
const productNameByCode = computed(() => {
  const values = new Map<string, string>()
  products.value.forEach((product) => {
    values.set(product.code.toUpperCase(), product.name || product.nameEs || product.nameEn || product.code)
  })
  return values
})
const noteSearchResults = computed(() => {
  const query = normalizeSearch(searchQuery.value)
  if (!query) return notes.value.map((note) => ({ note, match: '' }))
  return notes.value
    .map((note) => ({ note, match: searchMatch(note, query) }))
    .filter((result) => result.match)
})
const filteredNotes = computed(() => noteSearchResults.value.map((result) => result.note))
const searchSuggestions = computed(() => noteSearchResults.value.slice(0, 6))
const notesCountLabel = computed(() => {
  if (!searchQuery.value.trim()) return `${notes.value.length} notas`
  return `${filteredNotes.value.length} de ${notes.value.length} notas`
})

onMounted(initialize)
watch(searchQuery, (value) => {
  if (value.trim().length >= 2) {
    void ensureSearchUsages()
  }
})

async function initialize() {
  try {
    const [categoryValues, productValues] = await Promise.all([
      getProductCategories(),
      getProducts(),
    ])
    categories.value = categoryValues
    products.value = productValues
  } catch {
    categories.value = []
    products.value = []
  }
  await load()
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    notes.value = await getNotices('NOTE')
    if (selectedId.value && !notes.value.some((note) => note.id === selectedId.value)) {
      selectedId.value = null
    }
    if (!selectedId.value && notes.value.length) {
      selectedId.value = notes.value[0].id
    }
    searchUsagesLoaded.value = false
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'No se pudieron cargar las notas'
  } finally {
    loading.value = false
  }
}

function select(note: NoticeTemplateResponse) {
  selectedId.value = note.id
  saved.value = ''
}

function categoryCodes(value?: string) {
  if (!value) return []
  const available = new Set(categories.value.map((category) => category.code.toUpperCase()))
  return value
    .split(/[|,;]/)
    .map((entry) => entry.trim().split(/\s+-\s+/)[0]?.trim() || '')
    .filter((code, index, values) => Boolean(code) && available.has(code.toUpperCase()) && values.indexOf(code) === index)
}

function resetForm() {
  form.code = ''
  form.type = 'NOTE'
  form.title = ''
  form.visibleTitleEs = 'Nota'
  form.visibleTitleEn = 'Note'
  form.productCategory = ''
  selectedCategoryCodes.value = []
  form.productCodes = ''
  form.contentEs = ''
  form.contentEn = ''
  form.active = true
}

function fillForm(note: NoticeTemplateResponse) {
  form.code = note.code
  form.type = 'NOTE'
  form.title = note.title || ''
  form.visibleTitleEs = note.visibleTitleEs || 'Nota'
  form.visibleTitleEn = note.visibleTitleEn || 'Note'
  form.productCategory = note.productCategory || ''
  selectedCategoryCodes.value = categoryCodes(note.productCategory)
  form.productCodes = note.productCodes || ''
  form.contentEs = note.contentEs || ''
  form.contentEn = note.contentEn || ''
  form.active = note.active
}

function openCreate() {
  error.value = ''
  saved.value = ''
  selectedId.value = null
  resetForm()
  editOpen.value = true
}

function openEdit(note: NoticeTemplateResponse) {
  select(note)
  error.value = ''
  saved.value = ''
  fillForm(note)
  editOpen.value = true
}

function closeEdit() {
  editOpen.value = false
  if (!selectedId.value && notes.value.length) {
    selectedId.value = notes.value[0].id
  }
}

function payload(): NoticeTemplateRequest {
  const request: NoticeTemplateRequest = {
    type: 'NOTE',
    title: form.title.trim(),
    visibleTitleEs: form.visibleTitleEs || 'Nota',
    visibleTitleEn: form.visibleTitleEn || 'Note',
    productCategory: selectedCategoryCodes.value.join(', ') || undefined,
    productCodes: form.productCodes || undefined,
    contentEs: form.contentEs,
    contentEn: form.contentEn,
    active: form.active,
  }
  if (selectedId.value && form.code) {
    request.code = form.code
  }
  return request
}

async function save() {
  saving.value = true
  error.value = ''
  saved.value = ''
  try {
    const result = selectedId.value
      ? await updateNotice(selectedId.value, payload())
      : await createNotice(payload())
    selectedId.value = result.id
    await load()
    selectedId.value = result.id
    editOpen.value = false
    saved.value = 'Nota guardada.'
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'No se pudo guardar la nota'
  } finally {
    saving.value = false
  }
}

async function openUsages(note: NoticeTemplateResponse) {
  select(note)
  usagesNote.value = note
  usages.value = []
  usagesOpen.value = true
  loadingUsages.value = true
  error.value = ''
  try {
    usages.value = await getNoticeUsages(note.id)
    searchUsageCache.value = { ...searchUsageCache.value, [note.id]: usages.value }
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'No se pudieron cargar los manuales'
  } finally {
    loadingUsages.value = false
  }
}

async function openDelete(note: NoticeTemplateResponse) {
  select(note)
  deleteCandidate.value = note
  deleteUsages.value = []
  deleteOpen.value = true
  loadingUsages.value = true
  error.value = ''
  saved.value = ''
  try {
    deleteUsages.value = await getNoticeUsages(note.id)
    searchUsageCache.value = { ...searchUsageCache.value, [note.id]: deleteUsages.value }
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'No se pudo comprobar el uso de la nota'
  } finally {
    loadingUsages.value = false
  }
}

async function confirmDelete() {
  if (!deleteCandidate.value) return
  deleting.value = true
  error.value = ''
  saved.value = ''
  try {
    await deleteNotice(deleteCandidate.value.id)
    deleteOpen.value = false
    deleteCandidate.value = null
    deleteUsages.value = []
    selectedId.value = null
    await load()
    saved.value = 'Nota eliminada. Los bloques insertados quedan como contenido estático en sus manuales.'
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'No se pudo eliminar la nota'
  } finally {
    deleting.value = false
  }
}

function languages(note: NoticeTemplateResponse) {
  const values = []
  if (note.contentEs?.trim()) values.push('ES')
  if (note.contentEn?.trim()) values.push('EN')
  return values.length ? values.join(' | ') : '-'
}

function normalizeSearch(value?: string) {
  return (value || '')
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()
    .trim()
}

function splitCodes(value?: string) {
  if (!value) return []
  return value
    .split(/[|,;]/)
    .map((entry) => entry.trim())
    .filter(Boolean)
}

function productNamesForCodes(codes: string[]) {
  return codes
    .map((code) => productNameByCode.value.get(code.toUpperCase()))
    .filter((name): name is string => Boolean(name))
}

function usagesForSearch(note: NoticeTemplateResponse) {
  return searchUsageCache.value[note.id] || []
}

function searchMatch(note: NoticeTemplateResponse, query: string) {
  const productCodes = splitCodes(note.productCodes)
  const usageValues = usagesForSearch(note)
  const usageProductCodes = usageValues.map((usage) => usage.productCode).filter(Boolean)
  const fields = [
    { label: `Codigo: ${note.code}`, value: note.code },
    { label: `Titulo: ${note.title}`, value: note.title },
    { label: `Titulo visible ES: ${note.visibleTitleEs || 'Nota'}`, value: note.visibleTitleEs },
    { label: `Titulo visible EN: ${note.visibleTitleEn || 'Note'}`, value: note.visibleTitleEn },
    { label: `Categoria producto: ${note.productCategory}`, value: note.productCategory },
    { label: `Codigo producto: ${productCodes.join(', ')}`, value: productCodes.join(' ') },
    { label: `Producto: ${productNamesForCodes(productCodes).join(', ')}`, value: productNamesForCodes(productCodes).join(' ') },
    { label: 'Contenido ES', value: note.contentEs },
    { label: 'Contenido EN', value: note.contentEn },
    { label: `Manual: ${usageValues.map((usage) => usage.manualTitle).join(', ')}`, value: usageValues.map((usage) => usage.manualTitle).join(' ') },
    { label: `Codigo manual: ${usageValues.map((usage) => usage.manualCode).join(', ')}`, value: usageValues.map((usage) => usage.manualCode).join(' ') },
    { label: `Producto manual: ${usageProductCodes.join(', ')}`, value: usageProductCodes.join(' ') },
    { label: `Nombre producto: ${productNamesForCodes(usageProductCodes).join(', ')}`, value: productNamesForCodes(usageProductCodes).join(' ') },
  ]
  const match = fields.find((field) => normalizeSearch(field.value).includes(query))
  return match?.label || ''
}

async function ensureSearchUsages() {
  if (searchUsagesLoaded.value) return
  if (searchUsagesPromise) return searchUsagesPromise
  const missingNotes = notes.value.filter((note) => searchUsageCache.value[note.id] === undefined)
  if (!missingNotes.length) {
    searchUsagesLoaded.value = true
    return
  }
  loadingSearchUsages.value = true
  searchUsagesPromise = Promise.all(missingNotes.map(async (note) => {
    try {
      return [note.id, await getNoticeUsages(note.id)] as const
    } catch {
      return [note.id, []] as const
    }
  }))
    .then((entries) => {
      searchUsageCache.value = {
        ...searchUsageCache.value,
        ...Object.fromEntries(entries),
      }
      searchUsagesLoaded.value = true
    })
    .finally(() => {
      loadingSearchUsages.value = false
      searchUsagesPromise = null
    })
  return searchUsagesPromise
}

function selectSuggestion(note: NoticeTemplateResponse) {
  select(note)
  searchFocused.value = false
}

function closeSearchSuggestionsLater() {
  window.setTimeout(() => { searchFocused.value = false }, 150)
}

function uniqueManuals(values: NoticeUsageResponse[]) {
  const byManual = new Map<number, NoticeUsageResponse>()
  values.forEach((usage) => {
    if (!byManual.has(usage.manualId)) {
      byManual.set(usage.manualId, usage)
    }
  })
  return [...byManual.values()].sort((a, b) => a.manualCode.localeCompare(b.manualCode))
}

function formatDate(value?: string) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('es-ES', {
    day: '2-digit',
    month: '2-digit',
    year: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <section class="notes-page">
    <div class="head">
      <div>
        <h1 class="page-title">Notas</h1>
        <p class="text-muted">Biblioteca de notas reutilizables.</p>
      </div>
      <button class="btn btn-primary" @click="openCreate"><Plus :size="15" /> Nueva nota</button>
    </div>

    <BackendError :message="error" />
    <div v-if="saved" class="success-msg">{{ saved }}</div>

    <div class="notes-shell">
      <section class="card notes-inbox" aria-label="Lista de notas">
        <div class="list-head">
          <div>
            <h2>Biblioteca de notas</h2>
            <span>{{ notesCountLabel }}</span>
          </div>
          <div class="note-search">
            <Search :size="14" />
            <input
              v-model="searchQuery"
              type="search"
              placeholder="Buscar por codigo, titulo, manual, producto o contenido..."
              aria-label="Buscar notas"
              @focus="searchFocused = true"
              @blur="closeSearchSuggestionsLater"
            />
            <button v-if="searchQuery" type="button" title="Limpiar busqueda" @mousedown.prevent="searchQuery = ''">
              <X :size="14" />
            </button>
            <div v-if="searchFocused && searchQuery" class="search-suggestions">
              <button
                v-for="result in searchSuggestions"
                :key="result.note.id"
                type="button"
                @mousedown.prevent="selectSuggestion(result.note)"
              >
                <span class="mono">{{ result.note.code }}</span>
                <strong>{{ result.note.title }}</strong>
                <small>{{ result.match }}</small>
              </button>
              <div v-if="loadingSearchUsages" class="suggestion-state">Buscando en manuales...</div>
              <div v-else-if="!searchSuggestions.length" class="suggestion-state">No hay sugerencias.</div>
            </div>
          </div>
        </div>

        <div class="table-scroll">
          <table class="inbox-table">
            <thead>
              <tr>
                <th>Código</th>
                <th>Título</th>
                <th>Idiomas</th>
                <th>Categoría producto</th>
                <th>Creación</th>
                <th>Última modificación</th>
                <th class="actions-col">Acciones</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading && !notes.length">
                <td colspan="7">Cargando notas...</td>
              </tr>
              <tr v-else-if="!notes.length">
                <td colspan="7">No hay notas creadas.</td>
              </tr>
              <tr v-else-if="!filteredNotes.length">
                <td colspan="7">No hay notas que coincidan con la busqueda.</td>
              </tr>
              <tr v-for="note in filteredNotes" :key="note.id" :class="{ selected: note.id === selectedId }" @click="select(note)">
                <td class="mono code-cell">{{ note.code }}</td>
                <td class="title-cell">
                  <strong>{{ note.title }}</strong>
                  <span>{{ note.visibleTitleEs || 'Nota' }} / {{ note.visibleTitleEn || 'Note' }}</span>
                </td>
                <td><span class="language-pill">{{ languages(note) }}</span></td>
                <td>{{ note.productCategory || '-' }}</td>
                <td>{{ formatDate(note.createdAt) }}</td>
                <td>{{ formatDate(note.updatedAt || note.createdAt) }}</td>
                <td>
                  <div class="quick-actions">
                    <button type="button" title="Manuales con esta nota" @click.stop="openUsages(note)">
                      <BookOpenText :size="16" />
                    </button>
                    <button type="button" title="Editar" @click.stop="openEdit(note)">
                      <Pencil :size="16" />
                    </button>
                    <button type="button" title="Eliminar" @click.stop="openDelete(note)">
                      <Trash2 :size="16" />
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <aside class="preview-column" aria-label="Vista previa de la nota">
        <section class="card preview-panel">
          <div class="panel-title">
            <div>
              <span>Vista previa</span>
              <strong>{{ selectedNote?.code || 'Sin nota' }}</strong>
            </div>
            <small>{{ selectedNote?.active === false ? 'Inactiva' : 'Activa' }}</small>
          </div>

          <article class="note-preview">
            <span>ES</span>
            <strong>{{ selectedNote?.visibleTitleEs || 'Nota' }}</strong>
            <p>{{ selectedNote?.contentEs || 'Texto de la nota' }}</p>
          </article>

          <article class="note-preview">
            <span>EN</span>
            <strong>{{ selectedNote?.visibleTitleEn || 'Note' }}</strong>
            <p>{{ selectedNote?.contentEn || 'Note text' }}</p>
          </article>
        </section>
      </aside>
    </div>

    <AppModal
      v-if="usagesOpen"
      :title="`Manuales que contienen ${usagesNote?.code || ''}`"
      :description="usagesNote?.title"
      size="lg"
      @close="usagesOpen = false"
    >
      <div v-if="loadingUsages" class="modal-state">Cargando manuales...</div>
      <div v-else-if="!uniqueUsages.length" class="modal-state">Esta nota no está incluida en ningún manual.</div>
      <table v-else class="modal-table">
        <thead>
          <tr><th>Código</th><th>Nombre</th></tr>
        </thead>
        <tbody>
          <tr v-for="usage in uniqueUsages" :key="usage.manualId">
            <td class="mono">{{ usage.manualCode }}</td>
            <td>{{ usage.manualTitle }}</td>
          </tr>
        </tbody>
      </table>
    </AppModal>

    <AppModal
      v-if="editOpen"
      :title="editTitle"
      description="Formulario de nota reutilizable."
      size="lg"
      @close="closeEdit"
    >
      <form class="edit-form" @submit.prevent="save">
        <div class="form-top">
          <div class="readonly-code">
            <span>Código</span>
            <strong class="mono">{{ form.code || 'Autogenerado' }}</strong>
          </div>
          <label>Título <input v-model="form.title" class="field" required /></label>
          <label class="check"><input v-model="form.active" type="checkbox" /> Activa</label>
        </div>

        <div class="metadata-grid">
          <ProductCategoryMultiSelect v-model="selectedCategoryCodes" :categories="categories" />
          <label>Códigos producto <input v-model="form.productCodes" class="field mono" placeholder="FLB10.1, HY100" /></label>
        </div>

        <div class="language-editors" aria-label="Contenido bilingüe de la nota">
          <section class="language-panel">
            <div class="language-panel-title">
              <span>Español</span>
              <small>ES</small>
            </div>
            <label>Título visible <input v-model="form.visibleTitleEs" class="field" placeholder="Nota" /></label>
            <label>Texto <textarea v-model="form.contentEs" class="field note-text" rows="10" required /></label>
          </section>

          <section class="language-panel">
            <div class="language-panel-title">
              <span>English</span>
              <small>EN</small>
            </div>
            <label>Visible title <input v-model="form.visibleTitleEn" class="field" placeholder="Note" /></label>
            <label>Text <textarea v-model="form.contentEn" class="field note-text" rows="10" /></label>
          </section>
        </div>
      </form>

      <template #footer>
        <button type="button" class="btn btn-outline" :disabled="saving" @click="closeEdit"><X :size="15" /> Cancelar</button>
        <button type="button" class="btn btn-primary" :disabled="saving" @click="save"><Save :size="15" /> Guardar</button>
      </template>
    </AppModal>

    <AppModal
      v-if="deleteOpen"
      title="Eliminar nota"
      :description="deleteCandidate ? `${deleteCandidate.code} - ${deleteCandidate.title}` : undefined"
      size="lg"
      @close="deleteOpen = false"
    >
      <div class="delete-warning">
        <p>
          Al eliminar la nota, seguirá incluida en los manuales listados pero dejará de estar vinculada a la biblioteca.
          El contenido quedará como nota estática.
        </p>
      </div>

      <div v-if="loadingUsages" class="modal-state">Comprobando manuales...</div>
      <div v-else-if="!uniqueDeleteUsages.length" class="modal-state">Esta nota no está incluida en ningún manual.</div>
      <table v-else class="modal-table">
        <thead>
          <tr><th>Código</th><th>Nombre</th></tr>
        </thead>
        <tbody>
          <tr v-for="usage in uniqueDeleteUsages" :key="usage.manualId">
            <td class="mono">{{ usage.manualCode }}</td>
            <td>{{ usage.manualTitle }}</td>
          </tr>
        </tbody>
      </table>

      <template #footer>
        <button type="button" class="btn btn-outline" :disabled="deleting" @click="deleteOpen = false">Cancelar</button>
        <button type="button" class="btn btn-danger" :disabled="deleting" @click="confirmDelete"><Trash2 :size="15" /> Eliminar</button>
      </template>
    </AppModal>
  </section>
</template>

<style scoped>
.notes-page {
  min-height: 100%;
  padding: 24px;
  display: grid;
  grid-template-rows: auto auto auto 1fr;
  gap: 16px;
  overflow: auto;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.notes-shell {
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(340px, .8fr);
  gap: 16px;
  align-items: stretch;
}

.notes-inbox,
.preview-panel {
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.list-head {
  flex: 0 0 auto;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
  background: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.list-head h2 {
  margin: 0;
  font-size: 15px;
}

.list-head span {
  color: var(--muted-foreground);
  font-size: 12px;
}

.note-search {
  position: relative;
  width: min(520px, 58%);
  min-width: 320px;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--input-background);
  padding: 8px 10px;
}

.note-search input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--foreground);
}

.note-search input::-webkit-search-cancel-button {
  display: none;
}

.note-search > button {
  width: 24px;
  height: 24px;
  border: 0;
  background: transparent;
  color: var(--muted-foreground);
  display: grid;
  place-items: center;
  padding: 0;
}

.note-search > button:hover {
  color: var(--dikoin-blue);
}

.search-suggestions {
  position: absolute;
  z-index: 20;
  top: calc(100% + 6px);
  right: 0;
  width: min(520px, 90vw);
  max-height: 300px;
  overflow: auto;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: #fff;
  box-shadow: 0 16px 36px rgba(15, 23, 42, .14);
  padding: 6px;
}

.search-suggestions button {
  width: 100%;
  border: 0;
  background: transparent;
  border-radius: var(--radius);
  padding: 8px 9px;
  text-align: left;
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 2px 8px;
  color: var(--foreground);
}

.search-suggestions button:hover {
  background: var(--dikoin-blue-lighter);
}

.search-suggestions strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-suggestions small {
  grid-column: 1 / -1;
  color: var(--muted-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.suggestion-state {
  padding: 9px;
  color: var(--muted-foreground);
  font-size: 12px;
}

.table-scroll {
  min-height: 0;
  flex: 1;
  overflow: auto;
}

.inbox-table,
.modal-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.inbox-table th,
.inbox-table td,
.modal-table th,
.modal-table td {
  border-bottom: 1px solid var(--border);
  padding: 10px 12px;
  text-align: left;
  vertical-align: middle;
}

.inbox-table th,
.modal-table th {
  color: var(--muted-foreground);
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
}

.inbox-table tbody tr {
  cursor: pointer;
}

.inbox-table tbody tr:hover td,
.inbox-table tbody tr.selected td {
  background: var(--dikoin-blue-lighter);
}

.code-cell {
  width: 92px;
  color: var(--foreground);
  font-weight: 700;
}

.title-cell {
  min-width: 220px;
}

.title-cell strong,
.title-cell span {
  display: block;
}

.title-cell strong {
  color: var(--foreground);
}

.title-cell span {
  margin-top: 3px;
  color: var(--muted-foreground);
  font-size: 12px;
}

.language-pill {
  display: inline-flex;
  min-width: 58px;
  justify-content: center;
  border: 1px solid #fed7aa;
  border-radius: var(--radius);
  background: #fff7ed;
  color: #92400e;
  padding: 3px 7px;
  font-size: 11px;
  font-weight: 700;
}

.actions-col {
  width: 112px;
}

.quick-actions {
  display: flex;
  gap: 6px;
}

.quick-actions button {
  width: 30px;
  height: 30px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: #fff;
  color: var(--muted-foreground);
  display: grid;
  place-items: center;
  padding: 0;
}

.quick-actions button:hover {
  border-color: var(--dikoin-blue-light);
  color: var(--dikoin-blue);
}

.preview-column {
  min-height: 0;
}

.preview-panel {
  gap: 14px;
  padding: 16px;
  background: #fff;
}

.panel-title {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.panel-title > div {
  display: grid;
  gap: 3px;
}

.panel-title span {
  color: var(--muted-foreground);
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
}

.panel-title strong {
  color: var(--foreground);
  font-size: 15px;
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
  padding: 13px 15px;
  box-shadow: 0 8px 18px rgba(146, 64, 14, .08);
  font-family: Arial, sans-serif;
}

.note-preview span {
  display: inline-block;
  margin-bottom: 6px;
  color: #92400e;
  font-size: 11px;
  font-weight: 700;
}

.note-preview strong {
  display: block;
}

.note-preview p {
  margin: 7px 0 0;
  white-space: pre-wrap;
  line-height: 1.45;
}

.edit-form {
  display: grid;
  gap: 14px;
}

.form-top,
.metadata-grid {
  display: grid;
  grid-template-columns: minmax(120px, auto) minmax(220px, 1fr) auto;
  gap: 12px;
  align-items: end;
}

.metadata-grid {
  grid-template-columns: minmax(220px, 1fr) minmax(220px, 1fr);
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
  font-weight: 600;
}

label {
  display: grid;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--muted-foreground);
}

.check {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 38px;
}

.language-editors {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.language-panel {
  display: grid;
  gap: 12px;
  min-width: 0;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: #fff;
  padding: 14px;
}

.language-panel-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.language-panel-title span {
  color: var(--foreground);
  font-size: 14px;
  font-weight: 700;
}

.language-panel-title small {
  color: var(--muted-foreground);
  font-size: 11px;
  font-weight: 700;
}

.note-text {
  min-height: 220px;
  resize: vertical;
  font-family: Arial, sans-serif;
}

.modal-state {
  color: var(--muted-foreground);
  padding: 4px 0;
}

.delete-warning {
  margin-bottom: 12px;
  border: 1px solid #fdba74;
  border-radius: var(--radius);
  background: #fff7ed;
  color: #78350f;
  padding: 12px;
}

.delete-warning p {
  margin: 0;
  line-height: 1.45;
}

.success-msg {
  background: var(--dikoin-green-light);
  color: #065f46;
  border: 1px solid #86efac;
  padding: 10px;
  border-radius: var(--radius);
}

.btn-danger {
  background: var(--dikoin-red);
  color: #fff;
  border-color: var(--dikoin-red);
}

@media (max-width: 1180px) {
  .notes-shell,
  .language-editors,
  .form-top,
  .metadata-grid {
    grid-template-columns: 1fr;
  }

  .preview-column {
    min-width: 0;
  }

  .list-head {
    align-items: stretch;
    flex-direction: column;
  }

  .note-search {
    width: 100%;
    min-width: 0;
  }
}

@media (max-width: 640px) {
  .notes-page {
    padding: 16px;
  }

  .head {
    align-items: stretch;
    flex-direction: column;
  }

  .head .btn {
    justify-content: center;
  }
}
</style>
