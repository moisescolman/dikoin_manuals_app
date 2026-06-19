USE dikoin_manuales;

UPDATE manual_versions mv
JOIN manuals m ON m.id = mv.manual_id
SET mv.es_ready = TRUE
WHERE mv.active = TRUE
  AND mv.version_number = '0.1'
  AND mv.change_notes = 'Version inicial'
  AND UPPER(m.language_code) = 'ES';

UPDATE manual_versions mv
JOIN manuals m ON m.id = mv.manual_id
SET mv.en_ready = TRUE
WHERE mv.active = TRUE
  AND mv.version_number = '0.1'
  AND mv.change_notes = 'Version inicial'
  AND UPPER(m.language_code) = 'EN';
