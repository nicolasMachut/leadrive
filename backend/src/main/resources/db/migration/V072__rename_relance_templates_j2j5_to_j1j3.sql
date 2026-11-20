-- Normalise en base les templates de relance renommes cote code : les anciens
-- noms leadrive_relance_j2 / leadrive_relance_j5 (tenants onboardes AVANT le
-- renommage) deviennent leadrive_relance_j1 / leadrive_relance_j3.
--
-- Pourquoi : le code d'envoi (RelanceWhatsAppService) cherche desormais
-- leadrive_relance_j1 / _j3. Un tenant pre-renommage n'a que les lignes j2/j5 en
-- base -> template introuvable -> ses relances J+1/J+3 ne partent pas (silencieux),
-- et le bouton "Re-soumettre" echoue sur ces lignes ("Template introuvable dans le
-- classpath", le YAML j2/j5 n'existe plus).
--
-- Choix : RENOMMAGE SEUL, sans toucher au statut ni au template_sid. On preserve
-- ainsi l'approbation Meta existante -> les templates deja approuves continuent
-- d'envoyer immediatement (le SID pointe sur le Content approuve). Une eventuelle
-- mise a jour du contenu (nouveaux textes / categorie UTILITY) se fait ensuite via
-- le bouton admin "Re-soumettre" (self-heal), pas ici : reinitialiser en SQL
-- laisserait les tenants non-soumis, sans envoi possible, jusqu'a une action manuelle.
--
-- Garde anti-collision : contrainte UNIQUE (tenant_id, template_name). On ne renomme
-- que si le tenant n'a pas deja une ligne au nouveau nom (cas theorique). Les rares
-- orphelins non renommables restent inertes (purges par resoumettreUn/resoumettreTous).

UPDATE tenant_whatsapp_template t
   SET template_name = 'leadrive_relance_j1'
 WHERE t.template_name = 'leadrive_relance_j2'
   AND NOT EXISTS (
       SELECT 1 FROM tenant_whatsapp_template t2
        WHERE t2.tenant_id = t.tenant_id
          AND t2.template_name = 'leadrive_relance_j1');

UPDATE tenant_whatsapp_template t
   SET template_name = 'leadrive_relance_j3'
 WHERE t.template_name = 'leadrive_relance_j5'
   AND NOT EXISTS (
       SELECT 1 FROM tenant_whatsapp_template t2
        WHERE t2.tenant_id = t.tenant_id
          AND t2.template_name = 'leadrive_relance_j3');
