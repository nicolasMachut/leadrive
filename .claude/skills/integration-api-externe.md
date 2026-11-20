# Skill : Integration API externe

## Declencheur

Ce skill s'active des qu'une tache implique d'appeler ou de configurer une API tierce :
- Creation / modification d'un client REST (Twilio, Anthropic, Brevo, Clerk, Stripe, etc.)
- Mise a jour d'un webhook recu d'une plateforme externe
- Modification du payload d'une requete sortante
- Parsing d'une reponse JSON d'une API tierce
- Configuration d'auth (OAuth, Basic, API Key) pour un service externe

Ce skill **complete** `expert-dev.md` — il y ajoute une discipline specifique aux dependances tierces dont le contrat n'est pas dans notre repo et peut changer sans qu'on en soit notifie.

## Principe fondamental

**Quand on appelle une API externe, on lit la doc officielle a jour AVANT d'ecrire ou modifier le code.** Pas les souvenirs du LLM, pas un blog post, pas un Stack Overflow de 2022. La doc canonique de l'editeur, fetchee maintenant.

**Pourquoi cette discipline** : sur ce projet on a perdu 8 allers-retours en prod sur Twilio Senders v2 (form-encoded vs JSON, profile.name manquant, vertical="AUTO" rejete, profile mal place dans configuration, etc.) parce que l'integration etait basee sur un mix de memoire LLM + ancien pattern Twilio SMS. Chacun de ces bugs aurait ete evite par une lecture prealable de la doc Senders v2.

## Workflow obligatoire

### Etape 1 — Identifier la / les docs canoniques

Avant tout code :
1. Trouver la **doc officielle** de l'editeur (pas un fork, pas un mirror).
2. Identifier la **version exacte** de l'API utilisee (v1, v2, beta…). Beaucoup d'editeurs maintiennent plusieurs versions en parallele avec des contrats differents (Twilio Messaging API vs Twilio Senders v2 = 2 contrats incompatibles).
3. **WebFetch** sur l'URL exacte des endpoints concernes pour avoir le contenu le plus recent.

Exemples de URLs canoniques :
- Twilio : `https://www.twilio.com/docs/...`
- Anthropic : `https://docs.anthropic.com/...` ou `https://docs.claude.com/...`
- Stripe : `https://docs.stripe.com/...` ou `https://docs.stripe.com/api/...`
- Clerk : `https://clerk.com/docs/...`
- Brevo : `https://developers.brevo.com/...`

Si la doc renvoie 404, l'API a peut-etre ete renommee / restructuree : chercher la nouvelle URL via le moteur de recherche de l'editeur, pas via Google.

### Etape 2 — Verifier point par point (avant et apres ecriture du code)

Pour chaque appel API, verifier dans la doc :

1. **Endpoint** : URL exacte, version, methode HTTP (POST / PATCH / PUT / DELETE).
2. **Content-Type** :
   - JSON moderne : `application/json`
   - APIs Twilio historiques : `application/x-www-form-urlencoded`
   - Twilio Senders v2 : JSON strict (cf. piege Twilio sur ce projet)
3. **Auth** : Bearer, Basic, API Key in header, dans query param, etc.
4. **Noms de champs exacts** :
   - Casing : `snake_case` (Twilio), `camelCase` (Stripe), `PascalCase` (Microsoft Graph)
   - Singulier / pluriel : `offline_reasons` (array), `display_name_rejection_reason` (champ unique)
   - Format : URL, ISO 8601 datetime, E.164 phone, etc.
5. **Structure** : top-level vs nested.
   - Twilio Senders v2 : `profile` est top-level (pas dans `configuration`), `webhook` est top-level avec ses propres sous-champs.
6. **Enumerations** : la doc liste les valeurs autorisees mot pour mot.
   - Exemple : vertical Twilio = `"Automotive"` (pas `"AUTO"` ni `"automotive"`).
7. **Required vs optional** : un champ marque required produit une 4xx s'il manque, meme s'il "a l'air optionnel".
8. **Reponses** :
   - Codes HTTP attendus en succes (200, 201, 204 sans body).
   - Format du body retourne (parser tres precisement les noms de champs en GET — la reponse peut avoir des cles differentes du body de POST).
   - Format des erreurs (Twilio : `{code, message, more_info, status}`, Stripe : `{error: {type, code, message}}`).

### Etape 3 — Apres ecriture, relire la doc une seconde fois

Apres avoir ecrit le code mais AVANT le commit :
- Diff le code contre la doc, champ par champ.
- Verifier que les enumerations utilisees sont **copiees mot pour mot** depuis la doc (et pas inferees).
- Verifier que rien d'autre n'est requis par la doc (champ `required` souvent enfoui dans un sous-bloc).

### Etape 4 — Test en dry-run / sandbox quand possible

Pour les APIs qui ont un mode sandbox (Stripe test mode, Twilio test credentials, Anthropic dev key) :
- Tester l'appel en sandbox AVANT de pousser en prod.
- Logger la requete sortante complete (sans secrets) pour pouvoir comparer.

## Anti-patterns explicites a fuir

- **"Je me souviens que Twilio accepte du form-encoded"** : NON. Verifier sur la doc de l'endpoint specifique.
- **Copier-coller un blog post** : ils sont souvent obsoletes. Verifier sur la doc officielle.
- **"On va d'abord essayer en prod, on verra"** : 1 cycle deploiement Railway = 2-3 min. Chaque bug evite par lecture doc = 1 cycle epargne. La doc se lit en 1 min.
- **Reutiliser le pattern d'un autre endpoint du meme editeur** : Twilio Messaging API et Senders v2 ont des contrats incompatibles. Ne JAMAIS supposer.

## Reference pour ce projet

Les integrations externes actuellement utilisees dans Leadrive :
- **Twilio** :
  - Messaging API (SDK officiel) : `TwilioWhatsAppService.java`
  - Senders v2 (REST direct) : `TwilioSendersApiClient.java`
  - Webhooks : `WebhookController.java`
- **Anthropic / Spring AI** : Claude Haiku 4.5, configure via Spring AI
- **Clerk** : auth + organizations, frontend `@clerk/nextjs`

Avant de modifier l'un de ces clients ou de creer un appel API a un nouvel editeur, **applique le workflow ci-dessus**.
