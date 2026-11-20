# Skill : Expert Developer — Java 25 / Spring Boot 4 / React (Next.js)

## Declencheur

Ce skill s'active des qu'une tache de developpement implique :
- La creation ou modification de code Java (backend Spring Boot)
- La creation ou modification de code React / Next.js (frontend)
- Le choix ou la mise a jour de dependances (pom.xml, package.json)
- La configuration de frameworks, librairies, ou outils
- La mise en place de patterns d'architecture ou de bonnes pratiques
- Toute question sur "comment faire X" en Java 25, Spring Boot 4, ou React/Next.js

Ce skill **complete** les autres skills (create-entity, create-service, etc.) — il ne les remplace pas. Les skills de creation de domaine definissent la structure et les conventions du projet. Ce skill apporte l'expertise technique pour s'assurer que le code produit utilise les dernieres APIs, les patterns modernes, et les versions a jour.

## Principe fondamental

**Toujours verifier avant de coder.** Les LLMs ont un knowledge cutoff et peuvent proposer des APIs deprecated, des versions obsoletes, ou des patterns abandonnes. Ce skill impose une discipline de verification systematique via recherche web avant d'ecrire du code.

## Workflow obligatoire

### Etape 1 — Recherche prealable (AVANT tout code)

Avant d'ecrire la moindre ligne de code, effectuer des recherches web pour verifier :

1. **Versions des dependances** : Chercher la derniere version stable de chaque dependance impliquee
   - Requete type : `"[librairie] latest stable version site:mvnrepository.com"` ou `"[package] latest version site:npmjs.com"`
   - Pour Spring Boot : `"Spring Boot latest release site:spring.io"`
   - Pour React/Next.js : `"Next.js latest stable version site:nextjs.org"`

2. **APIs et methodes actuelles** : Verifier que les APIs utilisees ne sont pas deprecated
   - Requete type : `"Spring Boot 4 [feature] migration guide"`, `"Spring Security 7 configuration example"`
   - Pour Java 25 : `"Java 25 new features"`, `"Java 25 [API] changes"`

3. **Bonnes pratiques du moment** : S'assurer que le pattern choisi est le recommande actuellement
   - Requete type : `"Spring Boot 4 best practices [sujet] 2026"`, `"React 19 [pattern] recommended approach"`

### Etape 2 — Synthese des trouvailles

Apres les recherches, ecrire un court resume (3-5 lignes) dans les logs de ce qui a ete verifie :
```
Verification effectuee :
- Spring Boot 4.0.x : derniere version stable = 4.0.5 ✓
- Spring Security : SecurityFilterChain reste l'approche recommandee ✓
- MapStruct : version 1.6.x compatible Java 25 ✓
- Point d'attention : [eventuel changement notable]
```

### Etape 3 — Implementation

Coder en utilisant les informations verifiees. En cas de doute sur une API ou un pattern, faire une recherche supplementaire plutot que de deviner.

## Domaines d'expertise

### Java 25 + Spring Boot 4

Points de vigilance specifiques a ces versions recentes :

