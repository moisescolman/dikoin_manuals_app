USE dikoin_manuales;

ALTER TABLE manuals
  ADD COLUMN IF NOT EXISTS title_es VARCHAR(220) NULL AFTER title,
  ADD COLUMN IF NOT EXISTS title_en VARCHAR(220) NULL AFTER title_es,
  ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT TRUE AFTER category,
  ADD COLUMN IF NOT EXISTS deleted_at DATETIME NULL AFTER updated_at;

UPDATE manuals
SET title_es = COALESCE(title_es, title)
WHERE title_es IS NULL;

CREATE INDEX IF NOT EXISTS idx_manuals_enabled_deleted ON manuals(enabled, deleted_at);
CREATE INDEX IF NOT EXISTS idx_manuals_title_es ON manuals(title_es);
CREATE INDEX IF NOT EXISTS idx_manuals_title_en ON manuals(title_en);

ALTER TABLE templates
  ADD COLUMN IF NOT EXISTS description VARCHAR(300) NULL AFTER name,
  ADD COLUMN IF NOT EXISTS logo_asset_id BIGINT NULL AFTER logo_path;

ALTER TABLE templates
  ADD CONSTRAINT fk_templates_logo_asset
  FOREIGN KEY (logo_asset_id) REFERENCES assets(id);

CREATE TABLE IF NOT EXISTS template_assets (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  template_id BIGINT NOT NULL,
  asset_id BIGINT NOT NULL,
  usage_type VARCHAR(40) NOT NULL,
  sort_order INT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_template_assets_template FOREIGN KEY (template_id) REFERENCES templates(id),
  CONSTRAINT fk_template_assets_asset FOREIGN KEY (asset_id) REFERENCES assets(id)
);

CREATE INDEX IF NOT EXISTS idx_template_assets_template ON template_assets(template_id);
CREATE INDEX IF NOT EXISTS idx_template_assets_asset ON template_assets(asset_id);

ALTER TABLE template_versions
  ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT FALSE AFTER config_json,
  ADD COLUMN IF NOT EXISTS notes VARCHAR(600) NULL AFTER active;

CREATE INDEX IF NOT EXISTS idx_template_versions_template_active ON template_versions(template_id, active);

CREATE TABLE IF NOT EXISTS notice_templates (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(80) NOT NULL UNIQUE,
  type VARCHAR(30) NOT NULL,
  title_es VARCHAR(220) NOT NULL,
  title_en VARCHAR(220) NULL,
  content_es LONGTEXT NOT NULL,
  content_en LONGTEXT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NULL,
  CONSTRAINT chk_notice_templates_type CHECK (type IN ('WARNING', 'ALERT', 'NOTE', 'SUGGESTION'))
);

CREATE INDEX IF NOT EXISTS idx_notice_templates_type_active ON notice_templates(type, active);
CREATE INDEX IF NOT EXISTS idx_notice_templates_title_es ON notice_templates(title_es);

CREATE TABLE IF NOT EXISTS notice_applications (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  notice_template_id BIGINT NOT NULL,
  manual_id BIGINT NULL,
  product_id BIGINT NULL,
  section_id BIGINT NULL,
  apply_scope VARCHAR(30) NOT NULL,
  sort_order INT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_notice_applications_notice FOREIGN KEY (notice_template_id) REFERENCES notice_templates(id),
  CONSTRAINT fk_notice_applications_manual FOREIGN KEY (manual_id) REFERENCES manuals(id),
  CONSTRAINT fk_notice_applications_product FOREIGN KEY (product_id) REFERENCES products(id),
  CONSTRAINT fk_notice_applications_section FOREIGN KEY (section_id) REFERENCES manual_sections(id),
  CONSTRAINT chk_notice_applications_scope CHECK (apply_scope IN ('MANUAL', 'PRODUCT', 'SECTION'))
);

CREATE INDEX IF NOT EXISTS idx_notice_applications_manual ON notice_applications(manual_id);
CREATE INDEX IF NOT EXISTS idx_notice_applications_product ON notice_applications(product_id);
CREATE INDEX IF NOT EXISTS idx_notice_applications_section ON notice_applications(section_id);

CREATE INDEX IF NOT EXISTS idx_products_code_name ON products(code, name);
CREATE INDEX IF NOT EXISTS idx_assets_type_filename ON assets(asset_type, original_filename);
