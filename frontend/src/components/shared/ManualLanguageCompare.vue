<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import ManualRenderer from '@/components/shared/ManualRenderer.vue'
import type { ManualDetailResponse } from '@/types/api'

defineProps<{ manual: ManualDetailResponse }>()

const esPane = ref<HTMLElement | null>(null)
const enPane = ref<HTMLElement | null>(null)
const pageCounts = reactive({ ES: 0, EN: 0 })
const maxPages = computed(() => Math.max(pageCounts.ES, pageCounts.EN))
let syncing = false

function syncScroll(source: HTMLElement | null, target: HTMLElement | null) {
  if (!source || !target || syncing) return
  syncing = true
  target.scrollTop = source.scrollTop
  target.scrollLeft = source.scrollLeft
  window.requestAnimationFrame(() => {
    syncing = false
  })
}
</script>

<template>
  <section class="compare-wrap">
    <div class="compare-head">
      <strong>Español</strong>
      <span>{{ pageCounts.ES || '-' }} paginas</span>
      <strong>Ingles</strong>
      <span>{{ pageCounts.EN || '-' }} paginas</span>
    </div>
    <div class="compare-grid">
      <div ref="esPane" class="compare-pane" @scroll="syncScroll(esPane, enPane)">
        <ManualRenderer :manual="manual" language="ES" :min-pages="maxPages" @page-count="pageCounts.ES = $event" />
      </div>
      <div ref="enPane" class="compare-pane" @scroll="syncScroll(enPane, esPane)">
        <ManualRenderer :manual="manual" language="EN" :min-pages="maxPages" @page-count="pageCounts.EN = $event" />
      </div>
    </div>
  </section>
</template>

<style scoped>
.compare-wrap {
  display: grid;
  gap: 10px;
}

.compare-head {
  display: grid;
  grid-template-columns: 1fr auto 1fr auto;
  gap: 10px;
  align-items: center;
  color: var(--muted-foreground);
  font-size: 12px;
}

.compare-head strong {
  color: var(--foreground);
  font-size: 13px;
}

.compare-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
  height: calc(100vh - 220px);
  min-height: 680px;
}

.compare-pane {
  overflow: auto;
  border: 1px solid var(--border);
  background: #eef2f6;
  padding: 12px;
}

.compare-pane :deep(.manual-pages) {
  gap: 14px;
}

.compare-pane :deep(.manual-page) {
  width: 210mm;
  min-width: 210mm;
}

@media (max-width: 1100px) {
  .compare-grid {
    grid-template-columns: 1fr;
    height: auto;
  }

  .compare-pane {
    max-height: 75vh;
  }
}
</style>
