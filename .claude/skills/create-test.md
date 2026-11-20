# Skill : Create Test

## Declencheur

User asks to create unit tests, test classes, or test fixtures for a domain, service, mapper, or any Java class.

## Instructions

### Structure des tests

Chaque test suit la structure **GIVEN / WHEN / THEN** en commentaires :

```java
@Test
void nomMethode_scenario_resultatAttendu() {
    // GIVEN
    final Lead lead = LeadFixture.lead();

    // WHEN
    final RepLeadDto actual = mapper.toDto(lead);

    // THEN
    assertAll(
            () -> assertEquals(lead.getId(), actual.getIdLead()),
            () -> assertEquals(lead.getNom(), actual.getNom())
    );
}
```

### Conventions

1. **Nommage test** : `nomMethode_scenario_resultatAttendu` en camelCase
2. **Classe de test** : suffixe `Test`, dans le meme package que la classe testee (sous `src/test/java`)
3. **Annotations** : `@ExtendWith(MockitoExtension.class)` pour les tests avec mocks
4. **Mocks** : `@Mock` pour les dependances, `@InjectMocks` pour la classe testee
5. **Assertions groupees** : utiliser `assertAll()` quand on verifie plusieurs champs
6. **Assertions** : mixer `assertEquals` (JUnit) et `assertThat` (AssertJ) selon le contexte
   - `assertEquals` pour les comparaisons simples
   - `assertThat` pour les collections, containsExactly, hasSize, etc.
7. **`final` partout** : sur toutes les variables locales
8. **Pas de `var`** : typage explicite

### Fixtures avec EasyRandom

Les donnees de test sont generees via **EasyRandom** dans des classes **Fixture** dediees.

**Pattern Fixture** :

```java
package fr.leadrevive.fixtures;

import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.entity.LeadStatutEnum;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;

public class LeadFixture {

    private static final EasyRandomParameters PARAMETERS = new EasyRandomParameters()
            .excludeField(field -> field.getName().equals("rawData"));

    private static final EasyRandom GENERATOR = new EasyRandom(PARAMETERS);

    public static Lead lead() {
        final Lead lead = GENERATOR.nextObject(Lead.class);
        lead.setStatut(LeadStatutEnum.NOUVEAU);
        return lead;
    }

    public static Lead leadAvecStatut(final LeadStatutEnum statut) {
        final Lead lead = lead();
        lead.setStatut(statut);
        return lead;
    }
}
```

**Regles Fixture** :
- Une classe Fixture par domaine metier (ex: `LeadFixture`, `TenantFixture`, `UserFixture`)
- Rangees dans le package `fr.leadrevive.fixtures` (ou sous-packages par domaine)
- Methodes `static` retournant des objets pre-remplis aleatoirement
- `EasyRandomParameters` pour exclure les champs problematiques (relations JPA circulaires, JSONB, etc.)
- Surcharges pour des variantes specifiques (ex: `leadAvecStatut(...)`)

### Dependance EasyRandom

S'assurer que la dependance est dans le `pom.xml` :

```xml
<dependency>
    <groupId>io.github.dvgaba</groupId>
    <artifactId>easy-random-core</artifactId>
    <version>7.1.1</version>
    <scope>test</scope>
</dependency>
```

> Note : le fork `io.github.dvgaba` est la version maintenue de EasyRandom (l'original `org.jeasy` n'est plus maintenu). L'API reste identique.

### Types de tests

#### Test de Service

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
        // GIVEN
        final Lead lead = LeadFixture.lead();
        final RepLeadDto dto = RepLeadDto.builder().idLead(lead.getId()).build();
        when(leadRepository.findById(lead.getId())).thenReturn(Optional.of(lead));
        when(leadMapper.toDto(lead)).thenReturn(dto);

        // WHEN
        final RepLeadDto actual = leadService.getLead(lead.getId());

        // THEN
        assertEquals(lead.getId(), actual.getIdLead());
    }

    @Test
    void getLead_inexistant_lanceException() {
        // GIVEN
        when(leadRepository.findById(999L)).thenReturn(Optional.empty());

        // WHEN / THEN
        assertThrows(LeadNotFoundException.class, () -> leadService.getLead(999L));
    }
}
```

#### Test de Mapper

```java
@ExtendWith(MockitoExtension.class)
class LeadMapperTest {

    @InjectMocks
    private LeadMapperImpl mapper;

    @Test
    void toDto_leadComplet_retourneDtoCorrect() {
        // GIVEN
        final Lead lead = LeadFixture.lead();

        // WHEN
        final RepLeadDto actual = mapper.toDto(lead);

        // THEN
        assertAll(
                () -> assertEquals(lead.getId(), actual.getIdLead()),
                () -> assertEquals(lead.getNom(), actual.getNom()),
                () -> assertEquals(lead.getEmail(), actual.getEmail()),
                () -> assertEquals(lead.getTelephone(), actual.getTelephone())
        );
    }
}
```

#### Test de Validation (sans mocks)

```java
class LeadValidationServiceTest {

    private final LeadValidationService service = new LeadValidationService();

    @Test
    void validerLead_toutValide_retourneListeVide() {
        // GIVEN
        final String nom = "Dupont";
        final String email = "dupont@test.com";
        final String telephone = "+33612345678";

        // WHEN
        final List<String> erreurs = service.validerLead(nom, email, telephone);

        // THEN
        assertThat(erreurs).isEmpty();
    }
}
```

### Checklist

- [ ] Classe de test dans le bon package sous `src/test/java`
- [ ] Fixtures creees avec EasyRandom pour chaque entite utilisee
- [ ] Structure GIVEN / WHEN / THEN respectee
- [ ] `assertAll` pour les verifications multiples
- [ ] `final` sur toutes les variables
- [ ] Pas de `var`
- [ ] Scenarios positifs ET negatifs (cas d'erreur, not found, etc.)
- [ ] Dependance `easy-random-core` dans le pom.xml (scope test)
