<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { FileText, FileUp, Upload } from '@lucide/vue'
import { getDocumentTypes } from '@/api/document-types.api'
import { getApiError } from '@/api/http'
import { getImportJobs, importManual } from '@/api/imports.api'
import BackendError from '@/components/shared/BackendError.vue'
import { useProductsStore } from '@/stores/products.store'
import type { DocumentTypeResponse, ImportJobResponse, LanguageCode } from '@/types/api'
import { formatDate } from '@/utils/formatters'

const router = useRouter()
const productsStore = useProductsStore()
const jobs = ref<ImportJobResponse[]>([])
const documentTypes = ref<DocumentTypeResponse[]>([])
const selectedFile = ref<File | null>(null)
const productId = ref<number | ''>('')
const documentTypeId = ref<number | ''>('')
const documentYear = ref(new Date().getFullYear().toString().slice(-2))
const documentVersion = ref('01')
const title = ref('')
const languageCode = ref<LanguageCode>('ES')
const importType = ref<'documents' | 'pdf'>('documents')
const loading = ref(false)
const error = ref('')
const success = ref('')

const allowedHint = computed(() => importType.value === 'pdf' ? '.pdf' : '.docx, .doc, .odt')
const selectedProduct = computed(() => productsStore.products.find((product) => product.id === Number(productId.value)))
const selectedDocumentType = computed(() => documentTypes.value.find((type) => type.id === Number(documentTypeId.value)))
const generatedManualCode = computed(() => {
  if (!selectedProduct.value || !selectedDocumentType.value || !documentYear.value || !documentVersion.value) return ''
  return `${selectedDocumentType.value.code}-${selectedProduct.value.code}-${twoDigits(documentYear.value)}${twoDigits(documentVersion.value)}[${languageCode.value}]`
})
const canSubmit = computed(() => Boolean(selectedFile.value && productId.value && documentTypeId.value && documentYear.value && documentVersion.value && title.value))

onMounted(async () => {
  await Promise.all([productsStore.fetchProducts(), loadDocumentTypes(), loadJobs()])
})

async function loadDocumentTypes() {
  documentTypes.value = await getDocumentTypes()
  if (!documentTypeId.value && documentTypes.value.length) {
    documentTypeId.value = documentTypes.value[0].id
  }
}

async function loadJobs() {
  jobs.value = await getImportJobs()
}

function pickFile(event: Event) {
  const input = event.target as HTMLInputElement
  selectedFile.value = input.files?.[0] || null
  if (selectedFile.value && !title.value) {
    title.value = selectedFile.value.name.replace(/\.[^.]+$/, '')
  }
}

