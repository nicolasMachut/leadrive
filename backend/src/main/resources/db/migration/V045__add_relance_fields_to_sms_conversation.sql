ALTER TABLE sms_conversation ADD COLUMN nombre_relances INT NOT NULL DEFAULT 0;
ALTER TABLE sms_conversation ADD COLUMN derniere_relance_le TIMESTAMP;
ALTER TABLE sms_conversation ADD COLUMN prochaine_relance_le TIMESTAMP;

CREATE INDEX idx_sms_conversation_prochaine_relance ON sms_conversation (prochaine_relance_le)
    WHERE statut = 'EN_COURS' AND prochaine_relance_le IS NOT NULL;
