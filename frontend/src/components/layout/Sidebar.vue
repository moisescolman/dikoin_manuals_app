<script setup lang="ts">
import {
  Archive,
  BookOpen,
  Boxes,
  Database,
  FileClock,
  FileText,
  Home,
  LayoutTemplate,
  LogOut,
  Package,
  Settings,
  StickyNote,
  Upload,
} from '@lucide/vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth.store'
import { roleLabel } from '@/utils/formatters'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const nav = [
  { name: 'dashboard', label: 'Dashboard', icon: Home },
  { name: 'manuals', label: 'Manuales', icon: BookOpen },
  { name: 'import', label: 'Importar manual', icon: Upload },
  { name: 'products', label: 'Productos', icon: Package },
  { name: 'notes', label: 'Notas', icon: StickyNote },
  { name: 'reusable-blocks', label: 'Bloques', icon: Boxes },
  { name: 'templates', label: 'Plantillas', icon: LayoutTemplate },
  { name: 'assets', label: 'Assets', icon: Archive },
  { name: 'history', label: 'Historial', icon: FileClock },
  { name: 'portal', label: 'Portal cliente', icon: FileText },
  { name: 'config', label: 'Configuración', icon: Settings },
]

function logout() {
  auth.logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <aside class="sidebar">
    <div class="brand">
      <div class="brand-mark">DK</div>
      <div>
        <strong>DIKOIN</strong>
        <span>Manuales digitales</span>
      </div>
    </div>

    <nav class="nav">
      <button
        v-for="item in nav"
        :key="item.name"
        class="nav-item"
        :class="{ active: route.name === item.name }"
        @click="router.push({ name: item.name })"
      >
        <component :is="item.icon" :size="16" />
        {{ item.label }}
      </button>
    </nav>

    <div class="sidebar-footer">
      <!-- <div class="db-pill"><Database :size="13" /> Backend real</div> -->
      <div class="user-card">
        <div class="avatar">{{ auth.user?.fullName?.slice(0, 2).toUpperCase() }}</div>
        <div>
          <strong>{{ auth.user?.fullName }}</strong>
          <span>{{ roleLabel(auth.user?.role) }}</span>
        </div>
      </div>
      <button class="logout" @click="logout"><LogOut :size="14" /> Cerrar sesión</button>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 250px;
  background: var(--dikoin-blue-dark);
  color: #e8f4fb;
  display: flex;
  flex-direction: column;
  min-height: 100%;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px;
  border-bottom: 1px solid rgba(255,255,255,.12);
}
.brand-mark {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  background: var(--dikoin-blue);
  color: #fff;
  border-radius: 4px;
  font-weight: 800;
}
.brand strong, .brand span { display: block; }
.brand span { font-size: 12px; color: #c7e4f6; }
.nav { padding: 12px; display: grid; gap: 4px; }
.nav-item {
  width: 100%;
  border: 0;
  background: transparent;
  color: #d7edf8;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 10px;
  border-radius: var(--radius);
  text-align: left;
}
.nav-item:hover, .nav-item.active { background: var(--dikoin-blue); color: #fff; }
.sidebar-footer { margin-top: auto; padding: 12px; display: grid; gap: 10px; }
.db-pill {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  width: fit-content;
  font-size: 11px;
  padding: 4px 7px;
  border-radius: 999px;
  background: rgba(255,255,255,.12);
}
.user-card {
  display: flex;
  gap: 9px;
  align-items: center;
  padding: 8px;
  border-radius: var(--radius);
  background: rgba(255,255,255,.08);
}
.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #fff;
  color: var(--dikoin-blue-dark);
  display: grid;
  place-items: center;
  font-weight: 800;
  font-size: 12px;
}
.user-card strong, .user-card span { display: block; }
.user-card strong { font-size: 12px; }
.user-card span { font-size: 11px; color: #c7e4f6; }
.logout {
  display: flex;
  align-items: center;
  gap: 7px;
  border: 0;
  background: transparent;
  color: #c7e4f6;
  padding: 6px;
}
</style>
