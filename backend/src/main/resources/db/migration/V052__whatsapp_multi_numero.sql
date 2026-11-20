-- Epic 23 - WhatsApp multi-numero par concession
-- ===========================================================================
-- Chaque concession utilise son propre numero WhatsApp sous la WABA partagee
-- LeadRive. Le branding (display name, logo, description) est par sender.
--
-- Note : ce script ne touche pas aux donnees existantes. La concession en
-- prod actuelle se retrouve en mode "non configure" apres deploiement et
-- sera guidee par l'interface pour saisir son nouveau numero.

-- Table de configuration WhatsApp par tenant
CREATE TABLE tenant_whatsapp_config (
    id_tenant_whatsapp_config  BIGSERIAL PRIMARY KEY,
    tenant_id                  BIGINT NOT NULL UNIQUE REFERENCES tenant(id_tenant),
    whatsapp_from              VARCHAR(20),
    twilio_sender_sid          VARCHAR(50),
    display_name               VARCHAR(100),
    logo_url                   VARCHAR(500),
    whatsapp_description       VARCHAR(256),
    statut                     VARCHAR(30) NOT NULL DEFAULT 'EN_ATTENTE_NUMERO',
    quality_rating             VARCHAR(10),
    messaging_limit_tier       INT DEFAULT 250,
    meta_status                VARCHAR(20),
    meta_reject_reason         TEXT,
    date_derniere_synchro_meta TIMESTAMP,
    notes_internes             TEXT,
    tentatives_code            INT NOT NULL DEFAULT 0,
    bloque_jusqua              TIMESTAMP,
    version                    INT NOT NULL DEFAULT 0,
    cree_le                    TIMESTAMP NOT NULL DEFAULT NOW(),
    modifie_le                 TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_actif_avec_numero CHECK (
        statut NOT IN ('ACTIF', 'EN_VALIDATION_META') OR whatsapp_from IS NOT NULL
    )
);

COMMENT ON TABLE tenant_whatsapp_config IS
    'Configuration WhatsApp Business par tenant (numero, branding, statut Meta).';
COMMENT ON COLUMN tenant_whatsapp_config.twilio_sender_sid IS
    'SID du sender Twilio - utilise pour le polling Meta (job @Scheduled).';
COMMENT ON COLUMN tenant_whatsapp_config.display_name IS
    'Nom affiche cote prospect dans WhatsApp (peut differer du nomConcession TenantAiConfig).';
COMMENT ON COLUMN tenant_whatsapp_config.whatsapp_description IS
    'Description courte du profil WhatsApp (256 chars max - limite Meta).';
COMMENT ON COLUMN tenant_whatsapp_config.quality_rating IS
    'Quality rating Meta : GREEN, YELLOW, RED. Mis a jour par le job de polling.';
COMMENT ON COLUMN tenant_whatsapp_config.messaging_limit_tier IS
    'Plafond Meta de conversations business-initiated / 24h. Initial : 250.';
COMMENT ON COLUMN tenant_whatsapp_config.meta_status IS
    'Etat cote Twilio Senders API : CREATING, OFFLINE, ONLINE, REJECTED.';
COMMENT ON COLUMN tenant_whatsapp_config.tentatives_code IS
    'Anti-brute-force : 5 echecs successifs declenchent un blocage de 30 min.';
COMMENT ON COLUMN tenant_whatsapp_config.version IS
    'Lock optimiste JPA (@Version) - protege contre les ecritures concurrentes.';

-- Index pour le lookup webhook : "quel tenant possede ce numero ?"
-- Partiel sur les numeros non null (le champ peut etre null avant verification).
CREATE UNIQUE INDEX idx_twc_whatsapp_from
    ON tenant_whatsapp_config(whatsapp_from)
    WHERE whatsapp_from IS NOT NULL;

