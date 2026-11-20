-- V064 : Sunset du mode LEGACY WhatsApp (epic 23, 2026-Q2)
-- Le mode OTP manuel (numéro partagé avec vérification par code SMS) est officiellement
-- abandonné. Seul le mode SELF_SIGNUP (Facebook Embedded Signup, epic 25) est maintenu.
--
-- Effets :
--   1. Tout tenant encore en mode LEGACY ou avec un statut résiduel OTP
--      (EN_ATTENTE_NUMERO, CODE_ENVOYE, NUMERO_VERIFIE, EN_VALIDATION_META)
--      est basculé en statut=DESACTIVE / onboarding_mode=SELF_SIGNUP.
--      Le tenant LEGACY existant en prod sera ainsi redirigé vers le flow
--      self-signup côté UI lors de sa prochaine connexion (bannière "Activer").
--   2. La valeur DEFAULT SQL de onboarding_mode passe de 'LEGACY' à 'SELF_SIGNUP'
--      et celle de statut passe de 'EN_ATTENTE_NUMERO' à 'DESACTIVE'
--      pour que toute nouvelle ligne créée soit d'emblée alignée avec l'enum Java.
--   3. La contrainte CHECK chk_actif_avec_numero, qui référençait l'ancienne valeur
--      EN_VALIDATION_META, est recréée pour ne plus citer que les valeurs vivantes.
--   4. Les colonnes mortes (tentatives_code, bloque_jusqua) liées au flow OTP sont
--      droppées : plus aucun code Java ne les manipule.
--
-- Note : la table tenant_whatsapp_config n'a pas de colonne "est_actif" — l'état
--        binaire actif/inactif est porté uniquement par la colonne statut.
--
-- Non réversible : Flyway n'autorise pas le rollback d'une migration appliquée.

-- 1. Désactiver les configs en mode LEGACY ou avec statut résiduel OTP
UPDATE tenant_whatsapp_config
SET
    statut          = 'DESACTIVE',
    onboarding_mode = 'SELF_SIGNUP',
    modifie_le      = NOW()
WHERE
    onboarding_mode = 'LEGACY'
    OR statut IN ('EN_ATTENTE_NUMERO', 'CODE_ENVOYE', 'NUMERO_VERIFIE', 'EN_VALIDATION_META');

-- 2. Aligner les DEFAULT SQL avec l'enum Java après ménage
ALTER TABLE tenant_whatsapp_config
    ALTER COLUMN onboarding_mode SET DEFAULT 'SELF_SIGNUP';

ALTER TABLE tenant_whatsapp_config
    ALTER COLUMN statut SET DEFAULT 'DESACTIVE';

-- 3. Recréer la contrainte CHECK sans référence à l'ancien EN_VALIDATION_META
ALTER TABLE tenant_whatsapp_config
    DROP CONSTRAINT IF EXISTS chk_actif_avec_numero;

ALTER TABLE tenant_whatsapp_config
    ADD CONSTRAINT chk_actif_avec_numero
    CHECK (statut <> 'ACTIF' OR whatsapp_from IS NOT NULL);

-- 4. Dropper les colonnes liées au flow OTP, plus jamais utilisées par le code Java
ALTER TABLE tenant_whatsapp_config DROP COLUMN IF EXISTS tentatives_code;
ALTER TABLE tenant_whatsapp_config DROP COLUMN IF EXISTS bloque_jusqua;
