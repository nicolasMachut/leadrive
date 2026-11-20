-- Statut d'examen Meta du nom affiche du numero WhatsApp (verified name review).
-- Distinct de meta_status (statut du sender) : le nom peut etre en PENDING_REVIEW
-- alors que le compte est deja ONLINE. Tant que le nom n'est pas APPROVED, le
-- prospect voit le numero brut a la place du nom de l'etablissement.
-- Valeurs (Twilio/Meta) : APPROVED, PENDING_REVIEW, DECLINED,
-- AVAILABLE_WITHOUT_REVIEW, EXPIRED, NONE. NULL tant que non synchronise.
ALTER TABLE tenant_whatsapp_config
    ADD COLUMN name_status VARCHAR(40);
