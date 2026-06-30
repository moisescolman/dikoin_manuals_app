USE dikoin_manuales;

ALTER TABLE manual_sections
  ADD COLUMN IF NOT EXISTS linked_reusable_section_id BIGINT NULL AFTER completion_status;

CREATE INDEX IF NOT EXISTS idx_manual_sections_linked_reusable_section
  ON manual_sections(linked_reusable_section_id);
