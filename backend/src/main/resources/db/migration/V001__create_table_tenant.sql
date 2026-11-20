CREATE TABLE tenant (
    id_tenant BIGSERIAL PRIMARY KEY,
    nom VARCHAR(255) NOT NULL,
    adresse VARCHAR(500),
    telephone VARCHAR(20),
    email_contact VARCHAR(255),
    timezone VARCHAR(50) NOT NULL DEFAULT 'Europe/Paris',
    est_actif BOOLEAN NOT NULL DEFAULT TRUE,
    cree_le TIMESTAMP NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP
);