async function submit() {
  if (!selectedFile.value || !productId.value) return
  loading.value = true
  error.value = ''
  success.value = ''
  try {
    const job = await importManual({
      file: selectedFile.value,
      productId: Number(productId.value),
      manualCode: generatedManualCode.value,
      documentTypeId: Number(documentTypeId.value),
      documentYear: twoDigits(documentYear.value),
      documentVersion: twoDigits(documentVersion.value),
      title: title.value,
      languageCode: languageCode.value,
      type: importType.value,
    })
    success.value = job.logMessage || 'Importación completada. Revisa el borrador generado antes de publicarlo.'
    await loadJobs()
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
  <section class="import-page">
    <div class="head">
      <div>
        <h1 class="page-title">Importar manual</h1>
        <p class="text-muted">El sistema extrae texto, secciones, tablas e imágenes y genera un borrador.</p>
      </div>
      <button class="btn btn-outline" @click="router.push({ name: 'manuals' })">Ver manuales</button>
    </div>

    <BackendError :message="error || productsStore.error" />
    <div v-if="success" class="success-msg">{{ success }}</div>

    <div class="import-grid">
      <form class="card import-card" @submit.prevent="submit">
        <div class="type-switch">
          <button type="button" :class="{ active: importType === 'documents' }" @click="importType = 'documents'">
            <FileText :size="18" /> Texto / Word / ODT
          </button>
          <button type="button" :class="{ active: importType === 'pdf' }" @click="importType = 'pdf'">
            <FileUp :size="18" /> PDF
          </button>
        </div>

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
          <label>
            Año
            <input v-model="documentYear" class="field mono" maxlength="2" placeholder="26" required />
          </label>
          <label>
            Versión
            <input v-model="documentVersion" class="field mono" maxlength="2" placeholder="01" required />
          </label>
        </div>

        <label>
          Código generado
          <input class="field mono" :value="generatedManualCode" placeholder="DMP-FLB10.1-2601[ES]" readonly />
        </label>

        <label>
          Título
          <input v-model="title" class="field" placeholder="Manual de prácticas..." required />
        </label>

        <label>
          Idioma del archivo
          <select v-model="languageCode" class="field">
            <option value="ES">Español</option>
            <option value="EN">Inglés</option>
          </select>
        </label>

        <label class="drop-zone">
          <Upload :size="24" />
          <strong>{{ selectedFile?.name || 'Seleccionar archivo' }}</strong>
          <span>Formatos esperados: {{ allowedHint }}</span>
          <input type="file" :accept="allowedHint" @change="pickFile" />
        </label>

        <button class="btn btn-primary" :disabled="!canSubmit || loading">
          {{ loading ? 'Importando...' : 'Importar y crear borrador' }}
        </button>
      </form>

      <aside class="card help-card">
        <h2>Flujo recomendado</h2>
        <ol>
          <li>Seleccionar producto, tipo documental, año, versión e idioma.</li>
          <li>Importar el archivo original.</li>
          <li>Revisar el borrador generado en el editor por secciones.</li>
          <li>Completar idioma pendiente y publicar.</li>
        </ol>
      </aside>
    </div>

    <div class="card">
      <table class="table">
        <thead>
          <tr><th>Archivo</th><th>Tipo</th><th>Estado</th><th>Idioma</th><th>Secciones</th><th>Tablas</th><th>Imágenes</th><th>Fecha</th><th>Mensaje</th></tr>
        </thead>
        <tbody>
          <tr v-if="!jobs.length"><td colspan="9">No hay importaciones registradas.</td></tr>
          <tr v-for="job in jobs" :key="job.id">
            <td>{{ job.sourceFilename }}</td>
            <td class="mono">{{ job.fileExtension }}</td>
            <td>{{ job.status }}</td>
            <td>{{ job.languageCode }}</td>
            <td>{{ job.detectedSections ?? '-' }}</td>
            <td>{{ job.detectedTables ?? '-' }}</td>
            <td>{{ job.detectedImages ?? '-' }}</td>
            <td>{{ formatDate(job.createdAt) }}</td>
            <td>{{ job.logMessage }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.import-page {
  padding: 24px;
  display: grid;
  gap: 16px;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.import-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 330px;
  gap: 16px;
}

.import-card,
.help-card {
  padding: 16px;
  display: grid;
  gap: 14px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

label {
  display: grid;
  gap: 6px;
  color: var(--muted-foreground);
  font-weight: 700;
  font-size: 12px;
}

.type-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.type-switch button {
  border: 1px solid var(--border);
  background: #fff;
  padding: 14px;
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: center;
}

.type-switch button.active {
  border-color: var(--dikoin-blue);
  background: var(--dikoin-blue-lighter);
  color: var(--dikoin-blue-dark);
  font-weight: 700;
}

.drop-zone {
  border: 1px dashed var(--border);
  background: var(--input-background);
  padding: 28px;
  place-items: center;
  text-align: center;
}

.drop-zone:hover {
  border: 1px solid var(--dikoin-blue);
  background: var(--dikoin-blue-lighter);
}

.drop-zone input {
  display: none;
}

.drop-zone span {
  font-weight: 400;
}

.success-msg {
  background: var(--dikoin-green-light);
  color: #065f46;
  border: 1px solid #86efac;
  padding: 10px;
  border-radius: var(--radius);
}

@media (max-width: 960px) {
  .import-grid {
    grid-template-columns: 1fr;
  }

  .form-row {
    grid-template-columns: 1fr;
  }
}
</style>
