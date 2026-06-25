USE dikoin_manuales;

START TRANSACTION;

-- La tabla base se mantiene para no romper la FK manual_blocks.reusable_block_id.
-- reusable_type separa las bibliotecas: SINGLE_BLOCK = sección, FRAGMENT = fragmento.
ALTER TABLE reusable_blocks
  ADD COLUMN IF NOT EXISTS title_es VARCHAR(220) NULL AFTER title,
  ADD COLUMN IF NOT EXISTS title_en VARCHAR(220) NULL AFTER title_es,
  ADD COLUMN IF NOT EXISTS description_es VARCHAR(600) NULL AFTER description,
  ADD COLUMN IF NOT EXISTS description_en VARCHAR(600) NULL AFTER description_es;

UPDATE reusable_blocks
SET title_es = COALESCE(NULLIF(title_es, ''), title),
    description_es = COALESCE(NULLIF(description_es, ''), description)
WHERE title_es IS NULL
   OR title_es = ''
   OR (description_es IS NULL AND description IS NOT NULL);

UPDATE reusable_blocks
SET reusable_type = 'SINGLE_BLOCK'
WHERE reusable_type IS NULL
   OR reusable_type NOT IN ('SINGLE_BLOCK', 'FRAGMENT', 'NOTE');

CREATE INDEX IF NOT EXISTS idx_reusable_blocks_library
  ON reusable_blocks(reusable_type, active, updated_at);

COMMIT;

-- Vistas de lectura opcionales para administración/BI.
CREATE OR REPLACE VIEW reusable_sections AS
SELECT *
FROM reusable_blocks
WHERE reusable_type = 'SINGLE_BLOCK';

CREATE OR REPLACE VIEW reusable_fragments AS
SELECT *
FROM reusable_blocks
WHERE reusable_type = 'FRAGMENT';
