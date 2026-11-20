-- Idempotence des webhooks Twilio : trace les MessageSid deja traites pour
-- detecter les retries Twilio (cas typique : timeout/5xx => Twilio retry 3x en 90s).
--
-- Le cache Caffeine local en RAM (WebhookController) ne suffit pas en multi-instance
-- (Railway peut router le retry vers une autre replica qui n'a pas le SID en cache,
-- causant un double-traitement : double appel IA + double reponse au prospect).
--
-- La table sert de "lock" global : un INSERT en CONFLICT DO NOTHING garantit qu'un
-- seul webhook par MessageSid est traite, quelle que soit l'instance qui le recoit.
--
-- Un job @Scheduled purge les entrees > 24h.

CREATE TABLE IF NOT EXISTS webhook_idempotence (
    message_sid    VARCHAR(64) PRIMARY KEY,
    source         VARCHAR(32) NOT NULL DEFAULT 'twilio',
    traite_le      TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_webhook_idempotence_traite_le
    ON webhook_idempotence (traite_le);

COMMENT ON TABLE webhook_idempotence IS
    'Idempotence webhooks externes (Twilio principalement). Une ligne par MessageSid traite.';
COMMENT ON COLUMN webhook_idempotence.source IS
    'Origine du webhook : twilio, twilio-status, etc.';
