-- Table stockant l'analyse IA structuree des messages entrants d'une conversation WhatsApp.
-- Chaque fois que le prospect repond, on extrait l'intention, les signaux d'achat, les objections,
-- et on declenche les actions automatiques (update statut conversation/lead, handoff vendeur, etc.).

CREATE TABLE conversation_analyse (
    id_analyse BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    conversation_id VARCHAR(36) NOT NULL,
    sms_message_id BIGINT,
    intention VARCHAR(30) NOT NULL,
    signaux_achat JSONB,
    objections JSONB,
    besoin_handoff BOOLEAN NOT NULL DEFAULT FALSE,
    raison_handoff TEXT,
    resume_bref TEXT,
    rdv_propose BOOLEAN NOT NULL DEFAULT FALSE,
    rdv_creneau TEXT,
    cree_le TIMESTAMP NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP,
    CONSTRAINT fk_analyse_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id_tenant),
    CONSTRAINT fk_analyse_message FOREIGN KEY (sms_message_id) REFERENCES sms_message(id_sms_message)
);

CREATE INDEX idx_analyse_tenant_conv ON conversation_analyse(tenant_id, conversation_id);
CREATE INDEX idx_analyse_conv_cree ON conversation_analyse(conversation_id, cree_le DESC);
CREATE INDEX idx_analyse_intention ON conversation_analyse(intention);
CREATE INDEX idx_analyse_handoff ON conversation_analyse(tenant_id, besoin_handoff) WHERE besoin_handoff = TRUE;
