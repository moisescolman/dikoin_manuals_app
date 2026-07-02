<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { FileText, Image as ImageIcon, ImageUp, ListTree, Pencil, Plus, Save, Search, Trash2, X } from '@lucide/vue'
import AppModal from '@/components/shared/AppModal.vue'
import BackendError from '@/components/shared/BackendError.vue'
import { toBackendUrl } from '@/api/http'
import { useTemplatesStore } from '@/stores/templates.store'
import type { TemplateResponse } from '@/types/api'

type PreviewView = 'cover' | 'index' | 'pages'
type LogoPosition = 'left' | 'center' | 'right'
type ProductImagePosition = 'center' | 'bottom' | 'right'
type CoverAlignment = 'left' | 'center'
type PageNumberPosition = 'left' | 'center' | 'right'

type TemplateLayoutConfig = {
  version: number
  theme: {
    primary: string
    secondary: string
    accent: string
    text: string
    muted: string
    border: string
    pageBackground: string
    fontFamily: string
    baseFontSize: number
  }
  page: {
    marginTop: number
    marginRight: number
    marginBottom: number
    marginLeft: number
  }
  cover: {
    showLogo: boolean
    showProductImage: boolean
    showDate: boolean
    showProductCode: boolean
    showDocumentVersion: boolean
    alignment: CoverAlignment
    logoPosition: LogoPosition
    productImagePosition: ProductImagePosition
    titleSize: number
    subtitleSize: number
    codeSize: number
  }
  index: {
    title: string
    titleSize: number
    level1Size: number
    level2Size: number
    level3Size: number
    dottedLeaders: boolean
    lineSpacing: number
    pageNumberSize: number
  }
  header: {
    enabled: boolean
    showLogo: boolean
    showCompanyName: boolean
    showManualCode: boolean
    height: number
    background: string
    textColor: string
    borderColor: string
    logoPosition: LogoPosition
  }
  footer: {
    enabled: boolean
    showContact: boolean
    showWebsite: boolean
    showPageNumber: boolean
    height: number
    background: string
    textColor: string
    borderColor: string
    pageNumberPosition: PageNumberPosition
  }
  content: {
    sectionTitleBackground: string
    sectionTitleColor: string
    sectionTitleSize: number
    subsectionTitleColor: string
    subsectionTitleSize: number
    bodySize: number
    lineHeight: number
    tableHeaderBackground: string
    tableHeaderColor: string
    tableBorderColor: string
    tableCellPadding: number
    tableFontSize: number
    tableStripe: boolean
  }
}

const store = useTemplatesStore()
const selectedId = ref<number | null>(null)
const previewView = ref<PreviewView>('cover')
const modalPreviewView = ref<PreviewView>('cover')
const editOpen = ref(false)
const deleteOpen = ref(false)
const deleteCandidate = ref<TemplateResponse | null>(null)
const saving = ref(false)
const deleting = ref(false)
const activating = ref<number | null>(null)
const uploadingLogo = ref(false)
const uploadProgress = ref(0)
const savedMessage = ref('')
const localError = ref('')
const logoVersion = ref(Date.now())
const searchQuery = ref('')

const form = reactive({
  id: 0,
  name: '',
  description: '',
  companyName: '',
  contactEmail: '',
  contactPhone: '',
  website: '',
  logoPath: '',
  logoAssetId: null as number | null,
  logoUrl: '',
  active: false,
  systemDefault: false,
})

const formConfig = reactive<TemplateLayoutConfig>(defaultLayoutConfig())

const selectedTemplate = computed(() => store.templates.find((template) => template.id === selectedId.value) || store.templates[0] || null)
const filteredTemplates = computed(() => {
  const query = normalize(searchQuery.value)
  if (!query) return store.templates
  return store.templates.filter((template) => {
    return [
      template.name,
      template.description,
      template.contactEmail,
      template.contactPhone,
      template.website,
      template.active ? 'activa' : 'inactiva',
      template.systemDefault ? 'defecto' : '',
    ].some((value) => normalize(value).includes(query))
  })
})
const templatesCountLabel = computed(() => {
  if (!searchQuery.value.trim()) return `${store.templates.length} plantillas`
  return `${filteredTemplates.value.length} de ${store.templates.length} plantillas`
})
const editTitle = computed(() => form.id ? `Editar ${form.name || 'plantilla'}` : 'Nueva plantilla')
const rawLogoSrc = computed(() => templateLogoSource({
  logoUrl: form.logoUrl,
  logoAssetId: form.logoAssetId || undefined,
  logoPath: form.logoPath,
}))
const logoSrc = computed(() => withVersion(rawLogoSrc.value))
const canUploadLogo = computed(() => Boolean(form.id && !saving.value && !uploadingLogo.value))
const selectedPreviewConfig = computed(() => parseLayoutConfig(selectedTemplate.value?.layoutConfigJson, selectedTemplate.value))
const selectedPreviewLogo = computed(() => withVersion(templateLogoSource(selectedTemplate.value)))
const activeTemplateCount = computed(() => store.templates.filter((template) => template.active).length)

onMounted(async () => {
  await store.fetchTemplates()
  selectedId.value = store.active?.id || store.templates[0]?.id || null
})

function defaultLayoutConfig(): TemplateLayoutConfig {
  return {
    version: 1,
    theme: {
      primary: '#007cb8',
      secondary: '#0f3a54',
      accent: '#f97316',
      text: '#17202a',
      muted: '#607789',
      border: '#b8cce3',
      pageBackground: '#ffffff',
      fontFamily: 'Arial',
      baseFontSize: 11,
    },
    page: {
      marginTop: 14,
      marginRight: 14,
      marginBottom: 14,
      marginLeft: 14,
    },
    cover: {
      showLogo: true,
      showProductImage: true,
      showDate: true,
      showProductCode: true,
      showDocumentVersion: true,
      alignment: 'left',
      logoPosition: 'left',
      productImagePosition: 'center',
      titleSize: 28,
      subtitleSize: 15,
      codeSize: 12,
    },
    index: {
      title: 'Índice',
      titleSize: 22,
      level1Size: 12,
      level2Size: 11,
      level3Size: 10,
      dottedLeaders: true,
      lineSpacing: 1.55,
      pageNumberSize: 10,
    },
    header: {
      enabled: true,
      showLogo: true,
      showCompanyName: true,
      showManualCode: true,
      height: 16,
      background: '#ffffff',
      textColor: '#17202a',
      borderColor: '#007cb8',
      logoPosition: 'left',
    },
    footer: {
      enabled: true,
      showContact: true,
      showWebsite: true,
      showPageNumber: true,
      height: 12,
      background: '#007cb8',
      textColor: '#ffffff',
      borderColor: '#007cb8',
      pageNumberPosition: 'right',
    },
    content: {
      sectionTitleBackground: '#007cb8',
      sectionTitleColor: '#ffffff',
      sectionTitleSize: 15,
      subsectionTitleColor: '#0f3a54',
      subsectionTitleSize: 13,
      bodySize: 11,
      lineHeight: 1.45,
      tableHeaderBackground: '#007cb8',
      tableHeaderColor: '#ffffff',
      tableBorderColor: '#b8cce3',
      tableCellPadding: 6,
      tableFontSize: 10,
      tableStripe: true,
    },
  }
}

function normalize(value?: string | boolean) {
  return String(value || '')
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()
    .trim()
}

