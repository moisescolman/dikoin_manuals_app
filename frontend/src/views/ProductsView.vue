<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Check, Edit, Eye, ImageOff, ImageUp, Plus, Search, Trash2, X } from '@lucide/vue'
import BackendError from '@/components/shared/BackendError.vue'
import AppModal from '@/components/shared/AppModal.vue'
import { toBackendUrl } from '@/api/http'
import { useProductsStore } from '@/stores/products.store'
import type { ManualSummaryResponse, ProductDeleteImpactResponse, ProductRequest, ProductResponse } from '@/types/api'
import { formatDate, statusLabel } from '@/utils/formatters'

const store = useProductsStore()
const search = ref('')
const editingId = ref<number | undefined>()
const formOpen = ref(false)
const detailProduct = ref<ProductResponse | null>(null)
const deleteCandidate = ref<ProductResponse | null>(null)
const deleteImpact = ref<ProductDeleteImpactResponse | null>(null)
const selectedDeactivateManualIds = ref<number[]>([])
const applyImageProduct = ref<ProductResponse | null>(null)
const applyImageImpact = ref<ProductDeleteImpactResponse | null>(null)
const selectedApplyManualIds = ref<number[]>([])
const saving = ref(false)
const message = ref('')
const selectedImageFile = ref<File | null>(null)
const selectedImagePreview = ref('')
const removeImageRequested = ref(false)

const form = reactive<ProductRequest>({
  code: '',
  name: '',
  nameEs: '',
  nameEn: '',
  familyId: undefined,
  categoryIds: [],
  descriptionEs: '',
  descriptionEn: '',
  active: true,
})

const selectedFamily = computed(() => store.families.find((family) => family.id === form.familyId))
const formTitle = computed(() => editingId.value ? 'Editar producto' : 'Crear producto')
const selectedProductForForm = computed(() => store.products.find((product) => product.id === editingId.value))

onMounted(async () => {
  await Promise.all([store.fetchProducts(), store.fetchTaxonomy()])
})

function openCreate() {
  reset()
  formOpen.value = true
}

function openEdit(product: ProductResponse) {
  fillForm(product)
  formOpen.value = true
}

function closeForm() {
  formOpen.value = false
  resetImageSelection()
}

function fillForm(product: ProductResponse) {
  editingId.value = product.id
  form.code = product.code
  form.name = product.nameEs || product.name
  form.nameEs = product.nameEs || product.name
  form.nameEn = product.nameEn || ''
  form.familyId = product.familyId
  form.family = product.family || ''
  form.familyCode = product.familyCode || ''
  form.categoryIds = [...(product.categoryIds || [])]
  form.category = product.category || ''
  form.categoryCodes = product.categoryCodes || ''
  form.description = product.descriptionEs || product.description || ''
  form.descriptionEs = product.descriptionEs || product.description || ''
  form.descriptionEn = product.descriptionEn || ''
  form.active = product.active
  resetImageSelection()
}

function reset() {
  editingId.value = undefined
  form.code = ''
  form.name = ''
  form.nameEs = ''
  form.nameEn = ''
  form.familyId = undefined
  form.family = ''
  form.familyCode = ''
  form.categoryIds = []
  form.category = ''
  form.categoryCodes = ''
  form.description = ''
  form.descriptionEs = ''
  form.descriptionEn = ''
  form.active = true
  resetImageSelection()
}

function resetImageSelection() {
  selectedImageFile.value = null
  removeImageRequested.value = false
  if (selectedImagePreview.value) URL.revokeObjectURL(selectedImagePreview.value)
  selectedImagePreview.value = ''
}

function pickImage(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0] || null
  selectedImageFile.value = file
  removeImageRequested.value = false
  if (selectedImagePreview.value) URL.revokeObjectURL(selectedImagePreview.value)
  selectedImagePreview.value = file ? URL.createObjectURL(file) : ''
}

function requestRemoveImage() {
  resetImageSelection()
  removeImageRequested.value = true
}

function toggleCategory(id: number, checked: boolean) {
  const selected = new Set(form.categoryIds || [])
  if (checked) selected.add(id)
  else selected.delete(id)
  form.categoryIds = [...selected]
}

function categoryChecked(id: number) {
  return Boolean(form.categoryIds?.includes(id))
}

function productCategories(product: ProductResponse) {
  return product.categories?.length ? product.categories : []
}

