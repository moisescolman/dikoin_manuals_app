<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Eye, Trash2, Upload } from '@lucide/vue'
import { toBackendUrl } from '@/api/http'
import BackendError from '@/components/shared/BackendError.vue'
import { useAssetsStore } from '@/stores/assets.store'
import type { AssetResponse, AssetType } from '@/types/api'
import { assetTypeLabel, formatBytes, formatDate } from '@/utils/formatters'

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

function assetImage(asset: AssetResponse) {
  return asset.thumbnailUrl || asset.fileUrl
}

function canPreview(asset: AssetResponse) {
  return Boolean(asset.fileUrl && (asset.mimeType?.startsWith('image/') || ['IMAGE', 'PRODUCT_IMAGE', 'LOGO', 'EXTRACTED_IMAGE'].includes(asset.assetType)))
}

function openAsset(asset: AssetResponse) {
  if (!asset.fileUrl) return
  window.open(toBackendUrl(asset.fileUrl), '_blank', 'noopener')
}

async function removeAsset(asset: AssetResponse) {
  if (!window.confirm(`Eliminar ${asset.originalFilename}?`)) return
  await store.remove(asset.id)
  message.value = 'Asset eliminado.'
}
</script>

<template>
  <section class="assets-page">
    <div>
      <h1 class="page-title">Assets</h1>
      <p class="text-muted">Imagenes, logos, documentos fuente y recursos reutilizables almacenados.</p>
    </div>

    <BackendError :message="store.error" />
    <div v-if="message" class="success-msg">{{ message }}</div>

    <div class="card upload-card">
      <label>
        Tipo
        <select v-model="assetType" class="field">
          <option value="IMAGE">Imagen</option>
          <option value="PRODUCT_IMAGE">Imagen producto</option>
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

    <div class="card assets-card">
      <table class="table">
        <thead>
          <tr><th>Vista</th><th>Nombre</th><th>Tipo</th><th>MIME</th><th>Tamano</th><th>Manual</th><th>Ruta</th><th>Fecha</th><th>Acciones</th></tr>
        </thead>
        <tbody>
          <tr v-if="store.loading"><td colspan="9">Cargando assets...</td></tr>
          <tr v-else-if="!store.assets.length"><td colspan="9">No hay assets.</td></tr>
          <tr v-for="asset in store.assets" :key="asset.id">
            <td>
              <button v-if="assetImage(asset)" class="thumb" type="button" @click="openAsset(asset)">
                <img :src="toBackendUrl(assetImage(asset) || '')" :alt="asset.originalFilename" />
              </button>
              <span v-else class="thumb empty">-</span>
            </td>
            <td>{{ asset.originalFilename }}</td>
            <td>{{ assetTypeLabel(asset.assetType) }}</td>
            <td>{{ asset.mimeType || '-' }}</td>
            <td>{{ formatBytes(asset.fileSize) }}</td>
            <td>{{ asset.manualId || '-' }}</td>
            <td class="mono path-cell">{{ asset.storagePath }}</td>
            <td>{{ formatDate(asset.createdAt) }}</td>
            <td>
              <div class="row-actions">
                <button title="Visualizar" :disabled="!canPreview(asset)" @click="openAsset(asset)"><Eye :size="15" /></button>
                <button title="Eliminar" @click="removeAsset(asset)"><Trash2 :size="15" /></button>
              </div>
            </td>
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

.assets-card {
  overflow: auto;
}

label {
  display: grid;
  gap: 6px;
  color: var(--muted-foreground);
  font-size: 12px;
  font-weight: 600;
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

.thumb {
  width: 64px;
  height: 44px;
  border: 1px solid var(--border);
  background: #fff;
  padding: 0;
  display: grid;
  place-items: center;
  overflow: hidden;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumb.empty {
  color: var(--muted-foreground);
}

.path-cell {
  max-width: 260px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.row-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.row-actions button {
  border: 0;
  background: transparent;
  color: var(--muted-foreground);
  padding: 4px;
}

.row-actions button:hover:not(:disabled) {
  color: var(--dikoin-blue);
  background: var(--dikoin-blue-lighter);
}

@media (max-width: 980px) {
  .upload-card {
    grid-template-columns: 1fr;
  }
}
</style>
