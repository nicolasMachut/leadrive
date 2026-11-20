CREATE TABLE tenant_ai_config (
    id_tenant_ai_config BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL UNIQUE,
    nom_concession VARCHAR(255) NOT NULL,
    marques VARCHAR(500),
    villes_principales VARCHAR(500),
    arguments_cles TEXT,
    ton_commercial TEXT,
    horaires_ouverture VARCHAR(255),
    cree_le TIMESTAMP NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP,
    CONSTRAINT fk_tenant_ai_config_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id_tenant)
);

CREATE UNIQUE INDEX idx_tenant_ai_config_tenant_id ON tenant_ai_config(tenant_id);
