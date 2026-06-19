<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { CheckCircle, ImageUp, Plus, Save, Trash2 } from '@lucide/vue'
import BackendError from '@/components/shared/BackendError.vue'
import { toBackendUrl } from '@/api/http'
import { useTemplatesStore } from '@/stores/templates.store'
import type { TemplateResponse } from '@/types/api'

const store = useTemplatesStore()
const selectedId = ref<number | null>(null)
const uploadingLogo = ref(false)
const uploadProgress = ref(0)
const saving = ref(false)
const activating = ref(false)
const savedMessage = ref('')
const localError = ref('')
const logoVersion = ref(Date.now())

const headerDefaults = {
  showLogo: true,
  showCompanyName: true,
  showManualCode: true,
}

const footerDefaults = {
  showContact: true,
  showWebsite: true,
  showPageNumber: true,
}

const display = reactive({
  ...headerDefaults,
  ...footerDefaults,
})

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
  headerConfigJson: '',
  footerConfigJson: '',
  active: true,
})

const rawLogoSrc = computed(() => {
  if (form.logoUrl) return toBackendUrl(form.logoUrl)
  if (form.logoAssetId) return toBackendUrl(`/api/v1/assets/${form.logoAssetId}/file`)
  return toBackendUrl(form.logoPath)
})
const logoSrc = computed(() => {
  if (!rawLogoSrc.value) return ''
  const separator = rawLogoSrc.value.includes('?') ? '&' : '?'
  return `${rawLogoSrc.value}${separator}v=${logoVersion.value}`
})
const canUploadLogo = computed(() => Boolean(form.id && !saving.value && !uploadingLogo.value))

onMounted(async () => {
  await store.fetchTemplates()
  selectTemplate(store.active || store.templates[0])
})

watch(() => store.active, (template) => {
  if (!selectedId.value && template) selectTemplate(template)
})

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
  form.headerConfigJson = template.headerConfigJson || '{"showLogo":true,"showManualCode":true}'
  form.footerConfigJson = template.footerConfigJson || '{"showContact":true,"showPageNumber":true}'
  form.active = template.active
  applyDisplayConfig()
  logoVersion.value = Date.now()
}

function selectTemplate(template?: TemplateResponse | null) {
  if (!template) return
  selectedId.value = template.id
  fillForm(template)
  savedMessage.value = ''
  localError.value = ''
}

function newTemplate() {
  selectedId.value = null
  form.id = 0
  form.name = `Plantilla ${store.templates.length + 1}`
  form.description = ''
  form.companyName = 'DIKOIN'
  form.contactEmail = ''
  form.contactPhone = ''
  form.website = ''
  form.logoPath = ''
  form.logoAssetId = null
  form.logoUrl = ''
  form.headerConfigJson = '{"showLogo":true,"showManualCode":true}'
  form.footerConfigJson = '{"showContact":true,"showPageNumber":true}'
  form.active = false
  applyDisplayConfig()
  logoVersion.value = Date.now()
  savedMessage.value = ''
  localError.value = ''
}

function payload() {
  syncDisplayConfig()
  return {
    name: form.name,
    description: form.description,
    companyName: form.companyName,
    contactEmail: form.contactEmail,
    contactPhone: form.contactPhone,
    website: form.website,
    logoPath: form.logoPath,
    logoAssetId: form.logoAssetId,
    headerConfigJson: form.headerConfigJson,
    footerConfigJson: form.footerConfigJson,
    active: form.active,
  }
}

function parseConfig<T extends Record<string, boolean>>(value: string, fallback: T) {
  try {
    const parsed = JSON.parse(value || '{}')
    return { ...fallback, ...(parsed && typeof parsed === 'object' ? parsed : {}) }
  } catch {
    return { ...fallback }
  }
}

function applyDisplayConfig() {
  const headerConfig = parseConfig(form.headerConfigJson, headerDefaults)
  const footerConfig = parseConfig(form.footerConfigJson, footerDefaults)
  display.showLogo = headerConfig.showLogo
  display.showCompanyName = headerConfig.showCompanyName
  display.showManualCode = headerConfig.showManualCode
  display.showContact = footerConfig.showContact
  display.showWebsite = footerConfig.showWebsite
  display.showPageNumber = footerConfig.showPageNumber
}

function syncDisplayConfig() {
  form.headerConfigJson = JSON.stringify({
    showLogo: display.showLogo,
    showCompanyName: display.showCompanyName,
    showManualCode: display.showManualCode,
  })
  form.footerConfigJson = JSON.stringify({
    showContact: display.showContact,
    showWebsite: display.showWebsite,
    showPageNumber: display.showPageNumber,
  })
}

