package fr.leadrevive.common.admin;

import fr.leadrevive.ai.repository.AiLogRepository;
import fr.leadrevive.ai.repository.TenantAiConfigRepository;
import fr.leadrevive.common.admin.dto.ReqInviterVendeurDto;
import fr.leadrevive.user.dto.RepVendeurDto;
import fr.leadrevive.common.security.AuthService;
import fr.leadrevive.common.security.ClerkAdminClient;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.outreach.entity.SmsConversation;
import fr.leadrevive.outreach.entity.StatutConversationSmsEnum;
import fr.leadrevive.outreach.repository.SmsConversationRepository;
import fr.leadrevive.outreach.repository.SmsMessageRepository;
import fr.leadrevive.tenant.entity.Tenant;
import fr.leadrevive.tenant.exception.TenantNotFoundException;
import fr.leadrevive.tenant.repository.TenantRepository;
import fr.leadrevive.user.entity.RoleEnum;
import fr.leadrevive.user.entity.StatutUtilisateurEnum;
import fr.leadrevive.user.entity.User;
import fr.leadrevive.user.exception.UserAlreadyExistsException;
import fr.leadrevive.user.repository.UserRepository;
import fr.leadrevive.user.service.UserService;
import jakarta.persistence.EntityManager;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests des methodes admin liees a la gestion des utilisateurs d'une concession.
 * Couvre les chemins critiques de listerUtilisateurs, inviterVendeur,
 * supprimerVendeur et supprimerManager (garde-fous + retours user-friendly).
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private TenantRepository tenantRepository;
    @Mock private UserRepository userRepository;
    @Mock private AiLogRepository aiLogRepository;
    @Mock private TenantAiConfigRepository tenantAiConfigRepository;
    @Mock private LeadRepository leadRepository;
    @Mock private SmsConversationRepository smsConversationRepository;
    @Mock private SmsMessageRepository smsMessageRepository;
    @Mock private ClerkAdminClient clerkAdminClient;
    @Mock private AuthService authService;
    @Mock private EntityManager entityManager;
    @Mock private UserService userService;
    @Mock private fr.leadrevive.outreach.service.WhatsAppConfigService whatsAppConfigService;
    @Mock private fr.leadrevive.outreach.repository.TenantWhatsappConfigRepository tenantWhatsappConfigRepository;
    @Mock private fr.leadrevive.outreach.repository.TenantWhatsappNumberHistoryRepository tenantWhatsappNumberHistoryRepository;
    @Mock private fr.leadrevive.outreach.repository.WhatsappAdminActionRepository whatsappAdminActionRepository;
    @Mock private fr.leadrevive.outreach.repository.RappelRdvRepository rappelRdvRepository;
    @Mock private fr.leadrevive.outreach.service.WhatsAppSenderStatusJob whatsAppSenderStatusJob;
    @Mock private fr.leadrevive.emailimport.repository.TenantEmailConfigRepository tenantEmailConfigRepository;
    @Mock private fr.leadrevive.emailimport.service.EmailAddressGeneratorService emailAddressGeneratorService;
    @Mock private Session session;
    @Mock private Filter filter;

    private AdminService adminService;

    private static final Long TENANT_ID = 1L;
    private static final Long VENDEUR_ID = 10L;
    private static final Long MANAGER_ID = 20L;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(
                tenantRepository, userRepository, aiLogRepository, tenantAiConfigRepository,
                leadRepository, smsConversationRepository, smsMessageRepository,
                clerkAdminClient, authService, entityManager, userService,
                whatsAppConfigService, tenantWhatsappConfigRepository,
                tenantWhatsappNumberHistoryRepository, whatsappAdminActionRepository,
                rappelRdvRepository, whatsAppSenderStatusJob,
                tenantEmailConfigRepository, emailAddressGeneratorService);

        // Stubs neutres pour executerCrossTenant (utilise Session.disableFilter / enableFilter).
        // lenient() pour ne pas faire echouer les tests qui n'utilisent pas cross-tenant.
        lenient().when(entityManager.unwrap(Session.class)).thenReturn(session);
        lenient().when(session.enableFilter(anyString())).thenReturn(filter);
        lenient().when(filter.setParameter(anyString(), any())).thenReturn(filter);
    }

    // ────────────────────────────────────────────────────────────────────
    // listerUtilisateurs
    // ────────────────────────────────────────────────────────────────────

    @Test
    void listerUtilisateurs_filtreLesInactifs() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantDe(TENANT_ID, "org_xyz")));
        when(userRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(
                userDe(MANAGER_ID, "manager@x.fr", RoleEnum.MANAGER, StatutUtilisateurEnum.ACTIF),
                userDe(VENDEUR_ID, "vendeur@x.fr", RoleEnum.VENDEUR, StatutUtilisateurEnum.ACTIF),
                userDe(99L, "deleted-99-1@deleted.local", RoleEnum.VENDEUR, StatutUtilisateurEnum.INACTIF)
        ));

        final List<RepVendeurDto> result = adminService.listerUtilisateurs(TENANT_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(RepVendeurDto::getId).containsExactly(MANAGER_ID, VENDEUR_ID);
        assertThat(result).extracting(RepVendeurDto::getEmail).doesNotContain("deleted-99-1@deleted.local");
    }

    @Test
    void listerUtilisateurs_concessionInconnue_throws() {
        when(tenantRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.listerUtilisateurs(404L))
                .isInstanceOf(TenantNotFoundException.class);
    }

    // ────────────────────────────────────────────────────────────────────
    // inviterVendeur
    // ────────────────────────────────────────────────────────────────────

    @Test
    void inviterVendeur_ok_creeMirrorEtAppelleClerkAvecRoleVendeur() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantDe(TENANT_ID, "org_xyz")));
        when(userRepository.findByEmail("nouveau@x.fr")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        adminService.inviterVendeur(TENANT_ID, dtoInviterVendeur("Jean", "Dupont", "nouveau@x.fr"));

        final ArgumentCaptor<User> capt = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(capt.capture());
        assertThat(capt.getValue().getRole()).isEqualTo(RoleEnum.VENDEUR);
        assertThat(capt.getValue().getStatut()).isEqualTo(StatutUtilisateurEnum.INVITE);
        assertThat(capt.getValue().getTenantId()).isEqualTo(TENANT_ID);
        // 4e argument = frontendUrl injecte par @Value (null dans le test, pas critique)
        verify(clerkAdminClient).inviteUserToOrganization(eq("org_xyz"), eq("nouveau@x.fr"), eq("org:vendeur"), any());
    }

    @Test
    void inviterVendeur_emailDejaUtilise_throws() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantDe(TENANT_ID, "org_xyz")));
        when(userRepository.findByEmail("existant@x.fr")).thenReturn(Optional.of(userDe(VENDEUR_ID, "existant@x.fr", RoleEnum.VENDEUR, StatutUtilisateurEnum.ACTIF)));

        assertThatThrownBy(() -> adminService.inviterVendeur(TENANT_ID, dtoInviterVendeur("Jean", "Dupont", "existant@x.fr")))
                .isInstanceOf(UserAlreadyExistsException.class);

        verify(userRepository, never()).save(any(User.class));
        verify(clerkAdminClient, never()).inviteUserToOrganization(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void inviterVendeur_quotaMembresAtteint_relaieExceptionMetierSansWrapper() {
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantDe(TENANT_ID, "org_xyz")));
        when(userRepository.findByEmail("plein@x.fr")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new fr.leadrevive.common.security.OrganizationMembershipQuotaExceededException("Limite de membres atteinte"))
                .when(clerkAdminClient).inviteUserToOrganization(eq("org_xyz"), eq("plein@x.fr"), eq("org:vendeur"), any());

        // Le quota Clerk doit remonter comme exception metier (mappee en 409), et surtout PAS
        // etre re-wrappee dans une RuntimeException generique (qui finirait en 500 opaque).
        assertThatThrownBy(() -> adminService.inviterVendeur(TENANT_ID, dtoInviterVendeur("Jean", "Dupont", "plein@x.fr")))
                .isInstanceOf(fr.leadrevive.common.security.OrganizationMembershipQuotaExceededException.class);
    }

    // ────────────────────────────────────────────────────────────────────
    // supprimerVendeur
    // ────────────────────────────────────────────────────────────────────

    @Test
    void supprimerVendeur_ok_appelleClerkEtSoftDelete() {
        final User vendeur = userDe(VENDEUR_ID, "vendeur@x.fr", RoleEnum.VENDEUR, StatutUtilisateurEnum.ACTIF);
        vendeur.setClerkUserId("clerk_v_1");
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantDe(TENANT_ID, "org_xyz")));
        when(userRepository.findById(VENDEUR_ID)).thenReturn(Optional.of(vendeur));
        when(smsConversationRepository.findByTenantIdAndUserIdOrderByDernierMessageLeDesc(TENANT_ID, VENDEUR_ID))
                .thenReturn(List.of());
        when(leadRepository.findByTenantIdAndArchiveFalse(TENANT_ID)).thenReturn(List.of());

        final String erreur = adminService.supprimerVendeur(TENANT_ID, VENDEUR_ID, false);

        assertThat(erreur).isNull();
        verify(clerkAdminClient).removeMemberFromOrganization("org_xyz", "clerk_v_1");
        verify(userService).desactiverUtilisateur(vendeur);
    }

    @Test
    void supprimerVendeur_dejaInactif_retourneErreurUserFriendly() {
        final User vendeurInactif = userDe(VENDEUR_ID, "vendeur@x.fr", RoleEnum.VENDEUR, StatutUtilisateurEnum.INACTIF);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantDe(TENANT_ID, "org_xyz")));
        when(userRepository.findById(VENDEUR_ID)).thenReturn(Optional.of(vendeurInactif));

        final String erreur = adminService.supprimerVendeur(TENANT_ID, VENDEUR_ID, false);

        assertThat(erreur).isEqualTo("Ce vendeur est deja desactive.");
        verify(userService, never()).desactiverUtilisateur(any());
        verify(clerkAdminClient, never()).removeMemberFromOrganization(anyString(), anyString());
    }

    @Test
    void supprimerVendeur_pasUnVendeur_retourneErreurUserFriendly() {
        final User manager = userDe(MANAGER_ID, "manager@x.fr", RoleEnum.MANAGER, StatutUtilisateurEnum.ACTIF);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantDe(TENANT_ID, "org_xyz")));
        when(userRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager));

        final String erreur = adminService.supprimerVendeur(TENANT_ID, MANAGER_ID, false);

        assertThat(erreur).contains("n'est pas un vendeur");
        verify(userService, never()).desactiverUtilisateur(any());
    }

    @Test
    void supprimerVendeur_conversationsActives_bloque() {
        final User vendeur = userDe(VENDEUR_ID, "vendeur@x.fr", RoleEnum.VENDEUR, StatutUtilisateurEnum.ACTIF);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantDe(TENANT_ID, "org_xyz")));
        when(userRepository.findById(VENDEUR_ID)).thenReturn(Optional.of(vendeur));
        final SmsConversation conv = new SmsConversation();
        conv.setStatut(StatutConversationSmsEnum.EN_COURS);
        when(smsConversationRepository.findByTenantIdAndUserIdOrderByDernierMessageLeDesc(TENANT_ID, VENDEUR_ID))
                .thenReturn(List.of(conv));

        final String erreur = adminService.supprimerVendeur(TENANT_ID, VENDEUR_ID, false);

        assertThat(erreur).contains("conversation").contains("Reassignez");
        verify(userService, never()).desactiverUtilisateur(any());
    }

    @Test
    void supprimerVendeur_leadsAssignes_bloque() {
        final User vendeur = userDe(VENDEUR_ID, "vendeur@x.fr", RoleEnum.VENDEUR, StatutUtilisateurEnum.ACTIF);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantDe(TENANT_ID, "org_xyz")));
        when(userRepository.findById(VENDEUR_ID)).thenReturn(Optional.of(vendeur));
        when(smsConversationRepository.findByTenantIdAndUserIdOrderByDernierMessageLeDesc(TENANT_ID, VENDEUR_ID))
                .thenReturn(List.of());
        final Lead lead = new Lead();
        lead.setVendeurId(VENDEUR_ID);
        when(leadRepository.findByTenantIdAndArchiveFalse(TENANT_ID)).thenReturn(List.of(lead));

        final String erreur = adminService.supprimerVendeur(TENANT_ID, VENDEUR_ID, false);

        assertThat(erreur).contains("lead").contains("Reassignez");
        verify(userService, never()).desactiverUtilisateur(any());
    }

    // ────────────────────────────────────────────────────────────────────
    // supprimerVendeur avec reassigner=true (transfert automatique)
    // ────────────────────────────────────────────────────────────────────

    @Test
    void supprimerVendeur_reassigner_appelleTransfertEtDesactive() {
        final User vendeur = userDe(VENDEUR_ID, "vendeur@x.fr", RoleEnum.VENDEUR, StatutUtilisateurEnum.ACTIF);
        vendeur.setClerkUserId("clerk_v_1");
        final User managerActif = userDe(MANAGER_ID, "manager@x.fr", RoleEnum.MANAGER, StatutUtilisateurEnum.ACTIF);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantDe(TENANT_ID, "org_xyz")));
        when(userRepository.findById(VENDEUR_ID)).thenReturn(Optional.of(vendeur));
        when(userRepository.findByTenantIdAndRole(TENANT_ID, RoleEnum.MANAGER)).thenReturn(List.of(managerActif));

        final String erreur = adminService.supprimerVendeur(TENANT_ID, VENDEUR_ID, true);

        assertThat(erreur).isNull();
        // Le portefeuille est transfere AVANT le desactive
        verify(userService).transfererPortefeuille(VENDEUR_ID, MANAGER_ID, TENANT_ID);
        verify(clerkAdminClient).removeMemberFromOrganization("org_xyz", "clerk_v_1");
        verify(userService).desactiverUtilisateur(vendeur);
    }

    @Test
    void supprimerVendeur_reassigner_aucunManagerActif_retourneErreur() {
        final User vendeur = userDe(VENDEUR_ID, "vendeur@x.fr", RoleEnum.VENDEUR, StatutUtilisateurEnum.ACTIF);
        final User managerInactif = userDe(MANAGER_ID, "deleted@x.fr", RoleEnum.MANAGER, StatutUtilisateurEnum.INACTIF);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantDe(TENANT_ID, "org_xyz")));
        when(userRepository.findById(VENDEUR_ID)).thenReturn(Optional.of(vendeur));
        when(userRepository.findByTenantIdAndRole(TENANT_ID, RoleEnum.MANAGER)).thenReturn(List.of(managerInactif));

        final String erreur = adminService.supprimerVendeur(TENANT_ID, VENDEUR_ID, true);

        assertThat(erreur).contains("manager actif");
        verify(userService, never()).transfererPortefeuille(anyLong(), anyLong(), anyLong());
        verify(userService, never()).desactiverUtilisateur(any());
    }

    @Test
    void supprimerVendeur_reassigner_dejaInactif_retourneErreurAvantTransfert() {
        final User vendeurInactif = userDe(VENDEUR_ID, "vendeur@x.fr", RoleEnum.VENDEUR, StatutUtilisateurEnum.INACTIF);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantDe(TENANT_ID, "org_xyz")));
        when(userRepository.findById(VENDEUR_ID)).thenReturn(Optional.of(vendeurInactif));

        final String erreur = adminService.supprimerVendeur(TENANT_ID, VENDEUR_ID, true);

        assertThat(erreur).isEqualTo("Ce vendeur est deja desactive.");
        verify(userService, never()).transfererPortefeuille(anyLong(), anyLong(), anyLong());
        verify(userService, never()).desactiverUtilisateur(any());
    }

    // ────────────────────────────────────────────────────────────────────
    // calculerImpactVendeur
    // ────────────────────────────────────────────────────────────────────

    @Test
    void calculerImpactVendeur_avecManager_renvoieDestinataire() {
        final User vendeur = userDe(VENDEUR_ID, "vendeur@x.fr", RoleEnum.VENDEUR, StatutUtilisateurEnum.ACTIF);
        final User managerActif = userDe(MANAGER_ID, "manager@x.fr", RoleEnum.MANAGER, StatutUtilisateurEnum.ACTIF);
        final fr.leadrevive.user.dto.RepImpactDesactivationDto attendu =
                fr.leadrevive.user.dto.RepImpactDesactivationDto.builder()
                        .desactivationPossible(true).destinataireId(MANAGER_ID).build();
        when(userRepository.findById(VENDEUR_ID)).thenReturn(Optional.of(vendeur));
        when(userRepository.findByTenantIdAndRole(TENANT_ID, RoleEnum.MANAGER)).thenReturn(List.of(managerActif));
        when(userService.calculerImpactDesactivation(VENDEUR_ID, TENANT_ID, managerActif)).thenReturn(attendu);

        final fr.leadrevive.user.dto.RepImpactDesactivationDto result =
                adminService.calculerImpactVendeur(TENANT_ID, VENDEUR_ID);

        assertThat(result).isEqualTo(attendu);
        verify(userService).calculerImpactDesactivation(VENDEUR_ID, TENANT_ID, managerActif);
    }

    @Test
    void calculerImpactVendeur_sansManagerActif_appelleAvecDestinataireNull() {
        final User vendeur = userDe(VENDEUR_ID, "vendeur@x.fr", RoleEnum.VENDEUR, StatutUtilisateurEnum.ACTIF);
        when(userRepository.findById(VENDEUR_ID)).thenReturn(Optional.of(vendeur));
        when(userRepository.findByTenantIdAndRole(TENANT_ID, RoleEnum.MANAGER)).thenReturn(List.of());

        adminService.calculerImpactVendeur(TENANT_ID, VENDEUR_ID);

        // verifie que destinataire null est bien propage a userService pour bloquer
        verify(userService).calculerImpactDesactivation(VENDEUR_ID, TENANT_ID, null);
    }

    // ────────────────────────────────────────────────────────────────────
    // supprimerManager (garde-fou dernier manager)
    // ────────────────────────────────────────────────────────────────────

    @Test
    void supprimerManager_dernierManagerActif_bloque() {
        final User manager = userDe(MANAGER_ID, "manager@x.fr", RoleEnum.MANAGER, StatutUtilisateurEnum.ACTIF);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantDe(TENANT_ID, "org_xyz")));
        when(userRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager));
        when(userRepository.findByTenantIdAndRole(TENANT_ID, RoleEnum.MANAGER)).thenReturn(List.of(manager));

        final String erreur = adminService.supprimerManager(TENANT_ID, MANAGER_ID);

        assertThat(erreur).contains("dernier manager");
        verify(userService, never()).desactiverUtilisateur(any());
        verify(clerkAdminClient, never()).removeMemberFromOrganization(anyString(), anyString());
    }

    @Test
    void supprimerManager_avecAutreManager_ok() {
        final User manager = userDe(MANAGER_ID, "manager@x.fr", RoleEnum.MANAGER, StatutUtilisateurEnum.ACTIF);
        manager.setClerkUserId("clerk_m_1");
        final User autreManager = userDe(21L, "autre@x.fr", RoleEnum.MANAGER, StatutUtilisateurEnum.ACTIF);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantDe(TENANT_ID, "org_xyz")));
        when(userRepository.findById(MANAGER_ID)).thenReturn(Optional.of(manager));
        when(userRepository.findByTenantIdAndRole(TENANT_ID, RoleEnum.MANAGER)).thenReturn(List.of(manager, autreManager));

        final String erreur = adminService.supprimerManager(TENANT_ID, MANAGER_ID);

        assertThat(erreur).isNull();
        verify(clerkAdminClient).removeMemberFromOrganization("org_xyz", "clerk_m_1");
        verify(userService).desactiverUtilisateur(manager);
    }

    @Test
    void supprimerManager_ciblePendingInvite_autoriseeMemeSiUnSeulManagerActif() {
        // Cas reel signale : 1 manager ACTIF + 1 manager INVITE (jamais accepte l'invitation).
        // Retirer l'INVITE ne reduit PAS le nombre d'actifs -> doit etre autorise
        // (bug : etait bloque par "dernier manager actif" alors que l'actif reste).
        final User managerInvite = userDe(MANAGER_ID, "invite@x.fr", RoleEnum.MANAGER, StatutUtilisateurEnum.INVITE);
        final User managerActif = userDe(21L, "actif@x.fr", RoleEnum.MANAGER, StatutUtilisateurEnum.ACTIF);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantDe(TENANT_ID, "org_xyz")));
        when(userRepository.findById(MANAGER_ID)).thenReturn(Optional.of(managerInvite));
        when(userRepository.findByTenantIdAndRole(TENANT_ID, RoleEnum.MANAGER)).thenReturn(List.of(managerInvite, managerActif));

        final String erreur = adminService.supprimerManager(TENANT_ID, MANAGER_ID);

        assertThat(erreur).isNull();
        // INVITE sans clerkUserId -> revocation de l'invitation pending (pas removeMember)
        verify(clerkAdminClient).revokeInvitationByEmail("org_xyz", "invite@x.fr");
        verify(userService).desactiverUtilisateur(managerInvite);
    }

    @Test
    void supprimerManager_dernierActif_seulAutreEstInvitePending_messagePrecise() {
        // Le seul autre manager est INVITE (pending) : on bloque quand meme la desactivation
        // du dernier manager ACTIF, mais avec un message qui precise que l'autre n'a pas
        // encore accepte son invitation (au lieu de "invitez un autre manager").
        final User managerActif = userDe(MANAGER_ID, "actif@x.fr", RoleEnum.MANAGER, StatutUtilisateurEnum.ACTIF);
        final User managerInvite = userDe(21L, "invite@x.fr", RoleEnum.MANAGER, StatutUtilisateurEnum.INVITE);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantDe(TENANT_ID, "org_xyz")));
        when(userRepository.findById(MANAGER_ID)).thenReturn(Optional.of(managerActif));
        when(userRepository.findByTenantIdAndRole(TENANT_ID, RoleEnum.MANAGER)).thenReturn(List.of(managerActif, managerInvite));

        final String erreur = adminService.supprimerManager(TENANT_ID, MANAGER_ID);

        assertThat(erreur).contains("n'a pas encore accepte");
        verify(userService, never()).desactiverUtilisateur(any());
        verify(clerkAdminClient, never()).removeMemberFromOrganization(anyString(), anyString());
    }

    // ────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────

    private Tenant tenantDe(final Long id, final String clerkOrgId) {
        final Tenant t = new Tenant();
        t.setId(id);
        t.setClerkOrganizationId(clerkOrgId);
        t.setActif(true);
        return t;
    }

    private User userDe(final Long id, final String email, final RoleEnum role, final StatutUtilisateurEnum statut) {
        final User u = new User();
        u.setId(id);
        u.setTenantId(TENANT_ID);
        u.setEmail(email);
        u.setPrenom("Pre");
        u.setNom("Nom");
        u.setRole(role);
        u.setStatut(statut);
        u.setActif(statut != StatutUtilisateurEnum.INACTIF);
        return u;
    }

    private ReqInviterVendeurDto dtoInviterVendeur(final String prenom, final String nom, final String email) {
        final ReqInviterVendeurDto dto = new ReqInviterVendeurDto();
        dto.setPrenom(prenom);
        dto.setNom(nom);
        dto.setEmail(email);
        return dto;
    }
}
