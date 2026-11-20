CREATE TABLE demande_demo (
    id_demande_demo BIGSERIAL PRIMARY KEY,
    nom VARCHAR(255) NOT NULL,
    concession VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    telephone VARCHAR(50),
    volume_leads VARCHAR(50),
    statut VARCHAR(50) NOT NULL DEFAULT 'NOUVEAU',
    cree_le TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    traite_le TIMESTAMPTZ,
    traite_par VARCHAR(255)
);
