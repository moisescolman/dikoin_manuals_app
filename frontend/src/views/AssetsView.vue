<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Upload } from '@lucide/vue'
import BackendError from '@/components/shared/BackendError.vue'
import { useAssetsStore } from '@/stores/assets.store'
import type { AssetType } from '@/types/api'
import { formatBytes, formatDate } from '@/utils/formatters'

const store = useAssetsStore()
const selectedFile = ref<File | null>(null)
const assetType = ref<AssetType>('IMAGE')
const manualId = ref('')
const message = ref('')

onMounted(() => store.fetchAssets())

function pickFile(event: Event) {
  selectedFile.value = (event.target as HTMLInputElement).files?.[0] || null
}

async function upload() {
  if (!selectedFile.value) return
  const asset = await store.upload(selectedFile.value, assetType.value, manualId.value ? Number(manualId.value) : undefined)
  message.value = `Asset guardado en ${asset.storagePath}`
  selectedFile.value = null
}
</script>

<template>
  <section class="assets-page">
    <div>
      <h1 class="page-title">Assets</h1>
      <p class="text-muted">Imágenes, logos, documentos fuente y recursos reutilizables almacenados.</p>
    </div>

    <BackendError :message="store.error" />
    <div v-if="message" class="success-msg">{{ message }}</div>

    <div class="card upload-card">
      <label>
        Tipo
        <select v-model="assetType" class="field">
          <option value="IMAGE">Imagen</option>
          <option value="LOGO">Logo</option>
          <option value="DOCUMENT_SOURCE">Documento fuente</option>
          <option value="TEMPLATE_RESOURCE">Recurso plantilla</option>
          <option value="OTHER">Otro</option>
        </select>
      </label>
      <label>
        Manual ID opcional
        <input v-model="manualId" class="field" placeholder="Ej. 1" />
      </label>
      <label class="file-input">
        <input type="file" @change="pickFile" />
        <span>{{ selectedFile?.name || 'Seleccionar archivo' }}</span>
      </label>
      <button class="btn btn-primary" :disabled="!selectedFile" @click="upload"><Upload :size="14" /> Subir</button>
    </div>

    <div class="card">
      <table class="table">
        <thead><tr><th>Nombre</th><th>Tipo</th><th>MIME</th><th>Tamaño</th><th>Manual</th><th>Ruta</th><th>Fecha</th></tr></thead>
        <tbody>
          <tr v-if="store.loading"><td colspan="7">Cargando assets...</td></tr>
          <tr v-else-if="!store.assets.length"><td colspan="7">No hay assets.</td></tr>
          <tr v-for="asset in store.assets" :key="asset.id">
            <td>{{ asset.originalFilename }}</td>
            <td>{{ asset.assetType }}</td>
            <td>{{ asset.mimeType || '-' }}</td>
            <td>{{ formatBytes(asset.fileSize) }}</td>
            <td>{{ asset.manualId || '-' }}</td>
            <td class="mono">{{ asset.storagePath }}</td>
            <td>{{ formatDate(asset.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.assets-page {
  padding: 24px;
  display: grid;
  gap: 16px;
}

.upload-card {
  padding: 14px;
  display: grid;
  grid-template-columns: 190px 160px minmax(240px, 1fr) auto;
  gap: 12px;
  align-items: end;
}

label {
  display: grid;
  gap: 6px;
  color: var(--muted-foreground);
  font-size: 12px;
  font-weight: 700;
}

.file-input {
  border: 1px dashed var(--border);
  background: var(--input-background);
  padding: 9px 10px;
  color: var(--foreground);
}

.file-input input {
  display: none;
}

.success-msg {
  background: var(--dikoin-green-light);
  color: #065f46;
  border: 1px solid #86efac;
  padding: 10px;
  border-radius: var(--radius);
}

@media (max-width: 980px) {
  .upload-card {
    grid-template-columns: 1fr;
  }
}
</style>
