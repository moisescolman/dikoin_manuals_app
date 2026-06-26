<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Edit, Eye, Plus, Save, Trash2 } from '@lucide/vue'
import { useRouter } from 'vue-router'
import { getProductCategories } from '@/api/products.api'
import { createReusableBlock, deleteReusableBlock, getReusableBlocks, updateReusableBlock } from '@/api/reusable-blocks.api'
import ReusableContentPreview from '@/components/reusable/ReusableContentPreview.vue'
import AppModal from '@/components/shared/AppModal.vue'
import BackendError from '@/components/shared/BackendError.vue'
import LanguageSegmentedControl from '@/components/shared/LanguageSegmentedControl.vue'
import ProductCategoryMultiSelect from '@/components/shared/ProductCategoryMultiSelect.vue'
import type { LanguageCode, ProductCategoryResponse, ReusableBlockResponse } from '@/types/api'

const props = defineProps<{ kind: 'SINGLE_BLOCK' | 'FRAGMENT' }>()
const router = useRouter()
const items = ref<ReusableBlockResponse[]>([])
const selectedId = ref<number | null>(null)
const loading = ref(false)
const error = ref('')
const saved = ref('')
const previewLanguage = ref<LanguageCode>('ES')
const formLanguage = ref<LanguageCode>('ES')
const categories = ref<ProductCategoryResponse[]>([])
const selectedCategoryCodes = ref<string[]>([])
const deleteConfirmOpen = ref(false)
const form = reactive({
  code: '', titleEs: '', titleEn: '', descriptionEs: '', descriptionEn: '',
  productCategory: '', productCodes: '', active: true,
})

const isSection = computed(() => props.kind === 'SINGLE_BLOCK')
const pageTitle = computed(() => isSection.value ? 'Secciones' : 'Fragmentos')
const singular = computed(() => isSection.value ? 'sección' : 'fragmento')
const selected = computed(() => items.value.find((item) => item.id === selectedId.value))
const editorRoute = computed(() => isSection.value ? 'reusable-section-editor' : 'reusable-fragment-editor')
const currentTitle = computed({
  get: () => formLanguage.value === 'EN' ? form.titleEn : form.titleEs,
  set: (value: string) => {
    if (formLanguage.value === 'EN') form.titleEn = value
    else form.titleEs = value
  },
})
const currentDescription = computed({
  get: () => formLanguage.value === 'EN' ? form.descriptionEn : form.descriptionEs,
  set: (value: string) => {
    if (formLanguage.value === 'EN') form.descriptionEn = value
    else form.descriptionEs = value
  },
})

onMounted(initialize)

async function initialize() {
  try {
    categories.value = await getProductCategories()
  } catch {
    categories.value = []
  }
  await load()
}

function categoryCodes(value?: string) {
  if (!value) return []
  const available = new Set(categories.value.map((category) => category.code.toUpperCase()))
  return value
    .split(/[|,;]/)
    .map((entry) => entry.trim().split(/\s+-\s+/)[0]?.trim() || '')
    .filter((code, index, values) => Boolean(code) && available.has(code.toUpperCase()) && values.indexOf(code) === index)
}

async function load(preferredId?: number) {
  loading.value = true
  error.value = ''
  try {
    items.value = await getReusableBlocks(false, props.kind)
    const target = items.value.find((item) => item.id === (preferredId ?? selectedId.value)) || items.value[0]
    if (target) select(target)
    else createNew()
  } catch (err) {
    error.value = err instanceof Error ? err.message : `No se pudieron cargar los ${pageTitle.value.toLowerCase()}`
  } finally {
    loading.value = false
  }
}

function select(item: ReusableBlockResponse) {
  selectedId.value = item.id
  form.code = item.code
  form.titleEs = item.titleEs || item.title
  form.titleEn = item.titleEn || ''
  form.descriptionEs = item.descriptionEs || item.description || ''
  form.descriptionEn = item.descriptionEn || ''
  form.productCategory = item.productCategory || ''
  selectedCategoryCodes.value = categoryCodes(item.productCategory)
  form.productCodes = item.productCodes || ''
  form.active = item.active
}

function createNew() {
  selectedId.value = null
  Object.assign(form, {
    code: '', titleEs: '', titleEn: '', descriptionEs: '', descriptionEn: '',
    productCategory: '', productCodes: '', active: true,
  })
  selectedCategoryCodes.value = []
}

