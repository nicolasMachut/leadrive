# Skill : Create Service

## Declencheur

User asks to create a service (e.g., "create LeadService", "add business logic for Sequence management").

## Instructions

1. **Review CONVENTIONS.md** sections 5 (Services and Builders), 11 (Logging), and 15 (general rules)
2. **Check ARCHITECTURE.md** for package placement (e.g., `fr.leadrevive.lead.service`)
3. **Create the Service class** with:
   - `@Service` annotation
   - `@RequiredArgsConstructor` (constructor injection only)
   - `@Slf4j` for logging
   - `final` on all injected dependencies
   - All method parameters marked `final`
   - All local variables marked `final` where possible
   - No `var` usage

4. **Service Method Structure**:
   - **Entry log** (info level): log method entry with key parameters
   - **Business logic**: call repository, mapper, builder, validators
   - **Exit log** (info level): log successful completion with result summary
   - **Exception handling**: let checked exceptions propagate, or wrap in custom RuntimeException
   - **Return type**: always DTO (never entity)

5. **@Transactional Patterns**:
   - `@Transactional(readOnly = true)` on read methods (GET, LIST)
   - `@Transactional` (default write) on write methods (CREATE, UPDATE, DELETE)
   - Methods without explicit transaction behavior use Spring default

6. **Logging Conventions** (French messages):
   - Format: `"Description - key: {}, key: {}"` with tiret separator
   - Entry log: method name + params
   - Exit log: method name + result id or count
   - Use `log.warn()` for not found (recoverable)
   - Use `log.error()` for technical errors with stack trace

7. **Builder Integration**:
   - Service does NOT map directly (delegates to builder)
   - Builder constructs entities, service orchestrates and saves
   - Service calls `builder.buildFrom...()` before `repository.save()`

8. **ValidationService Pattern** (optional):
   - Extract reusable validations into `[Domain]ValidationService`
   - Validation methods are `void` and throw exceptions on failure
   - Service calls validation methods before processing

9. **Verify**:
   - Service compiles without errors
   - All methods have entry and exit logs
   - @Transactional patterns correct
   - All parameters and fields `final`
   - Returns DTOs, never entities
   - Calls builder for entity construction

## Templates

### Service Template (Basic CRUD)

```java
package fr.leadrevive.[domain].service;

import fr.leadrevive.[domain].dto.Rep[EntityName]Dto;
import fr.leadrevive.[domain].dto.ReqCreate[EntityName]Dto;
import fr.leadrevive.[domain].entity.[EntityName];
import fr.leadrevive.[domain].exception.[EntityName]NotFoundException;
import fr.leadrevive.[domain].mapper.[EntityName]Mapper;
import fr.leadrevive.[domain].repository.[EntityName]Repository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class [EntityName]Service {

    private final [EntityName]Repository [entity]Repository;
    private final [EntityName]Mapper [entity]Mapper;
    private final [EntityName]Builder [entity]Builder;

    @Transactional(readOnly = true)
    public Rep[EntityName]Dto get[EntityName](final Long id, final Long tenantId) {
        log.info("Recuperation de [Entity] - id: {}, tenantId: {}", id, tenantId);
        final [EntityName] entity = [entity]Repository.findById(id)
                .orElseThrow(() -> new [EntityName]NotFoundException(id));
        log.info("[Entity] recupere - id: {}", id);
        return [entity]Mapper.toDto(entity);
    }

    @Transactional(readOnly = true)
    public List<Rep[EntityName]Dto> getAll[EntityNames](final Long tenantId) {
        log.info("Recuperation de tous les [Entity] - tenantId: {}", tenantId);
        final List<[EntityName]> entities = [entity]Repository.findByTenantId(tenantId);
        log.info("Recuperation terminee - count: {}", entities.size());
        return [entity]Mapper.toDtoList(entities);
    }

    @Transactional
    public Rep[EntityName]Dto create[EntityName](final ReqCreate[EntityName]Dto request, final Long tenantId) {
        log.info("Creation de [Entity] - tenantId: {}", tenantId);
        final [EntityName] entity = [entity]Builder.buildFromRequest(request, tenantId);
        final [EntityName] saved = [entity]Repository.save(entity);
        log.info("[Entity] cree - id: {}", saved.getId());
        return [entity]Mapper.toDto(saved);
    }

    @Transactional
    public void delete[EntityName](final Long id, final Long tenantId) {
        log.info("Suppression de [Entity] - id: {}, tenantId: {}", id, tenantId);
        final [EntityName] entity = [entity]Repository.findById(id)
                .orElseThrow(() -> new [EntityName]NotFoundException(id));
        [entity]Repository.delete(entity);
        log.info("[Entity] supprime - id: {}", id);
    }
}
```

