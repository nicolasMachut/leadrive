CREATE TABLE conversation_message (
    id_conversation_message BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    conversation_id VARCHAR(36) NOT NULL,
    lead_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(10) NOT NULL,
    contenu TEXT NOT NULL,
    tokens_estimes INTEGER,
    ordre INTEGER NOT NULL,
    cree_le TIMESTAMP NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP,
    CONSTRAINT fk_conv_msg_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id_tenant),
    CONSTRAINT fk_conv_msg_lead FOREIGN KEY (lead_id) REFERENCES lead(id_lead),
    CONSTRAINT fk_conv_msg_user FOREIGN KEY (user_id) REFERENCES utilisateur(id_utilisateur)
);

CREATE INDEX idx_conv_msg_conversation_id ON conversation_message(conversation_id);
CREATE INDEX idx_conv_msg_tenant_lead ON conversation_message(tenant_id, lead_id);
CREATE INDEX idx_conv_msg_tenant_user ON conversation_message(tenant_id, user_id);
