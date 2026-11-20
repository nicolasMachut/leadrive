-- Supprime les champs ROI estimes : le CA reel vient de vente_confirmee, plus besoin d'estimations.
ALTER TABLE tenant_ai_config DROP COLUMN IF EXISTS panier_moyen_vehicule;
ALTER TABLE tenant_ai_config DROP COLUMN IF EXISTS taux_conversion_estime;
