package fr.leadrevive.ai.service;

import fr.leadrevive.ai.dto.ReqTenantAiConfigDto;
import fr.leadrevive.ai.entity.TenantAiConfig;
import fr.leadrevive.ai.repository.TenantAiConfigRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests de validation de la plage d'envoi du premier message auto :
 *  - contrainte de bean-validation sur l'heure de fin (1..24, 24 = minuit) ;
 *  - contrainte metier debut &lt; fin (TenantAiConfigService.saveConfig).
 */
@ExtendWith(MockitoExtension.class)
class TenantAiConfigServiceTest {

    @Mock private TenantAiConfigRepository tenantAiConfigRepository;
    @InjectMocks private TenantAiConfigService service;

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    private ReqTenantAiConfigDto dto(final Integer debut, final Integer fin) {
        return ReqTenantAiConfigDto.builder()
                .nomConcession("Garage Test")
                .premierMessageAutoActif(true)
                .premierMessageAutoDelaiMinutes(5)
                .premierMessageAutoFenetreDebutHeure(debut)
                .premierMessageAutoFenetreFinHeure(fin)
                .build();
    }

    // ── Bean validation sur l'heure de fin ─────────────────────────────────────

    @Test
    void beanValidation_fin24_estAcceptee() {
        final Set<ConstraintViolation<ReqTenantAiConfigDto>> violations = validator.validate(dto(8, 24));
        assertThat(violations).isEmpty();
    }

    @Test
    void beanValidation_fin25_estRefusee() {
        final Set<ConstraintViolation<ReqTenantAiConfigDto>> violations = validator.validate(dto(8, 25));
        assertThat(violations)
                .anyMatch(v -> "premierMessageAutoFenetreFinHeure".equals(v.getPropertyPath().toString()));
    }

    @Test
    void beanValidation_fin0_estRefusee() {
        final Set<ConstraintViolation<ReqTenantAiConfigDto>> violations = validator.validate(dto(8, 0));
        assertThat(violations)
                .anyMatch(v -> "premierMessageAutoFenetreFinHeure".equals(v.getPropertyPath().toString()));
    }

    // ── Contrainte metier debut < fin (service) ────────────────────────────────

    @BeforeEach
    void resetMock() {
        // Par defaut, pas de config existante : saveConfig en cree une nouvelle.
    }

    @Test
    void saveConfig_fin24_estAcceptee() {
        when(tenantAiConfigRepository.findByTenantId(1L)).thenReturn(Optional.empty());
        when(tenantAiConfigRepository.save(any(TenantAiConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.saveConfig(1L, dto(8, 24));
        // Pas d'exception : 8 < 24 respecte la contrainte metier.
    }

    @Test
    void saveConfig_finInferieureOuEgaleAuDebut_estRefusee() {
        when(tenantAiConfigRepository.findByTenantId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveConfig(1L, dto(20, 8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("anterieure");
    }
}
