USE dikoin_manuales;

START TRANSACTION;

ALTER TABLE manual_sections
  ADD COLUMN IF NOT EXISTS parent_section_id BIGINT NULL AFTER section_number,
  ADD COLUMN IF NOT EXISTS level INT NOT NULL DEFAULT 1 AFTER parent_section_id;

SET @constraint_exists = (
  SELECT COUNT(*)
  FROM information_schema.KEY_COLUMN_USAGE
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'manual_sections'
    AND COLUMN_NAME = 'parent_section_id'
    AND REFERENCED_TABLE_NAME = 'manual_sections'
);
SET @sql = IF(
  @constraint_exists = 0,
  'ALTER TABLE manual_sections ADD CONSTRAINT fk_manual_sections_parent FOREIGN KEY (parent_section_id) REFERENCES manual_sections(id)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE manual_blocks
  ADD COLUMN IF NOT EXISTS plain_text LONGTEXT NULL AFTER content_json,
  ADD COLUMN IF NOT EXISTS asset_id BIGINT NULL AFTER plain_text,
  ADD COLUMN IF NOT EXISTS reusable_block_id BIGINT NULL AFTER asset_id;

SET @constraint_exists = (
  SELECT COUNT(*)
  FROM information_schema.KEY_COLUMN_USAGE
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'manual_blocks'
    AND COLUMN_NAME = 'asset_id'
    AND REFERENCED_TABLE_NAME = 'assets'
);
SET @sql = IF(
  @constraint_exists = 0,
  'ALTER TABLE manual_blocks ADD CONSTRAINT fk_manual_blocks_asset FOREIGN KEY (asset_id) REFERENCES assets(id)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @constraint_exists = (
  SELECT COUNT(*)
  FROM information_schema.KEY_COLUMN_USAGE
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'manual_blocks'
    AND COLUMN_NAME = 'reusable_block_id'
    AND REFERENCED_TABLE_NAME = 'reusable_blocks'
);
SET @sql = IF(
  @constraint_exists = 0,
  'ALTER TABLE manual_blocks ADD CONSTRAINT fk_manual_blocks_reusable FOREIGN KEY (reusable_block_id) REFERENCES reusable_blocks(id)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE reusable_blocks
  ADD COLUMN IF NOT EXISTS description VARCHAR(600) NULL AFTER title,
  ADD COLUMN IF NOT EXISTS reusable_type VARCHAR(30) NOT NULL DEFAULT 'SINGLE_BLOCK' AFTER description;

CREATE INDEX IF NOT EXISTS idx_manual_sections_version_sort
  ON manual_sections(manual_version_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_manual_sections_parent_sort
  ON manual_sections(parent_section_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_manual_blocks_section_sort
  ON manual_blocks(section_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_manual_blocks_type
  ON manual_blocks(block_type);
CREATE INDEX IF NOT EXISTS idx_manual_blocks_asset
  ON manual_blocks(asset_id);
CREATE INDEX IF NOT EXISTS idx_manual_blocks_reusable
  ON manual_blocks(reusable_block_id);
CREATE INDEX IF NOT EXISTS idx_reusable_blocks_type_active
  ON reusable_blocks(reusable_type, active);

UPDATE manual_sections
SET level = 1
WHERE level IS NULL OR level < 1;

COMMIT;
