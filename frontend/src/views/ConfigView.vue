<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth.store'

const auth = useAuthStore()
const apiUrl = computed(() => import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1')
</script>

<template>
  <section class="config-page">
    <div>
      <h1 class="page-title">Configuración</h1>
      <p class="text-muted">Parámetros de conexión y estado de sesión del frontend.</p>
    </div>

    <div class="card config-card">
      <h2>Backend</h2>
      <dl>
        <dt>API Base URL</dt><dd class="mono">{{ apiUrl }}</dd>
        <dt>Autenticación</dt><dd>Token devuelto por backend y enviado en cabecera Bearer</dd>
      </dl>
    </div>

    <div class="card config-card">
      <h2>Usuario actual</h2>
      <dl>
        <dt>Nombre</dt><dd>{{ auth.user?.fullName }}</dd>
        <dt>Email</dt><dd>{{ auth.user?.email }}</dd>
        <dt>Rol</dt><dd>{{ auth.user?.role }}</dd>
      </dl>
    </div>
  </section>
</template>

<style scoped>
.config-page { padding: 24px; display: grid; gap: 16px; }
.config-card { padding: 16px; }
.config-card h2 { margin: 0 0 12px; font-size: 16px; }
dl { display: grid; grid-template-columns: 160px minmax(0, 1fr); gap: 10px; }
dt { color: var(--muted-foreground); }
dd { margin: 0; }
</style>
