-- ============================================================================
-- Epic 25 — WhatsApp multi-WABA : chaque concession connecte son propre compte
-- ============================================================================
-- Contexte : l'epic 23 (WABA partagee LeadRive) bloque les display names car
-- Meta refuse les noms qui ne correspondent pas au Meta Business verifie.
-- Solution : chaque concession a son propre WABA + sub-account Twilio.
--
-- Migration non-bloquante : ADD COLUMN avec valeurs par defaut + index concurrents
-- pour eviter les locks sur la table active.
-- ============================================================================

-- ── Nouveaux champs sur tenant_whatsapp_config ──────────────────────────────
-- onboarding_mode discrimine :
--   LEGACY       = ancien mode WABA partagee (epic 23) — sunset 2026-Q3
--   SELF_SIGNUP  = nouveau mode WABA propre (epic 25)
ALTER TABLE tenant_whatsapp_config
    ADD COLUMN IF NOT EXISTS onboarding_mode VARCHAR(20) NOT NULL DEFAULT 'LEGACY';

ALTER TABLE tenant_whatsapp_config
    ADD COLUMN IF NOT EXISTS twilio_sub_account_sid VARCHAR(50);

-- Token Twilio sub-account, chiffre via EncryptedStringConverter (AES-256-GCM)
-- avant ecriture. Roadmap : envelope encryption avec KMS pour rotation du
-- master key sans re-chiffrer toute la table.
ALTER TABLE tenant_whatsapp_config
    ADD COLUMN IF NOT EXISTS twilio_sub_auth_token TEXT;

-- WhatsApp Business Account ID (Meta), par tenant
ALTER TABLE tenant_whatsapp_config
    ADD COLUMN IF NOT EXISTS waba_id VARCHAR(50);

-- Meta Business ID du tenant (ce qu'on lit depuis Facebook OAuth)
ALTER TABLE tenant_whatsapp_config
    ADD COLUMN IF NOT EXISTS meta_business_id VARCHAR(50);

-- Idempotency key pour la creation du sub-account Twilio.
-- Permet de retry-then-lookup au lieu de creer un sub orphelin si la
-- creation Twilio reussit mais la sauvegarde BDD timeout.
ALTER TABLE tenant_whatsapp_config
    ADD COLUMN IF NOT EXISTS sub_account_idempotency_key VARCHAR(80);

CREATE UNIQUE INDEX IF NOT EXISTS uq_twc_idempotency_key
    ON tenant_whatsapp_config(sub_account_idempotency_key)
    WHERE sub_account_idempotency_key IS NOT NULL;

-- ── Table des templates WhatsApp par tenant ─────────────────────────────────
-- Chaque WABA a ses propres templates (Meta exige que chaque WABA soumette
-- ses templates). La source de verite reste les fichiers YAML versionnes
-- dans resources/whatsapp-templates/. Cette table garde uniquement le SID
-- Twilio + statut Meta pour chaque (tenant, template_name).
CREATE TABLE IF NOT EXISTS tenant_whatsapp_template (
    id_tenant_whatsapp_template BIGSERIAL PRIMARY KEY,
    tenant_id          BIGINT NOT NULL REFERENCES tenant(id_tenant),
    template_name      VARCHAR(100) NOT NULL,
    -- Statut local de soumission :
    --   EN_ATTENTE_SOUMISSION (vient d'etre cree, pas encore push Twilio)
    --   SOUMIS    (push fait, en attente du polling Meta)
    --   APPROUVE  (Meta a valide)
    --   REJETE    (Meta a refuse, voir meta_reject_reason)
    --   DESACTIVE (template retire manuellement)
    statut             VARCHAR(30) NOT NULL DEFAULT 'EN_ATTENTE_SOUMISSION',
    -- SID retourne par Twilio Content API (HXxxxx...). Null tant que pas soumis.
    template_sid       VARCHAR(50),
    -- Hash du contenu YAML soumis. Permet de detecter quand le template YAML
    -- a evolue cote source de verite et reglementer la re-soumission.
    yaml_content_hash  VARCHAR(64),
    -- Date de soumission a Twilio
    soumis_le          TIMESTAMP,
    -- Derniere reponse Meta (raison de rejet si applicable)
    meta_reject_reason TEXT,
    -- Tentatives de soumission (pour back-off exponentiel)
    nb_tentatives      INTEGER NOT NULL DEFAULT 0,
    -- Date d'expiration du back-off : ne pas re-soumettre avant cette date
    prochaine_tentative_le TIMESTAMP,
    -- Date de derniere synchro Meta (polling statut)
    date_derniere_synchro  TIMESTAMP,
    cree_le            TIMESTAMP NOT NULL DEFAULT NOW(),
    modifie_le         TIMESTAMP NOT NULL DEFAULT NOW(),
    version            INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uq_template_tenant_name UNIQUE (tenant_id, template_name)
);

CREATE INDEX IF NOT EXISTS idx_twt_tenant_id
    ON tenant_whatsapp_template(tenant_id);

CREATE INDEX IF NOT EXISTS idx_twt_statut
    ON tenant_whatsapp_template(statut)
    WHERE statut IN ('SOUMIS', 'EN_ATTENTE_SOUMISSION');

COMMENT ON TABLE tenant_whatsapp_template IS
    'Etat de soumission des templates WhatsApp par tenant. Source de verite YAML dans resources/whatsapp-templates/. Cette table garde uniquement le SID Twilio + statut Meta pour le polling.';

COMMENT ON COLUMN tenant_whatsapp_config.onboarding_mode IS
    'LEGACY (epic 23, WABA partagee) ou SELF_SIGNUP (epic 25, WABA dediee). Sunset LEGACY prevu 2026-Q3.';

COMMENT ON COLUMN tenant_whatsapp_config.twilio_sub_auth_token IS
    'Chiffre AES-256-GCM avant ecriture, jamais en cache memoire. Voir EncryptedStringConverter.';

COMMENT ON COLUMN tenant_whatsapp_config.sub_account_idempotency_key IS
    'UUID v4 genere AVANT l''appel Twilio createSubAccount. Permet retry-then-lookup au lieu de creer un sub orphelin si la BDD timeout post-Twilio.';
