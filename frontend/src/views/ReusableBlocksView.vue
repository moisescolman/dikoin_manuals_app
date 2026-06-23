<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Edit, Plus, Save } from '@lucide/vue'
import { createReusableBlock, getReusableBlockUsages, getReusableBlocks, updateReusableBlock } from '@/api/reusable-blocks.api'
import BackendError from '@/components/shared/BackendError.vue'
import SectionCard from '@/components/editor/SectionCard.vue'
import type { EditorSection } from '@/types/editor'
import { randomId, sectionsFromBackend, versionRequestFromEditor } from '@/types/editor'
import type { ReusableBlockResponse, ReusableBlockUsageResponse } from '@/types/api'

const blocks = ref<ReusableBlockResponse[]>([])
const usages = ref<ReusableBlockUsageResponse[]>([])
const selectedId = ref<number | null>(null)
const selectedBlockId = ref('')
const editing = ref(false)
const loading = ref(false)
const error = ref('')
const saved = ref('')
const form = reactive({
  code: '',
  title: '',
  productCategory: '',
  productCodes: '',
  active: true,
})
const section = ref<EditorSection>(emptySection())

const selected = computed(() => blocks.value.find((block) => block.id === selectedId.value))

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    blocks.value = await getReusableBlocks(true)
    if (!selectedId.value && blocks.value.length) await select(blocks.value[0])
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'No se pudieron cargar los bloques'
  } finally {
    loading.value = false
  }
}

async function select(block: ReusableBlockResponse) {
  selectedId.value = block.id
  editing.value = false
  fillForm(block)
  await loadUsages(block.id)
}

async function loadUsages(id: number) {
  usages.value = await getReusableBlockUsages(id)
}

function fillForm(block: ReusableBlockResponse) {
  form.code = block.code
  form.title = block.title
  form.productCategory = block.productCategory || ''
  form.productCodes = block.productCodes || ''
  form.active = block.active
  section.value = sectionFromContent(block.contentJson)
}

function createNew() {
  selectedId.value = null
  editing.value = true
  usages.value = []
  form.code = `BLQ-${String(blocks.value.length + 1).padStart(3, '0')}`
  form.title = 'Nuevo bloque común'
  form.productCategory = ''
  form.productCodes = ''
  form.active = true
  section.value = emptySection()
}

function emptySection(): EditorSection {
  return {
    id: randomId('section'),
    sortOrder: 1,
    sectionNumber: '1',
    titleEs: 'Contenido',
    status: 'READY',
    collapsed: false,
    blocks: [{ id: randomId('block'), type: 'parrafo', content: 'Contenido reutilizable.', languageCode: 'ES' }],
  }
}

function sectionFromContent(contentJson: string): EditorSection {
  try {
    const parsed = JSON.parse(contentJson)
    if (Array.isArray(parsed.blocks)) {
      return sectionsFromBackend([{
        id: 0,
        sortOrder: 1,
        sectionNumber: '1',
        titleEs: 'Contenido',
        completionStatus: 'READY',
        blocks: parsed.blocks.map((block: any, index: number) => ({
          id: index,
          sortOrder: block.sortOrder ?? index + 1,
          blockType: block.blockType,
          languageCode: block.languageCode,
          contentJson: block.contentJson,
        })),
      }])[0]
    }
  } catch {
    return emptySection()
  }
  return emptySection()
}

function contentFromSection() {
  const request = versionRequestFromEditor({
    versionNumber: '1',
    status: 'DRAFT',
    active: true,
    esReady: true,
    enReady: false,
    sections: [section.value],
  })
  return JSON.stringify({ blocks: request.sections[0].blocks })
}

