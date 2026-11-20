-- Epic 18 : Historique des changements de statut (Kanban)

CREATE TABLE historique_statut (
    id_historique_statut BIGSERIAL PRIMARY KEY,
    lead_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    statut_precedent VARCHAR(20),
    statut_suivant VARCHAR(20) NOT NULL,
    source VARCHAR(20) NOT NULL,
    user_id BIGINT,
    commentaire VARCHAR(500),
    cree_le TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_historique_statut_lead FOREIGN KEY (lead_id) REFERENCES lead(id_lead) ON DELETE CASCADE,
    CONSTRAINT fk_historique_statut_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id_tenant)
);

CREATE INDEX idx_historique_statut_lead ON historique_statut(lead_id);
CREATE INDEX idx_historique_statut_tenant ON historique_statut(tenant_id);
CREATE INDEX idx_historique_statut_cree_le ON historique_statut(cree_le);

-- Nouveaux champs sur lead pour le Kanban
ALTER TABLE lead ADD COLUMN IF NOT EXISTS statut_modifie_le TIMESTAMP;
ALTER TABLE lead ADD COLUMN IF NOT EXISTS statut_modifie_par VARCHAR(20);
ALTER TABLE lead ADD COLUMN IF NOT EXISTS raison_perte VARCHAR(500);

-- Date/heure absolue du RDV extrait par l'IA (fix du creneau relatif "demain" qui ne se met pas a jour)
ALTER TABLE conversation_analyse ADD COLUMN IF NOT EXISTS rdv_date_heure TIMESTAMP;