### Service with ValidationService Pattern

```java
package fr.leadrevive.[domain].service;

import fr.leadrevive.[domain].dto.Rep[EntityName]Dto;
import fr.leadrevive.[domain].dto.ReqCreate[EntityName]Dto;
import fr.leadrevive.[domain].entity.[EntityName];
import fr.leadrevive.[domain].exception.[EntityName]NotFoundException;
import fr.leadrevive.[domain].mapper.[EntityName]Mapper;
import fr.leadrevive.[domain].repository.[EntityName]Repository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class [EntityName]Service {

    private final [EntityName]Repository [entity]Repository;
    private final [EntityName]Mapper [entity]Mapper;
    private final [EntityName]Builder [entity]Builder;
    private final [EntityName]ValidationService validationService;

    @Transactional(readOnly = true)
    public Rep[EntityName]Dto get[EntityName](final Long id, final Long tenantId) {
        log.info("Recuperation de [Entity] - id: {}, tenantId: {}", id, tenantId);
        final [EntityName] entity = [entity]Repository.findById(id)
                .orElseThrow(() -> new [EntityName]NotFoundException(id));
        log.info("[Entity] recupere - id: {}", id);
        return [entity]Mapper.toDto(entity);
    }

    @Transactional
    public Rep[EntityName]Dto create[EntityName](final ReqCreate[EntityName]Dto request, final Long tenantId) {
        log.info("Creation de [Entity] - tenantId: {}", tenantId);
        
        // Validation
        validationService.validateRequest(request);
        validationService.validateUniqueness(request.getEmailProspect(), tenantId);
        
        // Construction et persistence
        final [EntityName] entity = [entity]Builder.buildFromRequest(request, tenantId);
        final [EntityName] saved = [entity]Repository.save(entity);
        log.info("[Entity] cree - id: {}", saved.getId());
        return [entity]Mapper.toDto(saved);
    }
}
```

## Example: LeadService

