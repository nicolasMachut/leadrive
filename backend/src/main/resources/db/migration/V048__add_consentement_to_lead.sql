-- RGPD : trace du consentement initial du prospect.
-- consentement_contact = true des qu'une preuve raisonnable existe :
--   - "csv_import" : le prospect a remis ses coordonnees a la concession dans un parcours commercial
--   - "formulaire_web" : opt-in explicite via formulaire
--   - "whatsapp_reply" : le prospect a repondu a un message — repond intrinsequement, vaut consentement
-- Si null, le envoi WhatsApp sortant doit etre refuse.

ALTER TABLE lead
    ADD COLUMN consentement_contact BOOLEAN,
    ADD COLUMN consentement_date    TIMESTAMP,
    ADD COLUMN consentement_source  VARCHAR(50);

-- Backfill : les leads existants sont presumes consentants (ils ont ete imports par le manager
-- en connaissance de cause). On ne bloque pas les flows existants. La source est marquee 'legacy'.
UPDATE lead
SET consentement_contact = TRUE,
    consentement_date    = COALESCE(cree_le, NOW()),
    consentement_source  = 'legacy_backfill'
WHERE consentement_contact IS NULL;
