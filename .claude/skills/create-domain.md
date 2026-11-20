# Skill : Create Domain (Orchestrator)

## Declencheur

User asks to create an entire domain (e.g., "create Lead domain", "scaffold Sequence module with all files").

## Instructions

This is an **orchestrator skill** that creates all files for a complete domain in the correct order, ensuring dependencies are satisfied and compilation succeeds.

### Step-by-Step Orchestration

**Phase 1: Data Layer** (Entity, Repository, Migration)
1. **Use create-entity.md** → Create JPA entity extending `BaseEntity`
   - Includes: `@Entity`, `@Table`, `@Getter @Setter`, `tenant_id` field
   - Creates corresponding Flyway migration
   - Verifies entity compiles

2. **Use create-repository.md** → Create JPA repository interface
   - Includes: `@Repository`, extends `JpaRepository<Entity, Long>`
   - Includes: `findByTenantId()` and custom `@Query` methods
   - Verifies repository compiles

**Phase 2: DTOs** (Response, Request, Generic)
3. **Use create-dto.md** → Create Req/Rep/Dto classes
   - RepDto: response DTO with `@Getter @Builder`, no validation
   - ReqDto: request DTO with `@Valid` annotations, Bean Validation
   - Generic Dto: internal DTO if needed
   - Verifies DTOs compile

**Phase 3: Mapping** (Mapper)
4. **Use create-mapper.md** → Create MapStruct mapper interface
   - `toDto(Entity)` → RepDto
   - `toEntity(ReqDto)` → Entity (ignoring auto-generated fields)
   - Verifies mapper interface compiles and generates implementation

**Phase 4: Exception Handling** (Custom Exceptions + Handler)
5. **Use create-exception.md** → Create custom exceptions
   - NotFoundException, AlreadyExistsException, InvalidDataException
   - GlobalExceptionHandler with `@ExceptionHandler` methods
   - Verifies exceptions compile

**Phase 5: Business Logic** (Builder, Service, ValidationService)
6. **Create builder** (if not already created):
   - `@Component`, `@RequiredArgsConstructor`, `@Slf4j`
   - Methods: `buildFrom[Request]()` for entity construction with enrichment
   - Inject: mapper, other services if needed
   
7. **Use create-service.md** → Create service class
   - `@Service`, `@RequiredArgsConstructor`, `@Slf4j`
   - CRUD methods with `@Transactional` annotations
   - Entry/exit logging in French
   - Inject: repository, mapper, builder, validationService
   
8. **Create ValidationService** (if complex validations exist):
   - `@Service`, `@RequiredArgsConstructor`, `@Slf4j`
   - Validation methods (`void`), throw exceptions on failure
   - Called by service before processing

**Phase 6: REST API** (Controller)
9. **Use create-controller.md** → Create REST controller
   - `@RestController`, `@RequestMapping("/[kebab-case]")`, `@RequiredArgsConstructor`
   - CRUD endpoints: GET, GET/:id, POST, PUT/:id, DELETE/:id
   - Inject: service, authService
   - Extract tenantId and pass to service

**Phase 7: Compilation Check**
10. **Verify Full Compilation**:
    - Run `mvn clean compile` in backend directory
    - Fix any compilation errors (imports, annotations, etc.)
    - Verify no warnings (except expected ones)

### Checklist During Creation

As you create files, verify:
- [ ] Entity created with `@Entity`, extends `BaseEntity`, includes `tenant_id`
- [ ] Flyway migration created with correct version number
- [ ] Repository created with `@Repository`, includes `findByTenantId()`
- [ ] DTOs (Rep/Req) created with correct annotations and validations
- [ ] Mapper created as interface with explicit `@Mapping` annotations
- [ ] Custom exceptions created extending `RuntimeException`
- [ ] GlobalExceptionHandler catches all custom exceptions
- [ ] Builder created as `@Component` with enrichment logic
- [ ] Service created with logging, `@Transactional` patterns
- [ ] ValidationService created if needed
- [ ] Controller created with REST endpoints, tenantId extraction
- [ ] All files use correct naming conventions (CONVENTIONS.md)
- [ ] All files use correct package structure (ARCHITECTURE.md)

