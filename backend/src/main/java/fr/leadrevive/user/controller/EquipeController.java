package fr.leadrevive.user.controller;

import fr.leadrevive.common.security.AuthService;
import fr.leadrevive.common.security.ClerkAdminClient;
import fr.leadrevive.common.util.PiiMasker;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.outreach.entity.SmsConversation;
import fr.leadrevive.outreach.repository.SmsConversationRepository;
import fr.leadrevive.tenant.entity.Tenant;
import fr.leadrevive.tenant.repository.TenantRepository;
import fr.leadrevive.user.dto.RepImpactDesactivationDto;
import fr.leadrevive.user.dto.RepVendeurDto;
import fr.leadrevive.user.dto.ReqInviterVendeurDto;
import fr.leadrevive.user.entity.RoleEnum;
import fr.leadrevive.user.entity.StatutUtilisateurEnum;
import fr.leadrevive.user.entity.User;
import fr.leadrevive.user.repository.UserRepository;
import fr.leadrevive.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/equipe")
@RequiredArgsConstructor
@Slf4j
public class EquipeController {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final ClerkAdminClient clerkAdminClient;
    private final AuthService authService;
    private final LeadRepository leadRepository;
    private final SmsConversationRepository smsConversationRepository;
    private final UserService userService;

    @org.springframework.beans.factory.annotation.Value("${leadrevive.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public List<RepVendeurDto> listerVendeurs(
            @RequestParam(required = false, defaultValue = "false") final boolean actifsUniquement) {
        final Long tenantId = authService.getCurrentTenantId();
        log.info("Liste des vendeurs - tenantId: {}, actifsUniquement: {}", tenantId, actifsUniquement);

        final List<User> utilisateurs = userRepository.findByTenantId(tenantId);
        return utilisateurs.stream()
                .filter(u -> u.getRole() != RoleEnum.SUPER_ADMIN)
                // Les comptes desactives (INACTIF) ne sont jamais affiches cote manager :
                // aucune action possible dessus, ils polluent la vue operationnelle "Mon equipe".
                // L'historique des anciens comptes reste du ressort de l'admin.
                .filter(u -> u.getStatut() != StatutUtilisateurEnum.INACTIF)
                // actifsUniquement=true (listes d'assignation de leads) restreint en plus aux
                // seuls ACTIF : on n'assigne pas un lead a une invitation non encore acceptee.
                .filter(u -> !actifsUniquement
                        || (Boolean.TRUE.equals(u.getActif()) && u.getStatut() == StatutUtilisateurEnum.ACTIF))
                .map(this::toRepVendeurDto)
                .toList();
    }

    @PostMapping("/inviter")
    @PreAuthorize("hasRole('MANAGER')")
    @Transactional
    public ResponseEntity<RepVendeurDto> inviter(@RequestBody @Valid final ReqInviterVendeurDto request) {
        final Long tenantId = authService.getCurrentTenantId();
        log.info("Invitation vendeur - tenantId: {}, email: {}", tenantId, PiiMasker.email(request.getEmail()));

        final Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant introuvable - id: " + tenantId));
        if (tenant.getClerkOrganizationId() == null) {
            log.warn("Tenant sans clerkOrganizationId - tenantId: {}", tenantId);
            return ResponseEntity.status(409).build();
        }

        // Pre-check email cross-tenant : evite un 500 cryptique sur la contrainte
        // UNIQUE quand l'email est deja utilise sur un autre tenant.
        userService.findByEmailCrossTenant(request.getEmail()).ifPresent(existant -> {
            throw new fr.leadrevive.user.exception.UserAlreadyExistsException(request.getEmail());
        });

        // 1. Cree le mirror utilisateur avec statut INVITE (clerk_user_id sera rempli au webhook user.created)
        final User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(request.getEmail());
        user.setPrenom(request.getPrenom());
        user.setNom(request.getNom());
        user.setRole(RoleEnum.VENDEUR);
        user.setActif(true);
        user.setStatut(StatutUtilisateurEnum.INVITE);
        final User saved = userRepository.save(user);

        // 2. Envoie l'invitation Clerk (role "org:vendeur" = role custom dans Clerk)
        try {
            clerkAdminClient.inviteUserToOrganization(tenant.getClerkOrganizationId(), request.getEmail(), "org:vendeur", frontendUrl);
        } catch (final fr.leadrevive.common.security.OrganizationMembershipQuotaExceededException e) {
            // Quota de membres atteint : le rollback du mirror est assure par @Transactional.
            // On relaie l'exception metier telle quelle -> 409 + message clair (GlobalExceptionHandler).
            log.warn("Invitation vendeur refusee (quota membres) - rollback mirror - email: {}",
                    PiiMasker.email(request.getEmail()));
            throw e;
        } catch (final Exception e) {
            log.error("Echec invitation Clerk - rollback mirror utilisateur - email: {}, cause: {}",
                    PiiMasker.email(request.getEmail()), e.getMessage());
            throw new RuntimeException("Erreur Clerk lors de l'invitation: " + e.getMessage(), e);
        }

        return ResponseEntity.status(201).body(toRepVendeurDto(saved));
    }

