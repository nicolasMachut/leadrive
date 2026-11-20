-- Une conversation WhatsApp peut etre demarree par le SYSTEME (envoi automatique
-- du premier message via PremierMessageAutoJob), sans utilisateur declencheur.
-- Dans ce cas user_id est null. La contrainte NOT NULL faisait echouer l'INSERT
-- (SQLState 23502) -> envoi auto jamais realise, bandeau "message auto" bloque.
-- La FK fk_sms_conv_user tolere deja null (un FK n'impose pas la presence).
ALTER TABLE sms_conversation ALTER COLUMN user_id DROP NOT NULL;
