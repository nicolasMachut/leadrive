-- Point 10 : adresse + telephone concession injectes dans le prompt WhatsApp
ALTER TABLE tenant_ai_config
    ADD COLUMN adresse_complete TEXT,
    ADD COLUMN telephone_concession VARCHAR(30);

-- Point 32 : tonCommercial supprime du modele (vouvoiement strict desormais impose dans le prompt)
ALTER TABLE tenant_ai_config DROP COLUMN IF EXISTS ton_commercial;

-- Point 11 : flag client irrite (set quand tonProspect=mecontent + besoinHandoff=true)
ALTER TABLE lead
    ADD COLUMN client_irrite BOOLEAN NOT NULL DEFAULT FALSE;

-- Point 22 + Q0bis : nouveaux champs d'extraction (essai routier + declencheur du projet)
ALTER TABLE conversation_analyse
    ADD COLUMN demande_essai BOOLEAN,
    ADD COLUMN raison_contact TEXT;
