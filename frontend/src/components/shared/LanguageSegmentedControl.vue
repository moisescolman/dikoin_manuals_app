<script setup lang="ts">
import type { LanguageCode } from '@/types/api'

defineProps<{
  modelValue: LanguageCode
  ariaLabel?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: LanguageCode]
}>()
</script>

<template>
  <div class="lang-switch">
    <button :class="{ active: modelValue === 'ES' }" @click="emit('update:modelValue', 'ES')">ES</button>
    <button :class="{ active: modelValue === 'EN' }" @click="emit('update:modelValue', 'EN')">EN</button>
  </div>
</template>

<style scoped>
.language-group {
  display: inline-grid;
  grid-template-columns: repeat(2, minmax(44px, 1fr));
  padding: 3px;
  border: 1px solid var(--border);
  border-radius: 9px;
  background: var(--input-background);
}

.language-group button {
  min-height: 30px;
  padding: 5px 14px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--muted-foreground);
  font: inherit;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.language-group button.active {
  background: var(--dikoin-blue);
  color: #fff;
  box-shadow: 0 1px 3px rgba(15, 23, 42, .18);
}

.lang-switch {
  display: inline-flex;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
  background: #fff;
}

.lang-switch button {
  border: 0;
  background: #fff;
  padding: 8px 11px;
  color: var(--muted-foreground);
  font-weight: 600;
}

.lang-switch button.active {
  background: var(--dikoin-blue);
  color: #fff;
}
</style>
