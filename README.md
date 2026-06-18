# DIKOIN Manuals App

Sistema web para gestionar manuales técnicos de DIKOIN. El proyecto está dividido en dos partes: un backend Spring Boot que expone la API y persiste los datos, y un frontend Vue 3 para la gestión visual de manuales, productos, plantillas, notas, bloques reutilizables y assets.

## Estructura

```text
Dikoin_manuals_app/
  backend/   API REST, lógica de negocio, importación/exportación y base de datos
  frontend/  Aplicación web Vue para usuarios internos y portal cliente
```

## Funcionalidades Principales

- Dashboard con estado general del sistema.
- Gestión de productos y manuales técnicos.
- Importación de documentos Word, ODT y PDF.
- Editor de manuales por secciones y bloques.
- Notas, advertencias y bloques comunes reutilizables.
- Plantillas corporativas con logo y configuración visual.
- Gestión de assets e historial de versiones.
- Portal cliente para consultar manuales publicados.

## Backend

Tecnologías principales:

- Java 21
- Spring Boot 3
- Spring Data JPA
- MariaDB
- Apache POI, Tika y PDFBox para importación/exportación documental

Configuración local principal:

```properties
server.port=8080
spring.datasource.url=jdbc:mariadb://localhost:3306/dikoin_manuales
spring.datasource.username=root
spring.datasource.password=1234
```

El backend se ejecuta desde Eclipse/Spring Boot y expone la API en:

```text
http://localhost:8080/api/v1
```

Los scripts de estructura y actualizaciones de base de datos están en:

```text
backend/database/
```

## Frontend

Tecnologías principales:

- Vue 3
- TypeScript
- Vite
- Pinia
- Vue Router
- Axios

Instalación y arranque:

```bash
cd frontend
pnpm install
pnpm dev
```

La aplicación queda disponible en:

```text
http://localhost:5173
```

La URL del backend se configura con:

```env
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

## Flujo De Trabajo Local

1. Levantar MariaDB.
2. Ejecutar el backend desde Eclipse o Spring Boot.
3. Arrancar el frontend con `pnpm dev`.
4. Entrar en `http://localhost:5173`.

## Scripts Útiles

Frontend:

```bash
pnpm dev      # servidor de desarrollo
pnpm build    # validación TypeScript y build de producción
pnpm preview  # previsualización del build
```

Backend:

```bash
mvn spring-boot:run
mvn test
```

En este entorno el backend se suele ejecutar desde Eclipse, por lo que cualquier cambio en backend puede requerir reiniciar la aplicación Spring.

## Estado Del Proyecto

El proyecto está en desarrollo activo. El objetivo es consolidar un flujo completo para crear, importar, editar, reutilizar contenido común, aplicar plantillas corporativas y publicar manuales técnicos para consulta interna o de cliente.
