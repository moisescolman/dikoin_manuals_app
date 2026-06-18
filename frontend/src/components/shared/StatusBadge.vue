<script setup lang="ts">
import type { ManualStatus } from '@/types/api'
import { statusClass, statusLabel } from '@/utils/formatters'

defineProps<{ status?: ManualStatus }>()
</script>

<template>
  <span class="badge" :class="statusClass(status)">{{ statusLabel(status) }}</span>
</template>

<style scoped>
.status-draft { background: #f3f4f6; color: #374151; }
.status-review { background: var(--dikoin-orange-light); color: #92400e; }
.status-approved { background: #e0f2fe; color: #075985; }
.status-published { background: var(--dikoin-green-light); color: #065f46; }
.status-archived { background: #e5e7eb; color: #4b5563; }
</style>
