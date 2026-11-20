CREATE TABLE lead (
    id_lead BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    nom VARCHAR(255) NOT NULL,
    telephone VARCHAR(20),
    email VARCHAR(255),
    marque VARCHAR(100),
    modele VARCHAR(100),
    source VARCHAR(50),
    territoire VARCHAR(100),
    etape VARCHAR(100),
    scoring NUMERIC(3, 2),
    devis VARCHAR(50),
    reprise BOOLEAN,
    type_opportunite VARCHAR(50),
    date_modification_devis TIMESTAMP,
    statut VARCHAR(20) NOT NULL DEFAULT 'NOUVEAU',
    vendeur_id BIGINT,
    import_id BIGINT,
    raw_data JSONB,
    cree_le TIMESTAMP NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP,
    CONSTRAINT fk_lead_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id_tenant),
    CONSTRAINT fk_lead_vendeur FOREIGN KEY (vendeur_id) REFERENCES utilisateur(id_utilisateur),
    CONSTRAINT fk_lead_import FOREIGN KEY (import_id) REFERENCES lead_import(id_import)
);

CREATE INDEX idx_lead_tenant_id ON lead(tenant_id);
CREATE INDEX idx_lead_statut ON lead(statut);
CREATE INDEX idx_lead_vendeur_id ON lead(vendeur_id);
CREATE INDEX idx_lead_import_id ON lead(import_id);
CREATE INDEX idx_lead_email ON lead(email);
CREATE INDEX idx_lead_telephone ON lead(telephone);