- **Java 25** : Verifier les nouvelles fonctionnalites du langage (records, sealed classes, pattern matching, virtual threads, etc.) et les utiliser quand c'est pertinent. Attention aux APIs retirees ou modifiees depuis Java 21.
- **Spring Boot 4** : Migration majeure depuis Spring Boot 3. Verifier systematiquement :
  - Jakarta EE 11 (namespace `jakarta.*`)
  - Spring Security 7 (configuration via `SecurityFilterChain`, fin de `WebSecurityConfigurerAdapter` depuis longtemps)
  - Spring Framework 7 (nouvelles APIs, deprecations)
  - Spring Data JPA (changements d'API sur les repositories)
  - Hibernate 7 (changements de configuration, naming strategies)
  - Spring AI (librairie relativement recente, API qui evolue vite — toujours verifier)

- **Dependances Maven** : Pour chaque dependance ajoutee au `pom.xml`, verifier :
  - La derniere version stable sur mvnrepository.com
  - La compatibilite avec Spring Boot 4 et Java 25
  - Si un starter Spring Boot existe (preferer `spring-boot-starter-*`)

### React / Next.js

- **Next.js** : Verifier la derniere version stable et les changements d'API (App Router vs Pages Router, Server Components, etc.)
- **React** : Verifier les hooks recommandes, les patterns de state management actuels
- **shadcn/ui** : Verifier les composants disponibles et leur API actuelle
- **Tailwind CSS** : Verifier la version compatible et les changements de classes

### Dependances a verifier systematiquement

| Dependance | Ou verifier | Pourquoi |
|---|---|---|
| Spring Boot | spring.io/projects/spring-boot | Version majeure recente, API qui change |
| Spring AI | docs.spring.io/spring-ai | Librairie jeune, API instable |
| MapStruct | mapstruct.org | Compatibilite processeur d'annotations Java 25 |
| Lombok | projectlombok.org | Compatibilite compilateur Java 25 |
| Flyway | flywaydb.org | Compatibilite Spring Boot 4 |
| Springdoc OpenAPI | springdoc.org | Changements frequents, v2 vs v1 |
| Next.js | nextjs.org | Versions majeures frequentes |
| shadcn/ui | ui.shadcn.com | Composants qui evoluent |

## Regles d'or

1. **Ne jamais coder de memoire** pour les versions et APIs — toujours verifier
2. **Preferer les sources officielles** : documentation Spring, Javadoc, React docs, MDN
3. **Si une recherche ne donne pas de resultat clair**, le signaler explicitement plutot que de deviner
4. **Documenter les choix techniques** : quand un pattern est choisi apres recherche, expliquer brievement pourquoi dans un commentaire ou dans le message
5. **Respecter les conventions du projet** (CONVENTIONS.md) meme si un article de blog suggere autre chose — les conventions du projet priment, sauf si elles sont en contradiction avec un changement breaking de la derniere version

## Integration avec les autres skills

Ce skill fonctionne en tandem avec les skills existants :

- **create-domain / create-entity / create-service / etc.** : Ces skills definissent la structure. L'expert-dev s'assure que le code genere utilise les bonnes versions et APIs.
- **Exemple concret** : Quand `create-service` genere un service avec `@Transactional`, l'expert-dev verifie que l'import `org.springframework.transaction.annotation.Transactional` est toujours le bon pour Spring Boot 4 (et pas une variante deprecated).

## Jackson 3 — OBLIGATOIRE dans Spring Boot 4

Spring Boot 4 utilise **Jackson 3** (package `tools.jackson.*`), PAS Jackson 2 (`com.fasterxml.jackson.*`).

**Regles strictes** :
- `ObjectMapper` → `tools.jackson.databind.ObjectMapper` (PAS `com.fasterxml.jackson.databind.ObjectMapper`)
- `JsonProcessingException` n'existe plus → utiliser `tools.jackson.core.JacksonException`
- `@JsonProperty`, `@JsonIgnore`, etc. → package `tools.jackson.annotation.*`
- Ne JAMAIS importer `com.fasterxml.jackson.*` — ces classes n'existent pas dans le classpath Spring Boot 4

## Anti-patterns a eviter

- Utiliser une API deprecated alors qu'un remplacement existe
- Hardcoder une version de dependance sans verifier si une plus recente est disponible
- Copier-coller du code d'un tutoriel sans verifier sa date et sa compatibilite
- Ignorer les warnings de deprecation
- Utiliser `WebSecurityConfigurerAdapter` (supprime depuis Spring Security 5.7)
- Utiliser `javax.*` au lieu de `jakarta.*` (migration Jakarta EE)
- Utiliser `com.fasterxml.jackson.*` au lieu de `tools.jackson.*` (migration Jackson 3 dans Spring Boot 4)
- Utiliser des patterns pre-Java 17 (comme les cascades de `instanceof` au lieu du pattern matching)

## Checklist

- [ ] Recherche web effectuee AVANT de coder
- [ ] Versions des dependances verifiees sur les sites officiels
- [ ] APIs utilisees non-deprecated (verifiees dans la doc officielle)
- [ ] Patterns recommandes pour la version actuelle du framework
- [ ] Compatibilite Java 25 confirmee
- [ ] Compatibilite Spring Boot 4 confirmee
- [ ] Resume de verification ecrit avant le code
- [ ] Conventions du projet (CONVENTIONS.md) respectees
