USE dikoin_manuales;

ALTER TABLE assets
  ADD COLUMN IF NOT EXISTS thumbnail_path VARCHAR(700) NULL AFTER storage_path;
