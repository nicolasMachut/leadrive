-- Suppression des tables d'auth maison
DROP TABLE IF EXISTS invitation_token;
DROP TABLE IF EXISTS reset_password_token;

-- Retirer le hash du mot de passe : Clerk gere l'auth
ALTER TABLE utilisateur DROP COLUMN IF EXISTS mot_de_passe;

-- Ajouter le lien vers le user Clerk (deja en colonne via JPA mais pas encore migration)
ALTER TABLE utilisateur ADD COLUMN IF NOT EXISTS clerk_user_id VARCHAR(64) UNIQUE;
ALTER TABLE utilisateur ADD COLUMN IF NOT EXISTS email_synced_at TIMESTAMP;

-- Lier le tenant a l'organization Clerk
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS clerk_organization_id VARCHAR(64) UNIQUE;

-- Retirer la contrainte CHECK qui imposait MANAGER/VENDEUR avec tenant_id non null :
-- Avec Clerk, le user maison peut etre cree par webhook avant l'OrganizationMembership,
-- donc tenantId peut etre temporairement null pour un user non-SUPER_ADMIN.
ALTER TABLE utilisateur DROP CONSTRAINT IF EXISTS chk_tenant_id_role;

-- Supprimer le SUPER_ADMIN initial cree avec un mot de passe maison : il sera recree via Clerk
DELETE FROM utilisateur WHERE email = 'admin@leadrevive.fr' AND clerk_user_id IS NULL;
