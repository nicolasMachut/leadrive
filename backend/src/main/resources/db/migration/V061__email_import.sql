-- Epic 24 : Import automatique de leads par email.
-- Chaque concession recoit une adresse dediee (slug-token@leads.leadrive.fr).
-- Les emails recus via SendGrid Inbound Parse sont parses par l'IA, dedupliques
-- et transformes en leads.

-- Configuration email par tenant (1 adresse par concession).
CREATE TABLE tenant_email_config (
    id_tenant_email_config BIGSERIAL PRIMARY KEY,
    tenant_id              BIGINT       NOT NULL UNIQUE REFERENCES tenant(id_tenant),
    email_slug             VARCHAR(80)  NOT NULL,
    email_address          VARCHAR(150) NOT NULL,
    statut                 VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    nb_emails_recus        INT          NOT NULL DEFAULT 0,
    dernier_email_recu_le  TIMESTAMP,
    cree_le                TIMESTAMP    NOT NULL DEFAULT NOW(),
    modifie_le             TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_tec_email_address ON tenant_email_config(email_address);

-- Log de tous les emails entrants (succes, dedup, echec, spam).
-- Conserve pour audit et debug, jamais purge automatiquement.
CREATE TABLE inbound_email_log (
    id_inbound_email   BIGSERIAL    PRIMARY KEY,
    tenant_id          BIGINT       NOT NULL REFERENCES tenant(id_tenant),
    email_from         VARCHAR(255),
    email_subject      VARCHAR(500),
    email_body_raw     TEXT,
    resultat_parsing   JSONB,
    statut             VARCHAR(30)  NOT NULL,
    lead_id            BIGINT       REFERENCES lead(id_lead),
    source_detectee    VARCHAR(50),
    duree_parsing_ms   INT,
    raison_rejet       VARCHAR(500),
    cree_le            TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_iel_tenant_cree ON inbound_email_log(tenant_id, cree_le DESC);
