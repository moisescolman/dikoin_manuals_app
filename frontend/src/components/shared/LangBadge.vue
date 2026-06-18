<script setup lang="ts">
defineProps<{ label: string; ready?: boolean }>()
</script>

<template>
  <span class="badge" :class="ready ? 'ready' : 'pending'">{{ label }} {{ ready ? 'listo' : 'pendiente' }}</span>
</template>

<style scoped>
.ready { background: var(--dikoin-green-light); color: #065f46; }
.pending { background: var(--dikoin-orange-light); color: #92400e; }
</style>
