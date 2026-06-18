# DIKOIN Manuals Backend

Backend Spring Boot para la gestion interna de manuales tecnicos DIKOIN.

## Requisitos

- Java 21
- Eclipse IDE for Enterprise Java and Web Developers
- Maven integrado en Eclipse o Maven local
- MariaDB en `localhost:3306`

## Base de datos local

El proyecto esta configurado para crear/actualizar tablas automaticamente con JPA:

```properties
spring.datasource.url=jdbc:mariadb://localhost:3306/dikoin_manuales?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8
spring.datasource.username=root
spring.datasource.password=1234
spring.jpa.hibernate.ddl-auto=update
```

Si el usuario `root` no puede crear bases de datos automaticamente, crea primero la base:

```sql
CREATE DATABASE IF NOT EXISTS dikoin_manuales
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

## Ejecutar en Eclipse

1. `File > Import > Maven > Existing Maven Projects`.
2. Seleccionar la carpeta `dikoin-manuals-backend`.
3. Esperar a que Maven descargue dependencias.
4. Ejecutar `ManualsApplication.java` como `Java Application` o `Spring Boot App`.

## Usuarios demo

Todas las contrasenas son `1234`.

| Rol | Email |
|---|---|
| ADMIN | admin@dikoin.local |
| TECNICO | tecnico@dikoin.local |
| REVISOR | revisor@dikoin.local |
| CLIENTE | cliente@dikoin.local |

Endpoint:

```http
POST http://localhost:8080/api/v1/auth/login
Content-Type: application/json

{
  "email": "tecnico@dikoin.local",
  "password": "1234"
}
```

## Almacenamiento local

El backend crea automaticamente esta estructura:

```text
storage/
  imports/
    docx/
    doc/
    odt/
    pdf/
  assets/
    manuals/
    templates/
    logos/
    extracted/
  exports/
    pdf/
  thumbnails/
  temp/
```

La ruta se puede cambiar en `application.properties`:

```properties
manuals.storage.base-path=./storage
```

Mas adelante se puede cambiar por una ruta del NAS.

## Endpoints principales

```text
POST /api/v1/auth/login
GET  /api/v1/dashboard
GET  /api/v1/products
POST /api/v1/products
GET  /api/v1/manuals
POST /api/v1/manuals
GET  /api/v1/manuals/{id}
PUT  /api/v1/manuals/{id}
PATCH /api/v1/manuals/{id}/enabled
DELETE /api/v1/manuals/{id}
PUT  /api/v1/manuals/{manualId}/versions
POST /api/v1/manuals/{manualId}/versions/{versionId}/publish
POST /api/v1/manuals/bulk/notices
GET  /api/v1/assets
POST /api/v1/assets
GET  /api/v1/assets/{id}/file
POST /api/v1/import-jobs/documents
POST /api/v1/import-jobs/pdf
POST /api/v1/export-jobs/manuals/{manualId}/pdf
GET  /api/v1/templates
GET  /api/v1/templates/active
POST /api/v1/templates/{id}/activate
POST /api/v1/templates/{id}/logo
GET  /api/v1/templates/{id}/versions
POST /api/v1/templates/{id}/versions
GET  /api/v1/notices
POST /api/v1/notices
PUT  /api/v1/notices/{id}
POST /api/v1/notices/apply
GET  /api/v1/search/suggestions?q=texto
```

## Migracion v2

El archivo `database/upgrade-v2-manuals.sql` contiene los ajustes para:

- Manuales bilingues y soft delete.
- Habilitar/deshabilitar manuales.
- Assets asociados a plantillas.
- Versiones de plantillas.
- Biblioteca de advertencias, alertas, notas y sugerencias.
- Aplicacion de avisos por manual, producto o seccion.

## Importacion

Formatos aceptados:

- `.docx`
- `.doc`
- `.odt`
- `.pdf`

El backend no requiere LibreOffice. La extraccion se hace con Apache Tika, Apache POI y PDFBox. La importacion de DOCX sera mas fiable que la de PDF o DOC antiguo.

La importacion crea un borrador editable, no una version publicada.

## Nota sobre PDF

El endpoint de exportacion genera un PDF basico real con Java/PDFBox. La maquetacion corporativa exacta debera evolucionar en una fase posterior, cuando se cierre el diseno visual final.
