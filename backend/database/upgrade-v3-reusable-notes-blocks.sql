USE dikoin_manuales;

ALTER TABLE notice_templates
  ADD COLUMN IF NOT EXISTS product_category VARCHAR(120) NULL AFTER title_en,
  ADD COLUMN IF NOT EXISTS product_codes VARCHAR(600) NULL AFTER product_category;

CREATE INDEX IF NOT EXISTS idx_notice_templates_category ON notice_templates(product_category);

CREATE TABLE IF NOT EXISTS reusable_blocks (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(80) NOT NULL UNIQUE,
  title VARCHAR(220) NOT NULL,
  product_category VARCHAR(120) NULL,
  product_codes VARCHAR(600) NULL,
  content_json LONGTEXT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NULL
);

CREATE INDEX IF NOT EXISTS idx_reusable_blocks_active_updated ON reusable_blocks(active, updated_at);
CREATE INDEX IF NOT EXISTS idx_reusable_blocks_code ON reusable_blocks(code);
CREATE INDEX IF NOT EXISTS idx_reusable_blocks_category ON reusable_blocks(product_category);

CREATE INDEX IF NOT EXISTS idx_manual_blocks_content ON manual_blocks(content_json(255));
