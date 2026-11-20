-- Ajout objectif mensuel RDV équipe (manager).
-- Permet au dashboard manager d'afficher une progress bar parité avec le vendeur :
-- "RDV ce mois / objectif équipe · X%".
-- Configurable par le manager via /admin/config-ia (futur).
-- Nullable car les tenants existants n'ont pas de target configurée à la migration.

ALTER TABLE tenant
    ADD COLUMN objectif_mensuel_rdv_equipe INTEGER NULL;

COMMENT ON COLUMN tenant.objectif_mensuel_rdv_equipe IS 'Objectif mensuel RDV pour l''équipe entière (somme attendue des RDV obtenus tous vendeurs confondus). Null = non configuré, le frontend dégrade gracieusement (pas de progress bar manager).';
