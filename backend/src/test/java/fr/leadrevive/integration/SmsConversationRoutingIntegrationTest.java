package fr.leadrevive.integration;

import fr.leadrevive.outreach.entity.CanalConversationEnum;
import fr.leadrevive.outreach.entity.DirectionSmsEnum;
import fr.leadrevive.outreach.entity.ExpediteurMessageEnum;
import fr.leadrevive.outreach.entity.ModePilotageEnum;
import fr.leadrevive.outreach.entity.SmsConversation;
import fr.leadrevive.outreach.entity.SmsMessage;
import fr.leadrevive.outreach.entity.StatutConversationSmsEnum;
import fr.leadrevive.outreach.entity.StatutSmsEnum;
import fr.leadrevive.outreach.repository.SmsConversationRepository;
import fr.leadrevive.outreach.repository.SmsMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifie le routage des messages entrants via findActiveByNumeroProspectOrderByLatestOutbound.
 * Cas couverts (bug #16) :
 *  - 2 conversations actives meme numero, 2 leads distincts → routage sur celle qui a le dernier SORTANT le plus recent
 *  - Conversation sans aucun message SORTANT → exclue
 *  - Conversation fermee (TERMINEE/STOP/ERREUR) → exclue meme si dernier SORTANT recent
 *  - Numero inconnu → empty
 */
@Transactional
class SmsConversationRoutingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SmsConversationRepository smsConversationRepository;

    @Autowired
    private SmsMessageRepository smsMessageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String NUMERO_TEST = "+33611111111";

    private Long tenantId;
    private Long userId;
    private Long convCounter;

    @BeforeEach
    void seed() {
        convCounter = System.currentTimeMillis();
        tenantId = jdbcTemplate.queryForObject(
                "INSERT INTO tenant (nom) VALUES ('Tenant Routing Test') RETURNING id_tenant", Long.class);
        userId = jdbcTemplate.queryForObject("""
                INSERT INTO utilisateur (tenant_id, email, nom, prenom, role)
                VALUES (?, ?, 'Vendeur', 'Test', 'VENDEUR')
                RETURNING id_utilisateur
                """, Long.class, tenantId, "vendeur-routing-" + convCounter + "@test.fr");
    }

    @Test
    void deuxConvsActivesMemeNumero_routeVersCelleAvecDernierSortantLePlusRecent() {
        // GIVEN — convA : SORTANT a -2h ; convB : SORTANT a -1h (B doit gagner)
        final SmsConversation convA = creerConversation(StatutConversationSmsEnum.EN_COURS);
        final SmsConversation convB = creerConversation(StatutConversationSmsEnum.EN_COURS);
        creerMessage(convA, DirectionSmsEnum.SORTANT, LocalDateTime.now().minusHours(2));
        creerMessage(convB, DirectionSmsEnum.SORTANT, LocalDateTime.now().minusHours(1));

        // WHEN
        final Optional<SmsConversation> result = smsConversationRepository
                .findActiveByNumeroProspectOrderByLatestOutbound(NUMERO_TEST);

        // THEN
        assertThat(result).isPresent();
        assertThat(result.get().getConversationId()).isEqualTo(convB.getConversationId());
    }

    @Test
    void deuxConvsActives_dernierEntrantSurA_dernierSortantSurB_routeVersB() {
        // GIVEN — Bug #16 :
        //  - convA : SORTANT a -3h, ENTRANT a -10min (dernierMessageLe ~ entrant)
        //  - convB : SORTANT a -1h
        // L'ancien tri par dernierMessageLe aurait rendu A. Le nouveau (dernier SORTANT) doit rendre B.
        final SmsConversation convA = creerConversation(StatutConversationSmsEnum.EN_COURS);
        final SmsConversation convB = creerConversation(StatutConversationSmsEnum.EN_COURS);
        creerMessage(convA, DirectionSmsEnum.SORTANT, LocalDateTime.now().minusHours(3));
        creerMessage(convA, DirectionSmsEnum.ENTRANT, LocalDateTime.now().minusMinutes(10));
        creerMessage(convB, DirectionSmsEnum.SORTANT, LocalDateTime.now().minusHours(1));

        // WHEN
        final Optional<SmsConversation> result = smsConversationRepository
                .findActiveByNumeroProspectOrderByLatestOutbound(NUMERO_TEST);

        // THEN
        assertThat(result).isPresent();
        assertThat(result.get().getConversationId()).isEqualTo(convB.getConversationId());
    }

    @Test
    void convSansSortant_estExclueDuRoutage() {
        // GIVEN — convA : aucun SORTANT ; convB : SORTANT
        final SmsConversation convA = creerConversation(StatutConversationSmsEnum.EN_COURS);
        final SmsConversation convB = creerConversation(StatutConversationSmsEnum.EN_COURS);
        creerMessage(convA, DirectionSmsEnum.ENTRANT, LocalDateTime.now().minusMinutes(5));
        creerMessage(convB, DirectionSmsEnum.SORTANT, LocalDateTime.now().minusHours(2));

        // WHEN
        final Optional<SmsConversation> result = smsConversationRepository
                .findActiveByNumeroProspectOrderByLatestOutbound(NUMERO_TEST);

        // THEN — meme si A a un message plus recent (entrant), B gagne car A n'a pas de SORTANT
        assertThat(result).isPresent();
        assertThat(result.get().getConversationId()).isEqualTo(convB.getConversationId());
    }

    @Test
    void convTerminee_estExclueMemeAvecSortantRecent() {
        // GIVEN — convA : TERMINEE avec SORTANT recent ; convB : EN_COURS avec SORTANT plus ancien
        final SmsConversation convA = creerConversation(StatutConversationSmsEnum.TERMINEE);
        final SmsConversation convB = creerConversation(StatutConversationSmsEnum.EN_COURS);
        creerMessage(convA, DirectionSmsEnum.SORTANT, LocalDateTime.now().minusMinutes(5));
        creerMessage(convB, DirectionSmsEnum.SORTANT, LocalDateTime.now().minusHours(2));

        // WHEN
        final Optional<SmsConversation> result = smsConversationRepository
                .findActiveByNumeroProspectOrderByLatestOutbound(NUMERO_TEST);

        // THEN
        assertThat(result).isPresent();
        assertThat(result.get().getConversationId()).isEqualTo(convB.getConversationId());
    }

    @Test
    void convStopDemande_estExclueDuRoutage() {
        // GIVEN
        final SmsConversation convStop = creerConversation(StatutConversationSmsEnum.STOP_DEMANDE);
        creerMessage(convStop, DirectionSmsEnum.SORTANT, LocalDateTime.now().minusMinutes(1));

        // WHEN
        final Optional<SmsConversation> result = smsConversationRepository
                .findActiveByNumeroProspectOrderByLatestOutbound(NUMERO_TEST);

        // THEN
        assertThat(result).isEmpty();
    }

    @Test
    void numeroInconnu_retourneEmpty() {
        // WHEN
        final Optional<SmsConversation> result = smsConversationRepository
                .findActiveByNumeroProspectOrderByLatestOutbound("+33999999999");

        // THEN
        assertThat(result).isEmpty();
    }

    @Test
    void convRdvObtenu_resteRoutable() {
        // GIVEN — RDV_OBTENU est un statut "actif" pour le routage
        final SmsConversation conv = creerConversation(StatutConversationSmsEnum.RDV_OBTENU);
        creerMessage(conv, DirectionSmsEnum.SORTANT, LocalDateTime.now().minusMinutes(5));

        // WHEN
        final Optional<SmsConversation> result = smsConversationRepository
                .findActiveByNumeroProspectOrderByLatestOutbound(NUMERO_TEST);

        // THEN
        assertThat(result).isPresent();
        assertThat(result.get().getConversationId()).isEqualTo(conv.getConversationId());
    }

    private SmsConversation creerConversation(final StatutConversationSmsEnum statut) {
        final Long leadId = jdbcTemplate.queryForObject("""
                INSERT INTO lead (tenant_id, nom, statut)
                VALUES (?, ?, 'NOUVEAU')
                RETURNING id_lead
                """, Long.class, tenantId, "Lead-" + (++convCounter));

        final SmsConversation conv = new SmsConversation();
        conv.setTenantId(tenantId);
        conv.setLeadId(leadId);
        conv.setUserId(userId);
        conv.setConversationId("conv-routing-" + convCounter);
        conv.setStatut(statut);
        conv.setNumeroProspect(NUMERO_TEST);
        conv.setNombreMessages(0);
        conv.setModePilotage(ModePilotageEnum.IA_AUTO);
        conv.setCanal(CanalConversationEnum.WHATSAPP);
        return smsConversationRepository.saveAndFlush(conv);
    }

    private void creerMessage(final SmsConversation conv, final DirectionSmsEnum direction, final LocalDateTime creeLe) {
        final SmsMessage msg = new SmsMessage();
        msg.setTenantId(tenantId);
        msg.setConversationId(conv.getConversationId());
        msg.setDirection(direction);
        msg.setContenu("test");
        msg.setStatut(StatutSmsEnum.RECU);
        msg.setOrdre(0);
        msg.setExpediteur(direction == DirectionSmsEnum.SORTANT ? ExpediteurMessageEnum.IA : null);
        final SmsMessage persisted = smsMessageRepository.saveAndFlush(msg);
        // creeLe est updatable=false (audit). On force la valeur en SQL natif pour piloter l'ordre du tri.
        jdbcTemplate.update("UPDATE sms_message SET cree_le = ? WHERE id_sms_message = ?", creeLe, persisted.getId());
    }
}
