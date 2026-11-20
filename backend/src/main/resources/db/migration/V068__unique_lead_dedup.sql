-- Contrainte d'unicité anti-doublon (garde-fou contre la race condition à l'import :
-- deux sources concurrentes — webhook email + CSV, ou deux emails rapprochés — peuvent
-- créer 2 leads pour le même prospect car la dédup applicative lit avant de committer).
--
-- Index UNIQUE PARTIEL : uniquement sur les leads ACTIFS (archive = false). Un même
-- téléphone/email peut légitimement réapparaître après archivage d'un ancien lead.
--
-- NETTOYAGE AUTOMATIQUE PRÉALABLE : si des doublons ACTIFS existent déjà, on ne fait PAS
-- échouer la migration. On archive (soft, réversible) les doublons en gardant le lead le
-- plus ANCIEN de chaque groupe (MIN(id)) : c'est lui qui porte l'historique (conversation,
-- transitions de statut, RDV). Les copies plus récentes (re-imports accidentels) passent
-- en archive = true. L'archivage ne touche PAS historique_statut (append-only) : aucun RDV
-- facturé n'est perdu. Opération réversible (un manager peut désarchiver si besoin).

-- 1) Doublons par TÉLÉPHONE : archiver tout sauf le plus ancien du groupe.
--    NB : la PK de la table lead est la colonne id_lead (pas id).
UPDATE lead l
SET archive = true,
    modifie_le = NOW()
WHERE l.telephone IS NOT NULL
  AND l.archive = false
  AND l.id_lead <> (
      SELECT MIN(l2.id_lead)
      FROM lead l2
      WHERE l2.tenant_id = l.tenant_id
        AND l2.telephone = l.telephone
        AND l2.archive = false
  );

-- 2) Doublons par EMAIL : réévalué APRÈS l'étape 1 (les leads déjà archivés sont exclus).
UPDATE lead l
SET archive = true,
    modifie_le = NOW()
WHERE l.email IS NOT NULL
  AND l.archive = false
  AND l.id_lead <> (
      SELECT MIN(l2.id_lead)
      FROM lead l2
      WHERE l2.tenant_id = l.tenant_id
        AND l2.email = l.email
        AND l2.archive = false
  );

-- 3) Index uniques partiels : la base est désormais propre, leur création réussit.
CREATE UNIQUE INDEX IF NOT EXISTS uq_lead_tenant_telephone_actif
    ON lead (tenant_id, telephone)
    WHERE telephone IS NOT NULL AND archive = false;

CREATE UNIQUE INDEX IF NOT EXISTS uq_lead_tenant_email_actif
    ON lead (tenant_id, email)
    WHERE email IS NOT NULL AND archive = false;
