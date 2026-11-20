-- Remplace les statuts CONVERTI et PERDU par TERMINE
UPDATE lead SET statut = 'TERMINE' WHERE statut IN ('CONVERTI', 'PERDU');
UPDATE historique_statut SET statut_precedent = 'TERMINE' WHERE statut_precedent IN ('CONVERTI', 'PERDU');
UPDATE historique_statut SET statut_suivant = 'TERMINE' WHERE statut_suivant IN ('CONVERTI', 'PERDU');