function imageSrc(product?: ProductResponse | null) {
  return toBackendUrl(product?.productImageThumbnailUrl || product?.productImageUrl)
}

async function save() {
  const family = selectedFamily.value
  const categories = store.categories.filter((category) => form.categoryIds?.includes(category.id))
  saving.value = true
  message.value = ''
  try {
    let saved = await store.saveProduct({
      ...form,
      name: form.nameEs || form.name,
      family: family ? `${family.code} - ${family.nameEs}` : form.family,
      familyCode: family?.code || form.familyCode,
      category: categories.map((category) => `${category.code} - ${category.nameEs}`).join(' | '),
      categoryCodes: categories.map((category) => category.code).join(', '),
      description: form.descriptionEs || form.description,
    }, editingId.value)

    if (removeImageRequested.value && editingId.value && selectedProductForForm.value?.productImageAssetId) {
      saved = await store.removeProductImage(saved.id)
    }
    if (selectedImageFile.value) {
      saved = await store.saveProductImage(saved.id, selectedImageFile.value)
      await offerApplyImage(saved)
    }

    message.value = editingId.value ? 'Producto actualizado.' : 'Producto creado.'
    closeForm()
  } finally {
    saving.value = false
  }
}

async function offerApplyImage(product: ProductResponse) {
  const impact = await store.fetchDeleteImpact(product.id)
  if (!impact.relatedManuals.length) return
  applyImageProduct.value = product
  applyImageImpact.value = impact
  selectedApplyManualIds.value = impact.activeManuals.map((manual) => manual.id)
}

async function openDelete(product: ProductResponse) {
  deleteCandidate.value = product
  deleteImpact.value = await store.fetchDeleteImpact(product.id)
  selectedDeactivateManualIds.value = deleteImpact.value.activeManuals.map((manual) => manual.id)
}

async function confirmDelete() {
  if (!deleteCandidate.value) return
  await store.removeProduct(deleteCandidate.value.id, selectedDeactivateManualIds.value)
  message.value = deleteImpact.value?.hasManualHistory
    ? 'Producto desactivado.'
    : 'Producto eliminado.'
  closeDelete()
}

function closeDelete() {
  deleteCandidate.value = null
  deleteImpact.value = null
  selectedDeactivateManualIds.value = []
}

async function applyImageToSelectedManuals() {
  if (!applyImageProduct.value) return
  await store.applyImageToManuals(applyImageProduct.value.id, selectedApplyManualIds.value)
  message.value = 'Imagen aplicada a los manuales seleccionados.'
  closeApplyImage()
}

function closeApplyImage() {
  applyImageProduct.value = null
  applyImageImpact.value = null
  selectedApplyManualIds.value = []
}

function toggleManualSelection(target: { value: number[] }, id: number, checked: boolean) {
  const selected = new Set(target.value)
  if (checked) selected.add(id)
  else selected.delete(id)
  target.value = [...selected]
}

function toggleDeactivateManual(id: number, checked: boolean) {
  toggleManualSelection(selectedDeactivateManualIds, id, checked)
}

function toggleApplyManual(id: number, checked: boolean) {
  toggleManualSelection(selectedApplyManualIds, id, checked)
}

function manualLabel(manual: ManualSummaryResponse) {
  return `${manual.code} - ${manual.title}`
}
</script>

