<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Plus, Save, Search } from '@lucide/vue'
import BackendError from '@/components/shared/BackendError.vue'
import { useProductsStore } from '@/stores/products.store'
import type { ProductRequest, ProductResponse } from '@/types/api'

const store = useProductsStore()
const search = ref('')
const editingId = ref<number | undefined>()
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

onMounted(async () => {
  await Promise.all([store.fetchProducts(), store.fetchTaxonomy()])
})

function edit(product: ProductResponse) {
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
}

function toggleCategory(id: number, checked: boolean) {
  const selected = new Set(form.categoryIds || [])
  if (checked) {
    selected.add(id)
  } else {
    selected.delete(id)
  }
  form.categoryIds = [...selected]
}

function categoryChecked(id: number) {
  return Boolean(form.categoryIds?.includes(id))
}

function productCategories(product: ProductResponse) {
  return product.categories?.length ? product.categories : []
}

async function save() {
  const family = selectedFamily.value
  const categories = store.categories.filter((category) => form.categoryIds?.includes(category.id))
  await store.saveProduct({
    ...form,
    name: form.nameEs || form.name,
    family: family ? `${family.code} - ${family.nameEs}` : form.family,
    familyCode: family?.code || form.familyCode,
    category: categories.map((category) => `${category.code} - ${category.nameEs}`).join(' | '),
    categoryCodes: categories.map((category) => category.code).join(', '),
    description: form.descriptionEs || form.description,
  }, editingId.value)
  reset()
}
</script>

<template>
  <section class="products-page">
    <div class="head">
      <div>
        <h1 class="page-title">Productos</h1>
        <p class="text-muted">Cada producto puede pertenecer a varias categorias y mantiene datos ES/EN.</p>
      </div>
      <button class="btn btn-outline" @click="reset"><Plus :size="14" /> Nuevo</button>
    </div>

    <BackendError :message="store.error" />

    <div class="grid">
      <form class="card product-form" @submit.prevent="save">
        <h2>{{ editingId ? 'Editar producto' : 'Crear producto' }}</h2>
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

        <label>Descripcion ES <textarea v-model="form.descriptionEs" class="field" rows="4" /></label>
        <label>Descripcion EN <textarea v-model="form.descriptionEn" class="field" rows="4" /></label>
        <label class="check"><input v-model="form.active" type="checkbox" /> Producto activo</label>
        <button class="btn btn-primary"><Save :size="14" /> Guardar</button>
      </form>

      <div class="card list-card">
        <div class="toolbar">
          <div class="search-box">
            <Search :size="14" />
            <input v-model="search" placeholder="Buscar producto..." @keydown.enter="store.fetchProducts(search)" />
          </div>
          <button class="btn btn-outline" @click="store.fetchProducts(search)">Buscar</button>
        </div>
        <table class="table">
          <thead><tr><th>Codigo</th><th>Nombre</th><th>Familia</th><th>Categorias</th><th>Activo</th></tr></thead>
          <tbody>
            <tr v-if="store.loading"><td colspan="5">Cargando productos...</td></tr>
            <tr v-else-if="!store.products.length"><td colspan="5">No hay productos.</td></tr>
            <tr v-for="product in store.products" :key="product.id" @click="edit(product)">
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
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>

<style scoped>
.products-page { padding: 24px; display: grid; gap: 16px; }
.head, .toolbar, .search-box { display: flex; align-items: center; gap: 10px; }
.head { justify-content: space-between; }
.grid { display: grid; grid-template-columns: 390px minmax(0, 1fr); gap: 16px; align-items: start; }
.product-form { padding: 16px; display: grid; gap: 12px; }
.product-form h2 { margin: 0; font-size: 16px; }
label { display: grid; gap: 6px; color: var(--muted-foreground); font-size: 12px; font-weight: 600; }
.check { display: flex; align-items: center; color: var(--foreground); font-size: 13px; }
.category-picker { display: grid; gap: 6px; max-height: 270px; overflow: auto; padding: 10px; border: 1px solid var(--border); background: var(--input-background); }
.category-picker strong { font-size: 12px; color: var(--foreground); }
.category-option { grid-template-columns: auto 32px 1fr; align-items: center; gap: 8px; padding: 7px; border-radius: var(--radius); background: #fff; color: var(--foreground); }
.category-option input { width: 14px; height: 14px; }
.category-option span { font-weight: 600; color: var(--dikoin-blue); }
.category-option small { color: var(--muted-foreground); font-size: 11px; line-height: 1.25; }
.list-card { overflow: auto; }
.toolbar { padding: 12px; border-bottom: 1px solid var(--border); }
.search-box { border: 1px solid var(--border); background: var(--input-background); padding: 8px 10px; min-width: 280px; }
.search-box input { border: 0; outline: 0; background: transparent; width: 100%; }
tbody tr { cursor: pointer; }
tbody tr:hover td { background: var(--dikoin-blue-lighter); }
td strong, td small { display: block; }
td small { margin-top: 2px; color: var(--muted-foreground); font-size: 11px; }
.category-chips { display: flex; flex-wrap: wrap; gap: 4px; }
.category-chips span { padding: 3px 6px; border-radius: 999px; background: var(--dikoin-blue-lighter); color: var(--dikoin-blue); font-size: 11px; font-weight: 600; }
@media (max-width: 980px) { .grid { grid-template-columns: 1fr; } }
</style>