function emptyContent() {
  return JSON.stringify({
    type: props.kind === 'FRAGMENT' ? 'FRAGMENT' : 'SECTION',
    titleEs: form.titleEs,
    titleEn: form.titleEn,
    blocks: [],
  })
}

async function save() {
  loading.value = true
  error.value = ''
  saved.value = ''
  try {
    const payload = {
      code: form.code || undefined,
      title: form.titleEs,
      titleEs: form.titleEs,
      titleEn: form.titleEn || undefined,
      description: form.descriptionEs || undefined,
      descriptionEs: form.descriptionEs || undefined,
      descriptionEn: form.descriptionEn || undefined,
      reusableType: props.kind,
      productCategory: selectedCategoryCodes.value.join(', ') || undefined,
      productCodes: form.productCodes || undefined,
      contentJson: selected.value?.contentJson || emptyContent(),
      active: form.active,
    }
    const result = selectedId.value
      ? await updateReusableBlock(selectedId.value, payload)
      : await createReusableBlock(payload)
    await load(result.id)
    saved.value = `${isSection.value ? 'Sección' : 'Fragmento'} guardado.`
  } catch (err) {
    error.value = err instanceof Error ? err.message : `No se pudo guardar el ${singular.value}`
  } finally {
    loading.value = false
  }
}

function edit() {
  if (!selectedId.value) return
  router.push({ name: editorRoute.value, params: { id: selectedId.value } })
}

