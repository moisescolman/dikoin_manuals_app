<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { BookOpenText, Edit, Pencil, Plus, Save, Search, Trash2, X } from '@lucide/vue'
import { getProductCategories } from '@/api/products.api'
import { createReusableBlock, deleteReusableBlock, getReusableBlocks, getReusableBlockUsages, updateReusableBlock } from '@/api/reusable-blocks.api'
import { createReusableFragmentRecord, deleteReusableFragment, getReusableFragments, getReusableFragmentUsages, updateReusableFragment } from '@/api/reusable-fragments.api'
import ReusableContentPreview from '@/components/reusable/ReusableContentPreview.vue'
import AppModal from '@/components/shared/AppModal.vue'
import BackendError from '@/components/shared/BackendError.vue'
import LanguageSegmentedControl from '@/components/shared/LanguageSegmentedControl.vue'
import ProductCategoryMultiSelect from '@/components/shared/ProductCategoryMultiSelect.vue'
import type { LanguageCode, ProductCategoryResponse, ReusableBlockResponse, ReusableBlockUsageResponse, ReusableFragmentResponse } from '@/types/api'

type LibraryItem = (ReusableBlockResponse | ReusableFragmentResponse) & { reusableType: 'SINGLE_BLOCK' | 'FRAGMENT' }

const props = defineProps<{ kind: 'SINGLE_BLOCK' | 'FRAGMENT' }>()
const router = useRouter()
const items = ref<LibraryItem[]>([])
const selectedId = ref<number | null>(null)
const loading = ref(false)
const saving = ref(false)
const deleting = ref(false)
const loadingUsages = ref(false)
const error = ref('')
const saved = ref('')
const previewLanguage = ref<LanguageCode>('ES')
const search = ref('')
const searchFocused = ref(false)
const categories = ref<ProductCategoryResponse[]>([])
const selectedCategoryCodes = ref<string[]>([])
const formOpen = ref(false)
const usagesOpen = ref(false)
const deleteOpen = ref(false)
const usages = ref<ReusableBlockUsageResponse[]>([])
let searchTimer: number | undefined

const form = reactive({
  code: '',
  title: '',
  titleEs: '',
  titleEn: '',
  description: '',
  descriptionEs: '',
  descriptionEn: '',
  productCategory: '',
  productCodes: '',
  active: true,
})

const isSection = computed(() => props.kind === 'SINGLE_BLOCK')
const pageTitle = computed(() => isSection.value ? 'Secciones' : 'Fragmentos')
const singular = computed(() => isSection.value ? 'sección' : 'fragmento')
const selected = computed(() => items.value.find((item) => item.id === selectedId.value) || items.value[0] || null)
const editorRoute = computed(() => isSection.value ? 'reusable-section-editor' : 'reusable-fragment-editor')
const countLabel = computed(() => search.value.trim() ? `${items.value.length} resultados` : `${items.value.length} registros`)
const suggestions = computed(() => items.value.slice(0, 6))
const uniqueUsages = computed(() => uniqueManuals(usages.value))

onMounted(initialize)

watch(search, () => {
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(() => { void load() }, 250)
})

async function initialize() {
  try {
    categories.value = await getProductCategories()
  } catch {
    categories.value = []
  }
  await load()
}

async function load(preferredId?: number) {
  loading.value = true
  error.value = ''
  try {
    const loaded = isSection.value
      ? await getReusableBlocks(false, 'SINGLE_BLOCK', search.value)
      : await getReusableFragments(false, search.value)
    items.value = loaded.map((item) => ({
      ...item,
      reusableType: props.kind,
    })) as LibraryItem[]
    const target = items.value.find((item) => item.id === (preferredId ?? selectedId.value)) || items.value[0] || null
    selectedId.value = target?.id || null
  } catch (err) {
    error.value = err instanceof Error ? err.message : `No se pudieron cargar los ${pageTitle.value.toLowerCase()}`
  } finally {
    loading.value = false
  }
}

function select(item: LibraryItem) {
  selectedId.value = item.id
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
  Object.assign(form, {
    code: '',
    title: '',
    titleEs: '',
    titleEn: '',
    description: '',
    descriptionEs: '',
    descriptionEn: '',
    productCategory: '',
    productCodes: '',
    active: true,
  })
  selectedCategoryCodes.value = []
}

function fillForm(item: LibraryItem) {
  form.code = item.code
  form.title = item.title || ''
  form.productCategory = item.productCategory || ''
  form.productCodes = item.productCodes || ''
  form.active = item.active
  selectedCategoryCodes.value = categoryCodes(item.productCategory)
  if (isSection.value && 'titleEs' in item) {
    form.titleEs = item.titleEs || item.title || ''
    form.titleEn = item.titleEn || ''
    form.descriptionEs = item.descriptionEs || item.description || ''
    form.descriptionEn = item.descriptionEn || ''
    return
  }
  form.description = item.description || ''
}

