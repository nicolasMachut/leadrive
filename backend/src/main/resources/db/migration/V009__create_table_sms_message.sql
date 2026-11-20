CREATE TABLE sms_message (
    id_sms_message BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    conversation_id VARCHAR(36) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    contenu TEXT NOT NULL,
    statut VARCHAR(15) NOT NULL DEFAULT 'EN_ATTENTE',
    brevo_message_id VARCHAR(100),
    erreur TEXT,
    ordre INTEGER NOT NULL,
    cree_le TIMESTAMP NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP,
    CONSTRAINT fk_sms_msg_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id_tenant)
);

CREATE INDEX idx_sms_msg_conversation_ordre ON sms_message(conversation_id, ordre);
CREATE INDEX idx_sms_msg_tenant ON sms_message(tenant_id);
CREATE INDEX idx_sms_msg_brevo_id ON sms_message(brevo_message_id);
