<script setup lang="ts">
import { computed, onMounted, ref, toRaw } from 'vue'
import { ArrowLeft, Copy, Languages, Save } from '@lucide/vue'
import { useRouter } from 'vue-router'
import { getReusableBlock, updateReusableBlock } from '@/api/reusable-blocks.api'
import { getReusableFragment, updateReusableFragment } from '@/api/reusable-fragments.api'
import RichSectionEditor from '@/components/editor/RichSectionEditor.vue'
import AppModal from '@/components/shared/AppModal.vue'
import BackendError from '@/components/shared/BackendError.vue'
import type { EditorBlock, EditorSection } from '@/types/editor'
import { randomId, sectionsFromBackend, versionRequestFromEditor } from '@/types/editor'
import type { LanguageCode, ReusableBlockResponse, ReusableFragmentResponse } from '@/types/api'

const props = defineProps<{ id: string; kind: 'SINGLE_BLOCK' | 'FRAGMENT' }>()
const router = useRouter()
type ReusableEditorItem = ReusableBlockResponse | ReusableFragmentResponse
type LanguageMode = LanguageCode | 'BOTH'
const item = ref<ReusableEditorItem>()
const section = ref<EditorSection>(emptySection())
const selectedLanguage = ref<LanguageCode>('ES')
const languageMode = ref<LanguageMode>('ES')
const saving = ref(false)
const saved = ref(false)
const error = ref('')
const infoMessage = ref('')
const cloneConfirmOpen = ref(false)
const activeEditorKey = ref('')
const editorContentVersion = ref(0)
const editorRefs = ref<Array<{ flushEditorSync: () => void }>>([])

const isSection = computed(() => props.kind === 'SINGLE_BLOCK')
const backRoute = computed(() => isSection.value ? 'reusable-blocks' : 'reusable-fragments')
const editorLanguages = computed<LanguageCode[]>(() => languageMode.value === 'BOTH' ? ['ES', 'EN'] : [languageMode.value])

onMounted(load)

