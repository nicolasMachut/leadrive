-- Sunset LEGACY définitif : tous les tenants sont en Self Sign-up (WABA dédiée par
-- établissement). Le discriminant onboarding_mode n'avait plus qu'une seule valeur
-- possible (SELF_SIGNUP) depuis le nettoyage V064 ; il ne sert plus à rien.
--
-- L'enum OnboardingModeEnum, le champ entité, le garde mort dans connecterMeta et le
-- champ "mode" du DTO ont été supprimés côté applicatif. On retire la colonne ici.

ALTER TABLE tenant_whatsapp_config DROP COLUMN IF EXISTS onboarding_mode;
