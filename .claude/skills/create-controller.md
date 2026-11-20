# Skill : Create Controller

## Declencheur

User asks to create a REST controller (e.g., "create LeadController", "add REST endpoints for Sequence").

## Instructions

1. **Review CONVENTIONS.md** section 4 (Controllers) and section 1 (endpoints in kebab-case)
2. **Check ARCHITECTURE.md** for package placement (e.g., `fr.leadrevive.lead.controller`)
3. **Create the Controller class** with:
   - `@RestController` annotation
   - `@RequestMapping("/[plural-kebab-case]")` (e.g., `"/leads"`, `"/sequences-relance"`)
   - `@RequiredArgsConstructor` (constructor injection)
   - `@Slf4j` for logging (optional but recommended)
   - `final` on all injected dependencies

4. **Endpoint Structure**:
   - **GET** (read): `@GetMapping("/{id}")` returns `RepDto` (or `ResponseEntity<RepDto>` if needed)
   - **POST** (create): `@PostMapping` with `@RequestBody @Valid ReqCreateDto` returns `ResponseEntity.status(CREATED).body(RepDto)`
   - **PUT** (update): `@PutMapping("/{id}")` with `@RequestBody @Valid ReqUpdateDto` returns `RepDto`
   - **DELETE** (delete): `@DeleteMapping("/{id}")` returns `ResponseEntity.noContent()` or `void`
   - **GET** (list): `@GetMapping` with optional filters, returns `List<RepDto>`

5. **Controller Rules**:
   - `ResponseEntity<T>` **only when controlling HTTP status** (201 CREATED, 204 NO_CONTENT, etc.)
   - Simple GET returns `RepDto` directly, not `ResponseEntity`
   - **Never manipulate entities**—only DTOs
   - `@Valid` on all request DTOs
   - **Explicit `@PathVariable` and `@RequestParam` names**: `@PathVariable("id")`, `@RequestParam("status")`
   - All parameters marked `final`
   - **No business logic**—delegate to service
   - Extract `tenantId` from security context or JWT (via `SecurityContextHolder`)
   - Endpoints follow **kebab-case**: `/leads`, `/sequences-relance`

6. **TenantId Extraction**:
   - Use security context: `SecurityContextHolder.getContext().getAuthentication()` or inject `AuthService`
   - Pass `tenantId` to service methods
   - Example: `final Long tenantId = getCurrentTenantId();`

7. **Verify**:
   - Controller compiles without errors
   - All endpoints follow REST conventions
   - Request/response use DTOs only
   - `@Valid` on all request DTOs
   - `@PathVariable` and `@RequestParam` have explicit names
   - No business logic in controller
   - `tenantId` properly extracted and passed to service

## Templates

### Controller Template (Basic CRUD)

```java
package fr.leadrevive.[domain].controller;

import fr.leadrevive.[domain].dto.Rep[EntityName]Dto;
import fr.leadrevive.[domain].dto.ReqCreate[EntityName]Dto;
import fr.leadrevive.[domain].dto.ReqUpdate[EntityName]Dto;
import fr.leadrevive.[domain].service.[EntityName]Service;
import fr.leadrevive.common.security.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/[plural-kebab-case]")
@RequiredArgsConstructor
@Slf4j
public class [EntityName]Controller {

    private final [EntityName]Service [entity]Service;
    private final AuthService authService;

    @GetMapping("/{id}")
    public Rep[EntityName]Dto get[EntityName](@PathVariable("id") final Long id) {
        final Long tenantId = authService.getCurrentTenantId();
        return [entity]Service.get[EntityName](id, tenantId);
    }

    @GetMapping
    public List<Rep[EntityName]Dto> getAll[EntityNames]() {
        final Long tenantId = authService.getCurrentTenantId();
        return [entity]Service.getAll[EntityNames](tenantId);
    }

    @PostMapping
    public ResponseEntity<Rep[EntityName]Dto> create[EntityName](
            @RequestBody @Valid final ReqCreate[EntityName]Dto request) {
        final Long tenantId = authService.getCurrentTenantId();
        final Rep[EntityName]Dto created = [entity]Service.create[EntityName](request, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public Rep[EntityName]Dto update[EntityName](
            @PathVariable("id") final Long id,
            @RequestBody @Valid final ReqUpdate[EntityName]Dto request) {
        final Long tenantId = authService.getCurrentTenantId();
        return [entity]Service.update[EntityName](id, request, tenantId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete[EntityName](@PathVariable("id") final Long id) {
        final Long tenantId = authService.getCurrentTenantId();
        [entity]Service.delete[EntityName](id, tenantId);
        return ResponseEntity.noContent().build();
    }
}
```

