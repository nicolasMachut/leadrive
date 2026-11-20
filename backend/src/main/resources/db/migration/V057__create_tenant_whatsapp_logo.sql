-- ============================================================================
-- V057 : stockage du logo WhatsApp en BDD (au lieu du systeme de fichier)
--
-- Probleme : le stockage sur disque /data/whatsapp-logos echoue sur Railway
-- (pas de volume monte par defaut). Et meme avec volume : multi-instance
-- expose au split-brain, et un redeploy sans volume persistant perd le logo.
--
-- Solution : table dediee 1-to-1 avec tenant_whatsapp_config. Table separee
-- (et pas une colonne BYTEA dans tenant_whatsapp_config) pour eviter de
-- trimballer les bytes du logo dans toutes les requetes Hibernate
-- (notamment le job cross-tenant WhatsAppSenderStatusJob qui charge
-- toutes les configs ACTIF / EN_VALIDATION_META toutes les 10 min).
-- ============================================================================

CREATE TABLE tenant_whatsapp_logo (
    tenant_id   BIGINT PRIMARY KEY REFERENCES tenant(id_tenant) ON DELETE CASCADE,
    logo_bytes  BYTEA NOT NULL,
    cree_le     TIMESTAMP NOT NULL DEFAULT NOW(),
    modifie_le  TIMESTAMP
);

COMMENT ON TABLE tenant_whatsapp_logo IS
    'Logo PNG (max 5 Mo) de la concession affiche sur WhatsApp Business + dans la sidebar Leadrive.';
