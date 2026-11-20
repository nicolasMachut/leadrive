CREATE TABLE briefing_quotidien (
    id_briefing_quotidien BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT,
    date_briefing DATE NOT NULL,
    contenu TEXT,
    donnees_contexte TEXT,
    tokens_prompt INTEGER,
    tokens_reponse INTEGER,
    cout_estime DECIMAL(10, 6),
    cree_le TIMESTAMP NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP,
    CONSTRAINT uk_briefing_tenant_date_user UNIQUE (tenant_id, date_briefing, user_id)
);
CREATE INDEX idx_briefing_tenant_date ON briefing_quotidien(tenant_id, date_briefing);