async function save() {
  loading.value = true
  error.value = ''
  saved.value = ''
  try {
    const payload = {
      ...form,
      contentJson: contentFromSection(),
    }
    const result = selectedId.value
      ? await updateReusableBlock(selectedId.value, payload)
      : await createReusableBlock(payload)
    await load()
    await select(result)
    saved.value = 'Bloque guardado. Los manuales enlazados usarán esta versión.'
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'No se pudo guardar el bloque'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="blocks-page">
    <div class="head">
      <div>
        <h1 class="page-title">Bloques</h1>
        <p class="text-muted">Biblioteca de bloques comunes reutilizables en manuales.</p>
      </div>
      <button class="btn btn-primary" @click="createNew"><Plus :size="15" /> Nuevo bloque</button>
    </div>

    <BackendError :message="error" />
    <div v-if="saved" class="success-msg">{{ saved }}</div>

    <div class="blocks-grid">
      <aside class="card list">
        <button v-for="block in blocks" :key="block.id" :class="{ active: block.id === selectedId }" @click="select(block)">
          <span class="mono">{{ block.code }}</span>
          <strong>{{ block.title }}</strong>
          <small>{{ block.productCategory || 'Sin categoría' }}</small>
        </button>
      </aside>

      <main class="workspace">
        <form class="card meta" @submit.prevent="save">
          <div class="meta-head">
            <strong>{{ selected?.title || 'Nuevo bloque' }}</strong>
            <button type="button" class="btn btn-outline" @click="editing = !editing"><Edit :size="14" /> {{ editing ? 'Ver' : 'Editar' }}</button>
          </div>
          <div class="form-row">
            <label>Código <input v-model="form.code" class="field mono" :readonly="!editing" required /></label>
            <label>Título <input v-model="form.title" class="field" :readonly="!editing" required /></label>
          </div>
          <div class="form-row">
            <label>Categoría producto <input v-model="form.productCategory" class="field" :readonly="!editing" /></label>
            <label>Códigos producto <input v-model="form.productCodes" class="field mono" :readonly="!editing" placeholder="FLB10.1, HY100" /></label>
          </div>
          <label class="check"><input v-model="form.active" type="checkbox" :disabled="!editing" /> Activo</label>
          <button v-if="editing" class="btn btn-primary" :disabled="loading"><Save :size="14" /> Guardar bloque</button>
        </form>

        <div class="editor-shell">
          <SectionCard
            :section="section"
            :selected-block-id="selectedBlockId"
            language="ES"
            @update="section = $event"
            @delete="section = emptySection()"
            @select-block="selectedBlockId = $event"
          />
        </div>

        <div class="card usages">
          <h2>Manuales que usan este bloque</h2>
          <table class="table">
            <thead><tr><th>Manual</th><th>Producto</th><th>Sección</th></tr></thead>
            <tbody>
              <tr v-if="!usages.length"><td colspan="3">Este bloque todavía no está enlazado en manuales.</td></tr>
              <tr v-for="usage in usages" :key="`${usage.manualId}-${usage.blockId}`">
                <td><span class="mono">{{ usage.manualCode }}</span> {{ usage.manualTitle }}</td>
                <td>{{ usage.productCode }}</td>
                <td>{{ usage.sectionNumber }} {{ usage.sectionTitle }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </main>
    </div>
  </section>
</template>

<style scoped>
.blocks-page { padding: 24px; display: grid; gap: 16px; }
.head, .meta-head { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.blocks-grid { display: grid; grid-template-columns: 340px minmax(0, 1fr); gap: 16px; }
.list { padding: 8px; display: grid; gap: 6px; align-content: start; }
.list button { border: 1px solid transparent; background: #fff; padding: 10px; text-align: left; display: grid; gap: 3px; border-radius: var(--radius); }
.list button.active, .list button:hover { border-color: var(--dikoin-blue); background: var(--dikoin-blue-lighter); }
.list small { color: var(--muted-foreground); }
.workspace { display: grid; gap: 16px; min-width: 0; }
.meta, .usages { padding: 16px; display: grid; gap: 12px; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
label { display: grid; gap: 6px; font-size: 12px; font-weight: 600; color: var(--muted-foreground); }
.check { display: flex; align-items: center; gap: 8px; }
.editor-shell { padding-left: 30px; }
.success-msg { background: var(--dikoin-green-light); color: #065f46; border: 1px solid #86efac; padding: 10px; border-radius: var(--radius); }
.usages h2 { margin: 0; font-size: 16px; }
@media (max-width: 1020px) { .blocks-grid, .form-row { grid-template-columns: 1fr; } }
</style>
