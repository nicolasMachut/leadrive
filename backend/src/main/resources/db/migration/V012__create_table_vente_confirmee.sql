CREATE TABLE vente_confirmee (
    id_vente_confirmee BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    lead_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    montant DECIMAL(12, 2),
    date_vente DATE NOT NULL,
    source VARCHAR(50) NOT NULL,
    commentaire TEXT,
    cree_le TIMESTAMP NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP,
    CONSTRAINT uk_vente_lead UNIQUE (lead_id)
);
CREATE INDEX idx_vente_tenant ON vente_confirmee(tenant_id);
CREATE INDEX idx_vente_lead ON vente_confirmee(lead_id);
CREATE INDEX idx_vente_date ON vente_confirmee(date_vente);
CREATE INDEX idx_vente_source ON vente_confirmee(source);