## Complete Domain Example: Lead

This example shows the **complete sequence** for the Lead domain.

### 1. Entity + Migration

**Lead.java** → CREATED (extends BaseEntity, tenant_id, Long id)

**V001__create_table_lead.sql** → CREATED
```sql
CREATE TABLE lead (
    id_lead BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    nom_prospect VARCHAR(255),
    email_prospect VARCHAR(255) NOT NULL,
    cree_le TIMESTAMP NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP,
    CONSTRAINT fk_lead_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id_tenant)
);
CREATE INDEX idx_lead_tenant_id ON lead(tenant_id);
```

### 2. Repository

**LeadRepository.java** → CREATED
```java
@Repository
public interface LeadRepository extends JpaRepository<Lead, Long> {
    List<Lead> findByTenantId(Long tenantId);
    @Query("""...""")
    Optional<Lead> findByTenantIdAndEmail(@Param("tenantId") Long tenantId, ...);
}
```

### 3. DTOs

**RepLeadDto.java** → CREATED (response, no validation)
**ReqCreateLeadDto.java** → CREATED (request, with @NotBlank, @Email, etc.)
**ReqUpdateLeadDto.java** → CREATED (update request, optional fields)

### 4. Mapper

**LeadMapper.java** → CREATED (interface, explicit @Mapping)
```java
@Mapper
public interface LeadMapper {
    @Mapping(source = "id", target = "idLead")
    RepLeadDto toDto(Lead entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "creeLe", ignore = true)
    @Mapping(target = "modifieLe", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    Lead toEntity(ReqCreateLeadDto dto);
}
```

### 5. Exceptions

**LeadNotFoundException.java** → CREATED
**LeadAlreadyExistsException.java** → CREATED
**InvalidLeadDataException.java** → CREATED

**GlobalExceptionHandler.java** → CREATED or UPDATED
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(LeadNotFoundException.class)
    public ResponseEntity<String> handleLeadNotFoundException(...) { ... }
    ...
}
```

### 6. Builder

**LeadBuilder.java** → CREATED
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class LeadBuilder {
    private final LeadMapper leadMapper;
    
    public Lead buildFromRequest(ReqCreateLeadDto request, Long tenantId) {
        final Lead lead = leadMapper.toEntity(request);
        lead.setTenantId(tenantId);
        lead.setStatutLead("NOUVEAU");
        return lead;
    }
}
```

### 7. Service

**LeadService.java** → CREATED
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class LeadService {
    private final LeadRepository leadRepository;
    private final LeadMapper leadMapper;
    private final LeadBuilder leadBuilder;
    
    @Transactional(readOnly = true)
    public RepLeadDto getLead(Long id, Long tenantId) {
        log.info("Recuperation du lead - idLead: {}, tenantId: {}", id, tenantId);
        final Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new LeadNotFoundException(id));
        log.info("Lead recupere - idLead: {}", id);
        return leadMapper.toDto(lead);
    }
    
    @Transactional
    public RepLeadDto createLead(ReqCreateLeadDto request, Long tenantId) {
        log.info("Creation d'un lead - email: {}", request.getEmailProspect());
        final Lead entity = leadBuilder.buildFromRequest(request, tenantId);
        final Lead saved = leadRepository.save(entity);
        log.info("Lead cree - idLead: {}", saved.getId());
        return leadMapper.toDto(saved);
    }
}
```

**LeadValidationService.java** → CREATED (if needed)
```java
@Service
@RequiredArgsConstructor
public class LeadValidationService {
    private final LeadRepository leadRepository;
    
    public void validateEmailUniqueness(String email, Long tenantId) {
        if (leadRepository.findByTenantIdAndEmail(tenantId, email).isPresent()) {
            throw new LeadAlreadyExistsException(email);
        }
    }
}
```

### 8. Controller

**LeadController.java** → CREATED
```java
@RestController
@RequestMapping("/leads")
@RequiredArgsConstructor
public class LeadController {
    private final LeadService leadService;
    private final AuthService authService;
    
