-- Score d'engagement du lead, calcule a partir de l'analyse IA des conversations.
-- Distinct du `scoring` qui represente le scoring marketing initial venu du CSV import.
-- Recalcule a chaque analyse (intention RDV, signaux d'achat detectes, objections, handoff).
-- Range : 0.00 (froid) -> 1.00 (chaud).

ALTER TABLE lead
    ADD COLUMN hotness_score DECIMAL(3, 2),
    ADD COLUMN hotness_calcule_le TIMESTAMP;

CREATE INDEX idx_lead_hotness ON lead (tenant_id, hotness_score DESC NULLS LAST);

COMMENT ON COLUMN lead.hotness_score IS 'Score d engagement comportemental (0-1), MAJ par ConversationAnalyseService apres chaque message du prospect';
COMMENT ON COLUMN lead.hotness_calcule_le IS 'Timestamp du dernier recalcul du hotness_score';