function openCreate() {
  selectedId.value = null
  resetForm()
  formOpen.value = true
}

function openEdit(item: LibraryItem) {
  select(item)
  resetForm()
  fillForm(item)
  formOpen.value = true
}

function closeForm() {
  formOpen.value = false
  if (!selectedId.value && items.value.length) selectedId.value = items.value[0].id
}

function emptyContent() {
  return JSON.stringify({
    type: isSection.value ? 'SECTION' : 'FRAGMENT',
    titleEs: form.titleEs || form.title,
    titleEn: form.titleEn || form.title,
    blocks: [],
  })
}

async function save() {
  saving.value = true
  error.value = ''
  saved.value = ''
  try {
    const current = selected.value
    const contentJson = current?.contentJson || emptyContent()
    const result = isSection.value
      ? await saveSection(contentJson)
      : await saveFragment(contentJson)
    selectedId.value = result.id
    await load(result.id)
    formOpen.value = false
    saved.value = `${isSection.value ? 'Sección' : 'Fragmento'} guardado.`
  } catch (err) {
    error.value = err instanceof Error ? err.message : `No se pudo guardar el ${singular.value}`
  } finally {
    saving.value = false
  }
}

function saveSection(contentJson: string) {
  const payload = {
    code: selectedId.value && form.code ? form.code : undefined,
    title: form.titleEs.trim(),
    titleEs: form.titleEs.trim(),
    titleEn: form.titleEn.trim() || undefined,
    description: form.descriptionEs.trim() || undefined,
    descriptionEs: form.descriptionEs.trim() || undefined,
    descriptionEn: form.descriptionEn.trim() || undefined,
    reusableType: 'SINGLE_BLOCK' as const,
    productCategory: selectedCategoryCodes.value.join(', ') || undefined,
    productCodes: form.productCodes || undefined,
    contentJson,
    active: form.active,
  }
  return selectedId.value ? updateReusableBlock(selectedId.value, payload) : createReusableBlock(payload)
}

function saveFragment(contentJson: string) {
  const payload = {
    code: selectedId.value && form.code ? form.code : undefined,
    title: form.title.trim(),
    description: form.description.trim() || undefined,
    productCategory: selectedCategoryCodes.value.join(', ') || undefined,
    productCodes: form.productCodes || undefined,
    contentJson,
    active: form.active,
  }
  return selectedId.value ? updateReusableFragment(selectedId.value, payload) : createReusableFragmentRecord(payload)
}

function editContent() {
  if (!selected.value) return
  router.push({ name: editorRoute.value, params: { id: selected.value.id } })
}

async function openUsages(item: LibraryItem) {
  select(item)
  usages.value = []
  usagesOpen.value = true
  loadingUsages.value = true
  error.value = ''
  try {
    usages.value = isSection.value
      ? await getReusableBlockUsages(item.id)
      : await getReusableFragmentUsages(item.id)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'No se pudieron cargar los manuales'
  } finally {
    loadingUsages.value = false
  }
}

function openDelete(item: LibraryItem) {
  select(item)
  deleteOpen.value = true
}

async function confirmDelete() {
  if (!selected.value) return
  deleting.value = true
  error.value = ''
  saved.value = ''
  try {
    if (isSection.value) await deleteReusableBlock(selected.value.id)
    else await deleteReusableFragment(selected.value.id)
    deleteOpen.value = false
    selectedId.value = null
    await load()
    saved.value = `${isSection.value ? 'Sección' : 'Fragmento'} eliminado de la biblioteca.`
  } catch (err) {
    error.value = err instanceof Error ? err.message : `No se pudo eliminar el ${singular.value}`
  } finally {
    deleting.value = false
  }
}

function selectSuggestion(item: LibraryItem) {
  select(item)
  searchFocused.value = false
}

function closeSuggestionsLater() {
  window.setTimeout(() => { searchFocused.value = false }, 150)
}

