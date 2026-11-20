# Skill : Create Mapper

## Declencheur

User asks to create a MapStruct mapper (e.g., "create a LeadMapper", "add mapper for Sequence").

## Instructions

1. **Review CONVENTIONS.md** section 6 (MapStruct) and section 15 (general rules)
2. **Check ARCHITECTURE.md** for package placement (e.g., `fr.leadrevive.lead.mapper`)
3. **Create the Mapper interface** with:
   - `@Mapper` annotation (no `componentModel` - configured globally in pom.xml)
   - All field mappings explicit via `@Mapping` annotations
   - `toDto(Entity)` → convert entity to response DTO
   - `toEntity(ReqDto)` → convert request DTO to entity for saving
   - Ignore auto-generated fields on `toEntity`: `id`, `creeLe`, `modifieLe`, `tenantId`
   - Never include `tenant_id` mapping in `toEntity` (set by service or builder)
   - Use `@Mapping(source = "...", target = "...")` for all fields
   - Use `@Mapping(target = "...", ignore = true)` for auto-generated fields

4. **Mapping Pattern**:
   - Entity field names: camelCase but often differ from DTO (e.g., entity `id` → DTO `idLead`)
   - Always map primary key explicitly
   - Map nested objects/relationships separately if complex
   - Ignore timestamp fields from BaseEntity in `toEntity`

5. **Verify**:
   - Mapper compiles without errors
   - All mappings are explicit (no implicit mappings)
   - Auto-generated fields marked `ignore = true`
   - `tenantId` never mapped in `toEntity`
   - Methods return proper types (DTO or Entity)

## Templates

### Mapper Template (Simple)

```java
package fr.leadrevive.[domain].mapper;

import fr.leadrevive.[domain].dto.Rep[EntityName]Dto;
import fr.leadrevive.[domain].dto.ReqCreate[EntityName]Dto;
import fr.leadrevive.[domain].entity.[EntityName];
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface [EntityName]Mapper {

    @Mapping(source = "id", target = "id[EntityName]")
    Rep[EntityName]Dto toDto(final [EntityName] entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "creeLe", ignore = true)
    @Mapping(target = "modifieLe", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    [EntityName] toEntity(final ReqCreate[EntityName]Dto dto);
}
```

### Mapper Template (Complex with Multiple Methods)

```java
package fr.leadrevive.[domain].mapper;

import fr.leadrevive.[domain].dto.Rep[EntityName]Dto;
import fr.leadrevive.[domain].dto.ReqCreate[EntityName]Dto;
import fr.leadrevive.[domain].dto.ReqUpdate[EntityName]Dto;
import fr.leadrevive.[domain].entity.[EntityName];
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper
public interface [EntityName]Mapper {

    @Mapping(source = "id", target = "id[EntityName]")
    @Mapping(source = "creeLe", target = "creeLe")
    @Mapping(source = "modifieLe", target = "modifieLe")
    Rep[EntityName]Dto toDto(final [EntityName] entity);

    List<Rep[EntityName]Dto> toDtoList(final List<[EntityName]> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "creeLe", ignore = true)
    @Mapping(target = "modifieLe", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    [EntityName] toEntity(final ReqCreate[EntityName]Dto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "creeLe", ignore = true)
    @Mapping(target = "modifieLe", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    void updateEntityFromDto(final ReqUpdate[EntityName]Dto dto, @MappingTarget final [EntityName] entity);
}
```

## Example: LeadMapper

```java
package fr.leadrevive.lead.mapper;

import fr.leadrevive.lead.dto.RepLeadDto;
import fr.leadrevive.lead.dto.ReqCreateLeadDto;
import fr.leadrevive.lead.dto.ReqUpdateLeadDto;
import fr.leadrevive.lead.entity.Lead;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper
public interface LeadMapper {

    @Mapping(source = "id", target = "idLead")
    @Mapping(source = "creeLe", target = "creeLe")
    @Mapping(source = "modifieLe", target = "modifieLe")
    RepLeadDto toDto(final Lead entity);

    List<RepLeadDto> toDtoList(final List<Lead> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "creeLe", ignore = true)
    @Mapping(target = "modifieLe", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    Lead toEntity(final ReqCreateLeadDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "creeLe", ignore = true)
    @Mapping(target = "modifieLe", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    void updateEntityFromDto(final ReqUpdateLeadDto dto, @MappingTarget final Lead entity);
}
```

## Example: SequenceMapper

```java
package fr.leadrevive.sequence.mapper;

import fr.leadrevive.sequence.dto.RepSequenceDto;
import fr.leadrevive.sequence.dto.ReqCreateSequenceDto;
import fr.leadrevive.sequence.entity.Sequence;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper
public interface SequenceMapper {

    @Mapping(source = "id", target = "idSequence")
    @Mapping(source = "nomSequence", target = "nomSequence")
    @Mapping(source = "typeSequence", target = "typeSequence")
    @Mapping(source = "creeLe", target = "creeLe")
    RepSequenceDto toDto(final Sequence entity);

    List<RepSequenceDto> toDtoList(final List<Sequence> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "creeLe", ignore = true)
    @Mapping(target = "modifieLe", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    Sequence toEntity(final ReqCreateSequenceDto dto);
}
```

## Key Notes

- **One mapper per entity** (one domain = one mapper interface)
- **No implementations**—MapStruct generates them at compile time
- **`@Mapper` without `componentModel`**—global config in pom.xml enables Spring integration
- **All mappings explicit**—no implicit field matching (be verbose)
- **Auto-generated fields always ignored**: `id`, `creeLe`, `modifieLe`, `tenantId`
- **Never include `tenantId` in request→entity mapping** (set by service or builder)
- **Primary key always explicit**: `@Mapping(source = "id", target = "id[EntityName]")`
- **Use `@MappingTarget`** for partial updates (fromDto, @MappingTarget entity)
- **List methods** for bulk conversions (if needed)

## Checklist

- [ ] Mapper interface created in correct package (`fr.leadrevive.[domain].mapper`)
- [ ] Annotated with `@Mapper` (no `componentModel`)
- [ ] All field mappings explicit via `@Mapping`
- [ ] `toDto(Entity)` method present with explicit mappings
- [ ] `toEntity(ReqDto)` method present with ignored auto-generated fields
- [ ] `id` field mapped explicitly (source "id" to target "id[EntityName]")
- [ ] `creeLe`, `modifieLe`, `tenantId` marked `ignore = true` in `toEntity`
- [ ] `tenantId` never mapped in request→entity conversion
- [ ] Timestamps (`creeLe`, `modifieLe`) included in `toDto` if needed
- [ ] No implicit field matching (all mappings declared)
- [ ] Mapper compiles without errors
- [ ] Reviewed CONVENTIONS.md section 6
