package fr.leadrevive.user.service;

import fr.leadrevive.common.tenant.TenantContext;
import fr.leadrevive.common.util.PiiMasker;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.entity.LeadStatutEnum;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.outreach.entity.SmsConversation;
import fr.leadrevive.outreach.entity.StatutConversationSmsEnum;
import fr.leadrevive.outreach.repository.SmsConversationRepository;
import fr.leadrevive.tache.entity.StatutTacheEnum;
import fr.leadrevive.tache.entity.Tache;
import fr.leadrevive.tache.entity.TypeTacheEnum;
import fr.leadrevive.tache.repository.TacheRepository;
import fr.leadrevive.user.dto.RepImpactDesactivationDto;
import fr.leadrevive.user.dto.RepUserDto;
import fr.leadrevive.user.dto.ReqCreateUserDto;
import fr.leadrevive.user.dto.ReqUpdateUserDto;
import fr.leadrevive.user.entity.RoleEnum;
import fr.leadrevive.user.entity.StatutUtilisateurEnum;
import fr.leadrevive.user.entity.User;
import fr.leadrevive.user.exception.UserAlreadyExistsException;
import fr.leadrevive.user.exception.UserNotFoundException;
import fr.leadrevive.user.mapper.UserMapper;
import fr.leadrevive.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserBuilder userBuilder;
    private final EntityManager entityManager;
    private final LeadRepository leadRepository;
    private final SmsConversationRepository smsConversationRepository;
    private final TacheRepository tacheRepository;

    /** Statuts de taches consideres "non terminees" et donc a transferer. */
    private static final EnumSet<StatutTacheEnum> STATUTS_TACHES_NON_TERMINEES =
            EnumSet.of(StatutTacheEnum.A_FAIRE, StatutTacheEnum.EN_COURS);

    /**
     * Calcule l'impact d'une desactivation de vendeur : compteurs des leads,
     * conversations actives, taches non terminees et RDV a venir.
     *
     * <p>Designe egalement le destinataire des conversations et taches :
     * le manager parametre. Si {@code destinataire} est null, la desactivation
     * n'est pas possible (le retour le signale via {@code desactivationPossible}).
     *
     * <p>Cette methode ne mute aucune donnee, elle peut etre appelee depuis
     * un endpoint GET pour preparer la modal de confirmation.
     */
    @Transactional(readOnly = true)
    public RepImpactDesactivationDto calculerImpactDesactivation(final Long vendeurId,
                                                                   final Long tenantId,
                                                                   final User destinataire) {
        // Compte uniquement les leads NON TERMINE : ceux-la seront passes en non-assignes.
        // Les leads TERMINE gardent leur vendeurId pour preserver l'historique des stats
        // (pipeline par vendeur, ventes confirmees, etc.).
        // Exclure les archives : un lead archive n'est pas une charge active a reassigner.
        final long nbLeads = leadRepository.findByTenantIdAndArchiveFalse(tenantId).stream()
                .filter(l -> vendeurId.equals(l.getVendeurId()))
                .filter(l -> l.getStatut() != LeadStatutEnum.TERMINE)
                .count();
        final long nbConvs = smsConversationRepository
                .findByTenantIdAndUserIdOrderByDernierMessageLeDesc(tenantId, vendeurId).stream()
                .filter(c -> c.getStatut() == StatutConversationSmsEnum.EN_COURS)
                .count();
        final List<Tache> taches = tacheRepository
                .findByTenantIdAndVendeurIdAndStatutIn(tenantId, vendeurId, STATUTS_TACHES_NON_TERMINEES);
        final long nbTaches = taches.size();
        final LocalDateTime maintenant = LocalDateTime.now();
        final long nbRdv = taches.stream()
                .filter(t -> t.getTypeTache() == TypeTacheEnum.RDV
                        && t.getDateEcheance() != null
                        && t.getDateEcheance().isAfter(maintenant))
                .count();

        final RepImpactDesactivationDto.RepImpactDesactivationDtoBuilder builder = RepImpactDesactivationDto.builder()
                .nbLeadsAssignes(nbLeads)
                .nbConversationsActives(nbConvs)
                .nbTachesNonTerminees(nbTaches)
                .nbRdvAVenir(nbRdv);

        if (destinataire == null) {
            return builder.desactivationPossible(false)
                    .raisonBlocage("Aucun manager actif disponible pour reprendre les conversations et taches du vendeur. Invitez d'abord un manager.")
                    .build();
        }
        return builder.desactivationPossible(true)
                .destinataireId(destinataire.getId())
                .destinataireNom(destinataire.getPrenom() + " " + destinataire.getNom())
                .build();
    }

    /**
     * Transfere le portefeuille d'un vendeur vers un destinataire :
     * - Leads : passent en non-assignes ({@code vendeurId = null}) — visibles
     *   par le manager qui peut les redistribuer manuellement.
     * - Conversations EN_COURS : transferees au destinataire pour que les
     *   echanges en cours puissent etre poursuivis (mode HUMAIN comme IA_AUTO).
     * - Taches non terminees (A_FAIRE, EN_COURS) : transferees au destinataire,
     *   incluant les RDV futurs qui apparaitront dans son calendrier.
     *
     * <p>Idempotent : si le vendeur n'a rien, ne fait rien.
     * <p>Doit etre appele dans une transaction parent qui inclut aussi la
     * desactivation pour garantir l'atomicite (transfert + desactivation = 1 unite).
     */
    @Transactional
    public void transfererPortefeuille(final Long vendeurId, final Long destinataireId, final Long tenantId) {
        log.info("Transfert portefeuille - vendeurId: {}, destinataireId: {}, tenantId: {}",
                vendeurId, destinataireId, tenantId);

        // 1. Leads NON TERMINE -> non-assignes.
        // Les leads TERMINE gardent leur vendeurId : c'est de l'historique
        // (ventes conclues / abandons), retirer l'attribution ferait disparaitre
        // ces leads des stats pipeline par vendeur. Cf PipelineStatsService.
        int leadsTransferes = 0;
        // Exclure les archives : cohérent avec le compte d'impact ci-dessus (nbLeads).
        for (final Lead lead : leadRepository.findByTenantIdAndArchiveFalse(tenantId)) {
            if (vendeurId.equals(lead.getVendeurId())
                    && lead.getStatut() != LeadStatutEnum.TERMINE) {
                lead.setVendeurId(null);
                leadRepository.save(lead);
                leadsTransferes++;
            }
        }

        // 2. Conversations EN_COURS -> destinataire
        int convsTransferees = 0;
        for (final SmsConversation conv : smsConversationRepository
                .findByTenantIdAndUserIdOrderByDernierMessageLeDesc(tenantId, vendeurId)) {
            if (conv.getStatut() == StatutConversationSmsEnum.EN_COURS) {
                conv.setUserId(destinataireId);
                smsConversationRepository.save(conv);
                convsTransferees++;
            }
        }

        // 3. Taches non terminees -> destinataire
        int tachesTransferees = 0;
        for (final Tache tache : tacheRepository
                .findByTenantIdAndVendeurIdAndStatutIn(tenantId, vendeurId, STATUTS_TACHES_NON_TERMINEES)) {
            tache.setVendeurId(destinataireId);
            tacheRepository.save(tache);
            tachesTransferees++;
        }

        log.info("Transfert portefeuille termine - vendeurId: {}, leads: {}, convs: {}, taches: {}",
                vendeurId, leadsTransferes, convsTransferees, tachesTransferees);
    }

    /**
     * Recherche un utilisateur par email en ignorant le filtre tenant Hibernate.
     * Necessaire pour les pre-checks d'unicite avant invitation : sinon un email
     * deja utilise sur un autre tenant n'est pas detecte, et la creation tombe sur
     * la contrainte UNIQUE en BDD avec un 500 cryptique au lieu d'un 409 explicite.
     */
    @Transactional(readOnly = true)
    public Optional<User> findByEmailCrossTenant(final String email) {
        final Session session = entityManager.unwrap(Session.class);
        session.disableFilter("tenantFilter");
        try {
            return userRepository.findByEmail(email);
        } finally {
            final Long tenantId = TenantContext.getCurrentTenantId();
            if (tenantId != null) {
                session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
            }
        }
    }

    @Transactional(readOnly = true)
    public RepUserDto getUser(final Long id) {
        log.info("Recuperation de l'utilisateur - idUtilisateur: {}", id);
        final User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        log.info("Utilisateur recupere - idUtilisateur: {}", id);
        return userMapper.toDto(user);
    }

    @Transactional(readOnly = true)
    public List<RepUserDto> getUsersByTenant(final Long tenantId) {
        log.info("Recuperation des utilisateurs du tenant - tenantId: {}", tenantId);
        final List<User> utilisateurs = userRepository.findByTenantId(tenantId);
        log.info("Utilisateurs recuperes - nombre: {}", utilisateurs.size());
        return utilisateurs.stream()
                .map(userMapper::toDto)
                .toList();
    }

    @Transactional
    public RepUserDto createUser(final ReqCreateUserDto request, final Long tenantId) {
        log.info("Creation d'un utilisateur - email: {}", PiiMasker.email(request.getEmail()));
        userRepository.findByEmail(request.getEmail())
                .ifPresent(u -> {
                    throw new UserAlreadyExistsException(request.getEmail());
                });

        final User entity = userBuilder.buildFromRequest(request, tenantId);
        final User saved = userRepository.save(entity);
        log.info("Utilisateur cree - idUtilisateur: {}", saved.getId());
        return userMapper.toDto(saved);
    }

    @Transactional
    public RepUserDto updateUser(final Long id, final ReqUpdateUserDto request) {
        log.info("Mise a jour de l'utilisateur - idUtilisateur: {}", id);
        final User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));

        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getNom() != null) {
            user.setNom(request.getNom());
        }
        if (request.getPrenom() != null) {
            user.setPrenom(request.getPrenom());
        }
        if (request.getRole() != null) {
            user.setRole(RoleEnum.valueOf(request.getRole()));
        }

        final User saved = userRepository.save(user);
        log.info("Utilisateur mis a jour - idUtilisateur: {}", id);
        return userMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public User findByEmail(final String email) {
        log.info("Recherche de l'utilisateur par email - email: {}", PiiMasker.email(email));
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(null));
    }

    /**
     * Desactive un utilisateur (soft delete) + anonymise son email pour
     * liberer la contrainte UNIQUE (autorise la reinvitation du meme email plus tard).
     *
     * L'historique du user (leads crees, conversations, RDV) reste intact en BDD,
     * uniquement son identite (email, clerk_user_id) est anonymisee.
     *
     * Format email anonymise : "deleted-{id}-{timestamp}@deleted.local"
     */
    @Transactional
    public void desactiverUtilisateur(final User user) {
        final String emailOrigine = user.getEmail();
        final String emailAnonyme = "deleted-" + user.getId() + "-"
                + System.currentTimeMillis() + "@deleted.local";

        user.setEmail(emailAnonyme);
        user.setClerkUserId(null); // libere aussi la contrainte UNIQUE sur clerk_user_id
        user.setStatut(StatutUtilisateurEnum.INACTIF);
        user.setActif(false);
        userRepository.save(user);

        log.info("Utilisateur desactive (soft delete + anonymisation) - userId: {}, ancienEmail (masque): {}***",
                user.getId(),
                emailOrigine != null && emailOrigine.length() > 3 ? emailOrigine.substring(0, 3) : "[null]");
    }
}
