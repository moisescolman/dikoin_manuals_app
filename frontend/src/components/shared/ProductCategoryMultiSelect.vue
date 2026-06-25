<script setup lang="ts">
import { computed } from 'vue'
import { ChevronDown } from '@lucide/vue'
import type { ProductCategoryResponse } from '@/types/api'

const props = defineProps<{
  modelValue: string[]
  categories: ProductCategoryResponse[]
  label?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string[]]
}>()

const selected = computed(() => new Set(props.modelValue))
const summary = computed(() => {
  if (!props.modelValue.length) return 'Seleccionar categorías'
  if (props.modelValue.length <= 3) return props.modelValue.join(', ')
  return `${props.modelValue.length} categorías seleccionadas`
})

function toggle(code: string, checked: boolean) {
  const next = new Set(props.modelValue)
  if (checked) next.add(code)
  else next.delete(code)
  emit('update:modelValue', [...next])
}
</script>

<template>
  <div class="picker-label">
    <span class="picker-title">{{ label || 'Categorías de producto' }}</span>
    <details class="category-select">
      <summary>
        <span :class="{ placeholder: !modelValue.length }">{{ summary }}</span>
        <ChevronDown :size="15" />
      </summary>
      <div class="category-options">
        <label v-for="category in categories" :key="category.id" class="category-option">
          <input
            type="checkbox"
            :checked="selected.has(category.code)"
            @change="toggle(category.code, ($event.target as HTMLInputElement).checked)"
          />
          <strong>{{ category.code }}</strong>
          <span>{{ category.nameEs }}</span>
        </label>
        <p v-if="!categories.length">No hay categorías disponibles.</p>
      </div>
    </details>
  </div>
</template>

<style scoped>
.picker-label {
  display: grid;
  gap: 6px;
  color: var(--muted-foreground);
  font-size: 12px;
  font-weight: 600;
}

.picker-title {
  color: var(--muted-foreground);
}

.category-select {
  position: relative;
}

.category-select summary {
  min-height: 38px;
  padding: 8px 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--input-background);
  color: var(--foreground);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  list-style: none;
  cursor: pointer;
}

.category-select summary::-webkit-details-marker {
  display: none;
}

.category-select[open] summary {
  border-color: var(--dikoin-blue);
  box-shadow: 0 0 0 2px rgba(14, 127, 187, .1);
}

.category-select[open] summary svg {
  transform: rotate(180deg);
}

.placeholder {
  color: var(--muted-foreground);
  font-weight: 400;
}

.category-options {
  position: absolute;
  z-index: 20;
  top: calc(100% + 5px);
  left: 0;
  right: 0;
  max-height: 260px;
  padding: 7px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: #fff;
  box-shadow: 0 12px 28px rgba(15, 23, 42, .14);
  overflow: auto;
}

.category-option {
  padding: 8px;
  border-radius: 6px;
  display: grid;
  grid-template-columns: auto 34px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  color: var(--foreground);
  cursor: pointer;
}

.category-option:hover {
  background: var(--dikoin-blue-lighter);
}

.category-option input {
  width: 15px;
  height: 15px;
}

.category-option strong {
  color: var(--dikoin-blue);
}

.category-option span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 400;
}

.category-options p {
  margin: 8px;
  color: var(--muted-foreground);
  font-weight: 400;
}
</style>
