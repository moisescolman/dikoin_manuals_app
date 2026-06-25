USE dikoin_manuales;

ALTER TABLE manual_sections
  ADD COLUMN IF NOT EXISTS visible BOOLEAN NOT NULL DEFAULT TRUE AFTER completion_status;

UPDATE manual_sections
SET completion_status = CASE UPPER(COALESCE(completion_status, ''))
  WHEN 'READY' THEN 'COMPLETED'
  WHEN 'PENDING' THEN 'DRAFT'
  WHEN 'IMPORTED' THEN 'DRAFT'
  WHEN 'REVIEWED' THEN 'REVIEW'
  WHEN 'DRAFT' THEN 'DRAFT'
  WHEN 'COMPLETED' THEN 'COMPLETED'
  WHEN 'REVIEW' THEN 'REVIEW'
  WHEN 'APPROVED' THEN 'APPROVED'
  ELSE 'DRAFT'
END;

CREATE INDEX IF NOT EXISTS idx_manual_sections_visible
  ON manual_sections(manual_version_id, visible, sort_order);
