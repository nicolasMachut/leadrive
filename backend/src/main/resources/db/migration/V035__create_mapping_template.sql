-- Epic 14 : Table mapping_template pour stocker les mappings CSV réutilisables par concession.
CREATE TABLE mapping_template (
    id_mapping_template BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    nom VARCHAR(255) NOT NULL,
    headers_originaux JSONB NOT NULL,
    mappings JSONB NOT NULL,
    options_split JSONB,
    format_date VARCHAR(50),
    nombre_utilisations INTEGER NOT NULL DEFAULT 0,
    derniere_utilisation TIMESTAMP,
    cree_par BIGINT,
    cree_le TIMESTAMP NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP,
    CONSTRAINT fk_mapping_template_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id_tenant),
    CONSTRAINT fk_mapping_template_user FOREIGN KEY (cree_par) REFERENCES utilisateur(id_utilisateur),
    CONSTRAINT uq_mapping_template_tenant_headers UNIQUE (tenant_id, headers_originaux)
);

CREATE INDEX idx_mapping_template_tenant ON mapping_template(tenant_id);

-- Ajout du type de fichier sur l'import
ALTER TABLE lead_import ADD COLUMN IF NOT EXISTS type_fichier VARCHAR(30);
