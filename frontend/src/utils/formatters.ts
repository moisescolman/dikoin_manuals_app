import type { AssetType, BackendRole, ManualStatus } from '@/types/api'

export function statusLabel(status?: ManualStatus) {
  const map: Record<ManualStatus, string> = {
    DRAFT: 'Borrador',
    REVIEW: 'Revisión',
    APPROVED: 'Aprobado',
    PUBLISHED: 'Publicado',
    ARCHIVED: 'Archivado',
    DEACTIVATED: 'Dado de baja',
  }
  return status ? map[status] : 'Sin versión'
}

export function statusClass(status?: ManualStatus) {
  const map: Record<ManualStatus, string> = {
    DRAFT: 'status-draft',
    REVIEW: 'status-review',
    APPROVED: 'status-approved',
    PUBLISHED: 'status-published',
    ARCHIVED: 'status-archived',
    DEACTIVATED: 'status-deactivated',
  }
  return status ? map[status] : 'status-archived'
}

export function roleLabel(role?: BackendRole) {
  const map: Record<BackendRole, string> = {
    ADMIN: 'Administrador',
    TECNICO: 'Técnico',
    REVISOR: 'Revisor',
    CLIENTE: 'Cliente',
  }
  return role ? map[role] : ''
}

export function assetTypeLabel(type?: AssetType) {
  const map: Record<AssetType, string> = {
    IMAGE: 'Imagen',
    PRODUCT_IMAGE: 'Imagen producto',
    LOGO: 'Logo',
    DOCUMENT_SOURCE: 'Documento fuente',
    PDF_EXPORT: 'PDF exportado',
    EXTRACTED_IMAGE: 'Imagen extraída',
    TEMPLATE_RESOURCE: 'Recurso plantilla',
    OTHER: 'Otro',
  }
  return type ? map[type] : 'Todos'
}

export function formatDate(value?: string) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('es-ES', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

export function formatBytes(bytes?: number) {
  if (!bytes) return '-'
  const units = ['B', 'KB', 'MB', 'GB']
  let size = bytes
  let index = 0
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024
    index++
  }
  return `${size.toFixed(index === 0 ? 0 : 1)} ${units[index]}`
}