<template>
  <section class="products-page">
    <div class="head">
      <div>
        <h1 class="page-title">Productos</h1>
        <p class="text-muted">Listado de productos con imagen, taxonomia y acciones rapidas.</p>
      </div>
      <button class="btn btn-primary" @click="openCreate"><Plus :size="14" /> Añadir producto</button>
    </div>

    <BackendError :message="store.error" />
    <div v-if="message" class="success-msg">{{ message }}</div>

    <div class="card list-card">
      <div class="toolbar">
        <div class="search-box">
          <Search :size="14" />
          <input v-model="search" placeholder="Buscar producto..." @keydown.enter="store.fetchProducts(search)" />
        </div>
        <button class="btn btn-outline" @click="store.fetchProducts(search)">Buscar</button>
      </div>
      <table class="table">
        <thead>
          <tr>
            <th>Imagen</th>
            <th>Codigo</th>
            <th>Nombre</th>
            <th>Familia</th>
            <th>Categorias</th>
            <th>Activo</th>
            <th>Acciones</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="store.loading"><td colspan="7">Cargando productos...</td></tr>
          <tr v-else-if="!store.products.length"><td colspan="7">No hay productos.</td></tr>
          <tr v-for="product in store.products" :key="product.id">
            <td>
              <div class="thumb">
                <img v-if="imageSrc(product)" :src="imageSrc(product)" :alt="product.nameEs || product.name" />
                <ImageOff v-else :size="18" />
              </div>
            </td>
            <td class="mono">{{ product.code }}</td>
            <td>
              <strong>{{ product.nameEs || product.name }}</strong>
              <small v-if="product.nameEn">{{ product.nameEn }}</small>
            </td>
            <td>{{ product.familyInfo ? `${product.familyInfo.code} - ${product.familyInfo.nameEs}` : product.family || '-' }}</td>
            <td>
              <div v-if="productCategories(product).length" class="category-chips">
                <span v-for="category in productCategories(product)" :key="category.id">{{ category.code }}</span>
              </div>
              <span v-else>{{ product.category || '-' }}</span>
            </td>
            <td>{{ product.active ? 'Si' : 'No' }}</td>
            <td>
              <div class="row-actions">
                <button type="button" title="Ver producto" @click="detailProduct = product"><Eye :size="15" /></button>
                <button type="button" title="Editar producto" @click="openEdit(product)"><Edit :size="15" /></button>
                <button type="button" title="Eliminar producto" @click="openDelete(product)"><Trash2 :size="15" /></button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <AppModal v-if="formOpen" :title="formTitle" description="La imagen es opcional y se copiara por defecto en manuales nuevos." size="lg" @close="closeForm">
      <form class="product-form" @submit.prevent="save">
        <div class="form-grid">
          <label>Codigo <input v-model="form.code" class="field mono" required /></label>
          <label>Nombre ES <input v-model="form.nameEs" class="field" required /></label>
          <label>Nombre EN <input v-model="form.nameEn" class="field" /></label>
          <label>
            Familia
            <select v-model="form.familyId" class="field">
              <option :value="undefined">Sin familia</option>
              <option v-for="family in store.families" :key="family.id" :value="family.id">
                {{ family.code }} - {{ family.nameEs }}
              </option>
            </select>
          </label>
        </div>

        <div class="image-row">
          <div class="image-preview">
            <img v-if="selectedImagePreview" :src="selectedImagePreview" alt="Nueva imagen" />
            <img v-else-if="!removeImageRequested && imageSrc(selectedProductForForm)" :src="imageSrc(selectedProductForForm)" alt="Imagen actual" />
            <ImageOff v-else :size="24" />
          </div>
          <div class="image-actions">
            <label class="image-upload">
              <input type="file" accept="image/*" @change="pickImage" />
              <ImageUp :size="16" />
              <span>{{ selectedImageFile?.name || 'Subir imagen' }}</span>
            </label>
            <button v-if="editingId && selectedProductForForm?.productImageAssetId" type="button" class="btn btn-outline" @click="requestRemoveImage">
              <X :size="14" /> Quitar imagen
            </button>
            <small v-if="removeImageRequested">La imagen se desvinculara del producto al guardar.</small>
          </div>
        </div>

        <div class="category-picker">
          <strong>Categorias</strong>
          <label v-for="category in store.categories" :key="category.id" class="category-option">
            <input
              type="checkbox"
              :checked="categoryChecked(category.id)"
              @change="toggleCategory(category.id, ($event.target as HTMLInputElement).checked)"
            />
            <span>{{ category.code }}</span>
            <small>{{ category.nameEs }}</small>
          </label>
        </div>

        <label>Descripcion ES <textarea v-model="form.descriptionEs" class="field" rows="3" /></label>
        <label>Descripcion EN <textarea v-model="form.descriptionEn" class="field" rows="3" /></label>
        <label class="check"><input v-model="form.active" type="checkbox" /> Producto activo</label>

        <div class="modal-actions">
          <button type="button" class="btn btn-outline" @click="closeForm">Cancelar</button>
          <button class="btn btn-primary" :disabled="saving"><Check :size="14" /> {{ saving ? 'Guardando...' : 'Guardar' }}</button>
        </div>
      </form>
    </AppModal>

    <AppModal v-if="detailProduct" title="Detalle de producto" size="md" @close="detailProduct = null">
      <div class="detail">
        <div class="detail-image">
          <img v-if="imageSrc(detailProduct)" :src="imageSrc(detailProduct)" :alt="detailProduct.nameEs || detailProduct.name" />
          <ImageOff v-else :size="28" />
        </div>
        <dl>
          <div><dt>Codigo</dt><dd class="mono">{{ detailProduct.code }}</dd></div>
          <div><dt>Nombre ES</dt><dd>{{ detailProduct.nameEs || detailProduct.name }}</dd></div>
          <div><dt>Nombre EN</dt><dd>{{ detailProduct.nameEn || '-' }}</dd></div>
          <div><dt>Familia</dt><dd>{{ detailProduct.familyInfo ? `${detailProduct.familyInfo.code} - ${detailProduct.familyInfo.nameEs}` : detailProduct.family || '-' }}</dd></div>
          <div><dt>Categorias</dt><dd>{{ detailProduct.category || '-' }}</dd></div>
          <div><dt>Descripcion ES</dt><dd>{{ detailProduct.descriptionEs || '-' }}</dd></div>
          <div><dt>Descripcion EN</dt><dd>{{ detailProduct.descriptionEn || '-' }}</dd></div>
          <div><dt>Activo</dt><dd>{{ detailProduct.active ? 'Si' : 'No' }}</dd></div>
          <div><dt>Actualizado</dt><dd>{{ formatDate(detailProduct.updatedAt) }}</dd></div>
        </dl>
      </div>
    </AppModal>

    <AppModal
      v-if="deleteCandidate"
      title="Eliminar producto"
      :description="deleteImpact?.hasManualHistory ? 'El producto tiene historial y se desactivara, no se borrara fisicamente.' : 'El producto no tiene historial y se eliminara.'"
      size="lg"
      @close="closeDelete"
    >
      <div class="confirm-panel">
        <p><strong>{{ deleteCandidate.code }}</strong> - {{ deleteCandidate.nameEs || deleteCandidate.name }}</p>
        <p v-if="deleteImpact && !deleteImpact.activeManuals.length">No hay manual activo relacionado con este producto.</p>
        <div v-if="deleteImpact?.activeManuals.length" class="manual-checklist">
          <h3>Manuales activos relacionados</h3>
          <p>Selecciona los manuales que quieres dar de baja junto con el producto.</p>
          <label v-for="manual in deleteImpact.activeManuals" :key="manual.id" class="manual-option">
            <input
              type="checkbox"
              :checked="selectedDeactivateManualIds.includes(manual.id)"
              @change="toggleDeactivateManual(manual.id, ($event.target as HTMLInputElement).checked)"
            />
            <span>{{ manualLabel(manual) }}</span>
            <small>{{ statusLabel(manual.activeStatus) }}</small>
          </label>
        </div>
      </div>
      <template #footer>
        <button type="button" class="btn btn-outline" @click="closeDelete">Cancelar</button>
        <button type="button" class="btn btn-danger" :disabled="store.loading" @click="confirmDelete">Confirmar</button>
      </template>
    </AppModal>

    <AppModal
      v-if="applyImageProduct && applyImageImpact"
      title="Aplicar imagen a manuales"
      description="Puedes aplicar la imagen nueva solo a los manuales existentes que selecciones."
      size="lg"
      @close="closeApplyImage"
    >
      <div class="manual-checklist">
        <label v-for="manual in applyImageImpact.relatedManuals" :key="manual.id" class="manual-option">
          <input
            type="checkbox"
            :checked="selectedApplyManualIds.includes(manual.id)"
            @change="toggleApplyManual(manual.id, ($event.target as HTMLInputElement).checked)"
          />
          <span>{{ manualLabel(manual) }}</span>
          <small>{{ statusLabel(manual.activeStatus) }}</small>
        </label>
      </div>
      <template #footer>
        <button type="button" class="btn btn-outline" @click="closeApplyImage">No aplicar</button>
        <button type="button" class="btn btn-primary" :disabled="!selectedApplyManualIds.length" @click="applyImageToSelectedManuals">Aplicar seleccionados</button>
      </template>
    </AppModal>
  </section>
