-- ============================================================================
-- V060 : suppression du champ ville de la table tenant
--
-- Le champ etait :
--   - Saisi a la creation par le SUPER_ADMIN (deja retire du form en V058+)
--   - Affiche dans la liste admin et la page detail
--
-- Il n'etait utilise dans aucune logique metier (pas dans le prompt IA, pas
-- dans le profil WhatsApp, pas dans le routage, pas dans les exports).
-- L'adresse complete saisie par le manager dans "Mon etablissement >
-- Coordonnees" couvre deja le besoin (l'IA peut extraire la ville si requis).
-- ============================================================================

ALTER TABLE tenant DROP COLUMN IF EXISTS ville;
