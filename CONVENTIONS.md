# CONVENTIONS.md

Regles de code detaillees pour le projet Leadrive (backend Spring Boot).

---

## 1. Nommage

### Classes et interfaces

| Element         | Langue   | Format      | Suffixe        | Exemple                        |
|-----------------|----------|-------------|----------------|--------------------------------|
| Controller      | Anglais  | PascalCase  | `Controller`   | `LeadController`               |
| Service         | Anglais  | PascalCase  | `Service`      | `LeadService`                  |
| Builder         | Anglais  | PascalCase  | `Builder`      | `LeadBuilder`                  |
| DTO response    | Anglais  | PascalCase  | `Rep...Dto`    | `RepLeadDto`                   |
| DTO request     | Anglais  | PascalCase  | `Req...Dto`    | `ReqCreateLeadDto`             |
| DTO generique   | Anglais  | PascalCase  | `Dto`          | `LeadDto`                      |
| Entity          | Anglais  | PascalCase  | aucun          | `Lead`, `Sequence`             |
| Repository      | Anglais  | PascalCase  | `Repository`   | `LeadRepository`               |
| Mapper          | Anglais  | PascalCase  | `Mapper`       | `LeadMapper`                   |
| Exception       | Anglais  | PascalCase  | descriptif     | `LeadNotFoundException`        |
| Handler         | Anglais  | PascalCase  | `ExceptionHandler` | `GlobalExceptionHandler`   |
| Enum            | Anglais  | PascalCase  | `Enum`         | `StatutLeadEnum`, `CanalEnum`  |
| Config          | Anglais  | PascalCase  | `Config`       | `SecurityConfig`               |

### Convention DTOs Rep/Req

- **`Rep`** (Response) : DTO retourne par l'API au client → `RepLeadDto`, `RepSequenceDetailDto`
- **`Req`** (Request) : DTO recu en entree de l'API → `ReqCreateLeadDto`, `ReqUpdateSequenceDto`
- **Generique** : DTO utilise en interne ou dans les deux sens → `LeadDto` (suffixe `Dto` simple)

### Champs et variables

| Contexte           | Langue    | Format        | Exemple                              |
|--------------------|-----------|---------------|--------------------------------------|
| Champs Java        | Francais  | camelCase     | `idLead`, `emailProspect`, `creeLe`  |
| Parametres methode | Francais  | camelCase     | `final Long idLead`                  |
| Variables locales  | Francais  | camelCase     | `final Lead lead = ...`              |
| Constantes         | Anglais   | UPPER_SNAKE   | `MESSAGE_MAX_LENGTH`                 |

### Base de donnees

| Element          | Langue    | Format        | Exemple                          |
|------------------|-----------|---------------|----------------------------------|
| Table            | Francais  | snake_case    | `lead`, `sequence_relance`       |
| Colonne          | Francais  | snake_case    | `id_lead`, `email_prospect`      |
| Cle primaire     | Francais  | snake_case    | `id_[nom_table]`                 |
| Schema           | Francais  | minuscule     | `leadrevive`                     |

### Booleens

- **Java** : pas de prefixe `est`/`is` → `actif`, `enErreur`, `valide`
- **SQL** : prefixe `est_` → `est_actif`, `est_en_erreur`, `est_valide`

### Endpoints REST

- **Format** : kebab-case
- **Exemples** : `/leads/{id}`, `/sequences-relance`, `/imports/{id}`
- **Pluriel** pour les collections, singulier pour les actions specifiques

---

## 2. Typage et variables

### Pas de `var`

**Ne jamais utiliser `var`**. Toujours typer explicitement les variables, sauf les parametres de lambdas.

```java
// CORRECT
final Lead lead = leadRepository.findById(id).orElseThrow();
final List<RepLeadDto> resultats = leadService.getAll();

// INTERDIT
final var lead = leadRepository.findById(id).orElseThrow();

// EXCEPTION : parametres de lambdas
leads.stream()
        .filter(l -> l.isActif())
        .map(l -> leadMapper.toDto(l))
        .toList();
```

