-- Remet a null le scoring CSV des leads existants.
-- Seul le hotness_score (calcule par l'IA) est desormais utilise.
UPDATE lead SET scoring = NULL WHERE scoring IS NOT NULL;
