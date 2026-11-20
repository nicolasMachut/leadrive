CREATE TABLE sms_conversation (
    id_sms_conversation BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    lead_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    conversation_id VARCHAR(36) NOT NULL UNIQUE,
    statut VARCHAR(20) NOT NULL DEFAULT 'EN_COURS',
    numero_prospect VARCHAR(20) NOT NULL,
    nombre_messages INTEGER DEFAULT 0,
    dernier_message_le TIMESTAMP,
    rdv_propose_le TIMESTAMP,
    rdv_confirme_le TIMESTAMP,
    cree_le TIMESTAMP NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP,
    CONSTRAINT fk_sms_conv_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id_tenant),
    CONSTRAINT fk_sms_conv_lead FOREIGN KEY (lead_id) REFERENCES lead(id_lead),
    CONSTRAINT fk_sms_conv_user FOREIGN KEY (user_id) REFERENCES utilisateur(id_utilisateur)
);

CREATE INDEX idx_sms_conv_tenant_lead ON sms_conversation(tenant_id, lead_id);
CREATE INDEX idx_sms_conv_conversation_id ON sms_conversation(conversation_id);
CREATE INDEX idx_sms_conv_statut ON sms_conversation(statut);
