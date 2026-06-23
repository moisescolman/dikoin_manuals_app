<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, ImageUp, Save } from '@lucide/vue'
import { getDocumentTypes } from '@/api/document-types.api'
import { getApiError } from '@/api/http'
import { createManual } from '@/api/manuals.api'
import { uploadAsset } from '@/api/assets.api'
import BackendError from '@/components/shared/BackendError.vue'
import { useProductsStore } from '@/stores/products.store'
import type { DocumentTypeResponse, LanguageCode } from '@/types/api'

const router = useRouter()
const productsStore = useProductsStore()
const documentTypes = ref<DocumentTypeResponse[]>([])
const productId = ref<number | ''>('')
const documentTypeId = ref<number | ''>('')
const documentYear = ref(new Date().getFullYear().toString().slice(-2))
const documentVersion = ref('01')
const languageCode = ref<LanguageCode>('ES')
const title = ref('')
const titleEs = ref('')
const titleEn = ref('')
const category = ref('')
const productImage = ref<File | null>(null)
const productImagePreview = ref('')
const loading = ref(false)
const error = ref('')

const selectedProduct = computed(() => productsStore.products.find((product) => product.id === Number(productId.value)))
const selectedDocumentType = computed(() => documentTypes.value.find((type) => type.id === Number(documentTypeId.value)))
const generatedManualCode = computed(() => {
  if (!selectedProduct.value || !selectedDocumentType.value || !documentYear.value || !documentVersion.value) return ''
  return `${selectedDocumentType.value.code}-${selectedProduct.value.code}-${twoDigits(documentYear.value)}${twoDigits(documentVersion.value)}[${languageCode.value}]`
})
const canCreate = computed(() => Boolean(title.value && productId.value && documentTypeId.value && documentYear.value && documentVersion.value))

onMounted(async () => {
  await Promise.all([productsStore.fetchProducts(), loadDocumentTypes()])
})

async function loadDocumentTypes() {
  documentTypes.value = await getDocumentTypes()
  if (!documentTypeId.value && documentTypes.value.length) {
    documentTypeId.value = documentTypes.value[0].id
  }
}

function pickProductImage(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0] || null
  productImage.value = file
  if (productImagePreview.value) URL.revokeObjectURL(productImagePreview.value)
  productImagePreview.value = file ? URL.createObjectURL(file) : ''
}

async function submit() {
  if (!canCreate.value) return
  loading.value = true
  error.value = ''
  try {
    const manual = await createManual({
      title: title.value,
      titleEs: titleEs.value || title.value,
      titleEn: titleEn.value || undefined,
      category: category.value || selectedDocumentType.value?.name,
      productId: Number(productId.value),
      documentTypeId: Number(documentTypeId.value),
      documentYear: twoDigits(documentYear.value),
      documentVersion: twoDigits(documentVersion.value),
      languageCode: languageCode.value,
    })

    if (productImage.value) {
      await uploadAsset({ file: productImage.value, assetType: 'PRODUCT_IMAGE', manualId: manual.id })
    }

    router.push({ name: 'manual-editor', params: { id: manual.id } })
  } catch (err) {
    error.value = getApiError(err)
  } finally {
    loading.value = false
  }
}

function twoDigits(value: string) {
  const digits = value.replace(/\D/g, '')
  if (digits.length === 1) return `0${digits}`
  if (digits.length > 2) return digits.slice(-2)
  return digits
}
</script>