</template>

<style scoped>
.products-page { padding: 24px; display: grid; gap: 16px; }
.head, .toolbar, .search-box, .row-actions, .modal-actions, .image-row, .image-actions { display: flex; align-items: center; gap: 10px; }
.head { justify-content: space-between; }
.list-card { overflow: auto; }
.toolbar { padding: 12px; border-bottom: 1px solid var(--border); }
.search-box { border: 1px solid var(--border); background: var(--input-background); padding: 8px 10px; min-width: 280px; }
.search-box input { border: 0; outline: 0; background: transparent; width: 100%; }
.thumb { width: 56px; height: 42px; border: 1px solid var(--border); background: var(--input-background); display: grid; place-items: center; color: var(--muted-foreground); }
.thumb img { width: 100%; height: 100%; object-fit: contain; background: #fff; }
td strong, td small { display: block; }
td small { margin-top: 2px; color: var(--muted-foreground); font-size: 11px; }
.row-actions button { width: 30px; height: 30px; border: 1px solid var(--border); background: #fff; color: var(--muted-foreground); display: grid; place-items: center; padding: 0; }
.row-actions button:hover { color: var(--dikoin-blue); background: var(--dikoin-blue-lighter); }
.product-form { display: grid; gap: 12px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
label { display: grid; gap: 6px; color: var(--muted-foreground); font-size: 12px; font-weight: 600; }
.check { display: flex; align-items: center; color: var(--foreground); font-size: 13px; }
.category-picker { display: grid; gap: 6px; max-height: 220px; overflow: auto; padding: 10px; border: 1px solid var(--border); background: var(--input-background); }
.category-picker strong { font-size: 12px; color: var(--foreground); }
.category-option { grid-template-columns: auto 32px 1fr; align-items: center; gap: 8px; padding: 7px; border-radius: var(--radius); background: #fff; color: var(--foreground); }
.category-option input { width: 14px; height: 14px; }
.category-option span { font-weight: 600; color: var(--dikoin-blue); }
.category-option small { color: var(--muted-foreground); font-size: 11px; line-height: 1.25; }
.category-chips { display: flex; flex-wrap: wrap; gap: 4px; }
.category-chips span { padding: 3px 6px; border-radius: 999px; background: var(--dikoin-blue-lighter); color: var(--dikoin-blue); font-size: 11px; font-weight: 600; }
.image-row { align-items: stretch; border: 1px solid var(--border); background: var(--input-background); padding: 10px; }
.image-preview { width: 112px; min-height: 84px; display: grid; place-items: center; border: 1px solid var(--border); background: #fff; color: var(--muted-foreground); }
.image-preview img { max-width: 100%; max-height: 84px; object-fit: contain; }
.image-actions { align-items: flex-start; flex-direction: column; justify-content: center; }
.image-upload { border: 1px dashed var(--border); background: #fff; padding: 9px 11px; display: flex; align-items: center; justify-content: center; gap: 8px; color: var(--dikoin-blue); cursor: pointer; }
.image-upload input { display: none; }
.modal-actions { justify-content: flex-end; padding-top: 4px; }
.detail { display: grid; grid-template-columns: 160px minmax(0, 1fr); gap: 16px; }
.detail-image { min-height: 130px; display: grid; place-items: center; border: 1px solid var(--border); background: var(--input-background); color: var(--muted-foreground); }
.detail-image img { max-width: 100%; max-height: 160px; object-fit: contain; background: #fff; }
dl { margin: 0; display: grid; gap: 8px; }
dl div { display: grid; grid-template-columns: 120px minmax(0, 1fr); gap: 10px; }
dt { color: var(--muted-foreground); font-size: 12px; font-weight: 600; }
dd { margin: 0; }
.confirm-panel { display: grid; gap: 12px; }
.manual-checklist { display: grid; gap: 8px; }
.manual-checklist h3 { margin: 0; font-size: 14px; }
.manual-checklist p { margin: 0; color: var(--muted-foreground); font-size: 12px; }
.manual-option { grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; padding: 9px; border: 1px solid var(--border); background: var(--input-background); color: var(--foreground); }
.manual-option span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.manual-option small { color: var(--muted-foreground); }
.success-msg { background: var(--dikoin-green-light); color: #065f46; border: 1px solid #86efac; padding: 10px; border-radius: var(--radius); }
@media (max-width: 760px) {
  .form-grid, .detail, dl div { grid-template-columns: 1fr; }
  .toolbar { align-items: stretch; flex-direction: column; }
  .search-box { min-width: 0; }
}
</style>