    @GetMapping("/{id}")
    public RepLeadDto getLead(@PathVariable("id") Long id) {
        final Long tenantId = authService.getCurrentTenantId();
        return leadService.getLead(id, tenantId);
    }
    
    @PostMapping
    public ResponseEntity<RepLeadDto> createLead(
            @RequestBody @Valid ReqCreateLeadDto request) {
        final Long tenantId = authService.getCurrentTenantId();
        final RepLeadDto created = leadService.createLead(request, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
```

### 9. Compilation Check

```bash
cd backend
mvn clean compile
```

Expected output:
```
[INFO] BUILD SUCCESS
```

If errors appear, fix them before proceeding.

## Files Created Summary

For a complete domain (e.g., Lead), you will have created:

```
fr.leadrevive.lead/
├── entity/
│   └── Lead.java                          (JPA entity)
├── repository/
│   └── LeadRepository.java                (JPA repository)
├── dto/
│   ├── RepLeadDto.java                    (response DTO)
│   ├── ReqCreateLeadDto.java              (create request DTO)
│   └── ReqUpdateLeadDto.java              (update request DTO)
├── mapper/
│   └── LeadMapper.java                    (MapStruct mapper)
├── exception/
│   ├── LeadNotFoundException.java
│   ├── LeadAlreadyExistsException.java
│   └── InvalidLeadDataException.java
├── service/
│   ├── LeadService.java                   (main service)
│   ├── LeadBuilder.java                   (@Component, entity construction)
│   └── LeadValidationService.java         (reusable validations, if needed)
└── controller/
    └── LeadController.java                (REST controller)

db/migration/
└── V001__create_table_lead.sql            (Flyway migration)

common/exception/
└── GlobalExceptionHandler.java            (global exception handler, shared)
```

## Compilation Issues

If you encounter compilation errors:

1. **Missing imports**: Add explicit imports (no `import *`)
2. **Missing annotations**: Check `@Entity`, `@Service`, `@Repository`, `@Mapper`
3. **Field access**: Ensure all fields are `private` with `@Getter`
4. **Constructor injection**: Ensure `@RequiredArgsConstructor` is present and all fields are `final`
5. **MapStruct mapper**: Ensure mapper extends correctly, check `@Mapping` annotations
6. **Flyway migration**: Check SQL syntax, ensure version number is unique and incremental
7. **Bean Validation**: Ensure `jakarta.validation` imports (not `javax.validation`)

## Next Steps After Creation

1. **Write unit tests** for Service and Repository using Mockito
2. **Write integration tests** for Controller using `@SpringBootTest`
3. **Document API** using Swagger/OpenAPI annotations
4. **Add caching** if performance needed (Redis for tenant configs, etc.)
5. **Add scheduled jobs** if domain has async processing needs

## Key References

- CONVENTIONS.md: Naming, annotations, patterns
- ARCHITECTURE.md: Package structure, multi-tenant flow
- Flyway docs: Migration syntax for PostgreSQL
- Spring Data JPA docs: Repository patterns
- MapStruct docs: Mapper configuration
- Spring Security docs: TenantId extraction

## Checklist - Full Domain Creation

- [ ] Phase 1: Entity + Migration created and compiles
- [ ] Phase 2: Repository created and compiles
- [ ] Phase 3: DTOs (Rep/Req/Dto) created and compile
- [ ] Phase 4: Mapper created and generates implementation
- [ ] Phase 5: Custom exceptions created and compile
- [ ] Phase 5: GlobalExceptionHandler created or updated
- [ ] Phase 5: Builder created and injects correctly
- [ ] Phase 6: Service created with logging and @Transactional
- [ ] Phase 6: ValidationService created (if needed)
- [ ] Phase 6: Controller created with REST endpoints
- [ ] Phase 7: `mvn clean compile` succeeds
- [ ] All files follow CONVENTIONS.md naming and patterns
- [ ] All files in correct packages per ARCHITECTURE.md
- [ ] All files reviewed for `final`, `@Getter`, no `var`
- [ ] No checked exceptions, no `System.out.println`
- [ ] All custom queries filter by `tenant_id`
- [ ] All DTOs validated in Req* classes
- [ ] All logs in French with proper format
