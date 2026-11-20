-- ============================================================================
-- V058 : messaging_limit_tier (Integer) -> TEXT
--
-- Audit Twilio (mai 2026) : l'API Senders v2 renvoie properties.messaging_limit
-- comme STRING (ex: "10K Customers/24hr"), pas un entier ni un champ
-- "messaging_limit_tier". Le typage Integer cote BDD/JPA empechait de stocker
-- la vraie valeur Twilio.
--
-- Le nom de colonne est conserve (messaging_limit_tier) pour minimiser le
-- risque (1 migration au lieu de 2). Le mapping JPA renomme cote Java en
-- "messagingLimit" pour refleter la realite Twilio.
-- ============================================================================

ALTER TABLE tenant_whatsapp_config
    ALTER COLUMN messaging_limit_tier TYPE TEXT USING messaging_limit_tier::TEXT;
