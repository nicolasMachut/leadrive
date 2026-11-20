-- Renommer ADMIN en MANAGER (il n'y a plus de distinction côté concession)
UPDATE utilisateur SET role = 'MANAGER' WHERE role = 'ADMIN';

-- Autoriser tenant_id NULL pour SUPER_ADMIN (équipe LeadRevive)
ALTER TABLE utilisateur ALTER COLUMN tenant_id DROP NOT NULL;

-- Contrainte : MANAGER et VENDEUR ont obligatoirement un tenant_id
ALTER TABLE utilisateur ADD CONSTRAINT chk_tenant_id_role
    CHECK ((role = 'SUPER_ADMIN' AND tenant_id IS NULL) OR (role IN ('MANAGER', 'VENDEUR') AND tenant_id IS NOT NULL));

-- Ajouter un statut user pour gérer INVITE / ACTIF / INACTIF
ALTER TABLE utilisateur ADD COLUMN statut VARCHAR(20) NOT NULL DEFAULT 'ACTIF';

-- Ajouter dernière connexion
ALTER TABLE utilisateur ADD COLUMN derniere_connexion_le TIMESTAMP;

-- Ajouter colonne ville sur tenant
ALTER TABLE tenant ADD COLUMN ville VARCHAR(255);

-- Créer un SUPER_ADMIN initial en local (email : admin@leadrevive.fr, mdp : admin par défaut — à changer immédiatement)
-- Mot de passe "admin" encodé BCrypt (coût 10)
INSERT INTO utilisateur (tenant_id, email, mot_de_passe, nom, prenom, role, est_actif, statut, cree_le)
VALUES (NULL, 'admin@leadrevive.fr', '$2a$10$gLAelw7ilE2UYtmLoqkt7urfnGzEZjUsFbDyltrDSpwXnAqRO7d.e', 'LeadRevive', 'Admin', 'SUPER_ADMIN', true, 'ACTIF', NOW())
ON CONFLICT (email) DO NOTHING;
