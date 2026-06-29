USE dikoin_manuales;

START TRANSACTION;

-- La vista opcional anterior se sustituye por una tabla real.
DROP VIEW IF EXISTS reusable_fragments;

CREATE TABLE IF NOT EXISTS reusable_fragments (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(80) NOT NULL UNIQUE,
  title VARCHAR(220) NOT NULL,
  description VARCHAR(600) NULL,
  product_category VARCHAR(120) NULL,
  product_codes VARCHAR(600) NULL,
  content_json LONGTEXT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NULL,
  updated_at DATETIME NULL
);

INSERT INTO reusable_fragments (
  id,
  code,
  title,
  description,
  product_category,
  product_codes,
  content_json,
  active,
  created_at,
  updated_at
)
SELECT
  rb.id,
  rb.code,
  COALESCE(NULLIF(rb.title, ''), NULLIF(rb.title_es, ''), 'Fragmento'),
  COALESCE(NULLIF(rb.description, ''), NULLIF(rb.description_es, '')),
  rb.product_category,
  rb.product_codes,
  rb.content_json,
  rb.active,
  rb.created_at,
  rb.updated_at
FROM reusable_blocks rb
WHERE rb.reusable_type = 'FRAGMENT'
  AND NOT EXISTS (
    SELECT 1
    FROM reusable_fragments rf
    WHERE rf.id = rb.id
       OR upper(rf.code) = upper(rb.code)
  );

ALTER TABLE manual_blocks
  ADD COLUMN IF NOT EXISTS reusable_fragment_id BIGINT NULL AFTER reusable_block_id;

UPDATE manual_blocks mb
JOIN reusable_blocks rb ON rb.id = mb.reusable_block_id
SET mb.reusable_fragment_id = rb.id,
    mb.reusable_block_id = NULL
WHERE rb.reusable_type = 'FRAGMENT'
  AND mb.reusable_fragment_id IS NULL;

DELETE FROM reusable_blocks
WHERE reusable_type = 'FRAGMENT';

UPDATE reusable_blocks
SET reusable_type = 'SINGLE_BLOCK'
WHERE reusable_type IS NULL
   OR reusable_type <> 'SINGLE_BLOCK';

CREATE INDEX IF NOT EXISTS idx_reusable_fragments_active_updated
  ON reusable_fragments(active, updated_at);

CREATE INDEX IF NOT EXISTS idx_reusable_fragments_code
  ON reusable_fragments(code);

CREATE INDEX IF NOT EXISTS idx_reusable_fragments_category
  ON reusable_fragments(product_category);

CREATE INDEX IF NOT EXISTS idx_manual_blocks_reusable_fragment
  ON manual_blocks(reusable_fragment_id);

SET @fk_manual_blocks_reusable_fragment_exists = (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'manual_blocks'
    AND COLUMN_NAME = 'reusable_fragment_id'
    AND REFERENCED_TABLE_NAME = 'reusable_fragments'
);

SET @fk_manual_blocks_reusable_fragment_sql = IF(
  @fk_manual_blocks_reusable_fragment_exists = 0,
  'ALTER TABLE manual_blocks ADD CONSTRAINT fk_manual_blocks_reusable_fragment FOREIGN KEY (reusable_fragment_id) REFERENCES reusable_fragments(id)',
  'SELECT 1'
);

PREPARE fk_manual_blocks_reusable_fragment_stmt FROM @fk_manual_blocks_reusable_fragment_sql;
EXECUTE fk_manual_blocks_reusable_fragment_stmt;
DEALLOCATE PREPARE fk_manual_blocks_reusable_fragment_stmt;

COMMIT;

CREATE OR REPLACE VIEW reusable_sections AS
SELECT *
FROM reusable_blocks
WHERE reusable_type = 'SINGLE_BLOCK';