    /**
     * Preview de l'impact d'une desactivation : compteurs + destinataire.
     * Le destinataire automatique cote manager est le manager qui appelle
     * (il reprend conversations + taches du vendeur a desactiver).
     */
    @GetMapping("/{id}/impact-desactivation")
    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(readOnly = true)
    public ResponseEntity<RepImpactDesactivationDto> impactDesactivation(@PathVariable final Long id) {
        final Long tenantId = authService.getCurrentTenantId();
        final Long currentUserId = authService.getCurrentUserId();

        final User cible = userRepository.findById(id)
                .orElseThrow(() -> new fr.leadrevive.user.exception.UserNotFoundException(id));
        if (!tenantId.equals(cible.getTenantId())) {
            return ResponseEntity.status(403).build();
        }
        if (id.equals(currentUserId)) {
            return ResponseEntity.badRequest().body(RepImpactDesactivationDto.builder()
                    .desactivationPossible(false)
                    .raisonBlocage("Vous ne pouvez pas vous desactiver vous-meme. Demandez a un autre manager.")
                    .build());
        }

        final User destinataire = userRepository.findById(currentUserId).orElse(null);
        return ResponseEntity.ok(userService.calculerImpactDesactivation(id, tenantId, destinataire));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @Transactional
    public ResponseEntity<String> desactiver(@PathVariable final Long id,
                                              @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") final boolean reassigner) {
        final Long tenantId = authService.getCurrentTenantId();
        final Long currentUserId = authService.getCurrentUserId();
        log.info("Desactivation membre equipe - tenantId: {}, userId: {}, par: {}, reassigner: {}",
                tenantId, id, currentUserId, reassigner);

        if (id.equals(currentUserId)) {
            return ResponseEntity.badRequest()
                    .body("Vous ne pouvez pas vous desactiver vous-meme. Demandez a un autre manager.");
        }

        final User user = userRepository.findById(id)
                .orElseThrow(() -> new fr.leadrevive.user.exception.UserNotFoundException(id));

        if (!tenantId.equals(user.getTenantId())) {
            return ResponseEntity.status(403).build();
        }

        // Garde-fou : au moins 1 manager ACTIF doit rester APRES la desactivation. On ne
        // bloque QUE si la cible est elle-meme ACTIF : retirer un manager INVITE (invitation
        // jamais acceptee) ne reduit pas le nombre d'actifs -> jamais orphelin, toujours
        // autorise. Sans la condition sur le statut de la cible, on bloquait a tort la
        // revocation d'une invitation en attente quand un seul manager etait reellement actif.
        // On compte STRICTEMENT les ACTIF (un INVITE ne peut pas reprendre conversations/taches).
        if (user.getRole() == RoleEnum.MANAGER && user.getStatut() == StatutUtilisateurEnum.ACTIF) {
            final List<User> managers = userRepository.findByTenantIdAndRole(tenantId, RoleEnum.MANAGER);
            final long managersActifs = managers.stream()
                    .filter(u -> u.getStatut() == StatutUtilisateurEnum.ACTIF).count();
            if (managersActifs <= 1) {
                final boolean invitationEnAttente = managers.stream()
                        .anyMatch(u -> u.getStatut() == StatutUtilisateurEnum.INVITE);
                return ResponseEntity.badRequest().body(invitationEnAttente
                        ? "Impossible de desactiver le dernier manager actif : l'autre manager n'a pas encore accepte son invitation. Attendez qu'il rejoigne l'etablissement avant de desactiver celui-ci."
                        : "Impossible de desactiver le dernier manager actif. Invitez d'abord un autre manager.");
            }
        }

        if (reassigner) {
            // Mode transfert automatique : le manager qui agit recupere les
            // conversations EN_COURS et les taches non terminees du vendeur,
            // les leads passent en non-assignes (visible par les managers).
            userService.transfererPortefeuille(id, currentUserId, tenantId);
        } else {
            // Mode legacy : on bloque si garde-fous non vides.
            final List<SmsConversation> conversationsActives = smsConversationRepository
                    .findByTenantIdAndUserIdOrderByDernierMessageLeDesc(tenantId, id)
                    .stream()
                    .filter(c -> "EN_COURS".equals(c.getStatut().name()))
                    .toList();
            if (!conversationsActives.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body("Ce vendeur a " + conversationsActives.size() + " conversation(s) active(s). Reassignez-les avant de le desactiver.");
            }
            // Exclure les archives : un lead archive n'est pas une charge active bloquant la desactivation.
            final long leadsAssignes = leadRepository.findByTenantIdAndArchiveFalse(tenantId).stream()
                    .filter(l -> id.equals(l.getVendeurId()))
                    .count();
            if (leadsAssignes > 0) {
                return ResponseEntity.badRequest()
                        .body("Ce vendeur a " + leadsAssignes + " lead(s) assigne(s). Reassignez-les avant de le desactiver.");
            }
        }

        // Desactiver dans Clerk (membre actif OU invitation pending)
        final Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant introuvable"));
        if (tenant.getClerkOrganizationId() != null) {
            try {
                if (user.getClerkUserId() != null) {
                    // User deja actif dans Clerk → retirer de l'org
                    clerkAdminClient.removeMemberFromOrganization(tenant.getClerkOrganizationId(), user.getClerkUserId());
                } else {
                    // User encore INVITE (pas accepte) → revoquer l'invitation pending
                    clerkAdminClient.revokeInvitationByEmail(tenant.getClerkOrganizationId(), user.getEmail());
                }
            } catch (final Exception e) {
                log.error("Echec desactivation Clerk - userId: {}, cause: {}", user.getId(), e.getMessage());
                throw new RuntimeException("Erreur Clerk : " + e.getMessage(), e);
            }
        }

        // Soft delete + anonymisation email (libere l'email pour reinvitation future)
        userService.desactiverUtilisateur(user);

        log.info("Membre equipe desactive - userId: {}, role: {}, reassignation: {}",
                id, user.getRole(), reassigner);
        return ResponseEntity.noContent().build();
    }

    private RepVendeurDto toRepVendeurDto(final User utilisateur) {
        // Les utilisateurs desactives ont un email anonymise (deleted-X-Y@deleted.local)
        // pour liberer la contrainte UNIQUE et permettre la reinvitation du meme email.
        // Cote UI on masque cette valeur technique avec un libelle explicite.
        final boolean estDesactive = Boolean.FALSE.equals(utilisateur.getActif());
        final String emailAffiche = estDesactive ? "Compte desactive" : utilisateur.getEmail();

        return RepVendeurDto.builder()
                .id(utilisateur.getId())
                .email(emailAffiche)
                .nom(utilisateur.getNom())
                .prenom(utilisateur.getPrenom())
                .role(utilisateur.getRole().getValeur())
                .statut(utilisateur.getStatut().name())
                .actif(utilisateur.getActif())
                .derniereConnexion(utilisateur.getDerniereConnexionLe())
                .build();
    }
}
