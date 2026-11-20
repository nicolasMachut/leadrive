# ARCHITECTURE.md

## Vue d'ensemble

Leadrive est un monolithe modulaire Spring Boot. Architecture en couches classique avec multi-tenant.

## Structure monorepo

```
leadrevive/
├── backend/                    # Projet Spring Boot (Java 25)
│   └── src/main/java/fr/leadrevive/
├── frontend/                   # Projet Next.js (React)
├── docs/                       # Documentation technique
├── .github/workflows/          # CI/CD GitHub Actions
├── docker-compose.yml          # Environnement local (PostgreSQL)
├── CLAUDE.md                   # Instructions Claude Code
├── CONVENTIONS.md              # Regles de code
└── ARCHITECTURE.md             # Ce fichier
```

## Couches applicatives (backend)

```
┌─────────────────────────────────────────────────┐
│                  Controllers                     │
│        (@RestController, @RequestMapping)         │
│        Recoit les requetes HTTP                   │
│        Retourne RepDto ou ResponseEntity<RepDto>  │
├─────────────────────────────────────────────────┤
│                   Services                        │
│        (@Service, @RequiredArgsConstructor)        │
│        Logique metier, orchestration              │
│        Appelle builders, repos                    │
│        Retourne des DTOs (Rep)                    │
├─────────────────────────────────────────────────┤
│                   Builders                        │
│        (@Component, intermediaire)                │
│        Mapping + enrichissement                   │
│        Appelle mappers + services externes        │
├──────────┬──────────┬──────────┬────────────────┤
│ Mappers  │  Repos   │ External │  Validation    │
│ MapStruct│  JPA     │ RestCli  │  Custom        │
│ @Mapper  │ JpaRepo  │ Anthropic   │  @Constraint   │
│ (simple) │          │ Twilio   │                │
├──────────┴──────────┴──────────┴────────────────┤
│              Modeles de donnees                   │
│    DTOs Rep/Req (@Getter @Builder)                │
│    Entities (@Entity @Getter @Setter)             │
│    BaseEntity (champs d'audit communs)            │
│    Enums (@Getter, suffixe Enum)                  │
└─────────────────────────────────────────────────┘
```

## Modules metier (packages)

```
fr.leadrevive/
│
├── NoraApplication.java
│
├── common/                       # Composants transversaux
│   ├── config/                   # SecurityConfig, JpaAuditingConfig, etc.
│   ├── entity/                   # BaseEntity
│   ├── exception/                # GlobalExceptionHandler
│   ├── security/                 # JWT, TenantFilter, AuthService
│   └── tenant/                   # TenantContext, TenantFilter
│
├── lead/                         # Module lead-core
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   ├── dto/
│   ├── mapper/
│   └── exception/
│
├── tenant/                       # Module gestion concessions
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   ├── dto/
│   └── mapper/
│
├── ai/                           # Module moteur IA
│   ├── service/                  # AiGenerationService, PromptService
│   ├── config/                   # Anthropic config, Spring AI
│   ├── dto/                      # AiRequest, AiResponse DTOs
│   └── log/                      # AILog entity, repository
│
├── outreach/                     # Module envoi messages
│   ├── service/                  # WhatsAppService
│   ├── config/                   # TwilioConfig
│   └── entity/                   # Message entity
│
├── sequence/                     # Module sequences de relance
│   ├── controller/
│   ├── service/                  # SequenceService, SequenceScheduler
│   ├── repository/
│   ├── entity/                   # Sequence, SequenceEnrollment
│   ├── dto/
│   └── mapper/
│
├── coaching/                     # Module tableau de bord manager
│   ├── controller/
│   ├── service/
│   └── dto/
│
├── notification/                 # Module alertes temps reel (SSE)
│   ├── controller/               # SseController
│   └── service/                  # NotificationService
│
├── csvimport/                    # Module import CSV
│   ├── controller/
│   ├── service/                  # CsvImportService, CsvMappingService
│   ├── entity/                   # LeadImport
│   └── dto/
│
└── user/                         # Module utilisateurs
    ├── controller/
    ├── service/
    ├── repository/
    ├── entity/                   # User (avec role ADMIN/MANAGER/VENDEUR)
    ├── dto/
    └── mapper/
```

