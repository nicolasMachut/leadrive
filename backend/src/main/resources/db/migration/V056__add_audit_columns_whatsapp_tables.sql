-- ============================================================================
-- V056 : ajout des colonnes d'audit (cree_le / modifie_le) manquantes sur les
--        tables de l'epic 23 (V052).
--
-- Contexte : les entites JPA TenantWhatsappNumberHistory, WhatsappAdminAction
-- et MessageModerationLog heritent toutes de BaseEntity, qui declare
-- cree_le (NOT NULL) et modifie_le (nullable). La migration V052 n'avait pas
-- aligne le schema avec cet heritage, ce qui faisait planter le 1er INSERT
-- en prod :
--   "column \"cree_le\" of relation \"tenant_whatsapp_number_history\" does not exist"
--
-- Strategie de migration :
--   - cree_le : NOT NULL avec DEFAULT NOW() pour ne pas casser les lignes
--     existantes (cas dev/staging). Hibernate l'ecrase a chaque INSERT,
--     le DEFAULT n'est qu'un filet de securite.
--   - modifie_le : nullable (cf BaseEntity), pas de DEFAULT.
--
-- Append-only : ces tables ne sont jamais UPDATE par le code applicatif
-- (sauf tenant_whatsapp_config qui avait deja les colonnes). Le @LastModifiedDate
-- ne tirera jamais sur ces lignes, mais la colonne doit exister pour que
-- Hibernate puisse construire le SQL INSERT (qui liste TOUTES les colonnes
-- mappees par BaseEntity).
-- ============================================================================

ALTER TABLE tenant_whatsapp_number_history
    ADD COLUMN IF NOT EXISTS cree_le    TIMESTAMP NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS modifie_le TIMESTAMP;

ALTER TABLE whatsapp_admin_actions
    ADD COLUMN IF NOT EXISTS modifie_le TIMESTAMP;

ALTER TABLE message_moderation_log
    ADD COLUMN IF NOT EXISTS modifie_le TIMESTAMP;
