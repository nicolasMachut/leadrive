CREATE TABLE tache (
    id_tache BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    vendeur_id BIGINT NOT NULL,
    lead_id BIGINT,
    titre VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    type_tache VARCHAR(20) NOT NULL,
    date_echeance TIMESTAMP NOT NULL,
    duree_minutes INTEGER,
    priorite VARCHAR(20) NOT NULL DEFAULT 'NORMALE',
    statut VARCHAR(20) NOT NULL DEFAULT 'A_FAIRE',
    source VARCHAR(20) NOT NULL,
    rappel_minutes_avant INTEGER,
    rappel_envoye BOOLEAN NOT NULL DEFAULT false,
    date_terminee TIMESTAMP,
    cree_le TIMESTAMP NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP,
    CONSTRAINT fk_tache_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id_tenant),
    CONSTRAINT fk_tache_lead FOREIGN KEY (lead_id) REFERENCES lead(id_lead) ON DELETE SET NULL
);

CREATE INDEX idx_tache_vendeur_echeance ON tache(tenant_id, vendeur_id, date_echeance);
CREATE INDEX idx_tache_lead ON tache(tenant_id, lead_id);
CREATE INDEX idx_tache_statut_echeance ON tache(tenant_id, statut, date_echeance);
CREATE INDEX idx_tache_rappel ON tache(rappel_envoye, date_echeance);
