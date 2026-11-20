-- Epic 21 : données supplémentaires (véhicule, prospect) en JSONB
ALTER TABLE lead ADD COLUMN donnees_supplementaires JSONB DEFAULT '{}';
CREATE INDEX idx_lead_donnees_supp ON lead USING GIN (donnees_supplementaires);

-- Texte brut collé par l'utilisateur (ou contenu email) pour référence
ALTER TABLE lead ADD COLUMN texte_source TEXT;

-- Mode d'import : SAISIE_MANUELLE, IMPORT_CSV, COLLER_TEXTE, EMAIL_AUTO, API
ALTER TABLE lead ADD COLUMN mode_import VARCHAR(30) DEFAULT 'IMPORT_CSV';
