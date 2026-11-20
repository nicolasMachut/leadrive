-- Permet de rejouer fidelement un message envoye via Content Template Twilio
-- (premier contact, relance, rappel RDV) lors d'un "Reessayer".
--
-- Sans ces colonnes, le retry renvoyait le texte rendu en message de SESSION
-- (body libre), ce qui echoue systematiquement pour un premier contact :
-- hors fenetre 24h, Twilio rejette (63016) car ce doit etre un template.
--
-- content_sid       : Content SID Twilio du template utilise (NULL = message libre de session).
-- content_variables : variables du template serialisees en JSON {"1":"Jean","2":"Nora"}.
ALTER TABLE sms_message ADD COLUMN content_sid VARCHAR(64);
ALTER TABLE sms_message ADD COLUMN content_variables TEXT;
