# Dikoin Manuals Frontend

Frontend Vue 3 para el sistema de manuales técnicos de Dikoin.

## Requisitos

- Node.js 24.x o compatible
- pnpm 11.x
- Backend Spring Boot en `http://localhost:8080`

## Puesta en marcha

```bash
pnpm install
pnpm dev
```

Abrir `http://localhost:5173`.

Si pnpm informa de builds ignoradas para `esbuild` o `vue-demi`, este proyecto ya las deja aprobadas en `pnpm-workspace.yaml` mediante `allowBuilds`. Si se está reutilizando una instalación anterior, borrar `node_modules` y `pnpm-lock.yaml` antes de instalar de nuevo.

## Configuración

Crear un archivo `.env` si se necesita cambiar la URL del backend:

```env
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

## Credenciales de prueba

Estas credenciales las valida el backend:

- `admin@dikoin.local` / `1234`
- `tecnico@dikoin.local` / `1234`
- `revisor@dikoin.local` / `1234`
- `cliente@dikoin.local` / `1234`

## Funcionalidades preparadas

- Login conectado al backend.
- Dashboard con datos reales.
- Listado, búsqueda, vista previa, edición y exportación PDF de manuales.
- Editor por tarjetas de sección con bloques de texto, listas, tablas, imágenes, fórmulas, notas y advertencias.
- Importación de documentos Word/ODT/PDF.
- Gestión básica de productos.
- Gestión y subida de assets.
- Edición visual básica de plantilla corporativa.
- Vista de historial de versiones.
- Portal de cliente en modo solo lectura.
