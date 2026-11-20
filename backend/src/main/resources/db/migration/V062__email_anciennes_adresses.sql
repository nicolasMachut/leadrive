-- Epic 24 : conservation de l'historique des anciennes adresses email d'import.
-- Quand l'admin "Regenere" l'adresse, l'ancienne est ajoutee dans cette liste
-- pour audit / debug. Format : [{"adresse": "x@y.fr", "remplaceeLe": "2026-..."}].
ALTER TABLE tenant_email_config
    ADD COLUMN anciennes_adresses JSONB NOT NULL DEFAULT '[]'::jsonb;