```java
package fr.leadrevive.lead.service;

import fr.leadrevive.lead.dto.RepLeadDto;
import fr.leadrevive.lead.dto.ReqCreateLeadDto;
import fr.leadrevive.lead.dto.ReqUpdateLeadDto;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.exception.LeadNotFoundException;
import fr.leadrevive.lead.mapper.LeadMapper;
import fr.leadrevive.lead.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadService {

    private final LeadRepository leadRepository;
    private final LeadMapper leadMapper;
    private final LeadBuilder leadBuilder;
    private final LeadValidationService validationService;

    @Transactional(readOnly = true)
    public RepLeadDto getLead(final Long id, final Long tenantId) {
        log.info("Recuperation du lead - idLead: {}, tenantId: {}", id, tenantId);
        final Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new LeadNotFoundException(id));
        log.info("Lead recupere - idLead: {}", id);
        return leadMapper.toDto(lead);
    }

    @Transactional(readOnly = true)
    public List<RepLeadDto> getAllLeads(final Long tenantId) {
        log.info("Recuperation de tous les leads - tenantId: {}", tenantId);
        final List<Lead> leads = leadRepository.findByTenantId(tenantId);
        log.info("Recuperation terminee - count: {}", leads.size());
        return leadMapper.toDtoList(leads);
    }

    @Transactional(readOnly = true)
    public List<RepLeadDto> getLeadsByStatus(final Long tenantId, final String status) {
        log.info("Recherche leads par statut - tenantId: {}, statut: {}", tenantId, status);
        final List<Lead> leads = leadRepository.findByTenantIdAndStatut(tenantId, status);
        log.info("Recherche terminee - count: {}", leads.size());
        return leadMapper.toDtoList(leads);
    }

    @Transactional
    public RepLeadDto createLead(final ReqCreateLeadDto request, final Long tenantId) {
        log.info("Creation d'un lead - email: {}, tenantId: {}", request.getEmailProspect(), tenantId);
        
        validationService.validateEmailUniqueness(request.getEmailProspect(), tenantId);
        
        final Lead entity = leadBuilder.buildFromRequest(request, tenantId);
        final Lead saved = leadRepository.save(entity);
        log.info("Lead cree - idLead: {}", saved.getId());
        return leadMapper.toDto(saved);
    }

    @Transactional
    public RepLeadDto updateLead(final Long id, final ReqUpdateLeadDto request, final Long tenantId) {
        log.info("Mise a jour du lead - idLead: {}, tenantId: {}", id, tenantId);
        
        final Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new LeadNotFoundException(id));
        
        if (request.getEmailProspect() != null) {
            validationService.validateEmailUniqueness(request.getEmailProspect(), tenantId);
        }
        
        leadMapper.updateEntityFromDto(request, lead);
        final Lead updated = leadRepository.save(lead);
        log.info("Lead mis a jour - idLead: {}", id);
        return leadMapper.toDto(updated);
    }

    @Transactional
    public void deleteLead(final Long id, final Long tenantId) {
        log.info("Suppression du lead - idLead: {}, tenantId: {}", id, tenantId);
        final Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new LeadNotFoundException(id));
        leadRepository.delete(lead);
        log.info("Lead supprime - idLead: {}", id);
    }
}
```

## Example: LeadValidationService

```java
package fr.leadrevive.lead.service;

import fr.leadrevive.lead.exception.LeadAlreadyExistsException;
import fr.leadrevive.lead.exception.InvalidLeadDataException;
import fr.leadrevive.lead.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadValidationService {

    private final LeadRepository leadRepository;

    public void validateEmailUniqueness(final String email, final Long tenantId) {
        log.info("Validation unicite email - email: {}, tenantId: {}", email, tenantId);
        
        final boolean exists = leadRepository.findByTenantIdAndEmail(tenantId, email)
                .isPresent();
        
        if (exists) {
            log.warn("Email deja existe - email: {}, tenantId: {}", email, tenantId);
            throw new LeadAlreadyExistsException(email);
        }
    }

    public void validateLeadData(final String email, final String phone) {
        log.info("Validation donnees lead - email: {}, phone: {}", email, phone);
        
        if (email == null || email.isBlank()) {
            throw new InvalidLeadDataException("Email obligatoire");
        }
        
        if (phone != null && phone.length() > 20) {
            throw new InvalidLeadDataException("Telephone max 20 caracteres");
        }
    }
}
```

## Key Notes

- Services **orchestrate** business logic, services call builders for complex objects
- Builders **construct** entities with enrichment
- Mappers do **simple** field-to-field mapping
- Services **never** return entities—always DTOs
- **Log entry/exit** in every service method
- `@Transactional(readOnly = true)` improves performance on queries
- `@Transactional` default (write) on mutation methods
- Extract **reusable validations** to ValidationService
- **ValidationService methods** throw exceptions, return nothing (`void`)
- All **parameters and fields** marked `final`

## Checklist

- [ ] Service class created in correct package (`fr.leadrevive.[domain].service`)
- [ ] Annotated with `@Service`, `@RequiredArgsConstructor`, `@Slf4j`
- [ ] All injected dependencies are `final`
- [ ] All method parameters marked `final`
- [ ] Entry log at start of every method (French, info level)
- [ ] Exit log after successful completion (French, info level)
- [ ] `@Transactional(readOnly = true)` on read methods
- [ ] `@Transactional` (default write) on mutation methods
- [ ] All methods return DTOs (never entities)
- [ ] Calls builder for entity construction (not mapper)
- [ ] Uses ValidationService for reusable validations
- [ ] No `var`, no implicit typing
- [ ] Service compiles without errors
- [ ] Reviewed CONVENTIONS.md sections 5 & 11
