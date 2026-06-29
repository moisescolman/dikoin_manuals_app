<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ArrowLeft, Save } from '@lucide/vue'
import { useRouter } from 'vue-router'
import { getReusableBlock, updateReusableBlock } from '@/api/reusable-blocks.api'
import { getReusableFragment, updateReusableFragment } from '@/api/reusable-fragments.api'
import RichSectionEditor from '@/components/editor/RichSectionEditor.vue'
import BackendError from '@/components/shared/BackendError.vue'
import type { EditorSection } from '@/types/editor'
import { randomId, sectionsFromBackend, versionRequestFromEditor } from '@/types/editor'
import type { LanguageCode, ReusableBlockResponse, ReusableFragmentResponse } from '@/types/api'

const props = defineProps<{ id: string; kind: 'SINGLE_BLOCK' | 'FRAGMENT' }>()
const router = useRouter()
type ReusableEditorItem = ReusableBlockResponse | ReusableFragmentResponse
const item = ref<ReusableEditorItem>()
const section = ref<EditorSection>(emptySection())
const language = ref<LanguageCode>('ES')
const saving = ref(false)
const saved = ref(false)
const error = ref('')
const editorRef = ref<{ flushEditorSync: () => void } | null>(null)

const isSection = computed(() => props.kind === 'SINGLE_BLOCK')
const backRoute = computed(() => isSection.value ? 'reusable-blocks' : 'reusable-fragments')

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

function updateForLanguage(value: EditorSection) {
  section.value = synchronizeBlocks({
    ...section.value,
    ...value,
    titleEs: language.value === 'ES' ? value.titleEs : section.value.titleEs,
    titleEn: language.value === 'EN' ? value.titleEn : section.value.titleEn,
    blocks: [
      ...section.value.blocks.filter((block) => block.languageCode !== language.value),
      ...value.blocks.filter((block) => block.languageCode === language.value),
    ],
  }, language.value)
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
  editorRef.value?.flushEditorSync?.()
  section.value = synchronizeBlocks(section.value, language.value)
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
  if (value === language.value) return
  editorRef.value?.flushEditorSync?.()
  language.value = value
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
        <button :class="{ active: language === 'ES' }" @click="changeLanguage('ES')">ES</button>
        <button :class="{ active: language === 'EN' }" @click="changeLanguage('EN')">EN</button>
      </div>
      <span v-if="saved" class="saved">Guardado</span>
      <button class="btn btn-primary" :disabled="saving" @click="save"><Save :size="14" /> Guardar</button>
      <div id="manual-editor-toolbar" class="toolbar-dock" />
    </header>
    <main class="editor-canvas">
      <RichSectionEditor
        v-if="item"
        ref="editorRef"
        :key="`${item.id}-${language}`"
        :section="section"
        :language="language"
        selected
        active-toolbar
        @update="updateForLanguage"
        @delete="section = emptySection()"
        @save-reusable="save"
        @save-section="save"
      />
    </main>
  </section>
</template>

<style scoped>
.editor-page { height: 100%; display: flex; flex-direction: column; }
.editor-top { position: relative; z-index: 200; display: flex; align-items: center; flex-wrap: wrap; gap: 10px; padding: 10px 16px 8px; background: #fff; border-bottom: 1px solid var(--border); }
.title { flex: 1; min-width: 180px; }
.title h1 { margin: 0; font-size: 16px; }
.title span { color: var(--muted-foreground); font-size: 12px; }
.lang-switch { display: flex; border: 1px solid var(--border); border-radius: var(--radius); overflow: hidden; }
.lang-switch button { border: 0; background: #fff; padding: 6px 12px; }
.lang-switch button.active { background: var(--dikoin-blue); color: #fff; }
.toolbar-dock { flex-basis: 100%; min-height: 0; }
.editor-canvas { flex: 1; overflow: auto; padding: 0 34px 40px; }
.saved { color: #047857; font-size: 12px; font-weight: 600; }
</style>
