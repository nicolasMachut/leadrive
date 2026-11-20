CREATE TABLE ai_log (
    id_ai_log BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    lead_id BIGINT,
    type_generation VARCHAR(30) NOT NULL,
    prompt TEXT NOT NULL,
    reponse TEXT,
    modele VARCHAR(50),
    tokens_prompt INTEGER,
    tokens_reponse INTEGER,
    cout_estime NUMERIC(10, 6),
    duree_ms BIGINT,
    statut VARCHAR(20) NOT NULL,
    erreur TEXT,
    cree_le TIMESTAMP NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP,
    CONSTRAINT fk_ai_log_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id_tenant),
    CONSTRAINT fk_ai_log_lead FOREIGN KEY (lead_id) REFERENCES lead(id_lead)
);

CREATE INDEX idx_ai_log_tenant_id ON ai_log(tenant_id);
CREATE INDEX idx_ai_log_lead_id ON ai_log(lead_id);
CREATE INDEX idx_ai_log_type_generation ON ai_log(type_generation);
CREATE INDEX idx_ai_log_cree_le ON ai_log(cree_le);
