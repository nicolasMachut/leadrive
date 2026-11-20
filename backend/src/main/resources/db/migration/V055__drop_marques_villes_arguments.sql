-- ============================================================================
-- V055 : suppression des champs marques / villes_principales / arguments_cles
--
-- Decision : ces 3 champs etaient configurables dans "Ma concession" mais
-- n'apportaient pas de valeur reelle.
--   - marques : utilise uniquement comme garde-fou anti-hallucination dans le
--     prompt IA (jamais cite explicitement par l'IA). Le stock evolue, le
--     champ se desynchronise avec la realite, et le prompt force deja l'IA
--     a renvoyer vers le conseiller pour toute question stock/marque precise.
--   - villes_principales : declare dans le prompt mais jamais mobilise par
--     un trigger concret. Doublon avec adresse_complete.
--   - arguments_cles : un seul script (gestion concurrence) mobilisait ces
--     atouts, et le prompt s'en passe sans degrader la qualite des reponses.
--
-- L'IA continue d'utiliser nom_concession, horaires_ouverture, adresse_complete
-- et telephone_concession qui restent dans tenant_ai_config.
-- ============================================================================

ALTER TABLE tenant_ai_config
    DROP COLUMN IF EXISTS marques,
    DROP COLUMN IF EXISTS villes_principales,
    DROP COLUMN IF EXISTS arguments_cles;
