CREATE TABLE utilisateur (
    id_utilisateur BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    mot_de_passe VARCHAR(255) NOT NULL,
    nom VARCHAR(255) NOT NULL,
    prenom VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    est_actif BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le TIMESTAMP NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP,
    CONSTRAINT fk_utilisateur_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id_tenant)
);

CREATE INDEX idx_utilisateur_tenant_id ON utilisateur(tenant_id);
CREATE INDEX idx_utilisateur_email ON utilisateur(email);
