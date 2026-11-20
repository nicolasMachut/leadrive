# Skill : Verifier les libs existantes avant de coder

## Declencheur

Ce skill s'active des qu'une tache implique d'implementer une fonctionnalite technique non-triviale (back ou front) :
- Parsing / serialisation (CSV, JSON, XML, Excel, dates, telephones, emails…)
- Manipulation de date / fuseaux horaires
- Validation de formats (E.164, IBAN, SIRET, etc.)
- Stockage / cache / file uploads / compression
- Algorithmes "evidents" (fuzzy match, diff, hash, retry, rate limit)
- Composants UI courants (modale, dropzone, autocomplete, datepicker, table avec tri)
- Cryptographie, signature, JWT, OAuth flows

Bref : tout ce qui ressemble a un probleme courant qui a deja ete resolu par quelqu'un d'autre.

## Principe fondamental

**Avant d'ecrire du code maison pour un probleme technique non-trivial, verifier qu'il n'existe pas deja une lib ou un outil maintenu qui le fait.** On ne reinvente pas la roue. Le code maison qu'on n'ecrit pas est le moins buggue et le moins couteux a maintenir.

**Pourquoi cette discipline** : sur ce projet on s'est deja fait piquer plusieurs fois (serialisation JSON manuelle des ContentVariables Twilio qui ne gerait pas l'escape des caracteres speciaux, par exemple). Et meme quand on ecrit "5 lignes" maison, on doit les tester, les documenter, les maintenir, gerer les edge cases. Une lib mainstream a souvent deja toutes les edge cases couverts.

## Workflow obligatoire

### Etape 1 — Identifier le probleme generique

Avant de coder, formuler le probleme de facon generique (pas en jargon projet) :
- "J'ai besoin de parser un email envoye par un client" (pas "j'ai besoin d'extraire le lead depuis l'email Citroen")
- "J'ai besoin d'afficher un selecteur de date" (pas "j'ai besoin d'un selecteur pour la date de RDV")

### Etape 2 — Chercher dans le projet d'abord

Avant de chercher sur internet, **chercher dans le repo** :
```
- backend/pom.xml : libs Java deja installees
- frontend/package.json : libs npm deja installees
- src/main/java/.../util/, .../common/ : utilitaires maison reutilisables
- src/components/common/ : composants UI partages
```

Si quelque chose existe deja (meme partiellement), partir de la.

### Etape 3 — Chercher sur internet

Si rien dans le projet :
1. **WebSearch** : `"[probleme generique] java library 2025"` ou `"[probleme] npm library 2025"` ou `"[probleme] react component library 2025"`.
2. Verifier pour chaque candidat :
   - **Maintenance** : derniere release < 12 mois, repo actif, issues fermees regulierement
   - **Adoption** : nombre de stars / downloads (>10k/sem npm, >100 stars github = stable)
   - **Compatibilite** : versions supportees (Java 25, React 19, Spring Boot 4 ici)
   - **License** : MIT / Apache / BSD acceptable. GPL = a discuter
   - **Taille** : pour npm, eviter les packages enormes (bundle phobia)
3. **Lire la doc** rapide pour s'assurer que le usage colle a notre cas (pas un mock-fonctionnel-en-fait).

### Etape 4 — Decision : lib ou maison ?

Prendre la lib si :
- Le besoin recouvre une vraie expertise (parsing CSV multi-formats, fuzzy match, date timezone, etc.)
- La lib couvre les edge cases qu'on n'a pas le temps de tester
- Maintenance active
- Pas de risque de supply chain (lib obscure d'un dev unique = a eviter)

Coder maison si :
- Besoin tres specifique non couvert par les libs trouvees
- Lib ajoute trop de poids pour ce qu'on en utilise (< 5 lignes de code maison vs 200 Ko de dep)
- Risque de supply chain trop eleve
- Toutes les libs trouvees sont mortes / obsoletes

**Toujours documenter le choix** dans le code (commentaire bref expliquant pourquoi on a choisi telle lib ou pourquoi on a fait maison).

### Etape 5 — Si lib choisie : ajouter proprement

- Backend Java : ajouter au `pom.xml` avec version explicite (pas de range).
- Frontend : `npm install <pkg>@<version>` puis verifier le `package.json`.
- Verifier que le build passe encore.

## Exemples concrets a appliquer dans Leadrive

| Probleme | A faire | A NE PAS faire |
|---|---|---|
| Parser un email | Lib type `mail-parser` (npm) ou parser SDK | Regex maison sur le body |
| Valider un email | `validator.js`, ou framework built-in | Regex personnalise |
| Format de date | `date-fns` (deja installe), `Temporal` API native | Manipulation manuelle de Date |
| Format telephone E.164 | `libphonenumber-js` | Notre helper actuel `phone.ts` (deja fait, OK pour FR/E.164 simple) |
| Drag-and-drop fichier | `react-dropzone` (mainstream, ~70k DL/sem) | Code maison comme on a fait dans `LogoDropzone` (OK pour le scope, mais a remplacer si besoin grandit) |
| Serialisation JSON | `ObjectMapper` Jackson (deja injecte cote back) | StringBuilder manuel |
| Fuzzy string match | `fuzzysort`, `fuse.js` | Damerau-Levenshtein maison |
| Slugify | `slugify` npm | Regex maison |
| Hash / signature | `crypto` native ou Bouncy Castle | Aucun cas |
| Excel parsing | `apache-poi` (deja installe) | Cas |
| HTTP retry / circuit | Resilience4j (deja installe) | Loop while maison |

## Anti-patterns explicites a fuir

- **"Je vais faire vite fait en regex"** : si la regex prend > 2 lignes ou si elle gere du formaye humain (date, telephone, email, URL), c'est qu'il faut une lib.
- **"5 lignes maison c'est plus simple que d'ajouter une dep"** : 5 lignes maison ne gerent pas les edge cases. Mesurer le vrai cout de maintenance.
- **"Je ne sais pas, je vais essayer"** : non, on cherche d'abord 2 minutes sur le web, on lit une doc, et on choisit.
- **Ajouter une lib pour 1 fonction de 3 lignes** : l'inverse. Si la dep ajoute 200 Ko pour `slugify`, on ecrit les 3 lignes.
- **Reinventer un composant UI mainstream** : modale, datepicker, dropdown… utiliser shadcn/ui (deja en place), radix-ui, headless-ui.

## Reference pour ce projet

Les libs deja installees a privilegier en premier :
- **Backend** : Jackson 3, Resilience4j, Apache POI, MapStruct, Spring AI, Spring Cache + Caffeine
- **Frontend** : shadcn/ui + Radix, Tailwind, date-fns, Clerk, Framer Motion, react-joyride, recharts
- **Communs** : voir `pom.xml` / `package.json`

Avant d'ajouter une dep ou de coder une fonctionnalite technique, **applique le workflow ci-dessus**.
