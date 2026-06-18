<script setup lang="ts">
import { computed, onMounted, reactive, watch } from 'vue'
import { ImageUp, Save } from '@lucide/vue'
import BackendError from '@/components/shared/BackendError.vue'
import { useAssetsStore } from '@/stores/assets.store'
import { useTemplatesStore } from '@/stores/templates.store'

const store = useTemplatesStore()
const assets = useAssetsStore()
const form = reactive({
  id: 0,
  name: '',
  companyName: '',
  contactEmail: '',
  contactPhone: '',
  website: '',
  logoPath: '',
  headerConfigJson: '',
  footerConfigJson: '',
  active: true,
})

const activeTemplate = computed(() => store.active)
const logoSrc = computed(() => {
  if (!form.logoPath) return ''
  if (form.logoPath.startsWith('http') || form.logoPath.startsWith('/api/')) return form.logoPath
  return ''
})

onMounted(() => store.fetchTemplates())

watch(activeTemplate, (template) => {
  if (!template) return
  form.id = template.id
  form.name = template.name
  form.companyName = template.companyName || ''
  form.contactEmail = template.contactEmail || ''
  form.contactPhone = template.contactPhone || ''
  form.website = template.website || ''
  form.logoPath = template.logoPath || ''
  form.headerConfigJson = template.headerConfigJson || '{"showLogo":true,"showManualCode":true}'
  form.footerConfigJson = template.footerConfigJson || '{"showContact":true,"showPageNumber":true}'
  form.active = template.active
}, { immediate: true })

function loadTemplateById(id: string) {
  const template = store.templates.find((item) => item.id === Number(id))
  if (!template) return
  form.id = template.id
  form.name = template.name
  form.companyName = template.companyName || ''
  form.contactEmail = template.contactEmail || ''
  form.contactPhone = template.contactPhone || ''
  form.website = template.website || ''
  form.logoPath = template.logoPath || ''
  form.headerConfigJson = template.headerConfigJson || '{"showLogo":true,"showManualCode":true}'
  form.footerConfigJson = template.footerConfigJson || '{"showContact":true,"showPageNumber":true}'
  form.active = template.active
}

async function save() {
  if (!form.id) return
  await store.saveTemplate(form.id, { ...form })
}

async function uploadLogo(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  const asset = await assets.upload(file, 'LOGO')
  form.logoPath = asset.storagePath
}

function textFromEvent(event: Event) {
  return (event.target as HTMLElement).innerText
}
</script>

<template>
  <section class="templates-page">
    <div>
      <h1 class="page-title">Plantilla corporativa</h1>
      <p class="text-muted">Encabezado, pie, logo y datos estáticos utilizados al visualizar y exportar manuales.</p>
    </div>

    <BackendError :message="store.error" />
    <BackendError :message="assets.error" />

    <div class="grid">
      <form class="card template-form" @submit.prevent="save">
        <h2>Datos editables</h2>
        <label>Seleccionar plantilla
          <select class="field" :value="form.id" @change="loadTemplateById(($event.target as HTMLSelectElement).value)">
            <option v-for="template in store.templates" :key="template.id" :value="template.id">
              {{ template.name }} {{ template.active ? '(activa)' : '' }}
            </option>
          </select>
        </label>
        <label>Nombre plantilla <input v-model="form.name" class="field" required /></label>
        <label>Empresa <input v-model="form.companyName" class="field" /></label>
        <label>Email <input v-model="form.contactEmail" class="field" /></label>
        <label>Teléfono <input v-model="form.contactPhone" class="field" /></label>
        <label>Web <input v-model="form.website" class="field" /></label>
        <label>Logo
          <span class="logo-upload">
            <input type="file" accept="image/*" @change="uploadLogo" />
            <ImageUp :size="14" /> Subir o reemplazar logo
          </span>
        </label>
        <label>Ruta logo <input v-model="form.logoPath" class="field mono" /></label>
        <details class="advanced">
          <summary>Configuración avanzada JSON</summary>
          <label>Header JSON <textarea v-model="form.headerConfigJson" class="field mono" rows="4" /></label>
          <label>Footer JSON <textarea v-model="form.footerConfigJson" class="field mono" rows="4" /></label>
        </details>
        <button class="btn btn-primary"><Save :size="14" /> Guardar plantilla</button>
      </form>

      <div class="preview">
        <div class="paper">
          <header>
            <div class="logo-box">
              <img v-if="logoSrc" :src="logoSrc" alt="Logo plantilla" />
              <span v-else>{{ form.logoPath ? 'LOGO' : 'DK' }}</span>
            </div>
            <strong contenteditable suppress-contenteditable-warning @input="form.companyName = textFromEvent($event)">{{ form.companyName || 'DIKOIN' }}</strong>
            <span contenteditable suppress-contenteditable-warning>Manual técnico · Ref. DMP-XXXX</span>
          </header>
          <div class="line"></div>
          <h2>1. Título de sección</h2>
          <p>Vista previa aproximada de estilos corporativos. El contenido real se renderiza desde las secciones del manual.</p>
          <table>
            <thead><tr><th>Parte</th><th>Descripción</th></tr></thead>
            <tbody><tr><td>1</td><td>Elemento de ejemplo</td></tr></tbody>
          </table>
          <footer contenteditable suppress-contenteditable-warning @input="form.footerConfigJson = JSON.stringify({ text: textFromEvent($event) })">{{ form.contactEmail }} · {{ form.contactPhone }} · {{ form.website }}</footer>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.templates-page { padding: 24px; display: grid; gap: 16px; }
.grid { display: grid; grid-template-columns: 380px minmax(0, 1fr); gap: 16px; }
.template-form { padding: 16px; display: grid; gap: 12px; }
.template-form h2 { margin: 0; font-size: 16px; }
label { display: grid; gap: 6px; color: var(--muted-foreground); font-size: 12px; font-weight: 700; }
.logo-upload { border: 1px dashed var(--border); background: var(--input-background); padding: 9px; display: inline-flex; align-items: center; justify-content: center; gap: 6px; color: var(--dikoin-blue); cursor: pointer; }
.logo-upload input { display: none; }
.advanced { border: 1px solid var(--border); padding: 10px; }
.advanced summary { color: var(--dikoin-blue); cursor: pointer; margin-bottom: 10px; }
.preview { overflow: auto; }
.paper { width: min(210mm, 100%); min-height: 297mm; margin: 0 auto; padding: 14mm; background: #fff; border: 1px solid var(--border); box-shadow: 0 10px 30px rgba(0,0,0,.08); }
header { display: flex; align-items: flex-end; gap: 8px; }
header span { margin-left: auto; font-size: 11px; color: var(--muted-foreground); }
.logo-box { width: 70px; height: 38px; display: grid; place-items: center; background: var(--dikoin-blue); color: #fff; font-weight: 800; overflow: hidden; }
.logo-box img { width: 100%; height: 100%; object-fit: contain; background: #fff; }
.line { height: 2px; background: var(--dikoin-blue); margin: 6px 0 22px; }
h2 { background: var(--dikoin-blue); color: #fff; padding: 7px 9px; font-size: 15px; }
table { width: 100%; border-collapse: collapse; font-size: 12px; }
th { background: var(--dikoin-blue); color: #fff; text-align: left; padding: 6px; }
td { border: 1px solid #b8cce3; padding: 6px; }
footer { margin-top: 28px; padding: 5px 8px; background: var(--dikoin-blue); color: #fff; font-size: 11px; }
@media (max-width: 980px) { .grid { grid-template-columns: 1fr; } }
</style>
