<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Eye, Search } from '@lucide/vue'
import BackendError from '@/components/shared/BackendError.vue'
import LangBadge from '@/components/shared/LangBadge.vue'
import StatusBadge from '@/components/shared/StatusBadge.vue'
import { useManualsStore } from '@/stores/manuals.store'

const router = useRouter()
const store = useManualsStore()
const search = ref('')

const visibleManuals = computed(() => store.manuals.filter((manual) => manual.enabled !== false && manual.activeStatus === 'PUBLISHED'))

onMounted(() => store.fetchManuals())
</script>

<template>
  <section class="portal-page">
    <div>
      <h1 class="page-title">Portal de cliente</h1>
      <p class="text-muted">Vista solo lectura. En producción se filtrará por productos comprados por el cliente.</p>
    </div>

    <BackendError :message="store.error" />

    <div class="filters card">
      <Search :size="14" />
      <input
        v-model="search"
        aria-label="Buscar manual publicado"
        placeholder="Buscar manual publicado..."
        @keydown.enter="store.fetchManuals(search)"
      />
      <button class="btn btn-outline" @click="store.fetchManuals(search)">Buscar</button>
    </div>

    <div class="manual-grid">
      <article v-for="manual in visibleManuals" :key="manual.id" class="card manual-card">
        <div class="code mono">{{ manual.code }}</div>
        <h2>{{ manual.title }}</h2>
        <p>{{ manual.productCode }} - {{ manual.productName }}</p>
        <div class="meta">
          <StatusBadge :status="manual.activeStatus" />
          <LangBadge label="ES" :ready="manual.esReady" />
          <LangBadge label="EN" :ready="manual.enReady" />
        </div>
        <button class="btn btn-primary" @click="router.push({ name: 'manual-detail', params: { id: manual.id } })">
          <Eye :size="14" /> Abrir manual
        </button>
      </article>
      <div v-if="!store.loading && !visibleManuals.length" class="card empty">No hay manuales publicados disponibles.</div>
    </div>
  </section>
</template>

<style scoped>
.portal-page { padding: 24px; display: grid; gap: 16px; }
.filters { display: flex; gap: 10px; align-items: center; padding: 12px; }
.filters input { border: 0; outline: 0; background: transparent; flex: 1; }
.manual-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 14px; }
.manual-card { padding: 16px; display: grid; gap: 10px; }
.manual-card h2 { margin: 0; font-size: 17px; color: var(--dikoin-blue-dark); }
.manual-card p { margin: 0; color: var(--muted-foreground); }
.code { color: var(--dikoin-blue); font-weight: 600; }
.meta { display: flex; gap: 6px; flex-wrap: wrap; }
.empty { padding: 18px; color: var(--muted-foreground); }
</style>
