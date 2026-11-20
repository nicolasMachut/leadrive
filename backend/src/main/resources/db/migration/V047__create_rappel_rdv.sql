-- Rappels RDV par WhatsApp (veille + jour J)
CREATE TABLE rappel_rdv (
    id_rappel_rdv BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    tache_id BIGINT NOT NULL REFERENCES tache(id_tache),
    sms_conversation_id BIGINT NOT NULL REFERENCES sms_conversation(id_sms_conversation),
    lead_id BIGINT NOT NULL REFERENCES lead(id_lead),
    type_rappel VARCHAR(10) NOT NULL,
    envoi_prevu_at TIMESTAMP NOT NULL,
    envoye BOOLEAN NOT NULL DEFAULT FALSE,
    envoye_at TIMESTAMP,
    cree_le TIMESTAMP NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP
);

CREATE INDEX idx_rappel_rdv_envoi ON rappel_rdv(envoi_prevu_at) WHERE envoye = FALSE;
CREATE INDEX idx_rappel_rdv_lead ON rappel_rdv(lead_id) WHERE envoye = FALSE;
