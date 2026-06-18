<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { BookOpen, CheckCircle, Clock, FileText, Languages, Package, Upload } from '@lucide/vue'
import { getDashboard } from '@/api/dashboard.api'
import { getApiError } from '@/api/http'
import { useManualsStore } from '@/stores/manuals.store'
import BackendError from '@/components/shared/BackendError.vue'
import StatusBadge from '@/components/shared/StatusBadge.vue'
import LangBadge from '@/components/shared/LangBadge.vue'
import type { DashboardResponse } from '@/types/api'

const router = useRouter()
const manualsStore = useManualsStore()
const stats = ref<DashboardResponse | null>(null)
const loading = ref(false)
const error = ref('')

onMounted(async () => {
  loading.value = true
  try {
    stats.value = await getDashboard()
    await manualsStore.fetchManuals()
  } catch (err) {
    error.value = getApiError(err)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <section class="dashboard">
    <div class="header-row">
      <div>
        <h1 class="page-title">Dashboard</h1>
        <p class="text-muted">Estado general del sistema de manuales conectado al backend.</p>
      </div>
      <div class="actions">
        <button class="btn btn-outline" @click="router.push({ name: 'manuals' })"><BookOpen :size="15" /> Ver manuales</button>
        <button class="btn btn-primary" @click="router.push({ name: 'import' })"><Upload :size="15" /> Importar manual</button>
      </div>
    </div>

    <BackendError :message="error || manualsStore.error" />

    <div class="stats-grid">
      <article class="stat-card"><Package /><span>Productos</span><strong>{{ stats?.products ?? '-' }}</strong></article>
      <article class="stat-card"><FileText /><span>Manuales</span><strong>{{ stats?.manuals ?? '-' }}</strong></article>
      <article class="stat-card"><CheckCircle /><span>Publicados</span><strong>{{ stats?.publishedManuals ?? '-' }}</strong></article>
      <article class="stat-card"><Clock /><span>Borradores</span><strong>{{ stats?.draftManuals ?? '-' }}</strong></article>
      <article class="stat-card"><Languages /><span>EN pendiente</span><strong>{{ stats?.manualsWithEnglishPending ?? '-' }}</strong></article>
    </div>

    <div class="panel card">
      <div class="panel-head">
        <h2>Últimos manuales</h2>
        <button class="btn btn-outline" @click="router.push({ name: 'manuals' })">Abrir listado</button>
      </div>
      <table class="table">
        <thead>
          <tr><th>Código</th><th>Producto</th><th>Título</th><th>Estado</th><th>Idiomas</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-if="manualsStore.loading"><td colspan="6">Cargando datos desde backend...</td></tr>
          <tr v-for="manual in manualsStore.manuals.slice(0, 6)" :key="manual.id">
            <td class="mono">{{ manual.code }}</td>
            <td>{{ manual.productCode }}</td>
            <td>{{ manual.title }}</td>
            <td><StatusBadge :status="manual.activeStatus" /></td>
            <td><div class="langs"><LangBadge label="ES" :ready="manual.esReady" /><LangBadge label="EN" :ready="manual.enReady" /></div></td>
            <td><button class="btn btn-outline" @click="router.push({ name: 'manual-detail', params: { id: manual.id } })">Ver</button></td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.dashboard { padding: 24px; display: grid; gap: 20px; }
.header-row, .panel-head { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.actions, .langs { display: flex; gap: 8px; flex-wrap: wrap; }
.stats-grid { display: grid; grid-template-columns: repeat(5, minmax(150px, 1fr)); gap: 14px; }
.stat-card { background: #fff; border: 1px solid var(--border); border-radius: var(--radius); padding: 16px; display: grid; gap: 8px; }
.stat-card svg { color: var(--dikoin-blue); }
.stat-card span { color: var(--muted-foreground); font-size: 12px; }
.stat-card strong { font-size: 28px; }
.panel { overflow: hidden; }
.panel-head { padding: 16px; border-bottom: 1px solid var(--border); }
.panel-head h2 { margin: 0; font-size: 16px; }
@media (max-width: 1000px) { .stats-grid { grid-template-columns: repeat(2, 1fr); } }
</style>
