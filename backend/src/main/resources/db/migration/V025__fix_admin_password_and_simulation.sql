-- Fix le mot de passe du SUPER_ADMIN initial
-- Mot de passe "admin123" encode BCrypt (cout 10)
UPDATE utilisateur
SET mot_de_passe = '$2a$10$YmLy7GEnerF57sC9wE879uiqNM7aQVfGFFE1g8mTnURiu3AVLRCh6'
WHERE email = 'admin@leadrevive.fr';

-- Supprimer les anciennes entrees AiLog avec type SIMULATION_PROSPECT (l'enum n'existe plus)
DELETE FROM ai_log WHERE type_generation = 'SIMULATION_PROSPECT';
