-- Cache de la fiche de preparation IA sur le lead.
-- Evite de regenerer si les donnees n'ont pas change.
ALTER TABLE lead ADD COLUMN fiche_preparation TEXT;
ALTER TABLE lead ADD COLUMN fiche_generee_le TIMESTAMP;
ALTER TABLE lead ADD COLUMN fiche_hash VARCHAR(64);
