-- Script de demo : remplace tous les numeros de telephone par le numero de test.
-- A SUPPRIMER avant la mise en production.
UPDATE lead SET telephone = '+33600000002' WHERE telephone IS NOT NULL;