function cloneConfig(value: TemplateLayoutConfig) {
  return JSON.parse(JSON.stringify(value)) as TemplateLayoutConfig
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function mergeDeep<T extends Record<string, unknown>>(base: T, patch: unknown): T {
  if (!isRecord(patch)) return base
  const output = { ...base }
  Object.entries(patch).forEach(([key, value]) => {
    const current = output[key]
    output[key as keyof T] = (isRecord(current) && isRecord(value)
      ? mergeDeep(current, value)
      : value) as T[keyof T]
  })
  return output
}

function parseLayoutConfig(value?: string, template?: TemplateResponse | null) {
  const base = cloneConfig(defaultLayoutConfig())
  try {
    const parsed = mergeDeep(base as unknown as Record<string, unknown>, JSON.parse(value || '{}')) as unknown as TemplateLayoutConfig
    return applyLegacyConfig(parsed, template)
  } catch {
    return applyLegacyConfig(base, template)
  }
}

function applyLegacyConfig(config: TemplateLayoutConfig, template?: TemplateResponse | null) {
  if (!template) return config
  const header = parseBoolConfig(template.headerConfigJson)
  const footer = parseBoolConfig(template.footerConfigJson)
  config.header.showLogo = header.showLogo ?? config.header.showLogo
  config.header.showCompanyName = header.showCompanyName ?? config.header.showCompanyName
  config.header.showManualCode = header.showManualCode ?? config.header.showManualCode
  config.footer.showContact = footer.showContact ?? config.footer.showContact
  config.footer.showWebsite = footer.showWebsite ?? config.footer.showWebsite
  config.footer.showPageNumber = footer.showPageNumber ?? config.footer.showPageNumber
  return config
}

function parseBoolConfig(value?: string) {
  try {
    return JSON.parse(value || '{}') as Record<string, boolean | undefined>
  } catch {
    return {}
  }
}

function assignConfig(target: TemplateLayoutConfig, source: TemplateLayoutConfig) {
  Object.assign(target.theme, source.theme)
  Object.assign(target.page, source.page)
  Object.assign(target.cover, source.cover)
  Object.assign(target.index, source.index)
  Object.assign(target.header, source.header)
  Object.assign(target.footer, source.footer)
  Object.assign(target.content, source.content)
  target.version = source.version
}

function templateLogoSource(template?: Pick<TemplateResponse, 'logoUrl' | 'logoAssetId' | 'logoPath'> | null) {
  if (!template) return ''
  if (template.logoUrl) return toBackendUrl(template.logoUrl)
  if (template.logoAssetId) return toBackendUrl(`/api/v1/assets/${template.logoAssetId}/file`)
  return toBackendUrl(template.logoPath)
}

function withVersion(src: string) {
  if (!src) return ''
  const separator = src.includes('?') ? '&' : '?'
  return `${src}${separator}v=${logoVersion.value}`
}

function selectTemplate(template: TemplateResponse) {
  selectedId.value = template.id
  savedMessage.value = ''
}

function fillForm(template: TemplateResponse) {
  form.id = template.id
  form.name = template.name
  form.description = template.description || ''
  form.companyName = template.companyName || ''
  form.contactEmail = template.contactEmail || ''
  form.contactPhone = template.contactPhone || ''
  form.website = template.website || ''
  form.logoPath = template.logoPath || ''
  form.logoAssetId = template.logoAssetId || null
  form.logoUrl = template.logoUrl || ''
  form.active = template.active
  form.systemDefault = Boolean(template.systemDefault)
  assignConfig(formConfig, parseLayoutConfig(template.layoutConfigJson, template))
  logoVersion.value = Date.now()
}

function resetForm() {
  form.id = 0
  form.name = `Plantilla ${store.templates.length + 1}`
  form.description = ''
  form.companyName = 'DIKOIN'
  form.contactEmail = 'info@dikoin.com'
  form.contactPhone = '+34 000 000 000'
  form.website = 'www.dikoin.com'
  form.logoPath = ''
  form.logoAssetId = null
  form.logoUrl = ''
  form.active = false
  form.systemDefault = false
  assignConfig(formConfig, defaultLayoutConfig())
  logoVersion.value = Date.now()
}

function openCreate() {
  selectedId.value = null
  resetForm()
  modalPreviewView.value = 'cover'
  editOpen.value = true
  savedMessage.value = ''
  localError.value = ''
}

function openEdit(template: TemplateResponse) {
  selectTemplate(template)
  fillForm(template)
  modalPreviewView.value = previewView.value
  editOpen.value = true
  savedMessage.value = ''
  localError.value = ''
}

function closeEdit() {
  editOpen.value = false
  if (!selectedId.value && store.templates.length) {
    selectedId.value = store.active?.id || store.templates[0].id
  }
}

function payload() {
  return {
    name: form.name.trim(),
    description: form.description,
    companyName: 'DIKOIN',
    contactEmail: form.contactEmail,
    contactPhone: form.contactPhone,
    website: form.website,
    logoPath: form.logoPath,
    logoAssetId: form.logoAssetId,
    headerConfigJson: JSON.stringify({
      showLogo: formConfig.header.showLogo,
      showCompanyName: formConfig.header.showCompanyName,
      showManualCode: formConfig.header.showManualCode,
    }),
    footerConfigJson: JSON.stringify({
      showContact: formConfig.footer.showContact,
      showWebsite: formConfig.footer.showWebsite,
      showPageNumber: formConfig.footer.showPageNumber,
    }),
    layoutConfigJson: JSON.stringify(formConfig),
    active: form.active,
  }
}

async function save(closeAfter = true) {
  saving.value = true
  savedMessage.value = ''
  localError.value = ''
  try {
    const result = form.id
      ? await store.saveTemplate(form.id, payload())
      : await store.addTemplate(payload())
    selectedId.value = result.id
    const refreshed = store.templates.find((template) => template.id === result.id) || result
    fillForm(refreshed)
    if (closeAfter) editOpen.value = false
    savedMessage.value = 'Plantilla guardada.'
    return refreshed
  } catch (err) {
    localError.value = err instanceof Error ? err.message : 'No se pudo guardar la plantilla'
    return null
  } finally {
    saving.value = false
  }
}

async function activate(template: TemplateResponse) {
  activating.value = template.id
  savedMessage.value = ''
  localError.value = ''
  try {
    const activated = await store.setActive(template.id)
    selectedId.value = activated?.id || template.id
    savedMessage.value = 'Plantilla activa aplicada a los manuales.'
  } catch (err) {
    localError.value = err instanceof Error ? err.message : 'No se pudo activar la plantilla'
  } finally {
    activating.value = null
  }
}

async function toggleTemplateActive(template: TemplateResponse) {
  if (activating.value !== null) return
  if (!template.active) {
    await activate(template)
    return
  }
  if (activeTemplateCount.value <= 1) {
    localError.value = ''
    savedMessage.value = 'Debe quedar al menos una plantilla activa.'
    return
  }
  activating.value = template.id
  savedMessage.value = ''
  localError.value = ''
  try {
    await store.saveTemplate(template.id, { active: false })
    selectedId.value = template.id
    savedMessage.value = 'Plantilla desactivada.'
  } catch (err) {
    localError.value = err instanceof Error ? err.message : 'No se pudo desactivar la plantilla'
  } finally {
    activating.value = null
  }
}

function canDeactivateTemplate(template: TemplateResponse) {
  return !template.active || activeTemplateCount.value > 1
}

async function uploadLogo(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  if (!form.id) {
    await save(false)
  }
  if (!form.id) return

  uploadingLogo.value = true
  uploadProgress.value = 0
  savedMessage.value = ''
  localError.value = ''
  try {
    const updated = await store.uploadLogo(form.id, file, (progress) => {
      uploadProgress.value = progress
    })
    form.logoPath = updated.logoPath || ''
    form.logoAssetId = updated.logoAssetId || null
    form.logoUrl = updated.logoUrl || ''
    logoVersion.value = Date.now()
    uploadProgress.value = 100
    savedMessage.value = 'Logo subido.'
  } catch (err) {
    localError.value = err instanceof Error ? err.message : 'No se pudo subir el logo'
  } finally {
    uploadingLogo.value = false
  }
}

function removeLogo() {
  form.logoPath = ''
  form.logoAssetId = null
  form.logoUrl = ''
  logoVersion.value = Date.now()
}

function openDelete(template: TemplateResponse) {
  selectTemplate(template)
  deleteCandidate.value = template
  deleteOpen.value = true
  savedMessage.value = ''
  localError.value = ''
}

async function confirmDelete() {
  if (!deleteCandidate.value) return
  deleting.value = true
  savedMessage.value = ''
  localError.value = ''
  try {
    await store.removeTemplate(deleteCandidate.value.id)
    deleteOpen.value = false
    deleteCandidate.value = null
    selectedId.value = store.active?.id || store.templates[0]?.id || null
    savedMessage.value = 'Plantilla eliminada.'
  } catch (err) {
    localError.value = err instanceof Error ? err.message : 'No se pudo eliminar la plantilla'
  } finally {
    deleting.value = false
  }
}

function previewStyles(config: TemplateLayoutConfig) {
  return {
    '--tpl-primary': config.theme.primary,
    '--tpl-secondary': config.theme.secondary,
    '--tpl-accent': config.theme.accent,
    '--tpl-text': config.theme.text,
    '--tpl-muted': config.theme.muted,
    '--tpl-border': config.theme.border,
    '--tpl-page-bg': config.theme.pageBackground,
    '--tpl-font': config.theme.fontFamily,
    '--tpl-base-size': `${config.theme.baseFontSize}px`,
    '--tpl-margin-top': `${config.page.marginTop}mm`,
    '--tpl-margin-right': `${config.page.marginRight}mm`,
    '--tpl-margin-bottom': `${config.page.marginBottom}mm`,
    '--tpl-margin-left': `${config.page.marginLeft}mm`,
    '--tpl-header-height': `${config.header.height}mm`,
    '--tpl-header-bg': config.header.background,
    '--tpl-header-text': config.header.textColor,
    '--tpl-header-border': config.header.borderColor,
    '--tpl-footer-height': `${config.footer.height}mm`,
    '--tpl-footer-bg': config.footer.background,
    '--tpl-footer-text': config.footer.textColor,
    '--tpl-footer-border': config.footer.borderColor,
    '--tpl-cover-title-size': `${config.cover.titleSize}px`,
    '--tpl-cover-subtitle-size': `${config.cover.subtitleSize}px`,
    '--tpl-cover-code-size': `${config.cover.codeSize}px`,
    '--tpl-index-title-size': `${config.index.titleSize}px`,
    '--tpl-index-level-1': `${config.index.level1Size}px`,
    '--tpl-index-level-2': `${config.index.level2Size}px`,
    '--tpl-index-level-3': `${config.index.level3Size}px`,
    '--tpl-index-line-height': config.index.lineSpacing,
    '--tpl-index-page-size': `${config.index.pageNumberSize}px`,
    '--tpl-section-bg': config.content.sectionTitleBackground,
    '--tpl-section-color': config.content.sectionTitleColor,
    '--tpl-section-size': `${config.content.sectionTitleSize}px`,
    '--tpl-subsection-color': config.content.subsectionTitleColor,
    '--tpl-subsection-size': `${config.content.subsectionTitleSize}px`,
    '--tpl-body-size': `${config.content.bodySize}px`,
    '--tpl-line-height': config.content.lineHeight,
    '--tpl-table-head-bg': config.content.tableHeaderBackground,
    '--tpl-table-head-color': config.content.tableHeaderColor,
    '--tpl-table-border': config.content.tableBorderColor,
    '--tpl-table-padding': `${config.content.tableCellPadding}px`,
    '--tpl-table-size': `${config.content.tableFontSize}px`,
  }
}

function formatDate(value?: string) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('es-ES', {
    day: '2-digit',
    month: '2-digit',
    year: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}
</script>

<template>
  <section class="templates-page">
    <div class="head">
      <div>
        <h1 class="page-title">Plantillas</h1>
        <p class="text-muted">Biblioteca de plantillas de impresión para manuales.</p>
      </div>
      <button class="btn btn-primary" @click="openCreate"><Plus :size="15" /> Nueva plantilla</button>
    </div>

    <BackendError :message="store.error || localError" />
    <div v-if="savedMessage" class="success-msg">{{ savedMessage }}</div>

    <div class="templates-shell">
      <section class="card templates-inbox" aria-label="Lista de plantillas">
        <div class="list-head">
          <div>
            <h2>Plantillas</h2>
            <span>{{ templatesCountLabel }}</span>
          </div>
          <div class="template-search">
            <Search :size="14" />
            <input v-model="searchQuery" type="search" placeholder="Buscar por nombre o estado..." />
            <button v-if="searchQuery" type="button" title="Limpiar búsqueda" @click="searchQuery = ''"><X :size="14" /></button>
          </div>
        </div>

        <div class="table-scroll">
          <table class="inbox-table">
            <thead>
              <tr>
                <th>Plantilla</th>
                <th>Estado</th>
                <th>Última modificación</th>
                <th class="actions-col">Acciones</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="store.loading && !store.templates.length">
                <td colspan="4">Cargando plantillas...</td>
              </tr>
              <tr v-else-if="!store.templates.length">
                <td colspan="4">No hay plantillas creadas.</td>
              </tr>
              <tr v-else-if="!filteredTemplates.length">
                <td colspan="4">No hay plantillas que coincidan con la búsqueda.</td>
              </tr>
              <tr
                v-for="template in filteredTemplates"
                :key="template.id"
                :class="{ selected: template.id === selectedId }"
                @click="selectTemplate(template)"
              >
                <td class="title-cell">
                  <strong>{{ template.name }}</strong>
                  <span>{{ template.description || 'Sin descripción' }}</span>
                </td>
                <td>
                  <button
                    type="button"
                    class="template-switch"
                    :class="{ active: template.active }"
                    :aria-pressed="template.active"
                    :aria-label="template.active ? 'Desactivar plantilla' : 'Activar plantilla'"
                    :title="template.active && !canDeactivateTemplate(template) ? 'Debe quedar al menos una plantilla activa' : template.active ? 'Desactivar plantilla' : 'Activar plantilla'"
                    :disabled="activating === template.id || !canDeactivateTemplate(template)"
                    @click.stop="toggleTemplateActive(template)"
                  >
                    <span></span>
                  </button>
                </td>
                <td>{{ formatDate(template.updatedAt || template.createdAt) }}</td>
                <td>
                  <div class="quick-actions">
                    <button type="button" title="Editar" @click.stop="openEdit(template)">
                      <Pencil :size="16" />
                    </button>
                    <button
                      type="button"
                      title="Eliminar"
                      :disabled="template.active || template.systemDefault"
                      @click.stop="openDelete(template)"
                    >
                      <Trash2 :size="16" />
                    </button>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <aside class="preview-column" aria-label="Vista previa de la plantilla">
        <section class="card preview-panel">
          <div class="preview-head">
            <div>
              <span>Vista previa</span>
              <strong>{{ selectedTemplate?.name || 'Sin plantilla' }}</strong>
            </div>
            <div class="preview-actions">
              <div class="preview-tabs" role="tablist">
                <button type="button" :class="{ active: previewView === 'cover' }" @click="previewView = 'cover'"><ImageIcon :size="14" /> Portada</button>
                <button type="button" :class="{ active: previewView === 'index' }" @click="previewView = 'index'"><ListTree :size="14" /> Índice</button>
                <button type="button" :class="{ active: previewView === 'pages' }" @click="previewView = 'pages'"><FileText :size="14" /> Páginas</button>
              </div>
            </div>
          </div>

          <div class="mini-paper-wrap">
            <div class="paper mini" :style="previewStyles(selectedPreviewConfig)">
              <div v-if="previewView === 'cover'" class="cover-page manual-preview-page">
                <div class="cover-mark">
                  <div v-if="selectedPreviewConfig.cover.showLogo" class="cover-logo" :class="{ 'logo-image': selectedPreviewLogo }">
                    <img v-if="selectedPreviewLogo" :src="selectedPreviewLogo" alt="Logo plantilla" />
                    <span v-else>DK</span>
                  </div>
                  <strong v-if="selectedPreviewConfig.header.showCompanyName">DIKOIN</strong>
                </div>
                <div class="cover-content">
                  <div v-if="selectedPreviewConfig.cover.showProductImage" class="cover-product">
                    <div class="product-placeholder"><ImageIcon :size="64" /></div>
                  </div>
                  <p v-if="selectedPreviewConfig.cover.showProductCode" class="manual-code">DMP-HY100-2501</p>
                  <h2>Manual técnico</h2>
                  <p class="cover-subtitle">Bomba hidráulica industrial</p>
                  <small v-if="selectedPreviewConfig.cover.showDocumentVersion">Versión 01.3 · ES</small>
                </div>
                <div v-if="false" class="product-placeholder" :class="`product-${selectedPreviewConfig.cover.productImagePosition}`">
                  <ImageIcon :size="44" />
                </div>
                <footer v-if="selectedPreviewConfig.cover.showDate" class="paper-footer"></footer>
              </div>

              <div v-else-if="previewView === 'index'" class="toc-page manual-preview-page">
                <header v-if="selectedPreviewConfig.header.enabled" class="paper-header">
                  <div v-if="selectedPreviewConfig.header.showLogo" class="logo" :class="{ 'logo-image': selectedPreviewLogo }">
                    <img v-if="selectedPreviewLogo" :src="selectedPreviewLogo" alt="Logo plantilla" />
                    <span v-else>DK</span>
                  </div>
                  <strong v-if="selectedPreviewConfig.header.showCompanyName">DIKOIN</strong>
                  <span v-if="selectedPreviewConfig.header.showManualCode">Ref.: DMP-HY100-2501 · ES · v01.3</span>
                </header>
                <div class="header-line"></div>
                <main class="paper-content">
                  <h1>{{ selectedPreviewConfig.index.title }}</h1>
                <ol class="toc-list" :class="{ 'no-dots': !selectedPreviewConfig.index.dottedLeaders }">
                  <li><span>1. Seguridad</span><em>3</em></li>
                  <li class="level-2"><span>1.1 Señalización</span><em>4</em></li>
                  <li><span>2. Instalación</span><em>7</em></li>
                  <li class="level-2"><span>2.1 Conexiones hidráulicas</span><em>9</em></li>
                  <li class="level-3"><span>2.1.1 Pares de apriete</span><em>10</em></li>
                  <li><span>3. Mantenimiento</span><em>14</em></li>
                </ol>
                </main>
              </div>

              <div v-else class="content-page manual-preview-page">
                <header v-if="selectedPreviewConfig.header.enabled" class="paper-header">
                  <img v-if="selectedPreviewConfig.header.showLogo && selectedPreviewLogo" :src="selectedPreviewLogo" alt="Logo plantilla" />
                  <strong v-if="selectedPreviewConfig.header.showCompanyName">DIKOIN</strong>
                  <span v-if="selectedPreviewConfig.header.showManualCode">Ref.: DMP-HY100-2501 · ES · v01.3</span>
                </header>
                <div class="header-line"></div>
                <main class="paper-content">
                  <h2>1. Título de sección</h2>
                  <h3>1.1 Subtítulo técnico</h3>
                  <p>Contenido genérico del manual con indicaciones de montaje, revisión y mantenimiento preventivo del equipo.</p>
                  <table :class="{ striped: selectedPreviewConfig.content.tableStripe }">
                    <thead><tr><th>Elemento</th><th>Valor</th><th>Unidad</th></tr></thead>
                    <tbody>
                      <tr><td>Caudal nominal</td><td>120</td><td>l/min</td></tr>
                      <tr><td>Presión máxima</td><td>250</td><td>bar</td></tr>
                      <tr><td>Temperatura</td><td>-10 / 80</td><td>°C</td></tr>
                    </tbody>
                  </table>
                </main>
                <footer v-if="selectedPreviewConfig.footer.enabled" class="paper-footer">
                  <span v-if="selectedPreviewConfig.footer.showPageNumber">3</span>
                </footer>
              </div>
            </div>
          </div>
        </section>
      </aside>
    </div>

    <AppModal
      v-if="editOpen"
      :title="editTitle"
      description="Formulario de edición de plantilla."
      size="xl"
      @close="closeEdit"
    >
      <form class="template-editor" @submit.prevent="save(true)">
        <div class="editor-controls">
          <section class="control-section">
            <h3>Datos</h3>
            <label>Nombre <input v-model="form.name" class="field" required /></label>
            <label>Descripción <input v-model="form.description" class="field" /></label>
            <label>Web <input v-model="form.website" class="field" /></label>
            <div class="control-grid">
              <label>Email <input v-model="form.contactEmail" class="field" /></label>
              <label>Teléfono <input v-model="form.contactPhone" class="field" /></label>
            </div>
            <label class="check"><input v-model="form.active" type="checkbox" :disabled="form.active" /> Activar al guardar</label>
          </section>

          <section class="control-section">
            <h3>Logo</h3>
            <div class="logo-actions">
              <label class="logo-upload" :class="{ disabled: !canUploadLogo }">
                <input type="file" accept=".svg,image/svg+xml,image/*" :disabled="!canUploadLogo" @change="uploadLogo" />
                <ImageUp :size="14" /> {{ uploadingLogo ? 'Subiendo...' : 'Subir o reemplazar logo' }}
              </label>
              <button type="button" class="btn btn-outline" :disabled="!logoSrc" @click="removeLogo">
                <Trash2 :size="14" /> Quitar
              </button>
            </div>
            <div v-if="uploadingLogo || uploadProgress" class="progress">
              <span :style="{ width: `${uploadProgress}%` }"></span>
            </div>
          </section>

          <section class="control-section">
            <h3>Marca y página</h3>
            <div class="color-grid">
              <label>Principal <input v-model="formConfig.theme.primary" type="color" /></label>
              <label>Secundario <input v-model="formConfig.theme.secondary" type="color" /></label>
              <label>Acento <input v-model="formConfig.theme.accent" type="color" /></label>
              <label>Texto <input v-model="formConfig.theme.text" type="color" /></label>
            </div>
            <div class="control-grid">
              <label>Tipografía
                <select v-model="formConfig.theme.fontFamily" class="field">
                  <option value="Arial">Arial</option>
                  <option value="Calibri">Calibri</option>
                  <option value="Georgia">Georgia</option>
                  <option value="Times New Roman">Times New Roman</option>
                  <option value="Verdana">Verdana</option>
                </select>
              </label>
              <label>Tamaño base <input v-model.number="formConfig.theme.baseFontSize" class="field" type="number" min="8" max="16" /></label>
            </div>
            <div class="control-grid four">
              <label>Margen sup. <input v-model.number="formConfig.page.marginTop" class="field" type="number" min="6" max="30" /></label>
              <label>Margen der. <input v-model.number="formConfig.page.marginRight" class="field" type="number" min="6" max="30" /></label>
              <label>Margen inf. <input v-model.number="formConfig.page.marginBottom" class="field" type="number" min="6" max="30" /></label>
              <label>Margen izq. <input v-model.number="formConfig.page.marginLeft" class="field" type="number" min="6" max="30" /></label>
            </div>
          </section>

          <section class="control-section">
            <h3>Portada</h3>
            <div class="check-grid">
              <label class="check"><input v-model="formConfig.cover.showLogo" type="checkbox" /> Logo</label>
              <label class="check"><input v-model="formConfig.cover.showProductImage" type="checkbox" /> Imagen producto</label>
              <label class="check"><input v-model="formConfig.cover.showProductCode" type="checkbox" /> Código producto</label>
              <label class="check"><input v-model="formConfig.cover.showDocumentVersion" type="checkbox" /> Versión</label>
              <label class="check"><input v-model="formConfig.cover.showDate" type="checkbox" /> Fecha</label>
            </div>
            <div class="control-grid">
              <label>Alineación
                <select v-model="formConfig.cover.alignment" class="field">
                  <option value="left">Izquierda</option>
                  <option value="center">Centro</option>
                </select>
              </label>
              <label>Posición logo
                <select v-model="formConfig.cover.logoPosition" class="field">
                  <option value="left">Izquierda</option>
                  <option value="center">Centro</option>
                  <option value="right">Derecha</option>
                </select>
              </label>
              <label>Posición producto
                <select v-model="formConfig.cover.productImagePosition" class="field">
                  <option value="center">Centro</option>
                  <option value="bottom">Inferior</option>
                  <option value="right">Derecha</option>
                </select>
              </label>
            </div>
            <div class="control-grid">
              <label>Título <input v-model.number="formConfig.cover.titleSize" class="field" type="number" min="18" max="44" /></label>
              <label>Subtítulo <input v-model.number="formConfig.cover.subtitleSize" class="field" type="number" min="10" max="24" /></label>
              <label>Código <input v-model.number="formConfig.cover.codeSize" class="field" type="number" min="8" max="18" /></label>
            </div>
          </section>

          <section class="control-section">
            <h3>Índice</h3>
            <label>Título <input v-model="formConfig.index.title" class="field" /></label>
            <div class="check-grid">
              <label class="check"><input v-model="formConfig.index.dottedLeaders" type="checkbox" /> Puntos de separación</label>
            </div>
            <div class="control-grid">
              <label>Título <input v-model.number="formConfig.index.titleSize" class="field" type="number" min="16" max="34" /></label>
              <label>Nivel 1 <input v-model.number="formConfig.index.level1Size" class="field" type="number" min="8" max="18" /></label>
              <label>Nivel 2 <input v-model.number="formConfig.index.level2Size" class="field" type="number" min="8" max="16" /></label>
              <label>Nivel 3 <input v-model.number="formConfig.index.level3Size" class="field" type="number" min="8" max="15" /></label>
            </div>
            <div class="control-grid">
              <label>Interlineado <input v-model.number="formConfig.index.lineSpacing" class="field" type="number" min="1" max="2.4" step=".05" /></label>
              <label>Número página <input v-model.number="formConfig.index.pageNumberSize" class="field" type="number" min="8" max="16" /></label>
            </div>
          </section>

          <section class="control-section">
            <h3>Encabezado y pie</h3>
            <div class="check-grid">
              <label class="check"><input v-model="formConfig.header.enabled" type="checkbox" /> Encabezado</label>
              <label class="check"><input v-model="formConfig.header.showLogo" type="checkbox" /> Logo cabecera</label>
              <label class="check"><input v-model="formConfig.header.showCompanyName" type="checkbox" /> Mostrar DIKOIN</label>
              <label class="check"><input v-model="formConfig.header.showManualCode" type="checkbox" /> Código manual</label>
              <label class="check"><input v-model="formConfig.footer.enabled" type="checkbox" /> Pie</label>
              <label class="check"><input v-model="formConfig.footer.showContact" type="checkbox" /> Contacto</label>
              <label class="check"><input v-model="formConfig.footer.showWebsite" type="checkbox" /> Web</label>
              <label class="check"><input v-model="formConfig.footer.showPageNumber" type="checkbox" /> Paginación</label>
            </div>
            <div class="color-grid">
              <label>Fondo cab. <input v-model="formConfig.header.background" type="color" /></label>
              <label>Texto cab. <input v-model="formConfig.header.textColor" type="color" /></label>
              <label>Fondo pie <input v-model="formConfig.footer.background" type="color" /></label>
              <label>Texto pie <input v-model="formConfig.footer.textColor" type="color" /></label>
            </div>
            <div class="control-grid">
              <label>Alto cabecera <input v-model.number="formConfig.header.height" class="field" type="number" min="8" max="28" /></label>
              <label>Alto pie <input v-model.number="formConfig.footer.height" class="field" type="number" min="8" max="24" /></label>
              <label>Paginación
                <select v-model="formConfig.footer.pageNumberPosition" class="field">
                  <option value="left">Izquierda</option>
                  <option value="center">Centro</option>
                  <option value="right">Derecha</option>
                </select>
              </label>
            </div>
          </section>

          <section class="control-section">
            <h3>Páginas</h3>
            <div class="color-grid">
              <label>Fondo título <input v-model="formConfig.content.sectionTitleBackground" type="color" /></label>
              <label>Texto título <input v-model="formConfig.content.sectionTitleColor" type="color" /></label>
              <label>Cabecera tabla <input v-model="formConfig.content.tableHeaderBackground" type="color" /></label>
              <label>Borde tabla <input v-model="formConfig.content.tableBorderColor" type="color" /></label>
            </div>
            <div class="control-grid">
              <label>Título sección <input v-model.number="formConfig.content.sectionTitleSize" class="field" type="number" min="11" max="24" /></label>
              <label>Subtítulo <input v-model.number="formConfig.content.subsectionTitleSize" class="field" type="number" min="10" max="20" /></label>
              <label>Cuerpo <input v-model.number="formConfig.content.bodySize" class="field" type="number" min="8" max="15" /></label>
              <label>Interlineado <input v-model.number="formConfig.content.lineHeight" class="field" type="number" min="1" max="2" step=".05" /></label>
            </div>
            <div class="control-grid">
              <label>Tabla texto <input v-model.number="formConfig.content.tableFontSize" class="field" type="number" min="8" max="14" /></label>
              <label>Tabla padding <input v-model.number="formConfig.content.tableCellPadding" class="field" type="number" min="3" max="12" /></label>
              <label class="check"><input v-model="formConfig.content.tableStripe" type="checkbox" /> Filas alternas</label>
            </div>
          </section>
        </div>

        <div class="editor-preview">
          <div class="modal-view-tabs">
            <button type="button" :class="{ active: modalPreviewView === 'cover' }" @click="modalPreviewView = 'cover'"><ImageIcon :size="14" /> Portada</button>
            <button type="button" :class="{ active: modalPreviewView === 'index' }" @click="modalPreviewView = 'index'"><ListTree :size="14" /> Índice</button>
            <button type="button" :class="{ active: modalPreviewView === 'pages' }" @click="modalPreviewView = 'pages'"><FileText :size="14" /> Páginas</button>
          </div>
          <div class="paper-wrap">
            <div class="paper" :style="previewStyles(formConfig)">
              <div v-if="modalPreviewView === 'cover'" class="cover-page manual-preview-page">
                <div class="cover-mark">
                  <div v-if="formConfig.cover.showLogo" class="cover-logo" :class="{ 'logo-image': logoSrc }">
                  <img v-if="logoSrc" :src="logoSrc" alt="Logo plantilla" />
                  <span v-else>DK</span>
                  </div>
                  <strong v-if="formConfig.header.showCompanyName">DIKOIN</strong>
                </div>
                <div class="cover-content">
                  <div v-if="formConfig.cover.showProductImage" class="cover-product">
                    <div class="product-placeholder"><ImageIcon :size="72" /></div>
                  </div>
                  <p v-if="formConfig.cover.showProductCode" class="manual-code">DMP-HY100-2501</p>
                  <h2>Manual técnico</h2>
                  <p class="cover-subtitle">Bomba hidráulica industrial</p>
                  <small v-if="formConfig.cover.showDocumentVersion">Versión 01.3 · ES</small>
                </div>
                <div v-if="false" class="product-placeholder" :class="`product-${formConfig.cover.productImagePosition}`">
                  <ImageIcon :size="72" />
                  <span>Imagen producto</span>
                </div>
                <footer v-if="formConfig.cover.showDate" class="paper-footer"></footer>
              </div>

              <div v-else-if="modalPreviewView === 'index'" class="toc-page manual-preview-page">
                <header v-if="formConfig.header.enabled" class="paper-header">
                  <div v-if="formConfig.header.showLogo" class="logo" :class="{ 'logo-image': logoSrc }">
                    <img v-if="logoSrc" :src="logoSrc" alt="Logo plantilla" />
                    <span v-else>DK</span>
                  </div>
                  <strong v-if="formConfig.header.showCompanyName">DIKOIN</strong>
                  <span v-if="formConfig.header.showManualCode">Ref.: DMP-HY100-2501 · ES · v01.3</span>
                </header>
                <div class="header-line"></div>
                <main class="paper-content">
                  <h1>{{ formConfig.index.title }}</h1>
                <ol class="toc-list" :class="{ 'no-dots': !formConfig.index.dottedLeaders }">
                  <li><span>1. Seguridad</span><em>3</em></li>
                  <li class="level-2"><span>1.1 Señalización</span><em>4</em></li>
                  <li><span>2. Instalación</span><em>7</em></li>
                  <li class="level-2"><span>2.1 Conexiones hidráulicas</span><em>9</em></li>
                  <li class="level-3"><span>2.1.1 Pares de apriete</span><em>10</em></li>
                  <li><span>3. Mantenimiento</span><em>14</em></li>
                </ol>
                </main>
              </div>

              <div v-else class="content-page manual-preview-page">
                <header v-if="formConfig.header.enabled" class="paper-header">
                  <img v-if="formConfig.header.showLogo && logoSrc" :src="logoSrc" alt="Logo plantilla" />
                  <strong v-if="formConfig.header.showCompanyName">DIKOIN</strong>
                  <span v-if="formConfig.header.showManualCode">Ref.: DMP-HY100-2501 · ES · v01.3</span>
                </header>
                <div class="header-line"></div>
                <main class="paper-content">
                  <h2>1. Título de sección</h2>
                  <h3>1.1 Subtítulo técnico</h3>
                  <p>Contenido genérico del manual con indicaciones de montaje, revisión, puesta en marcha y mantenimiento preventivo del equipo.</p>
                  <table :class="{ striped: formConfig.content.tableStripe }">
                    <thead><tr><th>Elemento</th><th>Valor</th><th>Unidad</th></tr></thead>
                    <tbody>
                      <tr><td>Caudal nominal</td><td>120</td><td>l/min</td></tr>
                      <tr><td>Presión máxima</td><td>250</td><td>bar</td></tr>
                      <tr><td>Temperatura</td><td>-10 / 80</td><td>°C</td></tr>
                    </tbody>
                  </table>
                  <p>La información de ejemplo reproduce el aspecto final de las páginas A4 del manual.</p>
                </main>
                <footer v-if="formConfig.footer.enabled" class="paper-footer">
                  <span v-if="formConfig.footer.showPageNumber">3</span>
                </footer>
              </div>
            </div>
          </div>
        </div>
      </form>

      <template #footer>
        <button type="button" class="btn btn-outline" :disabled="saving" @click="closeEdit"><X :size="15" /> Cancelar</button>
        <button type="button" class="btn btn-primary" :disabled="saving" @click="save(true)"><Save :size="15" /> {{ saving ? 'Guardando...' : 'Guardar' }}</button>
      </template>
    </AppModal>

    <AppModal
      v-if="deleteOpen"
      title="Eliminar plantilla"
      :description="deleteCandidate ? deleteCandidate.name : undefined"
      size="sm"
      @close="deleteOpen = false"
    >
      <div class="delete-warning">
        <p>La plantilla se eliminará permanentemente. Esta acción no afecta al contenido ya guardado de los manuales.</p>
      </div>
      <template #footer>
        <button type="button" class="btn btn-outline" :disabled="deleting" @click="deleteOpen = false">Cancelar</button>
        <button type="button" class="btn btn-danger" :disabled="deleting" @click="confirmDelete"><Trash2 :size="15" /> Eliminar</button>
      </template>
    </AppModal>
  </section>
</template>

<style scoped>
.templates-page {
  min-height: 100%;
  padding: 24px;
  display: grid;
  grid-template-rows: auto auto auto 1fr;
  gap: 16px;
  overflow: auto;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.templates-shell {
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  align-items: stretch;
}

.templates-inbox,
.preview-panel {
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.list-head {
  flex: 0 0 auto;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
  background: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.list-head h2 {
  margin: 0;
  font-size: 15px;
}

.list-head span {
  color: var(--muted-foreground);
  font-size: 12px;
}

.template-search {
  width: min(460px, 54%);
  min-width: 280px;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--input-background);
  padding: 8px 10px;
}

.template-search input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--foreground);
}

.template-search button {
  width: 24px;
  height: 24px;
  border: 0;
  background: transparent;
  color: var(--muted-foreground);
  display: grid;
  place-items: center;
  padding: 0;
}

.table-scroll {
  min-height: 0;
  flex: 1;
  overflow: auto;
}

.inbox-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.inbox-table th,
.inbox-table td {
  border-bottom: 1px solid var(--border);
  padding: 10px 12px;
  text-align: left;
  vertical-align: middle;
}

.inbox-table th {
  color: var(--muted-foreground);
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
}

.inbox-table tbody tr {
  cursor: pointer;
}

.inbox-table tbody tr:hover td,
.inbox-table tbody tr.selected td {
  background: var(--dikoin-blue-lighter);
}

.title-cell {
  min-width: 240px;
}

.title-cell strong,
.title-cell span {
  display: block;
}

.title-cell span {
  margin-top: 3px;
  color: var(--muted-foreground);
  font-size: 12px;
}

.actions-col {
  width: 92px;
}

.quick-actions {
  display: flex;
  gap: 6px;
}

.quick-actions button {
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

.quick-actions button:hover:not(:disabled) {
  border-color: var(--dikoin-blue-light);
  color: var(--dikoin-blue);
}

.quick-actions button:disabled {
  opacity: .42;
  cursor: not-allowed;
}

.template-switch {
  width: 54px;
  height: 30px;
  border: 0;
  border-radius: 999px;
  background: #d9d9d9;
  padding: 3px;
  display: inline-flex;
  align-items: center;
  cursor: pointer;
  box-shadow: inset 0 0 0 1px rgba(15, 23, 42, .04);
  transition: background .18s ease, opacity .18s ease;
}

.template-switch span {
  width: 24px;
  height: 24px;
  border-radius: 999px;
  background: #fff;
  box-shadow: 0 3px 8px rgba(15, 23, 42, .25);
  transition: transform .18s ease;
}

.template-switch.active {
  background: #65d857;
}

.template-switch.active span {
  transform: translateX(24px);
}

.template-switch:disabled {
  opacity: .58;
  cursor: not-allowed;
}

.preview-panel {
  background: #fff;
}

.preview-head {
  padding: 12px 15px;
  border-bottom: 1px solid var(--border);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.preview-head > div:first-child {
  display: grid;
  gap: 3px;
}

.preview-head span {
  color: var(--muted-foreground);
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
}

.preview-head strong {
  color: var(--foreground);
  font-size: 15px;
}

.preview-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.preview-tabs,
.modal-view-tabs {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--input-background);
  padding: 3px;
}

.preview-tabs button,
.modal-view-tabs button {
  border: 0;
  border-radius: calc(var(--radius) - 2px);
  background: transparent;
  color: var(--muted-foreground);
  padding: 7px 9px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  font-weight: 700;
}

.preview-tabs button.active,
.modal-view-tabs button.active {
  background: #fff;
  color: var(--dikoin-blue);
  box-shadow: 0 1px 3px rgba(15, 23, 42, .08);
}

.mini-paper-wrap,
.paper-wrap {
  min-height: 0;
  overflow: auto;
  background: #eef3f7;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 14px;
}

.mini-paper-wrap {
  flex: 1;
  display: grid;
  place-items: start center;
}

.paper {
  width: 210mm;
  min-height: 297mm;
  background: var(--tpl-page-bg);
  color: var(--tpl-text);
  font-family: var(--tpl-font), Arial, sans-serif;
  font-size: var(--tpl-base-size);
  box-shadow: 0 10px 28px rgba(15, 23, 42, .16);
  overflow: hidden;
}

.paper.mini {
  transform: scale(.58);
  transform-origin: top center;
  margin-bottom: calc(-297mm * .42);
}

.manual-preview-page {
  min-height: 297mm;
  padding: var(--tpl-margin-top) var(--tpl-margin-right) var(--tpl-margin-bottom) var(--tpl-margin-left);
  box-sizing: border-box;
  overflow: hidden;
}

.cover-page.manual-preview-page {
  display: grid;
  grid-template-rows: auto 1fr auto;
}

.cover-mark {
  display: grid;
  justify-items: center;
  gap: 8px;
}

.cover-content {
  align-self: stretch;
  display: grid;
  grid-template-rows: minmax(250px, 1fr) auto auto;
  gap: 22px;
  align-items: end;
  justify-items: center;
  text-align: center;
}

.cover-content > h1,
.cover-content > p:not(.manual-code),
.cover-content > span {
  display: none;
}

.cover-content h2 {
  max-width: 620px;
  margin: 0;
  color: var(--tpl-secondary);
  font-size: var(--tpl-cover-title-size);
  line-height: 1.12;
}

.cover-content .cover-subtitle {
  color: var(--tpl-primary);
  font-size: var(--tpl-cover-subtitle-size);
  font-weight: 600;
  border-top: 1px solid var(--tpl-primary);
  width: min(100%, 650px);
  padding-top: 6px;
}

.cover-content small {
  width: min(100%, 650px);
  background: var(--tpl-primary);
  color: #fff;
  padding: 5px 10px;
}

.manual-code {
  font-family: Consolas, Monaco, 'Courier New', monospace;
  justify-self: start;
  align-self: end;
  color: var(--tpl-muted) !important;
  font-size: var(--tpl-cover-code-size);
}

.cover-product {
  align-self: center;
  width: 100%;
  min-height: 250px;
  display: grid;
  place-items: center;
}

.cover-product .product-placeholder {
  width: 88%;
  min-height: 250px;
}

.toc-page.manual-preview-page,
.content-page.manual-preview-page {
  display: grid;
  grid-template-rows: auto auto 1fr auto;
}

.paper-header {
  min-height: var(--tpl-header-height);
  background: var(--tpl-header-bg);
  color: var(--tpl-header-text);
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.paper-header > img,
.paper-header .logo {
  width: 34mm;
  height: 10mm;
  object-fit: contain;
}

.paper-header span {
  margin-left: auto;
  color: var(--tpl-muted);
  font-size: 11px;
}

.header-line {
  height: 2px;
  background: var(--tpl-header-border);
  margin: 6px 0 18px;
}

.paper-content {
  min-height: 0;
  overflow: hidden;
}

.toc-page h1 {
  margin: 0 0 22px;
  color: var(--tpl-secondary);
  font-size: var(--tpl-index-title-size);
}

.paper-footer {
  justify-self: center;
  align-self: end;
  min-height: var(--tpl-footer-height);
  min-width: 42px;
  border-top: 1px solid var(--tpl-footer-border);
  background: var(--tpl-footer-bg);
  color: var(--tpl-footer-text);
  padding: 3px 8px;
  font-size: 11px;
}

.cover-page,
.index-page,
.content-page {
  min-height: 297mm;
  padding: var(--tpl-margin-top) var(--tpl-margin-right) var(--tpl-margin-bottom) var(--tpl-margin-left);
  box-sizing: border-box;
}

.cover-page {
  position: relative;
  display: grid;
  grid-template-rows: auto 1fr auto;
  gap: 16mm;
}

.cover-page.align-center {
  text-align: center;
}

.cover-logo {
  display: flex;
}

.cover-logo.pos-left {
  justify-content: flex-start;
}

.cover-logo.pos-center {
  justify-content: center;
}

.cover-logo.pos-right {
  justify-content: flex-end;
}

.cover-logo img {
  max-width: 44mm;
  max-height: 16mm;
  object-fit: contain;
}

.cover-logo span {
  display: inline-grid;
  place-items: center;
  width: 38mm;
  height: 13mm;
  background: var(--tpl-primary);
  color: #fff;
  font-weight: 800;
}

.cover-main {
  align-self: center;
  max-width: 135mm;
}

.cover-page.align-center .cover-main {
  justify-self: center;
}

.cover-code {
  margin: 0 0 8mm;
  color: var(--tpl-primary);
  font-size: var(--tpl-cover-code-size);
  font-weight: 800;
  text-transform: uppercase;
}

.cover-main h2 {
  margin: 0;
  color: var(--tpl-secondary);
  font-size: var(--tpl-cover-title-size);
  line-height: 1.05;
}

.cover-subtitle {
  margin: 5mm 0 4mm;
  color: var(--tpl-muted);
  font-size: var(--tpl-cover-subtitle-size);
  line-height: 1.35;
}

.cover-main small {
  color: var(--tpl-muted);
  font-size: var(--tpl-cover-code-size);
  font-weight: 700;
}

.product-placeholder {
  border: 1px dashed var(--tpl-border);
  background: #f7fafc;
  color: var(--tpl-muted);
  display: grid;
  place-items: center;
  gap: 4mm;
}

.product-placeholder span {
  font-size: 12px;
  font-weight: 700;
}

.product-center {
  position: absolute;
  right: var(--tpl-margin-right);
  bottom: 38mm;
  width: 70mm;
  height: 52mm;
}

.product-bottom {
  width: 100%;
  height: 56mm;
  align-self: end;
}

.product-right {
  position: absolute;
  right: var(--tpl-margin-right);
  top: 94mm;
  width: 62mm;
  height: 102mm;
}

.cover-page footer {
  color: var(--tpl-muted);
  font-weight: 700;
}

.index-page h2 {
  margin: 0 0 18mm;
  color: var(--tpl-secondary);
  font-size: var(--tpl-index-title-size);
}

.toc-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 4mm;
  line-height: var(--tpl-index-line-height);
}

.toc-list li {
  display: grid;
  grid-template-columns: auto minmax(24mm, 1fr) auto;
  align-items: baseline;
  color: var(--tpl-text);
  font-size: var(--tpl-index-level-1);
}

.toc-list li::before {
  content: "";
  grid-column: 2;
  border-bottom: 1px dotted var(--tpl-border);
  margin: 0 3mm;
  transform: translateY(-2px);
}

.toc-list:not(.dotted) li::before {
  border-bottom-color: transparent;
}

.toc-list span {
  grid-column: 1;
}

.toc-list em {
  grid-column: 3;
  color: var(--tpl-muted);
  font-size: var(--tpl-index-page-size);
  font-style: normal;
  font-weight: 700;
}

.toc-list .level-2 {
  padding-left: 8mm;
  font-size: var(--tpl-index-level-2);
}

.toc-list .level-3 {
  padding-left: 16mm;
  font-size: var(--tpl-index-level-3);
}

.content-page {
  display: grid;
  grid-template-rows: auto 1fr auto;
}

.doc-header {
  min-height: var(--tpl-header-height);
  border-bottom: 1.5px solid var(--tpl-header-border);
  background: var(--tpl-header-bg);
  color: var(--tpl-header-text);
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 8mm;
  padding: 0 0 3mm;
}

.doc-header img {
  max-width: 30mm;
  max-height: 11mm;
  object-fit: contain;
}

.doc-header strong {
  font-size: 12px;
}

.doc-header span {
  color: inherit;
  font-size: 10px;
  font-weight: 700;
}

.content-page main {
  padding: 9mm 0;
}

.content-page h2 {
  margin: 0 0 7mm;
  background: var(--tpl-section-bg);
  color: var(--tpl-section-color);
  padding: 2.5mm 3mm;
  font-size: var(--tpl-section-size);
}

.content-page h3 {
  margin: 0 0 4mm;
  color: var(--tpl-subsection-color);
  font-size: var(--tpl-subsection-size);
}

.content-page p {
  margin: 0 0 5mm;
  font-size: var(--tpl-body-size);
  line-height: var(--tpl-line-height);
}

.content-page table {
  width: 100%;
  margin: 4mm 0 7mm;
  border-collapse: collapse;
  font-size: var(--tpl-table-size);
}

.content-page th {
  background: var(--tpl-table-head-bg);
  color: var(--tpl-table-head-color);
  text-align: left;
}

.content-page th,
.content-page td {
  border: 1px solid var(--tpl-table-border);
  padding: var(--tpl-table-padding);
}

.content-page table.striped tbody tr:nth-child(even) td {
  background: #f7fafc;
}

.doc-footer {
  min-height: var(--tpl-footer-height);
  border-top: 1px solid var(--tpl-footer-border);
  background: var(--tpl-footer-bg);
  color: var(--tpl-footer-text);
  display: grid;
  grid-template-columns: 1fr auto auto;
  align-items: center;
  gap: 8mm;
  padding: 2mm 3mm;
  font-size: 10px;
}

.paper .manual-preview-page.cover-page {
  display: grid;
  grid-template-rows: auto 1fr auto;
  gap: 0;
}

.paper .cover-mark {
  display: grid;
  justify-items: center;
  gap: 8px;
}

.paper .cover-content {
  align-self: stretch;
  display: grid;
  grid-template-rows: minmax(250px, 1fr) auto auto;
  gap: 22px;
  align-items: end;
  justify-items: center;
  text-align: center;
  max-width: none;
}

.paper .cover-content > h1,
.paper .cover-content > p:not(.manual-code),
.paper .cover-content > span {
  display: none;
}

.paper .cover-content h2 {
  max-width: 620px;
  margin: 0;
  color: var(--tpl-secondary);
  font-size: var(--tpl-cover-title-size);
  line-height: 1.12;
}

.paper .cover-content .cover-subtitle {
  margin: 0;
  color: var(--tpl-primary);
  font-size: var(--tpl-cover-subtitle-size);
  font-weight: 600;
  border-top: 1px solid var(--tpl-primary);
  width: min(100%, 650px);
  padding-top: 6px;
}

.paper .cover-content small {
  width: min(100%, 650px);
  background: var(--tpl-primary);
  color: #fff;
  padding: 5px 10px;
}

.paper .cover-product {
  align-self: center;
  width: 100%;
  min-height: 250px;
  display: grid;
  place-items: center;
}

.paper .cover-product .product-placeholder {
  position: static;
  width: 88%;
  min-height: 250px;
}

.paper .manual-code {
  font-family: Consolas, Monaco, 'Courier New', monospace;
  justify-self: start;
  align-self: end;
  color: var(--tpl-muted) !important;
  font-size: var(--tpl-cover-code-size);
}

.paper .toc-page h1 {
  margin: 0 0 22px;
  color: var(--tpl-secondary);
  font-size: var(--tpl-index-title-size);
}

.template-editor {
  display: grid;
  grid-template-columns: minmax(420px, .82fr) minmax(0, 1fr);
  gap: 16px;
  min-height: 0;
}

.editor-controls {
  max-height: calc(86vh - 180px);
  overflow: auto;
  display: grid;
  gap: 12px;
  padding-right: 4px;
}

.control-section {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: #fff;
  padding: 12px;
  display: grid;
  gap: 11px;
}

.control-section h3 {
  margin: 0;
  color: var(--foreground);
  font-size: 13px;
}

label {
  display: grid;
  gap: 6px;
  color: var(--muted-foreground);
  font-size: 12px;
  font-weight: 700;
}

.control-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.control-grid.four {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.color-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.color-grid input {
  width: 100%;
  height: 34px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: #fff;
  padding: 3px;
}

.check-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.check {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;
  color: var(--foreground);
}

.check input {
  width: 16px;
  height: 16px;
  accent-color: var(--dikoin-blue);
}

.logo-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.logo-upload {
  border: 1px dashed var(--border);
  background: var(--input-background);
  padding: 9px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--dikoin-blue);
  cursor: pointer;
}

.logo-upload.disabled {
  opacity: .55;
  cursor: not-allowed;
}

.logo-upload input {
  display: none;
}

.progress {
  height: 7px;
  overflow: hidden;
  background: #e5e7eb;
  border-radius: 999px;
}

.progress span {
  display: block;
  height: 100%;
  background: var(--dikoin-blue);
  transition: width .2s ease;
}

.editor-preview {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.paper-wrap {
  flex: 1;
  max-height: calc(86vh - 230px);
}

.success-msg {
  background: var(--dikoin-green-light);
  color: #065f46;
  border: 1px solid #86efac;
  padding: 10px;
  border-radius: var(--radius);
}

.delete-warning {
  border: 1px solid #fdba74;
  border-radius: var(--radius);
  background: #fff7ed;
  color: #78350f;
  padding: 12px;
}

.delete-warning p {
  margin: 0;
  line-height: 1.45;
}

.btn-danger {
  background: var(--dikoin-red);
  color: #fff;
  border-color: var(--dikoin-red);
}

@media (max-width: 1180px) {
  .templates-shell,
  .template-editor {
    grid-template-columns: 1fr;
  }

  .list-head,
  .preview-head {
    align-items: stretch;
    flex-direction: column;
  }

  .template-search {
    width: 100%;
    min-width: 0;
  }

  .preview-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 720px) {
  .templates-page {
    padding: 16px;
  }

  .head,
  .logo-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .control-grid,
  .control-grid.four,
  .color-grid,
  .check-grid {
    grid-template-columns: 1fr;
  }
}
</style>
