<script setup lang="ts">
import { Plus, Search, UserCircle } from '@lucide/vue'
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getManuals } from '@/api/manuals.api'
import { useAuthStore } from '@/stores/auth.store'
import type { ManualSummaryResponse } from '@/types/api'

const router = useRouter()
const auth = useAuthStore()
const query = ref('')
const suggestions = ref<ManualSummaryResponse[]>([])
const focused = ref(false)
let timer: number | undefined

watch(query, (value) => {
  window.clearTimeout(timer)
  if (!value.trim()) {
    suggestions.value = []
    return
  }
  timer = window.setTimeout(async () => {
    suggestions.value = (await getManuals(value)).slice(0, 6)
  }, 220)
})

function openManual(id: number) {
  focused.value = false
  query.value = ''
  suggestions.value = []
  router.push({ name: 'manual-detail', params: { id } })
}

function submitSearch() {
  router.push({ name: 'manuals', query: { search: query.value } })
}

function closeSuggestionsLater() {
  window.setTimeout(() => { focused.value = false }, 150)
}
</script>

<template>
  <header class="topbar">
    <div class="search">
      <Search :size="15" />
      <input
        v-model="query"
        placeholder="Buscar manual, producto o código..."
        @focus="focused = true"
        @blur="closeSuggestionsLater"
        @keydown.enter="submitSearch"
      />
      <div v-if="focused && query && suggestions.length" class="top-suggestions">
        <button v-for="manual in suggestions" :key="manual.id" @mousedown.prevent="openManual(manual.id)">
          <strong class="mono">{{ manual.code }}</strong>
          <span>{{ manual.title }}</span>
        </button>
      </div>
    </div>
    <button class="btn btn-primary" @click="router.push({ name: 'import' })"><Plus :size="15" /> Importar</button>
    <button class="user-btn" @click="router.push({ name: 'config' })">
      <UserCircle :size="18" />
      {{ auth.user?.email }}
    </button>
  </header>
</template>

<style scoped>
.topbar {
  height: 54px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 18px;
  border-bottom: 1px solid var(--border);
  background: #fff;
}
.search {
  position: relative;
  flex: 1;
  max-width: 520px;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--input-background);
  padding: 7px 10px;
  color: var(--muted-foreground);
}
.top-suggestions {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  background: #fff;
  border: 1px solid var(--border);
  box-shadow: 0 10px 24px rgba(0,0,0,.12);
  z-index: 40;
}
.top-suggestions button {
  width: 100%;
  border: 0;
  background: #fff;
  padding: 9px 10px;
  display: grid;
  gap: 2px;
  text-align: left;
}
.top-suggestions button:hover { background: var(--dikoin-blue-lighter); }
.top-suggestions span { color: var(--muted-foreground); font-size: 12px; }
.search input {
  width: 100%;
  border: 0;
  background: transparent;
  outline: 0;
}
.user-btn {
  border: 0;
  background: transparent;
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--muted-foreground);
  font-size: 12px;
}
</style>
