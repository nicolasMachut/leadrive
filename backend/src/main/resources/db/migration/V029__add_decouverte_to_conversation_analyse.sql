-- Ajout des champs structures issus de la decouverte commerciale (Q0-Q4 du prompt WhatsApp).
-- L'IA d'extraction remplit ces champs au fur et a mesure de la conversation pour alimenter
-- la fiche de preparation du vendeur.

ALTER TABLE conversation_analyse
    ADD COLUMN delai_projet TEXT,
    ADD COLUMN montant_budget TEXT,
    ADD COLUMN apport_prevu TEXT,
    ADD COLUMN mode_financement TEXT,
    ADD COLUMN accord_banque TEXT,
    ADD COLUMN reprise_conditionne_achat BOOLEAN;

COMMENT ON COLUMN conversation_analyse.delai_projet IS 'Delai mentionne par le prospect (ex: "ce mois-ci", "dans 3 mois")';
COMMENT ON COLUMN conversation_analyse.montant_budget IS 'Budget mentionne en texte brut (ex: "25000 EUR", "350 EUR/mois")';
COMMENT ON COLUMN conversation_analyse.apport_prevu IS 'Apport mentionne en texte brut';
COMMENT ON COLUMN conversation_analyse.mode_financement IS 'Comptant | Credit | LOA/LLD | Pas encore reflechi';
COMMENT ON COLUMN conversation_analyse.accord_banque IS 'Statut accord bancaire (Oui / Non / En attente)';
COMMENT ON COLUMN conversation_analyse.reprise_conditionne_achat IS 'true si la reprise du vehicule conditionne l achat';