async function remove() {
  if (!selectedId.value) return
  loading.value = true
  try {
    await deleteReusableBlock(selectedId.value)
    selectedId.value = null
    deleteConfirmOpen.value = false
    await load()
    saved.value = `${isSection.value ? 'Sección' : 'Fragmento'} eliminado de la biblioteca.`
  } catch (err) {
    error.value = err instanceof Error ? err.message : `No se pudo eliminar el ${singular.value}`
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="library-page">
    <header class="page-head">
      <div>
        <h1 class="page-title">{{ pageTitle }}</h1>
        <p class="text-muted">Biblioteca bilingüe de {{ pageTitle.toLowerCase() }} reutilizables.</p>
      </div>
      <button class="btn btn-primary" @click="createNew"><Plus :size="15" /> Nuevo {{ singular }}</button>
    </header>

    <BackendError :message="error" />
    <div v-if="saved" class="success-msg">{{ saved }}</div>

    <div class="content-grid">
      <section class="card list-card">
        <div class="list-head">
          <strong>Biblioteca de {{ pageTitle.toLowerCase() }}</strong>
          <span>{{ items.length }} registros</span>
        </div>
        <div class="table-scroll">
          <table class="table">
            <thead><tr><th>Código</th><th>Título ES</th><th>Título EN</th><th>Categoría</th><th>Estado</th><th></th></tr></thead>
            <tbody>
              <tr v-if="loading && !items.length"><td colspan="6">Cargando...</td></tr>
              <tr v-else-if="!items.length"><td colspan="6">No hay {{ pageTitle.toLowerCase() }}.</td></tr>
              <tr v-for="item in items" :key="item.id" :class="{ selected: item.id === selectedId }" @click="select(item)">
                <td class="mono">{{ item.code }}</td>
                <td class="item-title">{{ item.titleEs || item.title }}</td>
                <td>{{ item.titleEn || '—' }}</td>
                <td>{{ item.productCategory || '—' }}</td>
                <td>{{ item.active ? 'Activo' : 'Inactivo' }}</td>
                <td><button class="row-button" title="Seleccionar"><Eye :size="14" /></button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <form class="card metadata" @submit.prevent="save">
        <div class="metadata-head">
          <div>
            <span>{{ selectedId ? `Ficha de ${singular}` : `Nuevo ${singular}` }}</span>
            <strong class="mono">{{ form.code || 'Código autogenerado' }}</strong>
          </div>
          <label class="check"><input v-model="form.active" type="checkbox" /> Activo</label>
        </div>
        <div class="form-language">
          <div>
            <strong>Idioma del formulario</strong>
            <span>{{ formLanguage === 'ES' ? 'Datos en español' : 'English data' }}</span>
          </div>
          <LanguageSegmentedControl v-model="formLanguage" :aria-label="`Idioma del formulario de ${singular}`" />
        </div>
        <label>
          {{ formLanguage === 'ES' ? 'Título en español' : 'Title in English' }}
          <input v-model="currentTitle" class="field" :required="formLanguage === 'ES'" />
        </label>
        <label>
          {{ formLanguage === 'ES' ? 'Descripción en español' : 'English description' }}
          <textarea v-model="currentDescription" class="field" rows="3" />
        </label>
        <ProductCategoryMultiSelect v-model="selectedCategoryCodes" :categories="categories" />
        <label>Códigos de producto <input v-model="form.productCodes" class="field mono" /></label>
        <div class="actions">
          <button class="btn btn-primary" :disabled="loading"><Save :size="15" /> Guardar</button>
          <button v-if="selectedId" type="button" class="btn btn-outline" @click="edit"><Edit :size="15" /> Editar</button>
          <button v-if="selectedId" type="button" class="btn btn-danger" :disabled="loading" @click="deleteConfirmOpen = true"><Trash2 :size="15" /></button>
        </div>
      </form>

      <section class="card preview-card">
        <div class="preview-head">
          <div>
            <strong>Vista previa</strong>
            <span>{{ selected?.code || 'Selecciona un registro' }}</span>
          </div>
          <LanguageSegmentedControl v-model="previewLanguage" aria-label="Idioma de la vista previa" />
        </div>
        <ReusableContentPreview :item="selected" :language="previewLanguage" />
      </section>
    </div>

    <AppModal
      v-if="deleteConfirmOpen"
      :title="`Eliminar ${singular}`"
      :description="`Se eliminará este ${singular} de la biblioteca.`"
      size="sm"
      @close="deleteConfirmOpen = false"
    >
      <p class="confirm-text">¿Confirmas la eliminación?</p>
      <template #footer>
        <button type="button" class="btn btn-outline" @click="deleteConfirmOpen = false">Cancelar</button>
        <button type="button" class="btn btn-danger" :disabled="loading" @click="remove">Eliminar</button>
      </template>
    </AppModal>
  </section>
</template>

<style scoped>
.library-page { min-height: 100%; padding: 24px; display: grid; grid-template-rows: auto auto minmax(0, 1fr); gap: 16px; overflow: auto; }
.page-head, .metadata-head, .preview-head, .actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.content-grid { min-height: 0; display: grid; grid-template-columns: minmax(480px, 1.15fr) minmax(430px, .85fr); grid-template-rows: auto minmax(620px, 1fr); gap: 16px; overflow: visible; }
.list-card { min-width: 0; grid-row: 1 / 3; display: flex; flex-direction: column; overflow: hidden; }
.list-head { display: flex; justify-content: space-between; padding: 13px 15px; border-bottom: 1px solid var(--border); }
.list-head span, .metadata-head span, .preview-head span { color: var(--muted-foreground); font-size: 12px; }
.table-scroll { flex: 1; overflow: auto; }
tbody tr { cursor: pointer; }
tbody tr.selected td { background: var(--dikoin-blue-lighter); }
.item-title { color: var(--dikoin-blue); font-weight: 600; }
.row-button { border: 0; background: transparent; color: var(--muted-foreground); }
.metadata { padding: 15px; display: grid; gap: 10px; align-self: start; overflow: visible; }
.metadata-head > div, .preview-head > div:first-child { display: grid; gap: 3px; }
label { display: grid; gap: 5px; color: var(--muted-foreground); font-size: 12px; font-weight: 600; }
.check { display: flex; align-items: center; gap: 6px; }
.actions { justify-content: flex-start; flex-wrap: wrap; }
.form-language { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.form-language > div { display: grid; gap: 3px; }
.form-language strong { color: var(--foreground); font-size: 12px; }
.form-language span { color: var(--muted-foreground); font-size: 11px; }
.preview-card { min-height: 0; display: flex; flex-direction: column; overflow: hidden; }
.preview-head { padding: 12px 15px; border-bottom: 1px solid var(--border); }
.preview-card :deep(.preview-shell) { flex: 1; min-height: 0; }
.success-msg { padding: 10px; border: 1px solid #86efac; border-radius: var(--radius); background: var(--dikoin-green-light); color: #065f46; }
.btn-danger { background: var(--dikoin-red); border-color: var(--dikoin-red); color: #fff; }
.confirm-text { margin: 0; }
@media (max-width: 1050px) {
  .library-page { height: auto; overflow: visible; }
  .content-grid { grid-template-columns: 1fr; grid-template-rows: auto; overflow: visible; }
  .list-card { grid-row: auto; }
  .table-scroll { max-height: 380px; }
  .preview-card { min-height: 620px; }
}
</style>

