<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import BackendError from '@/components/shared/BackendError.vue'
import StatusBadge from '@/components/shared/StatusBadge.vue'
import { useManualsStore } from '@/stores/manuals.store'
import { formatDate } from '@/utils/formatters'

const props = defineProps<{ id?: string }>()
const router = useRouter()
const store = useManualsStore()

onMounted(async () => {
  if (props.id) await store.fetchManual(Number(props.id))
})
</script>

<template>
  <section class="history-page">
    <div class="head">
      <div>
        <h1 class="page-title">Historial de versiones</h1>
        <p class="text-muted">{{ store.current?.code || 'Selecciona un manual desde el listado' }}</p>
      </div>
      <button class="btn btn-outline" @click="router.push({ name: 'manuals' })">Volver a manuales</button>
    </div>

    <BackendError :message="store.error" />

    <div class="card">
      <table class="table">
        <thead><tr><th>Versión</th><th>Estado</th><th>Activa</th><th>ES</th><th>EN</th><th>Creada</th><th>Publicada</th><th>Notas</th></tr></thead>
        <tbody>
          <tr v-if="store.loading"><td colspan="8">Cargando historial...</td></tr>
          <tr v-else-if="!store.current"><td colspan="8">No hay manual cargado.</td></tr>
          <tr v-for="version in store.current?.versions || []" :key="version.id">
            <td class="mono">v{{ version.versionNumber }}</td>
            <td><StatusBadge :status="version.status" /></td>
            <td>{{ version.active ? 'Sí' : 'No' }}</td>
            <td>{{ version.esReady ? 'Listo' : 'Pendiente' }}</td>
            <td>{{ version.enReady ? 'Listo' : 'Pendiente' }}</td>
            <td>{{ formatDate(version.createdAt) }}</td>
            <td>{{ formatDate(version.publishedAt) }}</td>
            <td>{{ version.changeNotes || '-' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.history-page { padding: 24px; display: grid; gap: 16px; }
.head { display: flex; justify-content: space-between; align-items: center; }
</style>
