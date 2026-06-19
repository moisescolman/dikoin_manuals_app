USE dikoin_manuales;

ALTER TABLE notice_templates
  ADD COLUMN IF NOT EXISTS visible_title_es VARCHAR(220) NULL AFTER title_en,
  ADD COLUMN IF NOT EXISTS visible_title_en VARCHAR(220) NULL AFTER visible_title_es;

UPDATE notice_templates
SET visible_title_es = 'Nota'
WHERE visible_title_es IS NULL
   OR TRIM(visible_title_es) = '';
