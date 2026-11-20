-- P2-3 : Index manquants sur les colonnes frequemment filtrees

-- Lead : filtres Kanban, dashboard, assignation
CREATE INDEX IF NOT EXISTS idx_lead_statut ON lead(tenant_id, statut);
CREATE INDEX IF NOT EXISTS idx_lead_vendeur ON lead(tenant_id, vendeur_id);
CREATE INDEX IF NOT EXISTS idx_lead_hotness ON lead(tenant_id, hotness_score DESC NULLS LAST);
CREATE INDEX IF NOT EXISTS idx_lead_scoring ON lead(tenant_id, scoring DESC NULLS LAST);

-- SmsConversation : lookup webhook + filtres inbox
CREATE INDEX IF NOT EXISTS idx_sms_conv_numero ON sms_conversation(numero_prospect);
CREATE INDEX IF NOT EXISTS idx_sms_conv_statut ON sms_conversation(tenant_id, statut);
CREATE INDEX IF NOT EXISTS idx_sms_conv_lead ON sms_conversation(lead_id, tenant_id);

-- AiLog : stats IA par tenant
CREATE INDEX IF NOT EXISTS idx_ai_log_tenant_date ON ai_log(tenant_id, cree_le);
