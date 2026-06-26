<script setup lang="ts">
import {
  Archive,
  BookOpen,
  SquareDashedText,
  ChevronLeft,
  ChevronRight,
  FileClock,
  FileText,
  Home,
  Group,
  LayoutTemplate,
  LogOut,
  Package,
  Settings,
  StickyNote,
  Trash2,
  Upload,
} from '@lucide/vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth.store'
import { roleLabel } from '@/utils/formatters'
import brandLogo from '@/assets/logos/dk_manuals_logo.svg'

defineProps<{
  collapsed: boolean
}>()

const emit = defineEmits<{
  toggle: []
}>()

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const nav = [
  { name: 'dashboard', label: 'Dashboard', icon: Home },
  { name: 'manuals', label: 'Manuales', icon: BookOpen },
  { name: 'import', label: 'Importar manual', icon: Upload },
  { name: 'products', label: 'Productos', icon: Package },
  { name: 'notes', label: 'Notas', icon: StickyNote },
  { name: 'reusable-blocks', label: 'Secciones', icon: SquareDashedText },
  { name: 'reusable-fragments', label: 'Fragmentos', icon: Group },
  { name: 'templates', label: 'Plantillas', icon: LayoutTemplate },
  { name: 'assets', label: 'Assets', icon: Archive },
  { name: 'trash', label: 'Papelera', icon: Trash2 },
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
  <div class="sidebar-shell" :class="{ collapsed }">
  <aside class="sidebar" :aria-hidden="collapsed">
    <div class="brand">
      <img :src="brandLogo" alt="DK Manuals" class="brand-logo" />
    </div>

    <nav class="nav">
      <button
        v-for="item in nav"
        :key="item.name"
        class="nav-item"
        :class="{ active: route.name === item.name }"
        :tabindex="collapsed ? -1 : 0"
        @click="router.push({ name: item.name })"
      >
        <component :is="item.icon" :size="16" />
        {{ item.label }}
      </button>
    </nav>

    <div class="sidebar-footer">
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

  <button
    type="button"
    class="sidebar-toggle"
    :aria-label="collapsed ? 'Mostrar sidebar' : 'Ocultar sidebar'"
    :title="collapsed ? 'Mostrar sidebar' : 'Ocultar sidebar'"
    @click="emit('toggle')"
  >
    <ChevronRight v-if="collapsed" :size="16" />
    <ChevronLeft v-else :size="16" />
  </button>
  </div>
</template>

<style scoped>
.sidebar-shell {
  position: relative;
  flex: 0 0 200px;
  width: 200px;
  min-height: 100%;
  transition: width .18s ease, flex-basis .18s ease;
}

.sidebar-shell.collapsed {
  flex-basis: 0;
  width: 0;
}

.sidebar {
  position: relative;
  z-index: 10;
  width: 200px;
  background: var(--dikoin-blue-dark);
  color: #e8f4fb;
  display: flex;
  flex-direction: column;
  min-height: 100%;
  height: 100%;
  overflow: hidden;
  transition: transform .18s ease, opacity .18s ease, visibility .18s ease;
}

.sidebar-shell.collapsed .sidebar {
  opacity: 0;
  pointer-events: none;
  transform: translateX(-100%);
  visibility: hidden;
}

.sidebar-toggle {
  position: absolute;
  top: 50%;
  right: -20px;
  z-index: 9;
  width: 28px;
  height: 44px;
  transform: translateY(-50%);
  border: 1px solid rgba(255,255,255,.2);
  border-left: 0;
  border-radius: 0 var(--radius) var(--radius) 0;
  background: var(--dikoin-blue-dark);
  color: #fff;
  display: grid;
  place-items: center;
  padding: 0;
  box-shadow: 0 8px 18px rgba(15, 23, 42, .18);
}

.sidebar-toggle:hover {
  background: var(--dikoin-blue);
}

.sidebar-shell.collapsed .sidebar-toggle {
  right: -28px;
}

.brand {
  display: flex;
  justify-content: center;
  padding: 18px;
  border-bottom: 1px solid rgba(255,255,255,.12);
}
.brand-logo {
  display: block;
  width: 100%;
  max-width: 150px;
  height: auto;
}
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
  font-weight: 600;
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
