-- ============================================================================
-- V059 : ajout du site web et email pro a tenant_ai_config
--
-- Ces champs sont pousses cote profil WhatsApp Business (profile.websites
-- et profile.emails dans Twilio Senders v2) pour apparaitre dans la fiche
-- contact du prospect cote WhatsApp.
-- ============================================================================

ALTER TABLE tenant_ai_config
    ADD COLUMN IF NOT EXISTS site_web      TEXT,
    ADD COLUMN IF NOT EXISTS email_contact VARCHAR(255);
