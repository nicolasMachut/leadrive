CREATE TABLE lead_import (
    id_import BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    nom_fichier VARCHAR(500) NOT NULL,
    date_import TIMESTAMP NOT NULL DEFAULT NOW(),
    nombre_lignes INTEGER,
    nombre_importees INTEGER DEFAULT 0,
    nombre_erreurs INTEGER DEFAULT 0,
    nombre_doublons INTEGER DEFAULT 0,
    statut VARCHAR(20) NOT NULL DEFAULT 'EN_COURS',
    mapping_colonnes JSONB,
    erreurs JSONB,
    importe_par BIGINT,
    cree_le TIMESTAMP NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP,
    CONSTRAINT fk_import_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id_tenant),
    CONSTRAINT fk_import_utilisateur FOREIGN KEY (importe_par) REFERENCES utilisateur(id_utilisateur)
);

CREATE INDEX idx_import_tenant_id ON lead_import(tenant_id);