function uniqueManuals(values: ReusableBlockUsageResponse[]) {
  const byManual = new Map<number, ReusableBlockUsageResponse>()
  values.forEach((usage) => {
    if (!byManual.has(usage.manualId)) byManual.set(usage.manualId, usage)
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
  <section class="library-page">
    <header class="page-head">
      <div>
        <h1 class="page-title">{{ pageTitle }}</h1>
        <p class="text-muted">Biblioteca de {{ pageTitle.toLowerCase() }} reutilizables.</p>
      </div>
      <button class="btn btn-primary" @click="openCreate"><Plus :size="15" /> Nuevo {{ singular }}</button>
    </header>

    <BackendError :message="error" />
    <div v-if="saved" class="success-msg">{{ saved }}</div>

    <div class="library-shell">
      <section class="card library-inbox" :aria-label="`Lista de ${pageTitle.toLowerCase()}`">
        <div class="list-head">
          <div>
            <h2>Biblioteca de {{ pageTitle.toLowerCase() }}</h2>
            <span>{{ countLabel }}</span>
          </div>
          <div class="library-search">
            <Search :size="14" />
            <input
              v-model="search"
              type="search"
              :placeholder="`Buscar ${singular}...`"
              @focus="searchFocused = true"
              @blur="closeSuggestionsLater"
            />
            <button v-if="search" type="button" title="Limpiar búsqueda" @mousedown.prevent="search = ''">
              <X :size="14" />
            </button>
            <div v-if="searchFocused && search" class="search-suggestions">
              <button v-for="item in suggestions" :key="item.id" type="button" @mousedown.prevent="selectSuggestion(item)">
                <span class="mono">{{ item.code }}</span>
                <strong>{{ item.title }}</strong>
                <small>{{ item.productCategory || 'Sin categoría' }}</small>
              </button>
              <div v-if="loading" class="suggestion-state">Buscando...</div>
              <div v-else-if="!suggestions.length" class="suggestion-state">No hay sugerencias.</div>
            </div>
          </div>
        </div>

        <div class="table-scroll">
          <table class="inbox-table">
            <thead>
              <tr>
                <th>Código</th>
                <th v-if="isSection">Título ES</th>
                <th v-if="isSection">Título EN</th>
                <th v-else>Título</th>
                <th>Categoría producto</th>
                <th>Creación</th>
                <th>Última modificación</th>
                <th class="actions-col">Acciones</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading && !items.length">
                <td colspan="7">Cargando...</td>
              </tr>
              <tr v-else-if="!items.length">
                <td colspan="7">No hay {{ pageTitle.toLowerCase() }}.</td>
              </tr>
              <tr v-for="item in items" :key="item.id" :class="{ selected: item.id === selectedId }" @click="select(item)">
                <td class="mono code-cell">{{ item.code }}</td>
                <td v-if="isSection" class="title-cell">{{ 'titleEs' in item ? item.titleEs || item.title : item.title }}</td>
                <td v-if="isSection">{{ 'titleEn' in item ? item.titleEn || '-' : '-' }}</td>
                <td v-else class="title-cell">{{ item.title }}</td>
                <td>{{ item.productCategory || '-' }}</td>
                <td>{{ formatDate(item.createdAt) }}</td>
                <td>{{ formatDate(item.updatedAt || item.createdAt) }}</td>
                <td>
                  <div class="quick-actions">
                    <button type="button" title="Manuales con este contenido" @click.stop="openUsages(item)">
                      <BookOpenText :size="16" />
                    </button>
                    <button type="button" title="Editar ficha" @click.stop="openEdit(item)">
                      <Pencil :size="16" />
                    </button>
                    <button type="button" title="Eliminar" @click.stop="openDelete(item)">
                      <Trash2 :size="16" />
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <aside class="preview-column" :aria-label="`Vista previa de ${singular}`">
        <section class="card preview-panel">
          <div class="preview-head">
            <div>
              <span>Vista previa</span>
              <strong>{{ selected?.code || `Sin ${singular}` }}</strong>
            </div>
            <div class="preview-actions">
              <LanguageSegmentedControl v-model="previewLanguage" aria-label="Idioma de la vista previa" />
              <button class="btn btn-outline" :disabled="!selected" @click="editContent"><Edit :size="15" /> Editar contenido</button>
            </div>
          </div>
          <ReusableContentPreview :item="selected || undefined" :language="previewLanguage" />
        </section>
      </aside>
    </div>

    <AppModal
      v-if="usagesOpen"
      :title="`Manuales que contienen ${selected?.code || ''}`"
      :description="selected?.title"
      size="lg"
      @close="usagesOpen = false"
    >
      <div v-if="loadingUsages" class="modal-state">Cargando manuales...</div>
      <div v-else-if="!uniqueUsages.length" class="modal-state">Este {{ singular }} no está incluido en ningún manual.</div>
      <table v-else class="modal-table">
        <thead><tr><th>Código</th><th>Nombre</th></tr></thead>
        <tbody>
          <tr v-for="usage in uniqueUsages" :key="usage.manualId">
            <td class="mono">{{ usage.manualCode }}</td>
            <td>{{ usage.manualTitle }}</td>
          </tr>
        </tbody>
      </table>
    </AppModal>

    <AppModal
      v-if="formOpen"
      :title="selectedId ? `Editar ficha de ${singular}` : `Nuevo ${singular}`"
      :description="form.code || 'Código autogenerado'"
      size="lg"
      @close="closeForm"
    >
      <form class="edit-form" @submit.prevent="save">
        <div class="form-top">
          <div class="readonly-code">
            <span>Código</span>
            <strong class="mono">{{ form.code || 'Autogenerado' }}</strong>
          </div>
          <label v-if="!isSection">Título <input v-model="form.title" class="field" required /></label>
          <label class="check"><input v-model="form.active" type="checkbox" /> Activo</label>
        </div>

        <div v-if="isSection" class="language-editors">
          <section class="language-panel">
            <div class="language-panel-title"><span>Español</span><small>ES</small></div>
            <label>Título <input v-model="form.titleEs" class="field" required /></label>
            <label>Descripción <textarea v-model="form.descriptionEs" class="field" rows="4" /></label>
          </section>
          <section class="language-panel">
            <div class="language-panel-title"><span>English</span><small>EN</small></div>
            <label>Title <input v-model="form.titleEn" class="field" /></label>
            <label>Description <textarea v-model="form.descriptionEn" class="field" rows="4" /></label>
          </section>
        </div>

        <label v-else>Descripción <textarea v-model="form.description" class="field" rows="4" /></label>

        <div class="metadata-grid">
          <ProductCategoryMultiSelect v-model="selectedCategoryCodes" :categories="categories" />
          <label>Códigos producto <input v-model="form.productCodes" class="field mono" placeholder="FLB10.1, HY100" /></label>
        </div>
      </form>

      <template #footer>
        <button type="button" class="btn btn-outline" :disabled="saving" @click="closeForm"><X :size="15" /> Cancelar</button>
        <button type="button" class="btn btn-primary" :disabled="saving" @click="save"><Save :size="15" /> Guardar</button>
      </template>
    </AppModal>

    <AppModal
      v-if="deleteOpen"
      :title="`Eliminar ${singular}`"
      :description="selected ? `${selected.code} - ${selected.title}` : undefined"
      size="sm"
      @close="deleteOpen = false"
    >
      <p class="confirm-text">¿Confirmas la eliminación?</p>
      <template #footer>
        <button type="button" class="btn btn-outline" :disabled="deleting" @click="deleteOpen = false">Cancelar</button>
        <button type="button" class="btn btn-danger" :disabled="deleting" @click="confirmDelete"><Trash2 :size="15" /> Eliminar</button>
      </template>
    </AppModal>
  </section>
</template>

<style scoped>
.library-page {
  min-height: 100%;
  padding: 24px;
  display: grid;
  grid-template-rows: auto auto auto 1fr;
  gap: 16px;
  overflow: auto;
}

.page-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.library-shell {
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));;
  /* grid-template-columns: minmax(0, 1.65fr) minmax(340px, .8fr); */
  gap: 16px;
  align-items: stretch;
}

.library-inbox,
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

.library-search {
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

.library-search input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--foreground);
}

