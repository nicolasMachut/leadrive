package fr.leadrevive.user.service;

import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.outreach.entity.SmsConversation;
import fr.leadrevive.outreach.entity.StatutConversationSmsEnum;
import fr.leadrevive.outreach.repository.SmsConversationRepository;
import fr.leadrevive.tache.entity.StatutTacheEnum;
import fr.leadrevive.tache.entity.Tache;
import fr.leadrevive.tache.entity.TypeTacheEnum;
import fr.leadrevive.tache.repository.TacheRepository;
import fr.leadrevive.user.dto.RepImpactDesactivationDto;
import fr.leadrevive.user.entity.RoleEnum;
import fr.leadrevive.user.entity.StatutUtilisateurEnum;
import fr.leadrevive.user.entity.User;
import fr.leadrevive.user.mapper.UserMapper;
import fr.leadrevive.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests des helpers de UserService cibles par le workflow de
 * desactivation/reassignation : transfererPortefeuille et calculerImpactDesactivation.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;
    @Mock private UserBuilder userBuilder;
    @Mock private EntityManager entityManager;
    @Mock private LeadRepository leadRepository;
    @Mock private SmsConversationRepository smsConversationRepository;
    @Mock private TacheRepository tacheRepository;

    private UserService userService;

    private static final Long TENANT_ID = 1L;
    private static final Long VENDEUR_ID = 10L;
    private static final Long MANAGER_ID = 20L;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, userMapper, userBuilder,
                entityManager, leadRepository, smsConversationRepository, tacheRepository);
    }

    // ────────────────────────────────────────────────────────────────────
    // transfererPortefeuille
    // ────────────────────────────────────────────────────────────────────

    @Test
    void transfererPortefeuille_leadsPassentEnNonAssignes() {
        final Lead leadVendeur1 = leadDe(100L, VENDEUR_ID);
        final Lead leadVendeur2 = leadDe(101L, VENDEUR_ID);
        final Lead leadAutre = leadDe(102L, 99L);
        when(leadRepository.findByTenantIdAndArchiveFalse(TENANT_ID)).thenReturn(List.of(leadVendeur1, leadVendeur2, leadAutre));
        when(smsConversationRepository.findByTenantIdAndUserIdOrderByDernierMessageLeDesc(TENANT_ID, VENDEUR_ID))
                .thenReturn(List.of());
        when(tacheRepository.findByTenantIdAndVendeurIdAndStatutIn(eq(TENANT_ID), eq(VENDEUR_ID), any()))
                .thenReturn(List.of());

        userService.transfererPortefeuille(VENDEUR_ID, MANAGER_ID, TENANT_ID);

        // Seuls les leads du vendeur sont nullifies, l'autre est intact
        assertThat(leadVendeur1.getVendeurId()).isNull();
        assertThat(leadVendeur2.getVendeurId()).isNull();
        assertThat(leadAutre.getVendeurId()).isEqualTo(99L);
        verify(leadRepository, never()).save(leadAutre);
    }

    @Test
    void transfererPortefeuille_convsEnCoursReassignees_autresIntactes() {
        final SmsConversation convEnCours = convDe(200L, VENDEUR_ID, StatutConversationSmsEnum.EN_COURS);
        final SmsConversation convTerminee = convDe(201L, VENDEUR_ID, StatutConversationSmsEnum.TERMINEE);
        when(leadRepository.findByTenantIdAndArchiveFalse(TENANT_ID)).thenReturn(List.of());
        when(smsConversationRepository.findByTenantIdAndUserIdOrderByDernierMessageLeDesc(TENANT_ID, VENDEUR_ID))
                .thenReturn(List.of(convEnCours, convTerminee));
        when(tacheRepository.findByTenantIdAndVendeurIdAndStatutIn(eq(TENANT_ID), eq(VENDEUR_ID), any()))
                .thenReturn(List.of());

        userService.transfererPortefeuille(VENDEUR_ID, MANAGER_ID, TENANT_ID);

        assertThat(convEnCours.getUserId()).isEqualTo(MANAGER_ID);
        // La terminee n'est pas touchee
        assertThat(convTerminee.getUserId()).isEqualTo(VENDEUR_ID);
        verify(smsConversationRepository, never()).save(convTerminee);
    }

    @Test
    void transfererPortefeuille_tachesNonTermineesReassignees() {
        final Tache tacheAFaire = tacheDe(300L, VENDEUR_ID, StatutTacheEnum.A_FAIRE);
        final Tache tacheEnCours = tacheDe(301L, VENDEUR_ID, StatutTacheEnum.EN_COURS);
        when(leadRepository.findByTenantIdAndArchiveFalse(TENANT_ID)).thenReturn(List.of());
        when(smsConversationRepository.findByTenantIdAndUserIdOrderByDernierMessageLeDesc(TENANT_ID, VENDEUR_ID))
                .thenReturn(List.of());
        // Le repo est appele avec le filtre A_FAIRE+EN_COURS donc renvoie seulement ces 2.
        final ArgumentCaptor<Collection<StatutTacheEnum>> statutsCaptor = ArgumentCaptor.forClass(Collection.class);
        when(tacheRepository.findByTenantIdAndVendeurIdAndStatutIn(eq(TENANT_ID), eq(VENDEUR_ID), statutsCaptor.capture()))
                .thenReturn(List.of(tacheAFaire, tacheEnCours));

        userService.transfererPortefeuille(VENDEUR_ID, MANAGER_ID, TENANT_ID);

        assertThat(tacheAFaire.getVendeurId()).isEqualTo(MANAGER_ID);
        assertThat(tacheEnCours.getVendeurId()).isEqualTo(MANAGER_ID);
        // Verifie que le filtre exclut TERMINEE et ANNULEE
        assertThat(statutsCaptor.getValue()).containsExactlyInAnyOrder(StatutTacheEnum.A_FAIRE, StatutTacheEnum.EN_COURS);
    }

    @Test
    void transfererPortefeuille_vendeurSansActifs_idempotent() {
        when(leadRepository.findByTenantIdAndArchiveFalse(TENANT_ID)).thenReturn(List.of());
        when(smsConversationRepository.findByTenantIdAndUserIdOrderByDernierMessageLeDesc(TENANT_ID, VENDEUR_ID))
                .thenReturn(List.of());
        when(tacheRepository.findByTenantIdAndVendeurIdAndStatutIn(eq(TENANT_ID), eq(VENDEUR_ID), any()))
                .thenReturn(List.of());

        userService.transfererPortefeuille(VENDEUR_ID, MANAGER_ID, TENANT_ID);

        verify(leadRepository, never()).save(any());
        verify(smsConversationRepository, never()).save(any());
        verify(tacheRepository, never()).save(any());
    }

    // ────────────────────────────────────────────────────────────────────
    // calculerImpactDesactivation
    // ────────────────────────────────────────────────────────────────────

    @Test
    void calculerImpactDesactivation_avecDestinataire_renvoieCompteurs() {
        final User destinataire = userDe(MANAGER_ID, "Marc", "Manager", RoleEnum.MANAGER);
        when(leadRepository.findByTenantIdAndArchiveFalse(TENANT_ID)).thenReturn(List.of(
                leadDe(100L, VENDEUR_ID),
                leadDe(101L, VENDEUR_ID),
                leadDe(102L, 99L) // autre vendeur, ne doit pas compter
        ));
        when(smsConversationRepository.findByTenantIdAndUserIdOrderByDernierMessageLeDesc(TENANT_ID, VENDEUR_ID))
                .thenReturn(List.of(
                        convDe(200L, VENDEUR_ID, StatutConversationSmsEnum.EN_COURS),
                        convDe(201L, VENDEUR_ID, StatutConversationSmsEnum.TERMINEE) // ne doit pas compter
                ));
        // 3 taches : 1 RDV futur, 1 RDV passe, 1 APPEL futur
        final Tache rdvFutur = tacheDe(300L, VENDEUR_ID, StatutTacheEnum.A_FAIRE);
        rdvFutur.setTypeTache(TypeTacheEnum.RDV);
        rdvFutur.setDateEcheance(LocalDateTime.now().plusDays(2));
        final Tache rdvPasse = tacheDe(301L, VENDEUR_ID, StatutTacheEnum.A_FAIRE);
        rdvPasse.setTypeTache(TypeTacheEnum.RDV);
        rdvPasse.setDateEcheance(LocalDateTime.now().minusDays(1));
        final Tache appelFutur = tacheDe(302L, VENDEUR_ID, StatutTacheEnum.A_FAIRE);
        appelFutur.setTypeTache(TypeTacheEnum.APPEL);
        appelFutur.setDateEcheance(LocalDateTime.now().plusDays(1));
        when(tacheRepository.findByTenantIdAndVendeurIdAndStatutIn(eq(TENANT_ID), eq(VENDEUR_ID), any()))
                .thenReturn(List.of(rdvFutur, rdvPasse, appelFutur));

        final RepImpactDesactivationDto result = userService.calculerImpactDesactivation(VENDEUR_ID, TENANT_ID, destinataire);

        assertThat(result.getNbLeadsAssignes()).isEqualTo(2);
        assertThat(result.getNbConversationsActives()).isEqualTo(1);
        assertThat(result.getNbTachesNonTerminees()).isEqualTo(3);
        // Seul le RDV futur compte
        assertThat(result.getNbRdvAVenir()).isEqualTo(1);
        assertThat(result.isDesactivationPossible()).isTrue();
        assertThat(result.getDestinataireId()).isEqualTo(MANAGER_ID);
        assertThat(result.getDestinataireNom()).isEqualTo("Marc Manager");
    }

    @Test
    void calculerImpactDesactivation_sansDestinataire_bloque() {
        when(leadRepository.findByTenantIdAndArchiveFalse(TENANT_ID)).thenReturn(List.of());
        when(smsConversationRepository.findByTenantIdAndUserIdOrderByDernierMessageLeDesc(TENANT_ID, VENDEUR_ID))
                .thenReturn(List.of());
        when(tacheRepository.findByTenantIdAndVendeurIdAndStatutIn(eq(TENANT_ID), eq(VENDEUR_ID), any()))
                .thenReturn(List.of());

        final RepImpactDesactivationDto result = userService.calculerImpactDesactivation(VENDEUR_ID, TENANT_ID, null);

        assertThat(result.isDesactivationPossible()).isFalse();
        assertThat(result.getRaisonBlocage()).contains("Aucun manager actif");
        assertThat(result.getDestinataireId()).isNull();
    }

    // ────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────

    private Lead leadDe(final Long id, final Long vendeurId) {
        final Lead l = new Lead();
        l.setId(id);
        l.setVendeurId(vendeurId);
        l.setTenantId(TENANT_ID);
        return l;
    }

    private SmsConversation convDe(final Long id, final Long userId, final StatutConversationSmsEnum statut) {
        final SmsConversation c = new SmsConversation();
        c.setId(id);
        c.setUserId(userId);
        c.setStatut(statut);
        c.setTenantId(TENANT_ID);
        return c;
    }

    private Tache tacheDe(final Long id, final Long vendeurId, final StatutTacheEnum statut) {
        final Tache t = new Tache();
        t.setId(id);
        t.setVendeurId(vendeurId);
        t.setStatut(statut);
        t.setTenantId(TENANT_ID);
        return t;
    }

    private User userDe(final Long id, final String prenom, final String nom, final RoleEnum role) {
        final User u = new User();
        u.setId(id);
        u.setPrenom(prenom);
        u.setNom(nom);
        u.setRole(role);
        u.setStatut(StatutUtilisateurEnum.ACTIF);
        u.setTenantId(TENANT_ID);
        return u;
    }
}
