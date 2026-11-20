-- Soft delete + anonymisation des utilisateurs orphelins
-- ===========================================================================
-- Contexte : avant ce fix, la suppression d'une concession laissait les
-- utilisateurs mirror en BDD avec un tenant_id pointant vers un tenant
-- inexistant (le filtre Hibernate `tenantFilter` masquait les users lors
-- du DELETE cascade). Resultat : emails verrouilles par la contrainte
-- UNIQUE → impossible de reinviter le meme email sur une nouvelle concession.
--
-- Ce script :
--   1. Identifie les utilisateurs orphelins (tenant_id ne pointe vers
--      aucun tenant existant)
--   2. Anonymise leur email (libere l'adresse pour reutilisation)
--   3. Force statut=INACTIF et est_actif=false
--   4. Vide clerk_user_id (libere la contrainte UNIQUE sur ce champ aussi)

UPDATE utilisateur
SET
    email = 'deleted-' || id_utilisateur || '-' || extract(epoch FROM NOW())::bigint || '@deleted.local',
    statut = 'INACTIF',
    est_actif = false,
    clerk_user_id = NULL,
    modifie_le = NOW()
WHERE tenant_id NOT IN (SELECT id_tenant FROM tenant);
