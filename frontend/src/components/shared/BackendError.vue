<script setup lang="ts">
defineProps<{ message?: string; title?: string }>()
</script>

<template>
  <div v-if="message" class="backend-error">
    <strong>{{ title || 'No se pudo conectar correctamente' }}</strong>
    <span>{{ message }}</span>
  </div>
</template>

<style scoped>
.backend-error {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  border: 1px solid #fecaca;
  background: #fef2f2;
  color: #991b1b;
  border-radius: var(--radius);
}
</style>
