-- Capitalize les noms existants : DUPONT JEAN → Dupont Jean
-- initcap() est une fonction PostgreSQL native qui capitalise chaque mot.
UPDATE lead SET nom = initcap(nom) WHERE nom IS NOT NULL AND nom != initcap(nom);
