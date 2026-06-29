-- Unifica el titulo interno de las notas reutilizables en una sola columna.
-- Conserva el primer valor disponible entre title existente, title_es y title_en.

ALTER TABLE notice_templates
  ADD COLUMN IF NOT EXISTS title VARCHAR(220) NULL AFTER type;

UPDATE notice_templates
SET title = COALESCE(NULLIF(title, ''), NULLIF(title_es, ''), NULLIF(title_en, ''), code)
WHERE title IS NULL OR title = '';

ALTER TABLE notice_templates
  MODIFY title VARCHAR(220) NOT NULL;

DROP INDEX IF EXISTS idx_notice_templates_title_es ON notice_templates;

ALTER TABLE notice_templates
  DROP COLUMN IF EXISTS title_es,
  DROP COLUMN IF EXISTS title_en;

CREATE INDEX IF NOT EXISTS idx_notice_templates_title ON notice_templates(title);
