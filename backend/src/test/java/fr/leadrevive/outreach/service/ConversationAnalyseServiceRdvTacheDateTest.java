package fr.leadrevive.outreach.service;

import fr.leadrevive.outreach.dto.RepConversationAnalyseDto;
import fr.leadrevive.outreach.entity.ConversationAnalyse;
import fr.leadrevive.outreach.entity.IntentionEnum;
import fr.leadrevive.outreach.entity.SmsConversation;
import fr.leadrevive.outreach.repository.ConversationAnalyseRepository;
import fr.leadrevive.outreach.repository.SmsConversationRepository;
import fr.leadrevive.tache.service.TacheIAService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * Tests de non-regression : {@link ConversationAnalyseService#getDerniereAnalyse} doit exposer
 * {@code rdvTacheDateHeure} = date de la tache RDV EN COURS du lead (source de verite mutable de
 * l'agenda), et non la seule date extraite par l'IA (figee). Sans ca, apres un report de RDV le
 * panneau d'analyse affichait l'ancienne date en "Passé".
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConversationAnalyseServiceRdvTacheDateTest {

    @Mock
    private ConversationAnalyseRepository conversationAnalyseRepository;
    @Mock
    private SmsConversationRepository smsConversationRepository;
    @Mock
    private TacheIAService tacheIAService;

    @InjectMocks
    private ConversationAnalyseService service;

    private ConversationAnalyse analyseAvecRdv() {
        final ConversationAnalyse analyse = new ConversationAnalyse();
        analyse.setId(10L);
        analyse.setConversationId("conv-1");
        analyse.setIntention(IntentionEnum.RDV);
        analyse.setRdvPropose(true);
        analyse.setRdvConfirme(true);
        analyse.setRdvDateHeure(LocalDateTime.of(2026, 7, 10, 14, 0));
        return analyse;
    }

    @Test
    void getDerniereAnalyse_avecTacheRdvEnCours_exposeLaDateDeLaTache() {
        // GIVEN : extraction figee au 10 juillet, mais tache RDV reportee au 15 juillet
        final LocalDateTime dateTache = LocalDateTime.of(2026, 7, 15, 9, 30);
        when(conversationAnalyseRepository.findDerniereByTenantIdAndConversationId(1L, "conv-1"))
                .thenReturn(Optional.of(analyseAvecRdv()));
        final SmsConversation conversation = new SmsConversation();
        conversation.setLeadId(42L);
        when(smsConversationRepository.findByConversationIdAndTenantId("conv-1", 1L))
                .thenReturn(Optional.of(conversation));
        when(tacheIAService.dateRdvEnCours(42L)).thenReturn(dateTache);

        // WHEN
        final RepConversationAnalyseDto dto = service.getDerniereAnalyse(1L, "conv-1");

        // THEN : rdvTacheDateHeure = date de la tache (mutable), rdvDateHeure reste l'extraction figee
        assertEquals(dateTache, dto.getRdvTacheDateHeure());
        assertEquals(LocalDateTime.of(2026, 7, 10, 14, 0), dto.getRdvDateHeure());
    }

    @Test
    void getDerniereAnalyse_sansTacheRdvEnCours_rdvTacheDateHeureNull() {
        // GIVEN : aucune tache RDV en cours pour le lead
        when(conversationAnalyseRepository.findDerniereByTenantIdAndConversationId(1L, "conv-1"))
                .thenReturn(Optional.of(analyseAvecRdv()));
        final SmsConversation conversation = new SmsConversation();
        conversation.setLeadId(42L);
        when(smsConversationRepository.findByConversationIdAndTenantId("conv-1", 1L))
                .thenReturn(Optional.of(conversation));
        when(tacheIAService.dateRdvEnCours(42L)).thenReturn(null);

        // WHEN
        final RepConversationAnalyseDto dto = service.getDerniereAnalyse(1L, "conv-1");

        // THEN : fallback null (le front retombera sur rdvDateHeure)
        assertNull(dto.getRdvTacheDateHeure());
    }

    @Test
    void getDerniereAnalyse_aucuneAnalyse_retourneNull() {
        when(conversationAnalyseRepository.findDerniereByTenantIdAndConversationId(1L, "conv-1"))
                .thenReturn(Optional.empty());

        assertNull(service.getDerniereAnalyse(1L, "conv-1"));
    }
}