async function save() {
  saving.value = true
  savedMessage.value = ''
  localError.value = ''
  try {
    const result = form.id
      ? await store.saveTemplate(form.id, payload())
      : await store.addTemplate(payload())
    if (!form.id && result) selectedId.value = result.id
    const refreshed = store.templates.find((template) => template.id === (form.id || result?.id))
    selectTemplate(refreshed || result)
    savedMessage.value = 'Plantilla guardada.'
  } catch (err) {
    localError.value = err instanceof Error ? err.message : 'No se pudo guardar la plantilla'
  } finally {
    saving.value = false
  }
}

async function activate() {
  if (!form.id) return
  activating.value = true
  savedMessage.value = ''
  localError.value = ''
  try {
    const activated = await store.setActive(form.id)
    selectTemplate(activated)
    savedMessage.value = 'Plantilla activa aplicada a los manuales.'
  } catch (err) {
    localError.value = err instanceof Error ? err.message : 'No se pudo activar la plantilla'
  } finally {
    activating.value = false
  }
}

async function uploadLogo(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return

  if (!form.id) {
    await save()
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
    savedMessage.value = 'Logo subido. Revisa la previsualización y guarda si quieres mantener otros cambios.'
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
  savedMessage.value = 'Logo quitado de la previsualización. Guarda la plantilla para confirmar el cambio.'
}

function textFromEvent(event: Event) {
  return (event.target as HTMLElement).innerText
}
</script>

<template>
  <section class="templates-page">
    <div class="head">
      <div>
        <h1 class="page-title">Plantillas</h1>
        <p class="text-muted">Plantillas corporativas usadas para visualizar y exportar manuales.</p>
      </div>
      <button class="btn btn-primary" @click="newTemplate"><Plus :size="15" /> Nueva plantilla</button>
    </div>

    <BackendError :message="store.error || localError" />
    <div v-if="savedMessage" class="success-msg">{{ savedMessage }}</div>

    <div class="layout">
      <aside class="card template-list">
        <button
          v-for="template in store.templates"
          :key="template.id"
          type="button"
          :class="{ active: template.id === selectedId }"
          @click="selectTemplate(template)"
        >
          <span>
            <strong>{{ template.name }}</strong>
            <small>{{ template.companyName || 'Sin empresa' }}</small>
          </span>
          <em v-if="template.active">Activa</em>
        </button>
      </aside>

      <form class="card template-form" @submit.prevent="save">
        <div class="form-head">
          <h2>Datos de plantilla</h2>
          <button v-if="form.id && !form.active" type="button" class="btn btn-outline" :disabled="activating" @click="activate">
            <CheckCircle :size="14" /> {{ activating ? 'Activando...' : 'Activar' }}
          </button>
        </div>

        <label>Nombre plantilla <input v-model="form.name" class="field" required /></label>
        <label>Descripción <input v-model="form.description" class="field" /></label>
        <label>Empresa <input v-model="form.companyName" class="field" /></label>
        <div class="form-row">
          <label>Email <input v-model="form.contactEmail" class="field" /></label>
          <label>Teléfono <input v-model="form.contactPhone" class="field" /></label>
        </div>
        <label>Web <input v-model="form.website" class="field" /></label>

        <div class="logo-panel">
          <div class="logo-actions">
            <label class="logo-upload" :class="{ disabled: !canUploadLogo }">
              <input type="file" accept=".svg,image/svg+xml,image/*" :disabled="!canUploadLogo" @change="uploadLogo" />
              <ImageUp :size="14" /> {{ uploadingLogo ? 'Subiendo...' : 'Subir o reemplazar logo (SVG/PNG)' }}
            </label>
            <button type="button" class="btn btn-outline" :disabled="!logoSrc" @click="removeLogo">
              <Trash2 :size="14" /> Quitar logo
            </button>
          </div>
          <div v-if="uploadingLogo || uploadProgress" class="progress">
            <span :style="{ width: `${uploadProgress}%` }"></span>
          </div>
          <small v-if="form.logoPath" class="mono">{{ form.logoPath }}</small>
        </div>

        <section class="visibility-panel">
          <h3>Elementos visibles</h3>
          <div class="check-grid">
            <label class="check"><input v-model="display.showLogo" type="checkbox" /> Logo</label>
            <label class="check"><input v-model="display.showCompanyName" type="checkbox" /> Empresa</label>
            <label class="check"><input v-model="display.showManualCode" type="checkbox" /> Código manual</label>
            <label class="check"><input v-model="display.showContact" type="checkbox" /> Contacto</label>
            <label class="check"><input v-model="display.showWebsite" type="checkbox" /> Web</label>
            <label class="check"><input v-model="display.showPageNumber" type="checkbox" /> Paginación</label>
          </div>
        </section>

        <button class="btn btn-primary" :disabled="saving">
          <Save :size="14" /> {{ saving ? 'Guardando...' : 'Guardar plantilla' }}
        </button>
      </form>

      <div class="preview">
        <div class="paper">
          <header>
            <div v-if="display.showLogo" class="logo-box">
              <img v-if="logoSrc" :src="logoSrc" alt="Logo plantilla" />
              <span v-else>DK</span>
            </div>
            <strong v-if="display.showCompanyName" contenteditable suppress-contenteditable-warning @input="form.companyName = textFromEvent($event)">{{ form.companyName || 'DIKOIN' }}</strong>
            <span v-if="display.showManualCode">Manual técnico · Ref. DMP-XXXX</span>
          </header>
          <div class="line"></div>
          <h2>1. Título de sección</h2>
          <p>Vista previa aproximada de estilos corporativos. El contenido real se renderiza desde las secciones del manual.</p>
          <table>
            <thead><tr><th>Parte</th><th>Descripción</th></tr></thead>
            <tbody><tr><td>1</td><td>Elemento de ejemplo</td></tr></tbody>
          </table>
          <footer v-if="display.showContact || display.showWebsite || display.showPageNumber">
            <span v-if="display.showContact">{{ form.contactEmail || 'info@dikoin.com' }} · {{ form.contactPhone || '+34 000 000 000' }}</span>
            <span v-if="display.showWebsite">{{ form.website || 'www.dikoin.com' }}</span>
            <span v-if="display.showPageNumber">Página 1</span>
          </footer>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.templates-page { padding: 24px; display: grid; gap: 16px; }
.head, .form-head, .logo-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.layout { display: grid; grid-template-columns: 300px 390px minmax(0, 1fr); gap: 16px; align-items: start; }
.template-list { padding: 8px; display: grid; gap: 6px; }
.template-list button { border: 1px solid transparent; background: #fff; padding: 10px; display: flex; justify-content: space-between; gap: 10px; text-align: left; border-radius: var(--radius); }
.template-list button.active, .template-list button:hover { border-color: var(--dikoin-blue); background: var(--dikoin-blue-lighter); }
.template-list strong, .template-list small { display: block; }
.template-list small { color: var(--muted-foreground); margin-top: 3px; }
.template-list em { color: #065f46; font-style: normal; font-weight: 800; font-size: 11px; }
.template-form { padding: 16px; display: grid; gap: 12px; }
.template-form h2 { margin: 0; font-size: 16px; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
label { display: grid; gap: 6px; color: var(--muted-foreground); font-size: 12px; font-weight: 700; }
.logo-panel { display: grid; gap: 8px; }
.logo-upload { border: 1px dashed var(--border); background: var(--input-background); padding: 9px; display: inline-flex; align-items: center; justify-content: center; gap: 6px; color: var(--dikoin-blue); cursor: pointer; }
.logo-upload.disabled { opacity: .55; cursor: not-allowed; }
.logo-upload input { display: none; }
.progress { height: 7px; overflow: hidden; background: #e5e7eb; border-radius: 999px; }
.progress span { display: block; height: 100%; background: var(--dikoin-blue); transition: width .2s ease; }
.visibility-panel { border: 1px solid var(--border); padding: 12px; display: grid; gap: 10px; background: #f8fafc; }
.visibility-panel h3 { margin: 0; font-size: 13px; color: var(--foreground); }
.check-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.check { display: flex; align-items: center; gap: 8px; color: var(--foreground); font-size: 13px; font-weight: 600; }
.check input { width: 16px; height: 16px; accent-color: var(--dikoin-blue); }
.success-msg { background: var(--dikoin-green-light); color: #065f46; border: 1px solid #86efac; padding: 10px; border-radius: var(--radius); }
.preview { overflow: auto; }
.paper { width: min(210mm, 100%); min-height: 297mm; margin: 0 auto; padding: 14mm; background: #fff; border: 1px solid var(--border); box-shadow: 0 10px 30px rgba(0,0,0,.08); }
header { display: flex; align-items: flex-end; gap: 8px; }
header span { margin-left: auto; font-size: 11px; color: var(--muted-foreground); }
.logo-box { width: 34mm; height: 10mm; display: grid; place-items: center; background: transparent; color: var(--dikoin-blue); font-weight: 800; overflow: hidden; }
.logo-box:not(:has(img)) { width: 86px; height: 46px; background: var(--dikoin-blue); color: #fff; }
.logo-box img { width: 100%; height: 100%; object-fit: contain; object-position: left center; background: transparent; }
.line { height: 2px; background: var(--dikoin-blue); margin: 6px 0 22px; }
h2 { background: var(--dikoin-blue); color: #fff; padding: 7px 9px; font-size: 15px; }
table { width: 100%; border-collapse: collapse; font-size: 12px; }
th { background: var(--dikoin-blue); color: #fff; text-align: left; padding: 6px; }
td { border: 1px solid #b8cce3; padding: 6px; }
footer { margin-top: 28px; padding: 5px 8px; background: var(--dikoin-blue); color: #fff; font-size: 11px; display: flex; justify-content: space-between; gap: 12px; }
@media (max-width: 1180px) { .layout { grid-template-columns: 1fr; } .form-row { grid-template-columns: 1fr; } }
</style>
