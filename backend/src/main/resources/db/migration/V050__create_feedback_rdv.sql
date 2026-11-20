-- Feedback post-RDV : qualite de qualification IA + suite prevue + raison abandon
CREATE TABLE feedback_rdv (
    id_feedback_rdv BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    lead_id BIGINT NOT NULL,
    rdv_id BIGINT,
    vendeur_id BIGINT NOT NULL,
    qualite_qualification VARCHAR(20),
    suite_prevue VARCHAR(20),
    raison_abandon VARCHAR(20),
    impact_notifie BOOLEAN NOT NULL DEFAULT FALSE,
    cree_le TIMESTAMP NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP
);

CREATE INDEX idx_feedback_rdv_tenant ON feedback_rdv(tenant_id);
CREATE INDEX idx_feedback_rdv_lead ON feedback_rdv(lead_id);
CREATE INDEX idx_feedback_rdv_rdv ON feedback_rdv(rdv_id);
CREATE INDEX idx_feedback_rdv_qualite ON feedback_rdv(tenant_id, qualite_qualification) WHERE qualite_qualification IS NOT NULL;
CREATE INDEX idx_feedback_rdv_impact ON feedback_rdv(tenant_id, vendeur_id, impact_notifie) WHERE impact_notifie = FALSE AND qualite_qualification = 'RATE';
