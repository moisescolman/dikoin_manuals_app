<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Plus, Save, Search } from '@lucide/vue'
import BackendError from '@/components/shared/BackendError.vue'
import { useProductsStore } from '@/stores/products.store'
import type { ProductRequest, ProductResponse } from '@/types/api'

const store = useProductsStore()
const search = ref('')
const editingId = ref<number | undefined>()
const form = reactive<ProductRequest>({ code: '', name: '', family: '', category: '', description: '', active: true })

onMounted(() => store.fetchProducts())

function edit(product: ProductResponse) {
  editingId.value = product.id
  form.code = product.code
  form.name = product.name
  form.family = product.family || ''
  form.category = product.category || ''
  form.description = product.description || ''
  form.active = product.active
}

function reset() {
  editingId.value = undefined
  form.code = ''
  form.name = ''
  form.family = ''
  form.category = ''
  form.description = ''
  form.active = true
}

async function save() {
  await store.saveProduct({ ...form }, editingId.value)
  reset()
}
</script>

<template>
  <section class="products-page">
    <div class="head">
      <div>
        <h1 class="page-title">Productos</h1>
        <p class="text-muted">Cada manual pertenece a un único producto.</p>
      </div>
      <button class="btn btn-outline" @click="reset"><Plus :size="14" /> Nuevo</button>
    </div>

    <BackendError :message="store.error" />

    <div class="grid">
      <form class="card product-form" @submit.prevent="save">
        <h2>{{ editingId ? 'Editar producto' : 'Crear producto' }}</h2>
        <label>Código <input v-model="form.code" class="field mono" required /></label>
        <label>Nombre <input v-model="form.name" class="field" required /></label>
        <label>Familia <input v-model="form.family" class="field" /></label>
        <label>Categoría <input v-model="form.category" class="field" /></label>
        <label>Descripción <textarea v-model="form.description" class="field" rows="4" /></label>
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
          <thead><tr><th>Código</th><th>Nombre</th><th>Familia</th><th>Categoría</th><th>Activo</th></tr></thead>
          <tbody>
            <tr v-if="store.loading"><td colspan="5">Cargando productos...</td></tr>
            <tr v-else-if="!store.products.length"><td colspan="5">No hay productos.</td></tr>
            <tr v-for="product in store.products" :key="product.id" @click="edit(product)">
              <td class="mono">{{ product.code }}</td>
              <td>{{ product.name }}</td>
              <td>{{ product.family || '-' }}</td>
              <td>{{ product.category || '-' }}</td>
              <td>{{ product.active ? 'Sí' : 'No' }}</td>
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
.grid { display: grid; grid-template-columns: 340px minmax(0, 1fr); gap: 16px; }
.product-form { padding: 16px; display: grid; gap: 12px; }
.product-form h2 { margin: 0; font-size: 16px; }
label { display: grid; gap: 6px; color: var(--muted-foreground); font-size: 12px; font-weight: 600; }
.check { display: flex; align-items: center; color: var(--foreground); font-size: 13px; }
.list-card { overflow: auto; }
.toolbar { padding: 12px; border-bottom: 1px solid var(--border); }
.search-box { border: 1px solid var(--border); background: var(--input-background); padding: 8px 10px; min-width: 280px; }
.search-box input { border: 0; outline: 0; background: transparent; width: 100%; }
tbody tr { cursor: pointer; }
tbody tr:hover td { background: var(--dikoin-blue-lighter); }
@media (max-width: 980px) { .grid { grid-template-columns: 1fr; } }
</style>
