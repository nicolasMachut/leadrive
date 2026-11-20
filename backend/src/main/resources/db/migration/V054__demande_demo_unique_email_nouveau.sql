-- Anti-spam demandes de demo : un meme email ne peut avoir qu'UNE demande au statut
-- NOUVEAU. Si une demande precedente est TRAITEE/REJETEE, l'email peut redemander.
--
-- Sans cette contrainte, un bot via VPN tournant peut bypasser le rate-limit IP et
-- inserer N copies de la meme demande => pollution du dashboard admin.
--
-- Avant d'ajouter l'index, on purge les eventuels doublons existants en gardant
-- la 1ere occurrence par email + statut.
DELETE FROM demande_demo
WHERE id_demande_demo NOT IN (
    SELECT MIN(id_demande_demo)
    FROM demande_demo
    WHERE statut = 'NOUVEAU'
    GROUP BY email
)
AND statut = 'NOUVEAU';

CREATE UNIQUE INDEX IF NOT EXISTS uk_demande_demo_email_nouveau
    ON demande_demo (email)
    WHERE statut = 'NOUVEAU';

COMMENT ON INDEX uk_demande_demo_email_nouveau IS
    'Empêche les doublons de demandes de démo en attente pour un même email.';