### Controller with Filters/Queries

```java
package fr.leadrevive.[domain].controller;

import fr.leadrevive.[domain].dto.Rep[EntityName]Dto;
import fr.leadrevive.[domain].dto.ReqCreate[EntityName]Dto;
import fr.leadrevive.[domain].service.[EntityName]Service;
import fr.leadrevive.common.security.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/[plural-kebab-case]")
@RequiredArgsConstructor
public class [EntityName]Controller {

    private final [EntityName]Service [entity]Service;
    private final AuthService authService;

    @GetMapping
    public List<Rep[EntityName]Dto> getAll[EntityNames](
            @RequestParam(value = "status", required = false) final String status,
            @RequestParam(value = "search", required = false) final String search) {
        final Long tenantId = authService.getCurrentTenantId();
        if (status != null) {
            return [entity]Service.get[EntityNames]ByStatus(tenantId, status);
        }
        if (search != null) {
            return [entity]Service.search[EntityNames](tenantId, search);
        }
        return [entity]Service.getAll[EntityNames](tenantId);
    }

    @GetMapping("/{id}")
    public Rep[EntityName]Dto get[EntityName](@PathVariable("id") final Long id) {
        final Long tenantId = authService.getCurrentTenantId();
        return [entity]Service.get[EntityName](id, tenantId);
    }

    @PostMapping
    public ResponseEntity<Rep[EntityName]Dto> create[EntityName](
            @RequestBody @Valid final ReqCreate[EntityName]Dto request) {
        final Long tenantId = authService.getCurrentTenantId();
        final Rep[EntityName]Dto created = [entity]Service.create[EntityName](request, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete[EntityName](@PathVariable("id") final Long id) {
        final Long tenantId = authService.getCurrentTenantId();
        [entity]Service.delete[EntityName](id, tenantId);
        return ResponseEntity.noContent().build();
    }
}
```

## Example: LeadController

```java
package fr.leadrevive.lead.controller;

import fr.leadrevive.lead.dto.RepLeadDto;
import fr.leadrevive.lead.dto.ReqCreateLeadDto;
import fr.leadrevive.lead.dto.ReqUpdateLeadDto;
import fr.leadrevive.lead.service.LeadService;
import fr.leadrevive.common.security.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/leads")
@RequiredArgsConstructor
@Slf4j
public class LeadController {

    private final LeadService leadService;
    private final AuthService authService;

    @GetMapping("/{id}")
    public RepLeadDto getLead(@PathVariable("id") final Long id) {
        final Long tenantId = authService.getCurrentTenantId();
        return leadService.getLead(id, tenantId);
    }

    @GetMapping
    public List<RepLeadDto> getAllLeads(
            @RequestParam(value = "status", required = false) final String status) {
        final Long tenantId = authService.getCurrentTenantId();
        if (status != null) {
            return leadService.getLeadsByStatus(tenantId, status);
        }
        return leadService.getAllLeads(tenantId);
    }

    @PostMapping
    public ResponseEntity<RepLeadDto> createLead(
            @RequestBody @Valid final ReqCreateLeadDto request) {
        final Long tenantId = authService.getCurrentTenantId();
        final RepLeadDto created = leadService.createLead(request, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public RepLeadDto updateLead(
            @PathVariable("id") final Long id,
            @RequestBody @Valid final ReqUpdateLeadDto request) {
        final Long tenantId = authService.getCurrentTenantId();
        return leadService.updateLead(id, request, tenantId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLead(@PathVariable("id") final Long id) {
        final Long tenantId = authService.getCurrentTenantId();
        leadService.deleteLead(id, tenantId);
        return ResponseEntity.noContent().build();
    }
}
```

