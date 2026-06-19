<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import ManualRenderer from '@/components/shared/ManualRenderer.vue'
import { getManual } from '@/api/manuals.api'
import type { LanguageCode, ManualDetailResponse } from '@/types/api'

const props = defineProps<{ id: string }>()
const route = useRoute()
const manual = ref<ManualDetailResponse | null>(null)
const error = ref('')
const language = computed<LanguageCode>(() => route.query.lang === 'EN' ? 'EN' : 'ES')

onMounted(loadManual)
watch(() => props.id, loadManual)

async function loadManual() {
  error.value = ''
  try {
    manual.value = await getManual(Number(props.id))
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'No se pudo cargar el manual'
  }
}
</script>

<template>
  <main class="print-view">
    <ManualRenderer v-if="manual" :manual="manual" :language="language" />
    <p v-else-if="error" class="print-error">{{ error }}</p>
  </main>
</template>

<style scoped>
.print-view {
  min-height: 100%;
  padding: 0;
  background: #fff;
}

.print-error {
  margin: 24px;
  color: var(--dikoin-red);
}
</style>