### `final` partout

Mettre `final` sur toutes les variables et parametres des que possible :

```java
public RepLeadDto getLead(final Long id) {
    final Lead lead = leadRepository.findById(id).orElseThrow();
    final RepLeadDto dto = leadMapper.toDto(lead);
```

---

## 3. Lombok

### DTOs

```java
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepLeadDto {
    private Long idLead;
    private String nomProspect;
    private String emailProspect;
    private String vehiculeConsulte;
}
```

> `@Data` uniquement si on a explicitement besoin de `equals`/`hashCode`/`toString`. Dans le doute, utiliser `@Getter`.

### Entities JPA

```java
@Entity
@Table(name = "lead")
@Getter
@Setter
public class Lead extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_lead")
    private Long id;
}
```

> **`@Data` interdit** sur les entities JPA. Utiliser `@Getter @Setter`.

### Services et Controllers

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class LeadService {
    private final LeadRepository leadRepository;
    private final LeadMapper leadMapper;
}
```

> Tous les champs injectes sont `final`. Pas de `@Autowired`.

---

## 4. Controllers

### Structure

```java
@RestController
@RequestMapping("/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;

    @GetMapping("/{id}")
    public RepLeadDto getLead(@PathVariable("id") final Long id) {
        return leadService.getLead(id);
    }

    @PostMapping
    public ResponseEntity<RepLeadDto> createLead(@RequestBody @Valid final ReqCreateLeadDto request) {
        final RepLeadDto created = leadService.createLead(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
```

### Regles

- `ResponseEntity<T>` uniquement quand on a besoin de controler le status HTTP (201, 204, etc.)
- Ne manipule jamais d'entities directement (uniquement des DTOs)
- `@Valid` sur les DTOs en entree
- **Nom explicite** dans `@RequestParam("nom")` et `@PathVariable("nom")`
- Parametres marques `final`
- Pas de logique metier dans le controller

---

## 5. Services et Builders

### Service

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class LeadService {

    private final LeadRepository leadRepository;
    private final LeadMapper leadMapper;

    @Transactional(readOnly = true)
    public RepLeadDto getLead(final Long id) {
        log.info("Recuperation du lead - idLead: {}", id);
        final Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new LeadNotFoundException(id));
        log.info("Lead recupere - idLead: {}", id);
        return leadMapper.toDto(lead);
    }

    @Transactional
    public RepLeadDto createLead(final ReqCreateLeadDto request) {
        log.info("Creation d'un lead - email: {}", request.getEmailProspect());
        final Lead entity = leadBuilder.buildFromRequest(request);
        final Lead saved = leadRepository.save(entity);
        log.info("Lead cree - idLead: {}", saved.getId());
        return leadMapper.toDto(saved);
    }
}
```

### Builder (intermediaire entre Mapper et Service)

Le builder sert d'intermediaire pour construire des entities ou DTOs complexes qui necessitent des enrichissements de donnees ou des appels externes.

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class LeadBuilder {

    private final LeadMapper leadMapper;

    public Lead buildFromRequest(final ReqCreateLeadDto request) {
        final Lead lead = leadMapper.toEntity(request);
        // Enrichissement si necessaire
        log.info("Lead construit depuis requete");
        return lead;
    }
}
```

### Regles

- Le **service** orchestre la logique metier, appelle le builder et le repository
- Le **builder** (`@Component`) construit les objets complexes : mapping + enrichissement
- Le **mapper** (MapStruct) fait le mapping simple champ-a-champ
- **Log d'entree** : toujours un `log.info(...)` en debut de methode
- **Log de sortie** : un `log.info(...)` apres le traitement
- `@Transactional(readOnly = true)` sur les methodes de lecture
- `@Transactional` sur les methodes d'ecriture
- Retourne toujours des DTOs (jamais d'entities)
- Injection par constructeur uniquement

### Decouplage : ValidationService

Extraire les validations metier reutilisables dans un `[Domaine]ValidationService` :
- Methodes `void` qui lancent une exception si echec
- Uniquement des controles, pas de lookup/find

---

## 6. MapStruct

### Mapper interface

```java
@Mapper
public interface LeadMapper {

    @Mapping(source = "id", target = "idLead")
    @Mapping(source = "nomProspect", target = "nomProspect")
    RepLeadDto toDto(Lead entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "creeLe", ignore = true)
    @Mapping(target = "modifieLe", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    Lead toEntity(ReqCreateLeadDto dto);
}
```

### Regles

- `@Mapper` sans `componentModel` (configure globalement dans le pom.xml)
- Tous les champs mappes explicitement avec `@Mapping`
- Les champs auto-generes marques `ignore = true`
- Le mapper fait le mapping simple uniquement
- Un mapper par domaine metier

---

## 7. Entities JPA

### BaseEntity (entite commune avec auditing automatique)

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "cree_le", nullable = false, updatable = false)
    private LocalDateTime creeLe;

    @LastModifiedDate
    @Column(name = "modifie_le")
    private LocalDateTime modifieLe;
}
```

### Entity concrete

```java
@Entity
@Table(name = "lead")
@Getter
@Setter
public class Lead extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_lead")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "nom_prospect")
    private String nomProspect;

    @Column(name = "email_prospect")
    private String emailProspect;
}
```

### Regles

- `@Getter @Setter` (pas `@Data`)
- Heriter de `BaseEntity` pour les champs d'audit
- `@Table(name = ...)` et `@Column(name = ...)` obligatoires
- Pas de suffixe "Entity" sur le nom de classe
- `tenant_id` obligatoire sur toutes les entities multi-tenant
- Cle primaire en `Long` avec `GenerationType.IDENTITY`
- Pas de `var`

---

## 8. Repositories

```java
@Repository
public interface LeadRepository extends JpaRepository<Lead, Long> {

    List<Lead> findByTenantId(Long tenantId);

    @Query("""
            SELECT l FROM Lead l
            WHERE l.tenantId = :tenantId
            AND l.statutLead = :statut
            """)
    List<Lead> findByTenantIdAndStatut(@Param("tenantId") Long tenantId,
                                       @Param("statut") String statut);
}
```

### Regles

- `@Repository` obligatoire
- Interface extends `JpaRepository<Entity, Long>`
- Query methods Spring Data quand possible
- `@Query` JPQL en text blocks pour les requetes complexes
- **Toujours privilegier JPQL** plutot que les native queries (`nativeQuery = true`). N'utiliser les native queries que lorsque JPQL ne peut pas exprimer la requete (fonctions SQL specifiques comme `DATE()`, `TO_CHAR()`, etc.)
- Pas de logique dans le repository

---

## 9. Multi-tenant

Toutes les requetes sont automatiquement filtrees par `tenant_id` via un filtre Hibernate global (`@FilterDef` / `@Filter`). Le `tenant_id` est extrait du JWT de l'utilisateur connecte.

### Regles

- `tenant_id` (Long) obligatoire sur toutes les entities metier
- Le filtre Hibernate est applique automatiquement via un `TenantFilter`
- Jamais de requete sans filtre tenant en production

---

## 10. Exceptions

### Exception custom

```java
@Getter
public class LeadNotFoundException extends RuntimeException {

    private final Long leadId;

    public LeadNotFoundException(final Long leadId) {
        super("Lead non trouve - id: " + leadId);
        this.leadId = leadId;
    }
}
```

### Handler global

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(LeadNotFoundException.class)
    public ResponseEntity<String> handleLeadNotFoundException(final LeadNotFoundException ex) {
        log.warn("Lead non trouve - id: {}", ex.getLeadId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }
}
```

### Regles

- Toujours `extends RuntimeException`
- Les exceptions portent le contexte (`@Getter`)
- `warn` pour not found, `error` pour erreurs techniques
- Pas de checked exceptions

---

## 11. Logging

### Format

```java
log.info("Description action - param1: {}, param2: {}", val1, val2);
log.warn("Element non trouve - id: {}", id);
log.error("Erreur traitement - cause: {}", ex.getMessage(), ex);
```

### Regles

- Messages en **francais**
- Format : `"Description - cle: {}"` avec tiret separateur
- **Log d'entree obligatoire** en debut de chaque methode de service
- **Privilegier `info`** : l'API doit etre bavarde
- `warn` : situations non bloquantes
- `error` : erreurs techniques avec stack trace
- Pas de log dans les grosses boucles
- Pas de `System.out.println`

---

## 12. Tests

### Conventions

- Une classe de test par classe source
- Suffixe `Test` : `LeadServiceTest`
- Tests unitaires : Mockito (`@Mock`, `@InjectMocks`)
- Tests integration : `@SpringBootTest` avec Testcontainers (PostgreSQL)
- Nommage : `methodeName_scenario_resultatAttendu`

### Structure test unitaire

```java
@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private LeadMapper leadMapper;

    @InjectMocks
    private LeadService leadService;

    @Test
    void getLead_existant_retourneDto() {
        // Given
        final Lead entity = new Lead();
        entity.setId(1L);
        final RepLeadDto dto = RepLeadDto.builder().idLead(1L).build();

        when(leadRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(leadMapper.toDto(entity)).thenReturn(dto);

        // When
        final RepLeadDto result = leadService.getLead(1L);

        // Then
        assertThat(result.getIdLead()).isEqualTo(1L);
    }
}
```

---

## 13. Configuration Spring

### Profils

| Profil   | Fichier                | Usage                         |
|----------|------------------------|-------------------------------|
| (defaut) | `application.yml`      | Config commune                |
| `local`  | `application-local.yml`| Dev local (datasource, clés)  |
| `prod`   | `application-prod.yml` | Production (Railway)          |

### Proprietes externalisees

- Credentials BDD : `${SPRING_DATASOURCE_USERNAME}`, `${SPRING_DATASOURCE_PASSWORD}`
- OpenAI API key : `${OPENAI_API_KEY}`
- Twilio credentials : `${TWILIO_ACCOUNT_SID}`, `${TWILIO_AUTH_TOKEN}`
- JWT secret : `${JWT_SECRET}`

---

## 14. Migrations BDD (Flyway)

### Regles

- Fichiers dans `src/main/resources/db/migration/`
- Nommage : `V[numero]__[description].sql` (ex: `V001__create_table_tenant.sql`)
- Numero incremental a 3 chiffres
- **Jamais modifier** une migration deja appliquee
- Contenu en SQL PostgreSQL

### Exemple

```sql
-- V001__create_table_tenant.sql
CREATE TABLE tenant (
    id_tenant BIGSERIAL PRIMARY KEY,
    nom VARCHAR(255) NOT NULL,
    adresse VARCHAR(500),
    timezone VARCHAR(50) DEFAULT 'Europe/Paris',
    cree_le TIMESTAMP NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP
);
```

---

## 15. Regles generales

- **Pas de `var`** : toujours typer explicitement
- **`final` partout** : parametres, variables locales, champs injectes
- **Pas de `@Data` sauf exception** : preferer `@Getter`
- **Pas de code mort** : supprimer le code inutilise
- **Pas d'import *** : imports explicites uniquement
- **Pas de `@Autowired`** : uniquement injection par constructeur
- **Pas de `System.out`** : uniquement SLF4J via `@Slf4j`
- **Pas de logique dans les controllers**
- **Pas d'entities dans les controllers** : uniquement des DTOs
- **Exceptions metier en `RuntimeException`**
- **`@Transactional(readOnly = true)`** sur toutes les methodes de lecture
- **Log d'entree** obligatoire en debut de methode de service
- **`tenant_id`** obligatoire sur toutes les entities metier
