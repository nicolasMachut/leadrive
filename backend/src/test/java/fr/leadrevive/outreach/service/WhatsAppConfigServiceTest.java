package fr.leadrevive.outreach.service;

import fr.leadrevive.outreach.entity.StatutWhatsappConfigEnum;
import fr.leadrevive.outreach.entity.TenantWhatsappConfig;
import fr.leadrevive.outreach.repository.TenantWhatsappConfigRepository;
import fr.leadrevive.outreach.repository.TenantWhatsappLogoRepository;
import fr.leadrevive.outreach.repository.TenantWhatsappNumberHistoryRepository;
import fr.leadrevive.outreach.repository.WhatsappAdminActionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link WhatsAppConfigService#signalerEchecEnvoi} : bascule en RESTREINT
 * uniquement quand le code d'erreur est un code de restriction ET que la config est
 * ACTIF (l'envoi se bloque alors via l'invariant statut != ACTIF).
 */
@ExtendWith(MockitoExtension.class)
class WhatsAppConfigServiceTest {

    @Mock private TenantWhatsappConfigRepository configRepository;
    @Mock private TenantWhatsappNumberHistoryRepository historyRepository;
    @Mock private TenantWhatsappLogoRepository logoRepository;
    @Mock private WhatsappAdminActionRepository adminActionRepository;

    private WhatsAppConfigService service;

    @BeforeEach
    void setUp() {
        service = new WhatsAppConfigService(
                configRepository, historyRepository, logoRepository, adminActionRepository);
        // Liste de codes de restriction simulee (en prod : vide par defaut, renseignee via env).
        ReflectionTestUtils.setField(service, "codesRestrictionCsv", "63024, 63042");
    }

    private TenantWhatsappConfig config(final StatutWhatsappConfigEnum statut) {
        final TenantWhatsappConfig c = new TenantWhatsappConfig();
        c.setTenantId(1L);
        c.setStatut(statut);
        return c;
    }

    @Test
    void codeDeRestriction_surConfigActive_basculeRestreint() {
        final TenantWhatsappConfig c = config(StatutWhatsappConfigEnum.ACTIF);
        when(configRepository.findByTenantId(1L)).thenReturn(Optional.of(c));

        service.signalerEchecEnvoi(1L, "63042", "compte restreint");

        assertThat(c.getStatut()).isEqualTo(StatutWhatsappConfigEnum.RESTREINT);
        assertThat(c.getMetaRejectReason()).contains("63042");
        verify(configRepository).save(c);
    }

    @Test
    void codeHorsListe_neChangeRien() {
        // 63016 = hors fenetre 24h, PAS une restriction : aucune bascule.
        service.signalerEchecEnvoi(1L, "63016", "hors fenetre 24h");

        verify(configRepository, never()).save(any());
    }

    @Test
    void configNonActive_neChangeRien() {
        final TenantWhatsappConfig c = config(StatutWhatsappConfigEnum.EN_VALIDATION);
        when(configRepository.findByTenantId(1L)).thenReturn(Optional.of(c));

        service.signalerEchecEnvoi(1L, "63042", "x");

        assertThat(c.getStatut()).isEqualTo(StatutWhatsappConfigEnum.EN_VALIDATION);
        verify(configRepository, never()).save(any());
    }

    @Test
    void codeNull_neChangeRien() {
        service.signalerEchecEnvoi(1L, null, "x");

        verify(configRepository, never()).save(any());
    }
}