async function load() {
  try {
    item.value = props.kind === 'FRAGMENT'
      ? { ...(await getReusableFragment(Number(props.id))), reusableType: 'FRAGMENT' as const }
      : await getReusableBlock(Number(props.id))
    if ('reusableType' in item.value && item.value.reusableType !== props.kind) {
      throw new Error('El contenido solicitado no pertenece a esta biblioteca.')
    }
    section.value = fromContent(item.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'No se pudo cargar el contenido reutilizable'
  }
}

function emptySection(): EditorSection {
  return {
    id: randomId('reusable'), sortOrder: 1, sectionNumber: '1', level: 1,
    titleEs: 'Contenido', titleEn: 'Content', status: 'DRAFT', visible: true, collapsed: false, blocks: [],
  }
}

function fromContent(value: ReusableEditorItem) {
  try {
    const parsed = JSON.parse(value.contentJson)
    const loaded = sectionsFromBackend([{
      id: 0,
      sortOrder: 1,
      sectionNumber: '1',
      level: 1,
      titleEs: 'titleEs' in value ? value.titleEs || value.title || parsed.titleEs : value.title || parsed.titleEs,
      titleEn: 'titleEn' in value ? value.titleEn || parsed.titleEn || value.titleEs || value.title : parsed.titleEn || value.title,
      completionStatus: 'READY',
      visible: true,
      blocks: (Array.isArray(parsed.blocks) ? parsed.blocks : []).map((block: any, index: number) => ({
        id: index,
        sortOrder: block.sortOrder ?? index + 1,
        blockType: block.blockType,
        languageCode: block.languageCode || 'ES',
        contentJson: typeof block.contentJson === 'string' ? block.contentJson : JSON.stringify(block.contentJson || {}),
        plainText: block.plainText,
        assetId: block.assetId,
        reusableBlockId: block.reusableBlockId,
      })),
    }])[0] || emptySection()
    const esCount = loaded.blocks.filter((block) => block.languageCode === 'ES').length
    const enCount = loaded.blocks.filter((block) => block.languageCode === 'EN').length
    return synchronizeBlocks(loaded, enCount > esCount ? 'EN' : 'ES')
  } catch {
    return emptySection()
  }
}

function updateForLanguage(value: EditorSection, language: LanguageCode) {
  const merged = {
    ...section.value,
    ...value,
    titleEs: language === 'ES' ? value.titleEs : section.value.titleEs,
    titleEn: language === 'EN' ? value.titleEn : section.value.titleEn,
    blocks: [
      ...section.value.blocks.filter((block) => block.languageCode !== language),
      ...value.blocks.filter((block) => block.languageCode === language),
    ],
  }
  section.value = languageMode.value === 'BOTH' ? merged : synchronizeBlocks(merged, language)
}

function synchronizeBlocks(value: EditorSection, sourceLanguage: LanguageCode): EditorSection {
  const targetLanguage: LanguageCode = sourceLanguage === 'ES' ? 'EN' : 'ES'
  const source = value.blocks.filter((block) => block.languageCode === sourceLanguage)
  const target = value.blocks.filter((block) => block.languageCode === targetLanguage)
  const mirrored = source.map((block, index) => {
    const existing = target[index]
    if (existing && existing.type === block.type) {
      return { ...existing, languageCode: targetLanguage }
    }
    return {
      ...structuredClone(block),
      id: randomId('block'),
      backendId: undefined,
      languageCode: targetLanguage,
    }
  })
  return {
    ...value,
    titleEn: value.titleEn || value.titleEs,
    blocks: sourceLanguage === 'ES' ? [...source, ...mirrored] : [...mirrored, ...source],
  }
}

async function save() {
  if (!item.value) return
  flushEditors()
  if (languageMode.value !== 'BOTH') {
    section.value = synchronizeBlocks(section.value, selectedLanguage.value)
  }
  saving.value = true
  error.value = ''
  try {
    const request = versionRequestFromEditor({
      versionNumber: '1', status: 'DRAFT', active: true, esReady: true, enReady: true, sections: [section.value],
    })
    const contentJson = JSON.stringify({
      type: props.kind === 'FRAGMENT' ? 'FRAGMENT' : 'SECTION',
      titleEs: section.value.titleEs,
      titleEn: section.value.titleEn,
      blocks: request.sections[0].blocks,
    })
    item.value = props.kind === 'FRAGMENT'
      ? { ...(await updateReusableFragment(item.value.id, {
        code: item.value.code,
        title: item.value.title,
        description: item.value.description,
        productCategory: item.value.productCategory,
        productCodes: item.value.productCodes,
        contentJson,
        active: item.value.active,
      })), reusableType: 'FRAGMENT' as const }
      : await updateReusableBlock(item.value.id, {
        code: item.value.code,
        title: ('titleEs' in item.value ? item.value.titleEs : undefined) || item.value.title,
        titleEs: 'titleEs' in item.value ? item.value.titleEs || item.value.title : item.value.title,
        titleEn: 'titleEn' in item.value ? item.value.titleEn : undefined,
        description: 'descriptionEs' in item.value ? item.value.descriptionEs || item.value.description : item.value.description,
        descriptionEs: 'descriptionEs' in item.value ? item.value.descriptionEs || item.value.description : item.value.description,
        descriptionEn: 'descriptionEn' in item.value ? item.value.descriptionEn : undefined,
        reusableType: props.kind,
        productCategory: item.value.productCategory,
        productCodes: item.value.productCodes,
        contentJson,
        active: item.value.active,
      })
    saved.value = true
    window.setTimeout(() => { saved.value = false }, 1800)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'No se pudo guardar'
  } finally {
    saving.value = false
  }
}

function changeLanguage(value: LanguageCode) {
  if (value === selectedLanguage.value && languageMode.value === value) return
  flushEditors()
  selectedLanguage.value = value
  languageMode.value = value
  activeEditorKey.value = ''
}

function showBothLanguages() {
  if (languageMode.value === 'BOTH') return
  flushEditors()
  languageMode.value = 'BOTH'
  activeEditorKey.value = ''
}

function editorKey(language: LanguageCode) {
  return `${section.value.id}:${language}`
}

function activateEditor(_sectionId: string, language: LanguageCode) {
  selectedLanguage.value = language
  activeEditorKey.value = editorKey(language)
}

function flushEditors() {
  editorRefs.value.forEach((editorRef) => editorRef?.flushEditorSync?.())
}

function cloneSpanishToEnglish(force = false) {
  if (saving.value) return
  flushEditors()
  const hasSpanish = Boolean(section.value.titleEs?.trim()) || section.value.blocks.some((block) => block.languageCode === 'ES')
  if (!hasSpanish) {
    infoMessage.value = 'No hay contenido en espanol para clonar.'
    return
  }
  const hasEnglish = Boolean(section.value.titleEn?.trim()) || section.value.blocks.some((block) => block.languageCode === 'EN')
  if (hasEnglish && !force) {
    cloneConfirmOpen.value = true
    return
  }
  cloneConfirmOpen.value = false
  section.value = {
    ...section.value,
    titleEn: section.value.titleEs,
    blocks: [
      ...section.value.blocks.filter((block) => block.languageCode !== 'EN'),
      ...section.value.blocks
        .filter((block) => block.languageCode === 'ES')
        .map(cloneSpanishBlockToEnglish),
    ],
  }
  selectedLanguage.value = 'EN'
  languageMode.value = 'EN'
  activeEditorKey.value = ''
  editorContentVersion.value += 1
  saved.value = true
  window.setTimeout(() => { saved.value = false }, 1800)
}

function cloneSpanishBlockToEnglish(block: EditorBlock): EditorBlock {
  const id = randomId('block')
  const cloned = clonePlain(block)
  return {
    ...cloned,
    id,
    backendId: undefined,
    languageCode: 'EN',
    data: cloneBlockDataForNewBlock(cloned.data, id),
  }
}

function cloneBlockDataForNewBlock(data: EditorBlock['data'], blockId: string): EditorBlock['data'] {
  if (!data) return undefined
  const cloned = clonePlain(data)
  if (cloned.json && typeof cloned.json === 'object') {
    cloned.json = cloneJsonNodeForNewBlock(cloned.json as Record<string, unknown>, blockId, true)
  }
  return cloned
}

function clonePlain<T>(value: T): T {
  return JSON.parse(JSON.stringify(toRaw(value))) as T
}

function cloneJsonNodeForNewBlock(node: Record<string, unknown>, blockId: string, root = false): Record<string, unknown> {
  const cloned: Record<string, unknown> = { ...node }
  if (cloned.attrs && typeof cloned.attrs === 'object' && !Array.isArray(cloned.attrs)) {
    const attrs = { ...(cloned.attrs as Record<string, unknown>) }
    if (root || 'blockId' in attrs) attrs.blockId = root ? blockId : randomId('block')
    if (root || 'backendId' in attrs) attrs.backendId = null
    cloned.attrs = attrs
  }
  if (Array.isArray(cloned.content)) {
    cloned.content = cloned.content.map((child) => {
      if (!child || typeof child !== 'object' || Array.isArray(child)) return child
      return cloneJsonNodeForNewBlock(child as Record<string, unknown>, blockId)
    })
  }
  return cloned
}
</script>

<template>
  <section class="editor-page">
    <BackendError :message="error" />
    <header class="editor-top">
      <button class="btn btn-outline" @click="router.push({ name: backRoute })"><ArrowLeft :size="14" /> Volver</button>
      <div class="title">
        <h1>{{ item?.code || 'Editor' }}</h1>
        <span>{{ item?.title }}</span>
      </div>
      <div class="lang-switch">
        <button :class="{ active: languageMode === 'ES' }" :disabled="saving" @click="changeLanguage('ES')">ES</button>
        <button :class="{ active: languageMode === 'EN' }" :disabled="saving" @click="changeLanguage('EN')">EN</button>
        <button :class="{ active: languageMode === 'BOTH' }" :disabled="saving" @click="showBothLanguages"><Languages :size="13" /> Ambos idiomas</button>
      </div>
      <button v-if="languageMode === 'EN'" class="btn btn-outline" :disabled="saving" @click="cloneSpanishToEnglish()">
        <Copy :size="14" /> Clonar del Español
      </button>
      <span v-if="saved" class="saved">Guardado</span>
      <button class="btn btn-primary" :disabled="saving" @click="save"><Save :size="14" /> Guardar</button>
      <div id="manual-editor-toolbar" class="toolbar-dock" />
    </header>
    <main class="editor-canvas" :class="{ 'both-languages': languageMode === 'BOTH' }">
      <div v-if="item" class="reusable-editor-row">
        <div v-for="lang in editorLanguages" :key="`${item.id}-${lang}-${editorContentVersion}`" class="language-editor-column">
          <span v-if="languageMode === 'BOTH'" class="language-column-label">{{ lang }}</span>
          <RichSectionEditor
            ref="editorRefs"
            :section="section"
            :language="lang"
            selected
            :active-toolbar="languageMode !== 'BOTH' || activeEditorKey === editorKey(lang)"
            :refresh-key="editorContentVersion"
            @update="updateForLanguage($event, lang)"
            @delete="section = emptySection()"
            @save-reusable="save"
            @save-section="save"
            @activate="activateEditor"
          />
        </div>
      </div>
    </main>
    <AppModal v-if="infoMessage" title="Accion no disponible" :description="infoMessage" size="sm" @close="infoMessage = ''">
      <template #footer>
        <button type="button" class="btn btn-primary" @click="infoMessage = ''">Entendido</button>
      </template>
    </AppModal>
    <AppModal
      v-if="cloneConfirmOpen"
      title="Clonar del Español"
      description="La version inglesa actual se reemplazara con una copia del espanol."
      size="sm"
      @close="cloneConfirmOpen = false"
    >
      <p class="confirm-text">¿Continuar?</p>
      <template #footer>
        <button type="button" class="btn btn-outline" @click="cloneConfirmOpen = false">Cancelar</button>
        <button type="button" class="btn btn-primary" @click="cloneSpanishToEnglish(true)">Clonar</button>
      </template>
    </AppModal>
  </section>
</template>

<style scoped>
.editor-page { height: 100%; display: flex; flex-direction: column; }
.editor-top { position: relative; z-index: 200; display: flex; align-items: center; flex-wrap: wrap; gap: 10px; padding: 10px 16px 8px; background: #fff; border-bottom: 1px solid var(--border); }
.title { flex: 1; min-width: 180px; }
.title h1 { margin: 0; font-size: 16px; }
.title span { color: var(--muted-foreground); font-size: 12px; }
.lang-switch { display: flex; border: 1px solid var(--border); border-radius: var(--radius); overflow: hidden; }
.lang-switch button { border: 0; background: #fff; padding: 6px 12px; display: inline-flex; align-items: center; gap: 5px; }
.lang-switch button.active { background: var(--dikoin-blue); color: #fff; }
.lang-switch button:disabled { cursor: wait; opacity: .7; }
.toolbar-dock { flex-basis: 100%; min-height: 0; }
.editor-canvas { flex: 1; overflow: auto; padding: 0 34px 40px; }
.reusable-editor-row { display: grid; grid-template-columns: 1fr; gap: 14px; }
.editor-canvas.both-languages { display: flex; flex-direction: column; align-items: center; }
.editor-canvas.both-languages .reusable-editor-row { grid-template-columns: repeat(2, 210mm); align-items: start; justify-content: center; width: max-content; max-width: 100%; }
.language-editor-column { min-width: 0; }
.language-column-label { position: sticky; top: 0; z-index: 60; display: inline-flex; align-items: center; height: 24px; padding: 0 8px; margin-bottom: 6px; border-radius: var(--radius); background: var(--dikoin-blue-dark); color: #fff; font-size: 11px; font-weight: 600; }
.saved { color: #047857; font-size: 12px; font-weight: 600; }
.confirm-text { margin: 0; color: var(--foreground); }

@media (max-width: 980px) {
  .editor-canvas.both-languages .reusable-editor-row { grid-template-columns: 1fr; width: 100%; }
}
</style>