.library-search input::-webkit-search-cancel-button {
  display: none;
}

.library-search > button {
  width: 24px;
  height: 24px;
  border: 0;
  background: transparent;
  color: var(--muted-foreground);
  display: grid;
  place-items: center;
  padding: 0;
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
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-suggestions small,
.suggestion-state {
  grid-column: 1 / -1;
  color: var(--muted-foreground);
  font-size: 12px;
}

.suggestion-state {
  padding: 9px;
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
  min-width: 180px;
  color: var(--foreground);
  font-weight: 600;
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
  background: #fff;
}

.preview-head {
  padding: 12px 15px;
  border-bottom: 1px solid var(--border);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.preview-head > div:first-child {
  display: grid;
  gap: 3px;
}

.preview-head span {
  color: var(--muted-foreground);
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
}

.preview-head strong {
  color: var(--foreground);
  font-size: 15px;
}

.preview-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.preview-panel :deep(.preview-shell) {
  flex: 1;
  min-height: 0;
}

.edit-form {
  display: grid;
  gap: 14px;
}

.form-top,
.metadata-grid,
.language-editors {
  display: grid;
  grid-template-columns: minmax(120px, auto) minmax(220px, 1fr) auto;
  gap: 12px;
  align-items: end;
}

.metadata-grid,
.language-editors {
  grid-template-columns: repeat(2, minmax(0, 1fr));
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

.modal-state,
.confirm-text {
  margin: 0;
  color: var(--muted-foreground);
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
  .library-shell,
  .form-top,
  .metadata-grid,
  .language-editors {
    grid-template-columns: 1fr;
  }

  .list-head,
  .preview-head {
    align-items: stretch;
    flex-direction: column;
  }

  .library-search {
    width: 100%;
    min-width: 0;
  }

  .preview-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .library-page {
    padding: 16px;
  }

  .page-head {
    align-items: stretch;
    flex-direction: column;
  }

  .page-head .btn {
    justify-content: center;
  }
}
</style>
