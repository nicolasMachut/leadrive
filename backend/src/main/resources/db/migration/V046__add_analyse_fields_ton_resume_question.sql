-- Ajout champs analyse conversation : ton prospect, resume vendeur, question en attente
-- Remplacement de resume_bref par resume_vendeur

ALTER TABLE conversation_analyse ADD COLUMN ton_prospect VARCHAR(20);
ALTER TABLE conversation_analyse ADD COLUMN resume_vendeur TEXT;
ALTER TABLE conversation_analyse ADD COLUMN question_en_attente TEXT;

-- Migration des donnees existantes de resume_bref vers resume_vendeur
UPDATE conversation_analyse SET resume_vendeur = resume_bref WHERE resume_vendeur IS NULL AND resume_bref IS NOT NULL;

-- Suppression de l'ancienne colonne
ALTER TABLE conversation_analyse DROP COLUMN IF EXISTS resume_bref;
