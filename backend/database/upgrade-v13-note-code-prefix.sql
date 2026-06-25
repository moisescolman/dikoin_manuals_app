USE dikoin_manuales;

START TRANSACTION;

-- Los nuevos códigos se generan como NOT-001, NOT-002, etc.
-- También normaliza los códigos históricos NOTA-### cuando no existe conflicto.
UPDATE notice_templates legacy
LEFT JOIN notice_templates current_code
  ON current_code.id <> legacy.id
 AND current_code.code = CONCAT('NOT-', SUBSTRING(legacy.code, 6))
SET legacy.code = CONCAT('NOT-', SUBSTRING(legacy.code, 6))
WHERE legacy.code REGEXP '^NOTA-[0-9]+$'
  AND current_code.id IS NULL;

COMMIT;
