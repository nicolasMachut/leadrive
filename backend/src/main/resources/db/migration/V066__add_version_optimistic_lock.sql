-- V066 : Ajout de la colonne `version` sur les entites les plus mutees
-- concurremment (webhook + scheduler + UI manager). Active l'optimistic
-- locking Hibernate (@Version) qui evite les "last writer wins" silencieux.
--
-- Comportement runtime : Hibernate ajoute automatiquement WHERE version = X
-- a chaque UPDATE et incremente la version. Si X ne matche pas (un autre
-- thread a modifie l'entite entre le read et le write), Hibernate throw
-- ObjectOptimisticLockingFailureException -> GlobalExceptionHandler renvoie
-- 409 CONFLIT_MODIFICATION avec message clair pour l'utilisateur.
--
-- Cas typiques anticipes :
--  - sms_conversation : webhook Twilio + scheduler relances + UI inbox toggle
--    mode IA/HUMAIN. Sans @Version, une UI toggle peut etre ecrasee par un
--    webhook qui arrive 2ms apres -> mode "humain" perdu silencieusement.
--  - lead : statut peut etre modifie par IA + manual UI + scoring IA en parallele.
--  - tache : webhook RDV report + UI manual mark done. Race possible.
--
-- DEFAULT 0 : tous les rows existants demarrent a 0, premier UPDATE passera
-- a 1. NOT NULL pour eviter les pieges Hibernate "null = 0 = same".

ALTER TABLE lead ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE sms_conversation ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tache ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN lead.version IS '@Version Hibernate, optimistic locking. Increment auto a chaque UPDATE.';
COMMENT ON COLUMN sms_conversation.version IS '@Version Hibernate, optimistic locking. Increment auto a chaque UPDATE.';
COMMENT ON COLUMN tache.version IS '@Version Hibernate, optimistic locking. Increment auto a chaque UPDATE.';
