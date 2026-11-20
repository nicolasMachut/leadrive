# Skill : Create DTOs

## Declencheur

User asks to create DTOs for a domain (e.g., "create Lead DTOs", "add Req/Rep DTOs for Sequence").

## Instructions

1. **Review CONVENTIONS.md** sections 1 (naming conventions) and 3 (Lombok for DTOs)
2. **Check ARCHITECTURE.md** for package placement (e.g., `fr.leadrevive.lead.dto`)
3. **Create three DTO types**:
   - **RepDto** (Response): returned by API to client → `RepLeadDto`, `RepSequenceDetailDto`
   - **ReqDto** (Request): received as input in request body → `ReqCreateLeadDto`, `ReqUpdateSequenceDto`
   - **Dto** (Generic): used internally or both ways → `LeadDto`

4. **DTO Template Rules**:
   - Use `@Getter @Builder @NoArgsConstructor @AllArgsConstructor`
   - Never use `@Data` (unless explicitly needing `equals`/`hashCode`/`toString`)
   - All fields should be typed explicitly (no `var`)
   - Field names use camelCase (Java convention): `idLead`, `nomProspect`, `emailProspect`
   - Never include `tenantId` in DTOs (internal field, not exposed to clients)
   - Never include `creeLe`, `modifieLe` timestamps in Req DTOs (auto-managed by backend)

5. **ReqDto (Request Input)**:
   - Add Bean Validation annotations (`@NotNull`, `@NotBlank`, `@Size`, `@Email`, etc.)
   - Only include writable fields (what clients can submit)
   - Include validation constraints for business rules
   - Optional: add custom validators via `@Constraint` if complex rules

6. **RepDto (Response Output)**:
   - No validation annotations (data already validated)
   - Include all relevant fields (including `idLead`, read-only fields)
   - May include `creeLe`, `modifieLe` for audit trail (optional)
   - Nested DTOs for relationships (avoid entity references)

7. **Dto (Generic)**:
   - Minimal fields, used for internal mappings
   - No validation annotations
   - Optional `@Builder` if complex construction

## Templates

### RepDto Template (Response)

```java
package fr.leadrevive.[domain].dto;

import tools.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Rep[EntityName]Dto {

    private Long id[EntityName];
    private String nomProspect;
    private String emailProspect;
    private LocalDateTime creeLe;
    private LocalDateTime modifieLe;
}
```

### ReqDto Template (Request Input)

```java
package fr.leadrevive.[domain].dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReqCreate[EntityName]Dto {

    @NotBlank(message = "Nom prospect obligatoire")
    @Size(max = 255, message = "Nom prospect max 255 caracteres")
    private String nomProspect;

    @NotBlank(message = "Email obligatoire")
    @Email(message = "Email invalide")
    private String emailProspect;

    @Size(max = 20, message = "Telephone max 20 caracteres")
    private String telephoneProspect;
}
```

### Generic Dto Template

```java
package fr.leadrevive.[domain].dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class [EntityName]Dto {

    private Long id[EntityName];
    private String nomProspect;
    private String emailProspect;
}
```

## Example: Lead DTOs

### RepLeadDto.java (Response)

```java
package fr.leadrevive.lead.dto;

import tools.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RepLeadDto {

    private Long idLead;
    private String nomProspect;
    private String emailProspect;
    private String telephoneProspect;
    private String vehiculeConsulte;
    private String statutLead;
    private Boolean estValide;
    private LocalDateTime creeLe;
    private LocalDateTime modifieLe;
}
```

### ReqCreateLeadDto.java (Request - Create)

```java
package fr.leadrevive.lead.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReqCreateLeadDto {

    @NotBlank(message = "Nom prospect obligatoire")
    @Size(max = 255, message = "Nom max 255 caracteres")
    private String nomProspect;

    @NotBlank(message = "Email obligatoire")
    @Email(message = "Email invalide")
    private String emailProspect;

    @Size(max = 20, message = "Telephone max 20 caracteres")
    private String telephoneProspect;

    @Size(max = 255, message = "Vehicule max 255 caracteres")
    private String vehiculeConsulte;
}
```

### ReqUpdateLeadDto.java (Request - Update)

```java
package fr.leadrevive.lead.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReqUpdateLeadDto {

    @Size(max = 255, message = "Nom max 255 caracteres")
    private String nomProspect;

    @Email(message = "Email invalide")
    private String emailProspect;

    @Size(max = 20, message = "Telephone max 20 caracteres")
    private String telephoneProspect;

    @Size(max = 255, message = "Vehicule max 255 caracteres")
    private String vehiculeConsulte;

    @Pattern(regexp = "^(NOUVEAU|EN_COURS|CONVERTIR|PERTE)$", message = "Statut invalide")
    private String statutLead;
}
```

## Common Validation Annotations

| Annotation | Usage |
|---|---|
| `@NotNull` | Field must not be null |
| `@NotBlank` | String must not be null or empty |
| `@NotEmpty` | Collection/array must not be empty |
| `@Size(min, max)` | String/collection length constraints |
| `@Email` | Valid email format |
| `@Pattern(regexp)` | Regex validation |
| `@Min`, `@Max` | Numeric range constraints |
| `@Positive`, `@Negative` | Number sign constraints |

## Checklist

- [ ] DTOs created in correct package (`fr.leadrevive.[domain].dto`)
- [ ] RepDto has `@Getter @Builder @NoArgsConstructor @AllArgsConstructor`
- [ ] ReqDto has `@Getter @Builder @NoArgsConstructor @AllArgsConstructor`
- [ ] No `@Data` on DTOs (use `@Getter` instead)
- [ ] All fields typed explicitly (no `var`)
- [ ] Field names use camelCase: `idLead`, `nomProspect`, `emailProspect`
- [ ] ReqDto includes Bean Validation annotations (`@NotBlank`, `@Email`, `@Size`, etc.)
- [ ] RepDto has no validation annotations
- [ ] `tenantId` never exposed in DTOs
- [ ] `creeLe`, `modifieLe` only in RepDto (not in ReqDto)
- [ ] RepDto includes `@JsonInclude(JsonInclude.Include.NON_NULL)` for optional fields
- [ ] DTOs compile without errors
- [ ] Reviewed CONVENTIONS.md sections 1 & 3