-- Audit historique des associations numero <-> tenant.
-- Critique pour eviter les fuites cross-tenant en cas de reattribution
-- d'un numero apres dissociation.
CREATE TABLE tenant_whatsapp_number_history (
    id_history          BIGSERIAL PRIMARY KEY,
    whatsapp_from       VARCHAR(20) NOT NULL,
    tenant_id           BIGINT NOT NULL REFERENCES tenant(id_tenant),
    associe_le          TIMESTAMP NOT NULL,
    dissocie_le         TIMESTAMP,
    raison_dissociation VARCHAR(100)
);
COMMENT ON TABLE tenant_whatsapp_number_history IS
    'Trace historique des associations numero <-> tenant. Append-only.';

CREATE INDEX idx_twnh_whatsapp_from ON tenant_whatsapp_number_history(whatsapp_from);
CREATE INDEX idx_twnh_tenant_id     ON tenant_whatsapp_number_history(tenant_id);

-- Audit des actions admin sensibles (force ACTIF, desactivation, modification statut).
-- Append-only : jamais de UPDATE/DELETE en code applicatif (controle DBA uniquement).
CREATE TABLE whatsapp_admin_actions (
    id_action     BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL REFERENCES tenant(id_tenant),
    admin_user_id BIGINT NOT NULL,
    action        VARCHAR(50) NOT NULL,
    justification TEXT NOT NULL,
    -- Contexte additionnel sur l'action (ancien numero, ancien statut, etc.).
    -- Stocke en TEXT (JSON serialise par Jackson cote applicatif) plutot
    -- qu'en JSONB pour eviter une dependance a hypersistence-utils. Le
    -- contenu n'est jamais requete par SQL, juste consulte par l'admin.
    metadata_json TEXT,
    cree_le       TIMESTAMP NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE whatsapp_admin_actions IS
    'Audit des actions admin sur les configs WhatsApp. Append-only.';
COMMENT ON COLUMN whatsapp_admin_actions.action IS
    'Type d''action : FORCE_ACTIF, DESACTIVATION, OVERRIDE_STATUT, RESET_COMPTEUR, CHANGEMENT_NUMERO.';
COMMENT ON COLUMN whatsapp_admin_actions.justification IS
    'Justification obligatoire (min 20 chars cote applicatif).';

CREATE INDEX idx_waa_tenant_id ON whatsapp_admin_actions(tenant_id);
CREATE INDEX idx_waa_cree_le   ON whatsapp_admin_actions(cree_le DESC);

-- Audit de la moderation IA des envois manuels (vendeurs / managers).
-- Conserve le contenu en clair pour audit Meta en cas de policy violation.
CREATE TABLE message_moderation_log (
    id_moderation   BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT NOT NULL,
    conversation_id VARCHAR(100),
    user_id         BIGINT,
    contenu         TEXT NOT NULL,
    decision        VARCHAR(20) NOT NULL,
    raison          TEXT,
    duree_ms        INT,
    cree_le         TIMESTAMP NOT NULL DEFAULT NOW()
);
COMMENT ON TABLE message_moderation_log IS
    'Log de la moderation IA des messages manuels. Append-only.';
COMMENT ON COLUMN message_moderation_log.decision IS
    'AUTORISE ou BLOQUE. Si BLOQUE, la raison est dans le champ raison.';
COMMENT ON COLUMN message_moderation_log.duree_ms IS
    'Duree de l''appel a l''IA de moderation en millisecondes.';

CREATE INDEX idx_mml_tenant_id ON message_moderation_log(tenant_id);
CREATE INDEX idx_mml_cree_le   ON message_moderation_log(cree_le DESC);

-- Tracabilite : quel numero expediteur pour quelle conversation.
-- Pour les conversations historiques (avant cet epic), le champ reste NULL.
ALTER TABLE sms_conversation ADD COLUMN whatsapp_from VARCHAR(20);
COMMENT ON COLUMN sms_conversation.whatsapp_from IS
    'Numero WhatsApp Business expediteur utilise pour cette conversation. NULL pour les conversations historiques pre-Epic-23.';

CREATE INDEX idx_sms_conv_whatsapp_from
    ON sms_conversation(whatsapp_from)
    WHERE whatsapp_from IS NOT NULL;
