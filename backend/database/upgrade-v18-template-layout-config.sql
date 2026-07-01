USE dikoin_manuales;

ALTER TABLE templates
  ADD COLUMN IF NOT EXISTS layout_config_json LONGTEXT NULL AFTER footer_config_json,
  ADD COLUMN IF NOT EXISTS system_default BIT(1) NOT NULL DEFAULT b'0' AFTER active;

UPDATE templates
SET
  system_default = b'1',
  layout_config_json = COALESCE(layout_config_json, '{"version":1}')
WHERE active = b'1'
  AND (
    LOWER(name) LIKE '%dikoin%'
    OR name = 'Plantilla corporativa DIKOIN'
  )
LIMIT 1;

