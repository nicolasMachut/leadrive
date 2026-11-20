# Skill : Create Entity

## Declencheur

User asks to create a new JPA entity (e.g., "create a Lead entity", "add a Sequence entity").

## Instructions

1. **Review CONVENTIONS.md** section 7 (Entities JPA) and section 15 (naming rules)
2. **Check ARCHITECTURE.md** for package placement (e.g., `fr.leadrevive.lead.entity`)
3. **Create the Entity class** with:
   - `@Entity` + `@Table(name = "...")`
   - Extend `BaseEntity` for audit fields (`creeLe`, `modifieLe`)
   - `@Getter @Setter` (never `@Data`)
   - Primary key: `Long id` with `@Id`, `@GeneratedValue(strategy = GenerationType.IDENTITY)`, `@Column(name = "id_...")`
   - `tenant_id` field: `@Column(name = "tenant_id", nullable = false)` (multi-tenant mandatory)
   - All fields with explicit `@Column(name = "...")` using snake_case database names
   - All parameters and fields marked `final` where possible
   - No field without type prefix (e.g., `private final Long id;` not `private var id;`)

4. **Create the Flyway migration** in `backend/src/main/resources/db/migration/`:
   - Filename: `V[number]__create_table_[entity_name].sql` (increment version)
   - Include table creation with:
     - Primary key: `id_[table_name] BIGSERIAL PRIMARY KEY`
     - All columns with snake_case names matching entity `@Column` declarations
     - `tenant_id BIGINT NOT NULL` (always required)
     - Timestamps: `cree_le TIMESTAMP NOT NULL DEFAULT NOW()`, `modifie_le TIMESTAMP`
     - Indexes on `tenant_id` and frequently queried fields
   - Never modify migrations after they're applied

5. **Verify**:
   - Entity compiles without errors
   - Migration filename follows convention
   - All fields have explicit types and `@Column` annotations
   - `tenant_id` is present and non-nullable
   - Entity does not use `@Data`

## Templates

### Entity Template

```java
package fr.leadrevive.[domain].entity;

import fr.leadrevive.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "[table_name]")
@Getter
@Setter
public class [EntityName] extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_[table_name]")
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "[field_name]")
    private String [fieldName];

    @Column(name = "[field_name]", nullable = false)
    private Boolean [fieldName];
}
```

### Flyway Migration Template

```sql
-- V[XXX]__create_table_[entity_name].sql

CREATE TABLE [table_name] (
    id_[table_name] BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    [field_name] VARCHAR(255),
    [field_name] BOOLEAN DEFAULT FALSE,
    cree_le TIMESTAMP NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP,
    CONSTRAINT fk_[table_name]_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id_tenant)
);

CREATE INDEX idx_[table_name]_tenant_id ON [table_name](tenant_id);
```

## Example: Lead Entity + Migration

### Entity: Lead.java

```java
package fr.leadrevive.lead.entity;

import fr.leadrevive.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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

    @Column(name = "email_prospect", nullable = false)
    private String emailProspect;

    @Column(name = "telephone_prospect")
    private String telephoneProspect;

    @Column(name = "vehicule_consulte")
    private String vehiculeConsulte;

    @Column(name = "statut_lead", nullable = false)
    private String statutLead;

    @Column(name = "est_valide")
    private Boolean estValide;
}
```

### Migration: V001__create_table_lead.sql

```sql
CREATE TABLE lead (
    id_lead BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    nom_prospect VARCHAR(255),
    email_prospect VARCHAR(255) NOT NULL,
    telephone_prospect VARCHAR(20),
    vehicule_consulte VARCHAR(255),
    statut_lead VARCHAR(50) NOT NULL,
    est_valide BOOLEAN DEFAULT FALSE,
    cree_le TIMESTAMP NOT NULL DEFAULT NOW(),
    modifie_le TIMESTAMP,
    CONSTRAINT fk_lead_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id_tenant)
);

CREATE INDEX idx_lead_tenant_id ON lead(tenant_id);
CREATE INDEX idx_lead_statut ON lead(statut_lead);
```

## Checklist

- [ ] Entity class created in correct package (`fr.leadrevive.[domain].entity`)
- [ ] Entity extends `BaseEntity` (no custom `creeLe`/`modifieLe` fields)
- [ ] Uses `@Entity`, `@Table(name = "...")`, `@Getter`, `@Setter`
- [ ] Never uses `@Data`
- [ ] Primary key is `Long id` with `@Id`, `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- [ ] All fields have `@Column(name = "...")` with snake_case database names
- [ ] `tenant_id` field present with `@Column(name = "tenant_id", nullable = false)`
- [ ] No `var` usage, all fields explicitly typed with `private`
- [ ] Flyway migration created: `V[number]__create_table_[name].sql`
- [ ] Migration includes: id, tenant_id, all fields, cree_le, modifie_le, indexes
- [ ] Migration includes foreign key constraint to tenant
- [ ] Entity compiles without errors
- [ ] Reviewed CONVENTIONS.md sections 7 & 15
