import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth.store'
import LoginView from '@/views/LoginView.vue'
import AppLayout from '@/components/layout/AppLayout.vue'
import DashboardView from '@/views/DashboardView.vue'
import ManualListView from '@/views/ManualListView.vue'
import ManualDetailView from '@/views/ManualDetailView.vue'
import ManualPrintView from '@/views/ManualPrintView.vue'
import ManualCreateView from '@/views/ManualCreateView.vue'
import ManualEditorView from '@/views/ManualEditorView.vue'
import ImportManualView from '@/views/ImportManualView.vue'
import ProductsView from '@/views/ProductsView.vue'
import AssetsView from '@/views/AssetsView.vue'
import NotesView from '@/views/NotesView.vue'
import ReusableBlocksView from '@/views/ReusableBlocksView.vue'
import ReusableFragmentsView from '@/views/ReusableFragmentsView.vue'
import ReusableContentEditorView from '@/views/ReusableContentEditorView.vue'
import TemplatesView from '@/views/TemplatesView.vue'
import HistoryView from '@/views/HistoryView.vue'
import ClientPortalView from '@/views/ClientPortalView.vue'
import ConfigView from '@/views/ConfigView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView },
    { path: '/manuales/:id/print', name: 'manual-print', component: ManualPrintView, props: true },
    {
      path: '/',
      component: AppLayout,
      meta: { requiresAuth: true },
      children: [
        { path: '', name: 'dashboard', component: DashboardView },
        { path: 'manuales', name: 'manuals', component: ManualListView },
        { path: 'manuales/nuevo', name: 'manual-create', component: ManualCreateView },
        { path: 'manuales/:id', name: 'manual-detail', component: ManualDetailView, props: true },
        { path: 'manuales/:id/editor', name: 'manual-editor', component: ManualEditorView, props: true },
        { path: 'importar', name: 'import', component: ImportManualView },
        { path: 'productos', name: 'products', component: ProductsView },
        { path: 'notas', name: 'notes', component: NotesView },
        { path: 'secciones', alias: '/bloques', name: 'reusable-blocks', component: ReusableBlocksView },
        { path: 'secciones/:id/editor', name: 'reusable-section-editor', component: ReusableContentEditorView, props: (route) => ({ id: route.params.id, kind: 'SINGLE_BLOCK' }) },
        { path: 'fragmentos', name: 'reusable-fragments', component: ReusableFragmentsView },
        { path: 'fragmentos/:id/editor', name: 'reusable-fragment-editor', component: ReusableContentEditorView, props: (route) => ({ id: route.params.id, kind: 'FRAGMENT' }) },
        { path: 'assets', name: 'assets', component: AssetsView },
        { path: 'plantillas', name: 'templates', component: TemplatesView },
        { path: 'historial/:id?', name: 'history', component: HistoryView, props: true },
        { path: 'portal', name: 'portal', component: ClientPortalView },
        { path: 'configuracion', name: 'config', component: ConfigView },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login' }
  }
  if (to.name === 'login' && auth.isAuthenticated) {
    return auth.isClient ? { name: 'portal' } : { name: 'dashboard' }
  }
  if (auth.isClient && to.name !== 'portal' && to.name !== 'login') {
    return { name: 'portal' }
  }
  return true
})

export default router
