-- Envoi automatique du premier message WhatsApp pour les leads recus par import email.
-- OFF par defaut pour tous les tenants (existants et nouveaux).

-- Parametres par tenant (config IA)
ALTER TABLE tenant_ai_config
    ADD COLUMN premier_message_auto_actif boolean NOT NULL DEFAULT false;

ALTER TABLE tenant_ai_config
    ADD COLUMN premier_message_auto_delai_minutes int NOT NULL DEFAULT 5;

ALTER TABLE tenant_ai_config
    ADD COLUMN premier_message_auto_fenetre_debut_heure int NOT NULL DEFAULT 8;

ALTER TABLE tenant_ai_config
    ADD COLUMN premier_message_auto_fenetre_fin_heure int NOT NULL DEFAULT 20;

-- Horodatage d'armement de l'envoi auto sur le lead. NULL = pas d'envoi auto en attente.
-- Efface apres envoi (anti double-envoi), sur abandon manuel, ou a l'expiration (>24h).
ALTER TABLE lead
    ADD COLUMN premier_message_auto_prevu_le timestamp NULL;

-- Index partiel : le scheduler ne balaye que les leads reellement armes.
CREATE INDEX idx_lead_premier_message_auto_prevu_le
    ON lead (premier_message_auto_prevu_le)
    WHERE premier_message_auto_prevu_le IS NOT NULL;
