-- Soft delete : les leads archivés restent en base pour la facturation
ALTER TABLE lead ADD COLUMN archive BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_lead_archive ON lead(tenant_id, archive);
