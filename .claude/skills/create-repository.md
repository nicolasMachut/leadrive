# Skill : Create Repository

## Declencheur

User asks to create a JPA repository (e.g., "create a LeadRepository", "add repository methods for Sequence").

## Instructions

1. **Review CONVENTIONS.md** section 8 (Repositories) and section 1 (naming - suffix `Repository`)
2. **Check ARCHITECTURE.md** for package placement (e.g., `fr.leadrevive.lead.repository`)
3. **Create the Repository interface** with:
   - `@Repository` annotation (mandatory)
   - Extends `JpaRepository<Entity, Long>`
   - No logic in repository—queries only
   - Spring Data query methods for simple filters (snake_case field names in queries)
   - `@Query("""...""")` with JPQL text blocks for complex queries
   - Always include methods that filter by `tenant_id` (multi-tenant requirement)
   - All `@Param` annotations explicit and matching method parameter names
   - Use JPQL format (entity names, camelCase), not SQL

4. **Common patterns**:
   - `findByTenantId(Long tenantId)`: list all for tenant
   - `@Query` methods for complex filtering with tenant_id
   - Custom queries filter by tenant_id automatically (per CONVENTIONS.md section 9)
   - Use JPQL field names (entity attributes), not database column names

5. **Verify**:
   - Repository compiles without errors
   - All methods filter by tenant_id (explicitly or via Hibernate filter)
   - Query methods use JPQL with entity attribute names
   - `@Query` uses text blocks (""" ... """)
   - `@Param` names match method parameter names

## Templates

### Repository Template (Simple)

```java
package fr.leadrevive.[domain].repository;

import fr.leadrevive.[domain].entity.[EntityName];
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface [EntityName]Repository extends JpaRepository<[EntityName], Long> {

    List<[EntityName]> findByTenantId(final Long tenantId);
}
```

### Repository Template (With Custom Queries)

```java
package fr.leadrevive.[domain].repository;

import fr.leadrevive.[domain].entity.[EntityName];
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface [EntityName]Repository extends JpaRepository<[EntityName], Long> {

    List<[EntityName]> findByTenantId(final Long tenantId);

    @Query("""
            SELECT e FROM [EntityName] e
            WHERE e.tenantId = :tenantId
            AND e.[fieldName] = :value
            """)
    List<[EntityName]> findByTenantIdAnd[FieldName](
        @Param("tenantId") final Long tenantId,
        @Param("value") final String value
    );

    @Query("""
            SELECT e FROM [EntityName] e
            WHERE e.tenantId = :tenantId
            AND e.[fieldName] IN :values
            ORDER BY e.id DESC
            """)
    List<[EntityName]> findByTenantIdAndValuesIn(
        @Param("tenantId") final Long tenantId,
        @Param("values") final List<String> values
    );
}
```

## Example: LeadRepository

```java
package fr.leadrevive.lead.repository;

import fr.leadrevive.lead.entity.Lead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeadRepository extends JpaRepository<Lead, Long> {

    List<Lead> findByTenantId(final Long tenantId);

    @Query("""
            SELECT l FROM Lead l
            WHERE l.tenantId = :tenantId
            AND l.emailProspect = :email
            """)
    Optional<Lead> findByTenantIdAndEmail(
        @Param("tenantId") final Long tenantId,
        @Param("email") final String email
    );

    @Query("""
            SELECT l FROM Lead l
            WHERE l.tenantId = :tenantId
            AND l.statutLead = :statut
            ORDER BY l.creeLe DESC
            """)
    List<Lead> findByTenantIdAndStatut(
        @Param("tenantId") final Long tenantId,
        @Param("statut") final String statut
    );

    @Query("""
            SELECT l FROM Lead l
            WHERE l.tenantId = :tenantId
            AND l.estValide = TRUE
            AND l.nomProspect LIKE CONCAT('%', :search, '%')
            """)
    List<Lead> searchValidLeadsByName(
        @Param("tenantId") final Long tenantId,
        @Param("search") final String search
    );
}
```

## Example: SequenceRepository

```java
package fr.leadrevive.sequence.repository;

import fr.leadrevive.sequence.entity.Sequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SequenceRepository extends JpaRepository<Sequence, Long> {

    List<Sequence> findByTenantId(final Long tenantId);

    @Query("""
            SELECT s FROM Sequence s
            WHERE s.tenantId = :tenantId
            AND s.actif = TRUE
            ORDER BY s.nomSequence ASC
            """)
    List<Sequence> findActiveSequencesByTenant(
        @Param("tenantId") final Long tenantId
    );

    @Query("""
            SELECT s FROM Sequence s
            WHERE s.tenantId = :tenantId
            AND s.typeSequence = :type
            """)
    List<Sequence> findByTenantIdAndType(
        @Param("tenantId") final Long tenantId,
        @Param("type") final String type
    );
}
```

## Key Notes

- **Toujours privilegier JPQL plutot que les native queries** (`nativeQuery = true`). N'utiliser les native queries que lorsque JPQL ne peut pas exprimer la requete (fonctions SQL specifiques comme `DATE()`, `TO_CHAR()`, sous-requetes complexes, etc.). Si une native query existante peut etre reecrite en JPQL, la convertir.
- Always include `findByTenantId(Long tenantId)` for multi-tenant filtering
- Use JPQL entity field names (e.g., `l.emailProspect`), not database column names (e.g., `email_prospect`)
- `@Query` queries are text blocks (""" ... """)
- All `@Param` names match method parameters exactly
- No stored procedures, no TVP (Table-Valued Parameters)
- Spring Data query methods (e.g., `findByField`) automatically filter by tenant via Hibernate `@FilterDef`
- Order results explicitly when relevant (e.g., `ORDER BY l.creeLe DESC`)
- Optional<T> for queries returning 0 or 1 result
- List<T> for queries returning 0 or N results

## Checklist

- [ ] Repository interface created in correct package (`fr.leadrevive.[domain].repository`)
- [ ] Annotated with `@Repository`
- [ ] Extends `JpaRepository<[Entity], Long>`
- [ ] Includes `findByTenantId(Long tenantId)` method
- [ ] All custom queries include `tenant_id` filtering
- [ ] `@Query` uses text blocks (""" ... """)
- [ ] Uses JPQL format (entity attribute names, not column names)
- [ ] All `@Param` annotations present and match parameter names
- [ ] Return types correct: `Optional<T>` for single, `List<T>` for multiple
- [ ] No SQL Server stored procedures or TVP syntax
- [ ] Repository compiles without errors
- [ ] Reviewed CONVENTIONS.md section 8 and ARCHITECTURE.md