## Flux de donnees

### Requete entrante (lecture)

```
Client HTTP
  → Controller (@GetMapping)
    → TenantFilter (extrait tenant_id du JWT)
    → Service (logique metier)
      → Repository (JPA query filtree par tenant)
        → Entity (resultat BDD)
      → Mapper (entity → DTO)
    → ResponseEntity<DTO>
  ← Reponse JSON
```

### Requete entrante (ecriture)

```
Client HTTP (JSON body)
  → Controller (@PostMapping, @Valid ReqDto)
    → TenantFilter (extrait tenant_id du JWT)
    → Service
      → Builder (construction objet complexe)
        → Mapper (ReqDto → entity)
        → Enrichissement si necessaire
      → Repository.save(entity)
      → Mapper (entity → RepDto)
    → ResponseEntity<RepDto>
  ← Reponse JSON
```

### Import CSV

```
Upload CSV
  → CsvImportController
    → CsvImportService
      → Parse CSV, applique mapping
      → Validation, deduplication
      → Batch insert via LeadRepository
      → Cree LeadImport (trace)
    → RepImportResultDto (stats)
```

### Relance automatique (scheduler)

```
Spring Scheduler (cron)
  → SequenceScheduler
    → Identifie leads en attente de relance
    → AiGenerationService (genere le message)
    → OutreachService (envoie WhatsApp via Twilio)
    → Met a jour SequenceEnrollment
    → Log AILog
```

## Securite

### JWT (JSON Web Token)

- Authentification email/mot de passe → retourne JWT
- Le JWT contient : userId, tenantId, role (ADMIN/MANAGER/VENDEUR)
- Spring Security valide le JWT sur chaque requete
- Le `TenantFilter` extrait le `tenantId` et le place dans le `TenantContext`

### Roles

| Role     | Acces                                                    |
|----------|----------------------------------------------------------|
| ADMIN    | Creation vendeurs, config sequences, imports, acces total|
| MANAGER  | Dashboard coaching, stats de tous les vendeurs           |
| VENDEUR  | Ses leads assignes, ses alertes, ses fiches uniquement   |

## Base de donnees

### Configuration

- **SGBD** : PostgreSQL 16+
- **Migrations** : Flyway (fichiers dans `src/main/resources/db/migration/`)
- **Naming** : `PhysicalNamingStrategyStandardImpl` (noms `@Column` utilises tels quels)
- **Multi-tenant** : filtre Hibernate global sur `tenant_id`

### Conventions SQL

- Tables en francais snake_case : `lead`, `sequence_relance`
- Colonnes en francais snake_case : `id_lead`, `email_prospect`
- Cle primaire : `id_[table]` avec `BIGSERIAL`
- Timestamps : `cree_le`, `modifie_le`

## APIs externes

| API           | Usage                              | Client             |
|---------------|------------------------------------|--------------------|
| Anthropic     | Generation de messages IA (Claude Haiku 4.5) | Spring AI          |
| Twilio        | Envoi de messages WhatsApp         | Twilio SDK         |

## Cache

- **Framework** : Spring Cache + Caffeine (cache en memoire)
- **Usage** : cache des configs tenant, des templates de prompts
- **Configuration** : `CacheConfig` avec `CaffeineCacheManager`
- **Evolution** : Redis sera ajoute quand les volumes le justifieront (15-20+ clients)

## CI/CD

- **Repository** : GitHub (monorepo)
- **Pipeline** : GitHub Actions
- **Deploiement** : Railway (backend + PostgreSQL)
- **Branches** : main, develop, feature/*
