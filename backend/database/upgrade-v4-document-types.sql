USE dikoin_manuales;

CREATE TABLE IF NOT EXISTS document_types (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(10) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(300) NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order INT NOT NULL DEFAULT 0,
  created_at DATETIME NULL,
  updated_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

ALTER TABLE document_types CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_uca1400_ai_ci;

INSERT INTO document_types (code, name, description, active, sort_order, created_at, updated_at)
SELECT 'DMP', 'Cuaderno de practicas', 'Documentacion de practicas de laboratorio', TRUE, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM document_types WHERE code COLLATE utf8mb4_uca1400_ai_ci = 'DMP' COLLATE utf8mb4_uca1400_ai_ci);

INSERT INTO document_types (code, name, description, active, sort_order, created_at, updated_at)
SELECT 'DMT', 'Tablas no resueltas', 'Tablas o anexos tecnicos pendientes de resolucion', TRUE, 2, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM document_types WHERE code COLLATE utf8mb4_uca1400_ai_ci = 'DMT' COLLATE utf8mb4_uca1400_ai_ci);

INSERT INTO document_types (code, name, description, active, sort_order, created_at, updated_at)
SELECT 'DMS', 'Manual de software', 'Manual de uso de software asociado al equipo', TRUE, 3, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM document_types WHERE code COLLATE utf8mb4_uca1400_ai_ci = 'DMS' COLLATE utf8mb4_uca1400_ai_ci);

INSERT INTO document_types (code, name, description, active, sort_order, created_at, updated_at)
SELECT 'DMM', 'Manual de montaje', 'Instrucciones de montaje del equipo', TRUE, 4, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM document_types WHERE code COLLATE utf8mb4_uca1400_ai_ci = 'DMM' COLLATE utf8mb4_uca1400_ai_ci);

INSERT INTO document_types (code, name, description, active, sort_order, created_at, updated_at)
SELECT 'DMC', 'Manual de pruebas', 'Manual de pruebas y comprobaciones', TRUE, 5, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM document_types WHERE code COLLATE utf8mb4_uca1400_ai_ci = 'DMC' COLLATE utf8mb4_uca1400_ai_ci);

ALTER TABLE manuals
  ADD COLUMN IF NOT EXISTS document_type_id BIGINT NULL AFTER category,
  ADD COLUMN IF NOT EXISTS document_year VARCHAR(2) COLLATE utf8mb4_uca1400_ai_ci NULL AFTER document_type_id,
  ADD COLUMN IF NOT EXISTS document_version VARCHAR(2) COLLATE utf8mb4_uca1400_ai_ci NULL AFTER document_year,
  ADD COLUMN IF NOT EXISTS language_code VARCHAR(10) COLLATE utf8mb4_uca1400_ai_ci NULL AFTER document_version;

CREATE INDEX IF NOT EXISTS idx_manuals_document_type ON manuals(document_type_id);
CREATE INDEX IF NOT EXISTS idx_manuals_document_fields ON manuals(document_year, document_version, language_code);

SET @fk_manuals_document_type_exists := (
  SELECT COUNT(*)
  FROM information_schema.referential_constraints
  WHERE constraint_schema = DATABASE()
    AND constraint_name = 'fk_manuals_document_type'
);

SET @fk_manuals_document_type_sql := IF(
  @fk_manuals_document_type_exists = 0,
  'ALTER TABLE manuals ADD CONSTRAINT fk_manuals_document_type FOREIGN KEY (document_type_id) REFERENCES document_types(id)',
  'SELECT ''fk_manuals_document_type already exists'' AS info'
);

PREPARE fk_manuals_document_type_stmt FROM @fk_manuals_document_type_sql;
EXECUTE fk_manuals_document_type_stmt;
DEALLOCATE PREPARE fk_manuals_document_type_stmt;

UPDATE manuals m
JOIN document_types dt
  ON dt.code COLLATE utf8mb4_uca1400_ai_ci = SUBSTRING_INDEX(m.code, '-', 1) COLLATE utf8mb4_uca1400_ai_ci
SET m.document_type_id = dt.id
WHERE m.document_type_id IS NULL
  AND m.code COLLATE utf8mb4_uca1400_ai_ci REGEXP '^(DMP|DMT|DMS|DMM|DMC)-';

UPDATE manuals
SET language_code = SUBSTRING_INDEX(SUBSTRING_INDEX(code, '[', -1), ']', 1)
WHERE language_code IS NULL
  AND code COLLATE utf8mb4_uca1400_ai_ci LIKE '%[%]';

UPDATE manuals
SET language_code = 'ES'
WHERE language_code IS NULL
  AND code COLLATE utf8mb4_uca1400_ai_ci REGEXP '^(DMP|DMT|DMS|DMM|DMC)-';

UPDATE manuals
SET document_year = SUBSTRING(SUBSTRING_INDEX(SUBSTRING_INDEX(code, '[', 1), '-', -1), 1, 2),
    document_version = SUBSTRING(SUBSTRING_INDEX(SUBSTRING_INDEX(code, '[', 1), '-', -1), 3, 2)
WHERE (document_year IS NULL OR document_version IS NULL)
  AND SUBSTRING_INDEX(SUBSTRING_INDEX(code, '[', 1), '-', -1) COLLATE utf8mb4_uca1400_ai_ci REGEXP '^[0-9]{4}$';
