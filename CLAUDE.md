# CLAUDE.md — Leadrive

## Projet

Leadrive est un SaaS B2B dedie a la performance commerciale sur les leads dans les concessions automobiles en France. Le produit est un outil d'activation qui se connecte par-dessus les DMS/CRM existants pour exploiter les donnees dormantes et declencher des actions automatiques pilotees par l'IA.

### Modules

1. **Vitesse et traitement temps reel** : alertes vendeur + messages auto si pas de rappel en 15 min
2. **Reactivation des leads dormants** : sequences de relance IA personnalisees
3. **Reactivation base historique** : detection cycles de renouvellement 2-4 ans
4. **Coaching transversal** : tableau de bord manager + recommandations IA hebdomadaires

### MVP v1

Import CSV (pas d'integration DMS). WhatsApp via Twilio.

---

## Stack technique

| Couche         | Technologie                                  |
|----------------|----------------------------------------------|
| Back-end       | Java 25 + Spring Boot 4.0.5                  |
| Framework IA   | Spring AI + Anthropic Claude Haiku 4.5       |
| Base de donnees| PostgreSQL 16+                               |
| Migrations     | Flyway                                       |
| Cache          | Spring Cache + Caffeine (en memoire)         |
| Mapping        | MapStruct + Lombok                           |
| Serialization  | **Jackson 3** (`tools.jackson.*`) — PAS Jackson 2 (`com.fasterxml.jackson.*`) |
| Front-end      | React (Next.js) + shadcn/ui + Tailwind CSS   |
| WhatsApp       | Twilio                                       |
| Auth           | Spring Security + JWT                        |
| CI/CD          | GitHub Actions                               |
| Hebergement    | Railway                                      |

---

## Structure monorepo

```
leadrevive/
├── backend/                      # Spring Boot
│   ├── src/main/java/fr/leadrevive/
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── application-local.yml
│   │   └── db/migration/         # Flyway
│   └── pom.xml
├── frontend/                     # Next.js
├── docs/
├── .github/workflows/
├── docker-compose.yml
├── CLAUDE.md                     # Ce fichier
├── CONVENTIONS.md                # Regles de code detaillees
└── ARCHITECTURE.md               # Architecture et modules
```

---

## Conventions de code (resume)

Regles detaillees dans `CONVENTIONS.md`. Points cles :

- **Pas de `var`** : typage explicite partout
- **`final` partout** : parametres, variables locales, champs injectes
- **Nommage** : classes en anglais PascalCase, champs/variables en francais camelCase, BDD en francais snake_case
- **DTOs** : `Rep[Domaine]Dto` (response), `Req[Action][Domaine]Dto` (request), `[Domaine]Dto` (generique)
- **Entities** : `@Getter @Setter` (pas `@Data`), pas de suffixe "Entity", heritent de `BaseEntity`
- **Services** : `@Service @RequiredArgsConstructor @Slf4j`, log d'entree/sortie en francais, `@Transactional`
- **Controllers** : pas de logique metier, retour direct sauf si status HTTP specifique
- **Mappers** : `@Mapper` MapStruct, tous les `@Mapping` explicites
- **Exceptions** : `extends RuntimeException`, `@RestControllerAdvice` pour le handler
- **Injection** : par constructeur uniquement, pas de `@Autowired`
- **Multi-tenant** : `tenant_id` obligatoire sur toutes les entities metier

---

## Architecture

Architecture detaillee dans `ARCHITECTURE.md`. Points cles :

- **Monolithe modulaire** : un seul deployable Spring Boot, modules par package
- **Couches** : Controller → Service → Builder → Mapper/Repository
- **Multi-tenant** : filtre Hibernate global sur `tenant_id`, extrait du JWT
- **Package racine** : `fr.leadrevive`
- **Modules** : lead, tenant, user, ai, outreach, sequence, coaching, notification, csvimport

---

## Skills disponibles

Les skills Claude Code sont dans `.claude/skills/` :

| Skill              | Usage                                          |
|--------------------|-------------------------------------------------|
| `create-entity`    | Creer une entity JPA + migration Flyway         |
| `create-repository`| Creer un repository JPA                         |
| `create-dto`       | Creer des DTOs Rep/Req                          |
| `create-mapper`    | Creer un mapper MapStruct                       |
| `create-service`   | Creer un service + builder                      |
| `create-controller`| Creer un controller REST                        |
| `create-exception` | Creer une exception custom + handler            |
| `create-domain`    | Creer un domaine complet (tous les fichiers)    |
| `create-test`      | Creer des tests unitaires avec Fixtures EasyRandom |
| `expert-dev`       | Expert Java 25/Spring Boot 4/React — verifie versions et bonnes pratiques via web avant de coder |

---

## Documentation technique

Le dossier d'analyse technique complet est dans `docs/Leadrive_Analyse_Technique.pdf`. Points cles a retenir :

- **Backlog MVP** : 8 epics (Fondations, Import leads, Moteur IA, Relance auto, Module vitesse, Base historique, Dashboard manager, Polish)
- **Planning** : MVP cible octobre 2026, demo fonctionnelle mi-aout 2026
- **Strategie de test** : couverture 60-70% concentree sur la logique metier. Tests unitaires (moteur sequences, validation) = critique. Tests integration (import CSV) = critique. Tests e2e = reportes post-MVP.
- **Resilience** : retry backoff exponentiel sur envois, dead letter queue, circuit breaker OpenAI (Resilience4j), idempotence des envois
- **Observabilite** : logs structures JSON, Actuator health checks, dashboard admin (messages envoyes, taux echec, cout IA)
- **RGPD** : DPA obligatoire avec chaque concession avant pilote. La concession = responsable de traitement, Leadrive = sous-traitant.
- **Couts pilote** : Railway 25-40e/mois, OpenAI 20-40e/mois, Twilio ~10-20e/mois (pay-as-you-go)
- **Differenciateur** : qualite des messages IA = moat technique. Investir dans le prompt engineering.

---

## Commandes utiles

```bash
# Dev local
cd backend && mvn clean compile -q          # Compilation
cd backend && mvn test                       # Tests
docker-compose up -d                         # PostgreSQL local

# Flyway
cd backend && mvn flyway:migrate             # Appliquer migrations
cd backend && mvn flyway:info                # Statut migrations

# Frontend
cd frontend && npm run dev                   # Dev server
cd frontend && npm run build                 # Build production
```

---

## Regles importantes

1. **Toujours lire CONVENTIONS.md** avant de creer du code Java
2. **Utiliser le skill `expert-dev`** avant toute implementation : verifier les versions et APIs via recherche web
3. **Utiliser les skills de creation** pour creer les composants (entity, service, controller, etc.)
4. **Ne jamais oublier `tenant_id`** sur les entities metier
5. **Flyway** : ne jamais modifier une migration existante, toujours en creer une nouvelle
6. **Logs en francais** avec format `"Description - cle: {}"`
7. **Compiler apres chaque modification** : `cd backend && mvn clean compile -q`
8. **Tester** : au minimum les services (logique metier) et l'import CSV
9. **TOUJOURS verifier que le build passe AVANT de commit** (back ET front) :
   - Backend : `cd backend && JAVA_HOME=$JAVA_HOME mvn clean compile -q`
   - Frontend : `cd frontend && npm run build`
   - Ne JAMAIS commit du code qui ne compile pas ou ne build pas. Un build casse sur Railway bloque le deploiement pour tout le monde.
   - **AVANT chaque commit de code non trivial** : lancer le `bug-hunter` ET le `code-reviewer` (`oh-my-claudecode:code-reviewer`) sur le diff (en parallele), consolider un rapport priorise P0-P3, et corriger au moins les P0/P1 avant de committer. La rigueur d'audit doit s'appliquer en continu, pas seulement en fin de cycle.
10. **Persister l'etat dans l'URL** : filtres, onglets actifs, elements selectionnes (conversation, lead) doivent etre refletes dans les query params de l'URL. Un F5 doit restituer l'etat exact de la page. Utiliser `router.replace(..., { scroll: false })` pour mettre a jour sans recharger.
11. **Jackson 3 obligatoire** : Spring Boot 4 utilise Jackson 3 (`tools.jackson.*`). Ne JAMAIS importer `com.fasterxml.jackson.*` (Jackson 2.x, present dans le classpath via le SDK Anthropic mais aucun bean ObjectMapper Jackson 2 n'est expose - injection echoue au demarrage). Detail dans `.claude/skills/expert-dev.md`.
12. **CSS : coherence stricte avec le design system** (UI de qualite professionnelle attendue) :
    - **TOUJOURS** utiliser les variables CSS du theme (`bg-accent`, `bg-success/10`, `text-warning`, `bg-[var(--muted)]`, `border-[var(--border)]`, `text-[var(--foreground)]`, `text-[var(--color-lr-text-secondary)]`, etc.) definies dans `frontend/src/app/globals.css`.
    - **INTERDIT** : couleurs Tailwind hardcodees (`bg-emerald-*`, `text-amber-*`, `bg-red-500`, `bg-zinc-*`, `bg-cyan-*`, `text-indigo-*`, etc.). Mapping : emerald → success, amber → warning, red → danger, blue/indigo/cyan → accent, zinc/gray → `--color-lr-*` ou `--muted`/`--border`.
    - **Pas de duplication** : reutiliser les composants de `frontend/src/components/common/` (Button, Alert, Card, Modal, Input, Tooltip, InfoIcon, StatutBadge, ProgressionBadge, ScoringGauge). Avant de coder un badge/bouton/alerte inline, verifier qu'un composant existe deja.
    - **Spacing standardise** : `p-3` (sm), `p-4` (md), `p-6` (lg). Ne pas inventer `p-5` ou `p-7`.
    - **Typo coherente** : titres en `text-sm font-semibold` pour les bannieres, hierarchies de gris via `--color-lr-text-secondary` (sous-titres) et `--color-lr-text-tertiary` (meta) plutot que `text-gray-X`.
    - Exception : bannieres full-width (style `AlerteRappelBanner`) peuvent garder la palette Tailwind explicite pour le rendu marketing, mais doivent rester coherentes entre elles.

## Rigueur de codage — appliquer le niveau d'audit DES la 1ere ecriture (TRES IMPORTANT)

J'ai ecrit 100% du code de cette application. Chaque session d'audit (mai 2026) a revele
des dizaines de bugs P0/P1 que j'aurais du eviter au moment de coder. La rigueur que je
mets a CHASSER les bugs en audit doit etre EQUIVALENTE a celle que je mets a NE PAS LES
INTRODUIRE au moment du code. Sinon le projet fait du sur-place : je corrige autant que
j'introduis.

**Checklist avant de considerer une fonctionnalite "finie"** (pas seulement "ca compile") :

### Backend — chaque service / endpoint mutateur
- [ ] **Multi-tenant** : tenant_id verifie (filtre Hibernate) ET ownership verifiee (le user peut-il toucher CET objet ? VENDEUR peut-il modifier les taches/leads d'un AUTRE vendeur ?).
- [ ] **Statut / cycle de vie** : toutes les transitions sont-elles valides ? Une conversation STOP_DEMANDE / TERMINEE est-elle proteggee contre la reprise / les actions IA ? Un opt-out RGPD est-il garanti ?
- [ ] **Dates et bornes** : dates dans le passe acceptees ? Trop loin dans le futur (>180j) ? Division par zero possible (totalLeads=0 sur stats) ?
- [ ] **Idempotence** : si le webhook / job tourne 2x ou que Twilio retry, on envoie 2 messages au prospect ? Cache local Caffeine ne suffit PAS en multi-instance Railway (utiliser table DB ou Redis).
- [ ] **N+1 / `findAll`** : aucun `repository.findAll()` cross-tenant en service. Aucune boucle `for (x : liste) { repo.findByX(x.id) }`. Toujours en batch query.
- [ ] **Catch generique** : un catch qui masque l'erreur metier (`catch (Exception e) { return statut_ECHEC }`) cache un cas que l'utilisateur DOIT savoir. Privilegier le pre-check + exception remontee + handler global qui renvoie 4xx avec message clair.
- [ ] **Validation IA** : si l'IA retourne `rdvConfirme=true` SANS `rdvDateHeure`, c'est une hallucination. Garde-fou obligatoire avant d'engager un effet de bord (creation tache, transition statut).
- [ ] **PII dans logs** : aucun email/telephone/contenu de message en clair dans `log.info/warn/error`. Toujours via `PiiMasker.email/phone/content/name`. Aucun token (reset/invitation) jamais loggé.
- [ ] **24h-window WhatsApp** : tout envoi >24h apres dernier message prospect doit utiliser un template Meta-approuve, sinon Twilio rejette.

### Frontend — chaque composant qui appelle l'API
- [ ] **Silent catch interdit** : `catch { /* silent */ }` ou `.catch(() => {})` MASQUE un bug pour l'utilisateur. Au minimum afficher une toast/banniere "Action echouee, reessayez". Sentry capture peut-etre, mais l'utilisateur doit savoir.
- [ ] **Optimistic update** : toujours prevoir un rollback si l'API echoue, ET afficher le message d'erreur (pas seulement annuler visuellement).
- [ ] **401 / 403** : `useApiClient` redirige vers /login. Ne pas re-implementer un fetch direct qui passerait outre.
- [ ] **Boutons disabled** : si une feature est conditionnelle (WhatsApp non configure, mode incompatible), le bouton doit etre VISUELLEMENT desactive + tooltip explicatif. Pas le laisser cliquable pour echouer silencieusement cote backend.
- [ ] **Filtres + URL** : tout filtre/recherche/page doit etre dans les query params (CLAUDE.md regle #10). F5 doit restituer l'etat. Et un changement de filtre doit `setPage(0)`.
- [ ] **Cleanup useEffect** : tout `setInterval` / `setTimeout` / `EventSource` / `fetch` avec `AbortController` doit avoir son cleanup dans le return du useEffect.

### Tests
- [ ] Pas que le chemin nominal. **Au moins 1 test par chemin d'erreur** (4xx, exception metier, edge case donnees).
- [ ] Aucun appel reel a Anthropic / Twilio (mockés). Cle bidon dans `application-test.yml` empeche l'envoi accidentel.

### Avant de declarer "fini"
- [ ] `mvn clean compile` ET `mvn test` ET `npm run build` passent.
- [ ] Aide-app.txt + `frontend/src/app/aide/page.tsx` + `docs/FONCTIONNALITES_MVP.md` mis a jour si comportement visible client change.

Si je deroge a cette checklist en codant, je m'engage moi-meme dans une dette qui demandera un nouvel audit. Le but est de **ne pas avoir besoin de relancer un audit comme celui de mai 2026 tous les 2 jours**.

## Définition canonique du « RDV pris » (FACTURABLE — TRES IMPORTANT)

**On facture le client au RDV pris.** La notion doit donc être **identique partout**, **non-manipulable** et **sans erreur**. Une seule définition fait foi dans tout le code :

> **Un « RDV pris » = un événement immuable et horodaté dans `historique_statut` : une transition vers `RDV_PRIS` de source `"IA"`.** Compté **une fois par lead distinct** sur la période considérée (par date de l'événement `cree_le`).

### Pourquoi cette définition (et pas une autre)

- **Non-manipulable** : la source `"IA"` n'est posée QUE par `ConversationAnalyseService` quand le prospect **confirme un RDV daté** en WhatsApp (garde-fou : `rdvConfirme == true && rdvDateHeure != null`, sinon refus). Une tâche RDV datée est créée dans la foulée. Le concessionnaire ne peut ni **fabriquer** un RDV (il faut une vraie confirmation prospect) ni le **minorer** (c'est automatique).
- **Les passages MANUELS ne comptent PAS** : un changement de statut via le Kanban (drag&drop) ou le dropdown de la fiche lead a `source = "DRAG_DROP"` / `"SELECTBOX"`. Ces transitions sont **exclues** de la facturation (sinon le compte serait gonflable/minorable à la main).
- **Immuable** : `historique_statut` est append-only. Si le lead repasse ensuite à un autre statut (annulation, `TERMINE`), l'événement **reste** → un RDV facturé ne disparaît jamais. On ne se base JAMAIS sur le statut *actuel* du lead (`lead.statut == RDV_PRIS`) ni sur le snapshot de conversation (`sms_conversation.statut == RDV_OBTENU`) pour facturer : ce sont des états **mutables**.
- **Borné dans le temps** par la date de la transition (`cree_le`), pas par la date de création du lead.

### Invariant produit à connaître

Quand l'IA détecte un RDV confirmé, elle pose **atomiquement** : `sms_conversation.statut = RDV_OBTENU` **ET** `lead.statut = RDV_PRIS` + trace la transition `historique_statut(statut_suivant=RDV_PRIS, source="IA")` + crée la tâche RDV. Donc `RDV_OBTENU` (conversation) et l'événement `IA→RDV_PRIS` (historique) désignent **le même RDV** ; la différence est que l'historique est **immuable** alors que le statut de conversation est **mutable**. **Pour tout comptage de RDV (facturation, stats, dashboard), utiliser l'événement historique `IA→RDV_PRIS`.**

### Règle d'implémentation (à réutiliser partout)

- Backend : filtrer `historique_statut` sur `statut_suivant = 'RDV_PRIS' AND source = 'IA'`, `COUNT(DISTINCT lead_id)`, borné par `cree_le` sur la période. Constante de référence : `PipelineStatsService.SOURCE_RDV_FACTURABLE = "IA"`.
- Méthodes de comptage canoniques disponibles : `HistoriqueStatutRepository.countRdvPrisIaSince` / `countRdvPrisIaBetween` / `countRdvPrisIaSinceForVendeur` / `countRdvPrisIaSinceByVendeur`. **Réutiliser ces méthodes** pour tout nouveau comptage de RDV (ne pas réécrire le filtre à la main).
- Implémentations alignées sur la définition canonique (juin 2026) : `PipelineStatsService` (`rdvPris`/`tauxRdv`, hero `/stats`), `LeadRepository.statsParSourceParTenant`/`statsParSourceGlobal` (taux par source), `DashboardStatsService` (dashboard global `rdvObtenus`, manager `rdvMois`/`rdvSemaine`/`rdvSemainePrec`, vendeur `rdvMois`/`rdvSemaine`), `BriefingDataService` (rdvHier/rdvSemaine + top vendeurs).
- **Ne jamais** recompter un RDV via `entonnoir[RDV_PRIS].count` (snapshot statut actuel) ni via `EXISTS sms_conversation.statut='RDV_OBTENU'` / `countByTenantIdAndStatut(RDV_OBTENU)` (mutable). Si tu ajoutes un nouvel écran/export qui compte des RDV, réutilise les méthodes du repository ci-dessus.
- Attribution par vendeur : par **propriétaire actuel du lead** (`lead.vendeur_id`), pas par le vendeur ayant tenu la conversation (l'événement `historique_statut` IA ne porte pas de `user_id`). Cohérent et réconciliable avec les totaux tenant (aux leads non assignés près).
- Seul usage légitime restant de `RDV_OBTENU` (snapshot conversation) : indicateurs **opérationnels** non facturables qui ont besoin de l'état courant de la conversation, ex. compteur live de l'inbox (`InboxService`) et liste « RDV en attente de confirmation » (`DashboardStatsService.listerRdvEnAttente`). Ce ne sont PAS des comptages de RDV pris par période.

## Vocabulaire user-facing (TRES IMPORTANT)

Les textes visibles par l'utilisateur (UI, modales, tooltips, emails, messages d'erreur, prompts IA d'aide, /aide page) ne doivent **JAMAIS** contenir certains termes techniques qui ne parlent pas a la cible (concessionnaire automobile / garagiste / revendeur multi-marques) :

### Termes interdits dans l'UI

| ❌ Interdit | ✅ A la place |
|---|---|
| **Meta** | "WhatsApp Business" / "validation" / "votre profil" / juste retirer |
| **Display name** | "Nom affiché" |
| **OTP** | "Code de vérification" |
| **Quality rating** | Ne pas afficher cote concessionnaire (info Meta interne) |
| **Messaging limit / Tier** | Ne pas afficher cote concessionnaire |
| **Webhook / Callback** | "Notification" si vraiment necessaire |
| **Sender / Sender SID** | "Numéro" / "Configuration" |
| **Concession** (en exclusif) | "Établissement" (englobe garages, multi-marques, revendeurs) |
| **Tenant** | "Établissement" / "Compte" |
| **API** | "Connexion" si vraiment necessaire |

### Pourquoi

- "Meta" : jargon technique, le concessionnaire n'a aucune visibilité ni contrôle sur Meta. Le mot est anxiogene ("le geant americain valide mon profil ?") et n'apporte rien. Le manager veut juste savoir : c'est valide ou pas.
- "Concession" : exclusif au monde Stellantis/Renault/etc. Notre cible inclut garages independants, revendeurs multi-marques, MRA. Toujours "établissement" pour rester inclusif.
- Termes techniques (OTP, webhook, etc.) : utilisables dans le **code** (variables, fonctions), JAMAIS dans les textes affiches.

### Application

Avant chaque commit qui touche un texte user-facing, **chercher ces termes** :
```
grep -rn "Meta\|display name\|OTP\|webhook\|concession" frontend/src --include="*.tsx" --include="*.ts"
grep -rn "Meta\|display name\|OTP\|webhook" backend/src/main/resources/prompts/
```

Et reformuler. Les noms internes de variables / classes / colonnes BDD peuvent garder ces termes (pas visibles a l'utilisateur).

## Formatage des numeros de telephone (TRES IMPORTANT)

**Tout numero de telephone affiche a l'utilisateur DOIT etre formate avec des espaces** pour rester lisible. Jamais d'E.164 brut "+33700000001", toujours "+33 7 00 00 00 01".

Helper deja en place dans `frontend/src/lib/phone.ts` :
- `formatPhoneInput(value)` : ajoute les espaces (`+33 7 00 00 00 01` ou `06 12 34 56 78`)
- `sanitizePhoneInput(value)` : retire les espaces (saisie -> stockage)
- `toE164(value)` : convertit FR national en E.164 avant envoi backend
- `isValidPhone(value)` : valide regex E.164 FR/international

### Regles

| Cas | Quoi utiliser |
|---|---|
| **Affichage** dans l'UI (cards, listes, modales, mockups, exports CSV) | `formatPhoneInput(numero)` |
| **Input** controle par l'utilisateur | `value={formatPhoneInput(state)}` + `onChange={e => setState(formatPhoneInput(e.target.value))}` |
| **Avant POST/PUT backend** | `toE164(state)` ou `sanitizePhoneInput(state)` selon le contexte |
| **Validation cote client** | `isValidPhone(state)` avant submit, bordure rouge + message si invalide |
| **Stockage en BDD / API Twilio** | E.164 strict sans espaces (`+33700000001`) |

### Check rapide avant commit

```bash
grep -rn "whatsappFrom\}\|telephoneConcession\}\|telephone\}" frontend/src --include="*.tsx" | grep -v "formatPhoneInput\|sanitize\|toE164"
```

Si un match -> probablement un affichage brut a corriger.

### Pourquoi

Un numero "+33700000001" est illisible : l'oeil doit compter les chiffres. Avec espaces, lecture instantanee. C'est une convention nationale (les operateurs, banques, administrations FR utilisent tous le format espace). Pareil pour l'international : "+44 7700 123 456" plutot que "+447700123456".

## Pas d'em-dashes (—) dans les textes français (TRES IMPORTANT)

L'em-dash `—` est une signature classique d'IA en français. En France, on ne l'utilise quasiment jamais à l'écrit. Le voir partout dans le code (textes UI, prompts IA, docs visibles utilisateur) crie "rédigé par une IA".

**Règle** : zéro em-dash (`—`) dans les chaînes de caractères qui finissent dans l'UI, dans les prompts IA (`backend/src/main/resources/prompts/*.txt`), ou dans les docs utilisateur (page `/aide`). Remplacer par : virgule, point, deux-points, parenthèses, ou point-virgule selon le contexte.

Exception légitime : `—` comme placeholder de cellule vide dans un tableau (équivalent "n/a"). Là c'est typographique standard.

## Toujours accentuer les textes français (TRES IMPORTANT)

Tout texte français **généré** qui finit devant l'utilisateur ou qui guide la sortie de l'IA DOIT porter les bons accents (é è ê ë à â ç ù û î ï ô œ). Écrire « verifie », « etablissement », « creer », « reponse », « numero », « deja », « tres », « 2 a 5 jours » au lieu de « vérifié », « établissement », « créer », « réponse », « numéro », « déjà », « très », « 2 à 5 jours » est un défaut de qualité visible (et fait « bâclé / traduit par une machine »).

**Règle** : quand je rédige ou modifie une chaîne dans ces emplacements, je mets systématiquement les accents corrects :
- textes UI (`frontend/src/**` : labels, messages, toasts, modales, tooltips, copy)
- prompts IA (`backend/src/main/resources/prompts/*.txt`) — le chatbot reprend ce texte
- docs utilisateur (`frontend/src/app/aide/page.tsx`, `docs/*.md` visibles client)
- emails, messages d'erreur affichés

**Exceptions (restent sans accents)** : identifiants de code (classes, méthodes, variables), noms de colonnes BDD, clés d'enum, valeurs techniques. Les **logs** backend peuvent rester sans accents (convention existante, évite les soucis d'encodage console).

Avant de livrer un texte FR, relire pour les mots fréquents mal accentués : `établissement, vérifié/vérification, créer/créé, réponse, numéro, téléphone, préféré, géré, déjà, après, très, à, française, problème, paramètres, données`.

Note : le fichier `backend/src/main/resources/prompts/aide-app.txt` est historiquement rédigé sans accents (dette à reprendre globalement). Toute nouvelle ligne ou ligne modifiée dans ce fichier doit être accentuée correctement.

## Maintenir l'agent bug-hunter a jour (TRES IMPORTANT)

A chaque fois qu'un bug est decouvert (que ce soit par moi, par un utilisateur, ou en cours de dev), tu DOIS ajouter une ligne au fichier `.claude/agents/bug-hunter.md` decrivant :

- **Le pattern du bug** (en 1 phrase)
- **Le pattern a grep / la cible a inspecter** pour le retrouver
- **La regle a respecter** pour eviter qu'il revienne

Choisis la categorie existante la plus pertinente (Authorization, Multi-tenant, Soft delete, etc.) ou cree une nouvelle section si le bug ne rentre dans aucune. Objectif : que le bug ne se reproduise plus jamais ailleurs dans le code, et que `bug-hunter` le detecte automatiquement lors du prochain audit.

Cette regle est non-negociable : un bug fixe sans entree dans bug-hunter.md a vocation a reapparaitre.

## Maintenir l'aide a jour (TRES IMPORTANT)

L'application embarque deux aides distinctes, qui doivent **toutes les deux** refleter l'etat reel de l'app :
1. **Le CHATBOT d'aide** (bouton `?` flottant) : un assistant IA conversationnel qui repond en langage naturel. **Son cerveau = le system prompt `backend/src/main/resources/prompts/aide-app.txt`.** Si ce fichier est faux/perime, le chatbot **invente** ou donne de mauvaises reponses aux utilisateurs. C'est le piege n°1 a ne jamais oublier : mettre a jour le chatbot = mettre a jour `aide-app.txt`.
2. **La page d'aide statique** `/aide` (`frontend/src/app/aide/page.tsx`) : documentation lisible directement.

A chaque fois que tu modifies une fonctionnalite visible par l'utilisateur (nouvelle page, nouveau statut, nouveau workflow, renommage, suppression, changement de calcul/sens d'un indicateur), tu DOIS mettre a jour les 3 endroits :

1. **`backend/src/main/resources/prompts/aide-app.txt`** (= le CHATBOT, system prompt LLM).
   - Ajoute / modifie / retire la section concernee.
   - Verifie que les routes mentionnees existent toujours.
   - Verifie que les noms de statuts/champs/boutons/indicateurs cites correspondent au code.
   - Verifie la coherence des permissions decrites (ce qu'un VENDEUR voit vs un MANAGER).

2. **`frontend/src/app/aide/page.tsx`** : page de documentation statique.
   - Section concernee dans `SECTIONS` (ajoute une nouvelle section si fonctionnalite majeure).
   - Met a jour la FAQ si une question frequente change.

3. **`docs/FONCTIONNALITES_MVP.md`** : liste maitre des fonctionnalites livrees.

Si tu fais un changement de fonctionnalite **sans** mettre a jour ces 3 endroits, le chatbot ET la page d'aide deviendront faux et donneront de mauvaises reponses aux utilisateurs. Considere ca comme partie integrante du Definition of Done. **Le chatbot (`aide-app.txt`) est le plus facile a oublier car il n'est pas "visible" dans le code UI : pense-y systematiquement.**

## Maintenir les skills a jour automatiquement (TRES IMPORTANT)

Les skills dans `.claude/skills/*.md` encodent les conventions du projet (comment gerer les erreurs, creer une entity, un service, etc.). Ils doivent **toujours refleter les conventions reellement en vigueur**.

**Regle, non-negociable** : des qu'une convention, un pattern ou une regle recurrente emerge ou evolue au fil d'une conversation (ex. "toujours gerer les erreurs comme ceci", "tous les numeros formates avec espaces", "tel composant partage remplace tel inline"), tu DOIS, **sans qu'on te le redemande** :

1. **Mettre a jour le skill existant** le plus pertinent (`.claude/skills/<nom>.md`), ou **en creer un nouveau** si aucun ne couvre le sujet (meme format : `# Skill : <titre>` + `## Declencheur` + sections + checklist).
2. Si c'est un bug ou un anti-pattern a empecher : ajouter aussi l'entree dans `.claude/agents/bug-hunter.md` (cf. section dediee ci-dessus).
3. Si ca touche le comportement user-facing : mettre a jour l'aide (cf. section ci-dessus).

Le but : l'utilisateur ne doit PAS avoir a repeter les memes consignes. Une convention enoncee une fois = capturee dans le skill correspondant, appliquee automatiquement ensuite. Considere la mise a jour des skills comme partie integrante du Definition of Done, au meme titre que le build qui passe.

Skills de convention transverses existants a garder a jour : `gestion-erreurs.md` (gestion des erreurs frontend), `expert-dev.md` (versions/bonnes pratiques). Les skills `create-*` couvrent la creation de composants backend.

## Maintenir la suite de tests de non-regression des prompts IA (TRES IMPORTANT)

Les prompts IA (`backend/src/main/resources/prompts/whatsapp-conversation.txt` = generation de la conversation, et `whatsapp-extraction.txt` = extraction JSON structuree) sont couverts par une **suite de tests de non-regression pilotee par prompt** : `backend/src/main/resources/prompts/prompt-tests-regression.txt`. C'est un fichier d'instructions qu'un agent execute (il lit les VRAIS prompts du projet, joue les roles IA + prospect, et produit un rapport PASS/FAIL). Module A = conversation, **Module B = extraction JSON**, C-J = fiche/briefings/coherence/terrain.

**Regle, non-negociable** : a **chaque fois qu'un bug est corrige sur le prompt de conversation OU d'extraction** (ou qu'une regle de prompt evolue), tu DOIS, **sans qu'on te le redemande**, ajouter le(s) scenario(s) de non-regression correspondant(s) dans `prompt-tests-regression.txt` :

1. Choisir le bon module (A = conversation, B = extraction JSON) et le sous-module le plus pertinent.
2. Ecrire le scenario au format existant : `### <id> <titre>` + `- Historique :` (si utile) + `- Attendu :` (le comportement CORRECT, pas le bug) + `- Raison :` + `- Verifier :`. Taguer `[BUG xxx]` ou `[FIX xxx <date>]`.
3. Encoder le comportement **ATTENDU (correct)** : le scenario doit PASSER une fois le prompt corrige, et documenter ce qui etait casse. Pour une date, asserter le JOUR et l'HEURE (pas la minute) ; pour un champ enum/booleen, valeur exacte ; pour les champs libres (`resumeVendeur`), assertions "contient" / jugement qualitatif.
4. Mettre a jour le compteur du module (`(N scenarios)`) et la ligne de `## Version` en tete.

Un bug de prompt corrige **sans** scenario ajoute ici a vocation a reapparaitre (meme logique que `bug-hunter.md` pour le code). Considere cet ajout comme partie integrante du Definition of Done d'un fix de prompt. Precedent : bugs RDV-DATE (mauvais jour sur "vers 15h" / "demain" perime) et RDV-RESCHEDULE (re-planning d'un RDV deja acte marque "a confirmer") -> Module B, sous-modules B8 et B9 (juillet 2026).
