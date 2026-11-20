-- Ajout des champs d'onboarding sur la table utilisateur
ALTER TABLE utilisateur ADD COLUMN onboarding_termine BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE utilisateur ADD COLUMN onboarding_etape INTEGER;