## Example: SequenceController

```java
package fr.leadrevive.sequence.controller;

import fr.leadrevive.sequence.dto.RepSequenceDto;
import fr.leadrevive.sequence.dto.ReqCreateSequenceDto;
import fr.leadrevive.sequence.service.SequenceService;
import fr.leadrevive.common.security.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sequences-relance")
@RequiredArgsConstructor
public class SequenceController {

    private final SequenceService sequenceService;
    private final AuthService authService;

    @GetMapping
    public List<RepSequenceDto> getAllSequences() {
        final Long tenantId = authService.getCurrentTenantId();
        return sequenceService.getAllSequences(tenantId);
    }

    @GetMapping("/{id}")
    public RepSequenceDto getSequence(@PathVariable("id") final Long id) {
        final Long tenantId = authService.getCurrentTenantId();
        return sequenceService.getSequence(id, tenantId);
    }

    @PostMapping
    public ResponseEntity<RepSequenceDto> createSequence(
            @RequestBody @Valid final ReqCreateSequenceDto request) {
        final Long tenantId = authService.getCurrentTenantId();
        final RepSequenceDto created = sequenceService.createSequence(request, tenantId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSequence(@PathVariable("id") final Long id) {
        final Long tenantId = authService.getCurrentTenantId();
        sequenceService.deleteSequence(id, tenantId);
        return ResponseEntity.noContent().build();
    }
}
```

## HTTP Status Codes

| Code | Usage |
|------|-------|
| 200 OK | GET successful, PUT successful |
| 201 CREATED | POST successful (new resource created) |
| 204 NO_CONTENT | DELETE successful, no response body |
| 400 BAD_REQUEST | Invalid request (validation failure) |
| 404 NOT_FOUND | Resource not found |
| 409 CONFLICT | Conflict (duplicate, constraint violation) |
| 500 INTERNAL_SERVER_ERROR | Unexpected server error |

## Key Notes

- **Kebab-case endpoints**: `/leads`, `/sequences-relance`, NOT `/Leads` or `/sequenceRelance`
- **Plural for collections**: `/leads`, **singular for actions**: `/leads/{id}`
- **ResponseEntity only for status control**—simple GET returns DTO
- **POST always returns 201 CREATED** with Location header (optional)
- **DELETE returns 204 NO_CONTENT** (no response body)
- **@Valid on all request DTOs** (enforces Bean Validation)
- **No business logic**—controller delegates to service
- **TenantId extraction** via `AuthService` or security context
- **@PathVariable and @RequestParam always explicit**: `@PathVariable("id")`
- **All parameters final**

## Checklist

- [ ] Controller created in correct package (`fr.leadrevive.[domain].controller`)
- [ ] Annotated with `@RestController`, `@RequestMapping`, `@RequiredArgsConstructor`
- [ ] Endpoint path uses kebab-case (e.g., `/leads`, `/sequences-relance`)
- [ ] All injected dependencies are `final`
- [ ] All method parameters marked `final`
- [ ] GET methods return `RepDto` directly (not `ResponseEntity`)
- [ ] POST returns `ResponseEntity.status(HttpStatus.CREATED).body(RepDto)`
- [ ] DELETE returns `ResponseEntity.noContent().build()`
- [ ] All request DTOs have `@Valid` annotation
- [ ] `@PathVariable` and `@RequestParam` have explicit names
- [ ] `tenantId` extracted and passed to all service methods
- [ ] No business logic in controller
- [ ] Only DTOs used (no entities)
- [ ] Controller compiles without errors
- [ ] Reviewed CONVENTIONS.md section 4