<template>
  <section class="create-page">
    <div class="head">
      <div>
        <h1 class="page-title">Crear manual</h1>
        <p class="text-muted">Asistente para crear el borrador inicial y la portada del manual.</p>
      </div>
      <button class="btn btn-outline" @click="router.push({ name: 'manuals' })"><ArrowLeft :size="15" /> Manuales</button>
    </div>

    <BackendError :message="error || productsStore.error" />

    <div class="create-grid">
      <form class="card wizard" @submit.prevent="submit">
        <h2>Datos iniciales</h2>

        <label>
          Producto
          <select v-model="productId" class="field" required>
            <option value="">Selecciona producto</option>
            <option v-for="product in productsStore.products" :key="product.id" :value="product.id">
              {{ product.code }} - {{ product.name }}
            </option>
          </select>
        </label>

        <label>
          Tipo documental
          <select v-model="documentTypeId" class="field" required>
            <option value="">Selecciona tipo</option>
            <option v-for="type in documentTypes" :key="type.id" :value="type.id">
              {{ type.code }} - {{ type.name }}
            </option>
          </select>
        </label>

        <div class="form-row">
          <label>Año <input v-model="documentYear" class="field mono" maxlength="2" required /></label>
          <label>Version <input v-model="documentVersion" class="field mono" maxlength="2" required /></label>
          <label>
            Idioma
            <select v-model="languageCode" class="field">
              <option value="ES">ES</option>
              <option value="EN">EN</option>
            </select>
          </label>
        </div>

        <label>Codigo generado <input class="field mono" :value="generatedManualCode" readonly /></label>
        <label>Titulo del manual <input v-model="title" class="field" required /></label>
        <label>Titulo ES <input v-model="titleEs" class="field" placeholder="Si se deja vacio usa el titulo" /></label>
        <label>Titulo EN <input v-model="titleEn" class="field" /></label>
        <label>Categoria <input v-model="category" class="field" :placeholder="selectedDocumentType?.name || 'Categoria del manual'" /></label>

        <label class="image-upload">
          <input type="file" accept="image/*" @change="pickProductImage" />
          <ImageUp :size="18" />
          <span>{{ productImage?.name || 'Subir imagen del producto' }}</span>
        </label>

        <button class="btn btn-primary" :disabled="!canCreate || loading">
          <Save :size="15" /> {{ loading ? 'Creando...' : 'Crear manual y abrir editor' }}
        </button>
      </form>

      <aside class="card cover-preview">
        <div class="preview-logo">DIKOIN</div>
        <div class="preview-product">
          <img v-if="productImagePreview" :src="productImagePreview" alt="Producto" />
          <span v-else>Imagen del producto</span>
        </div>
        <div class="preview-title">
          <h2>{{ selectedProduct?.name || 'Nombre del producto' }}</h2>
          <p>{{ title || 'Titulo del manual' }}</p>
          <strong>{{ selectedDocumentType?.name || 'Tipo de manual' }}</strong>
        </div>
        <small class="mono">{{ generatedManualCode || 'CODIGO' }}</small>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.create-page {
  padding: 24px;
  display: grid;
  gap: 16px;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.create-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 390px;
  gap: 16px;
  align-items: start;
}

.wizard {
  padding: 16px;
  display: grid;
  gap: 12px;
}

.wizard h2 {
  margin: 0;
  font-size: 16px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 10px;
}

label {
  display: grid;
  gap: 6px;
  color: var(--muted-foreground);
  font-size: 12px;
  font-weight: 600;
}

.image-upload {
  border: 1px dashed var(--border);
  background: var(--input-background);
  padding: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--dikoin-blue);
  cursor: pointer;
}

.image-upload input {
  display: none;
}

.cover-preview {
  min-height: 620px;
  padding: 28px;
  display: grid;
  grid-template-rows: auto minmax(250px, 1fr) auto auto;
  gap: 24px;
  text-align: center;
}

.preview-logo {
  color: var(--dikoin-blue);
  font-size: 44px;
  font-weight: 600;
  letter-spacing: 2px;
}

.preview-product {
  display: grid;
  place-items: center;
  color: var(--muted-foreground);
  border: 1px dashed var(--border);
  background: #fff;
}

.preview-product img {
  max-width: 100%;
  max-height: 300px;
  object-fit: contain;
}

.preview-title {
  display: grid;
  gap: 6px;
  justify-items: center;
}

.preview-title h2 {
  margin: 0;
  color: var(--dikoin-blue);
  font-size: 28px;
  line-height: 1.15;
}

.preview-title p {
  margin: 0;
  color: var(--dikoin-blue);
  font-weight: 600;
  border-top: 1px solid var(--dikoin-blue);
  width: 100%;
  padding-top: 6px;
}

.preview-title strong {
  width: 100%;
  background: var(--dikoin-blue);
  color: #fff;
  padding: 5px 8px;
}

.cover-preview small {
  justify-self: start;
  color: var(--muted-foreground);
}

@media (max-width: 1060px) {

  .create-grid,
  .form-row {
    grid-template-columns: 1fr;
  }
}
</style>
