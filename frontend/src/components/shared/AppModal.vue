<script setup lang="ts">
import { X } from '@lucide/vue'

defineProps<{
  title: string
  description?: string
  size?: 'sm' | 'md' | 'lg'
}>()

const emit = defineEmits<{
  close: []
}>()
</script>

<template>
  <Teleport to="body">
    <div class="app-modal-backdrop" @click.self="emit('close')">
      <section class="app-modal-card" :class="size || 'md'" role="dialog" aria-modal="true" :aria-label="title">
        <header class="app-modal-header">
          <div>
            <h2>{{ title }}</h2>
            <p v-if="description">{{ description }}</p>
          </div>
          <button type="button" class="app-modal-close" title="Cerrar" @click="emit('close')">
            <X :size="17" />
          </button>
        </header>
        <div class="app-modal-body">
          <slot />
        </div>
        <footer v-if="$slots.footer" class="app-modal-footer">
          <slot name="footer" />
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.app-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, .42);
}

.app-modal-card {
  width: min(100%, 560px);
  max-height: min(86vh, 780px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: #fff;
  box-shadow: 0 24px 70px rgba(15, 23, 42, .24);
}

.app-modal-card.sm { width: min(100%, 420px); }
.app-modal-card.lg { width: min(100%, 820px); }

.app-modal-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--border);
}

.app-modal-header h2 {
  margin: 0;
  font-size: 17px;
}

.app-modal-header p {
  margin: 4px 0 0;
  color: var(--muted-foreground);
  font-size: 12px;
}

.app-modal-close {
  width: 30px;
  height: 30px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: #fff;
  color: var(--muted-foreground);
  display: grid;
  place-items: center;
  padding: 0;
}

.app-modal-body {
  min-height: 0;
  overflow: auto;
  padding: 16px 18px;
}

.app-modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 18px 16px;
  border-top: 1px solid var(--border);
}
</style>
