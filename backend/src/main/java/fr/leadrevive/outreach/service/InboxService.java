package fr.leadrevive.outreach.service;

import fr.leadrevive.common.util.TexteSearchUtils;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.outreach.dto.RepConversationCompteursDto;
import fr.leadrevive.outreach.dto.RepConversationInboxDto;
import fr.leadrevive.outreach.entity.DirectionSmsEnum;
import fr.leadrevive.outreach.entity.ExpediteurMessageEnum;
import fr.leadrevive.outreach.entity.ModePilotageEnum;
import fr.leadrevive.outreach.entity.SmsConversation;
import fr.leadrevive.outreach.entity.SmsMessage;
import fr.leadrevive.outreach.entity.StatutConversationSmsEnum;
import fr.leadrevive.outreach.entity.StatutSmsEnum;
import fr.leadrevive.outreach.entity.StatutWhatsappConfigEnum;
import fr.leadrevive.outreach.exception.WhatsAppNonConfigureException;
import fr.leadrevive.outreach.repository.SmsConversationRepository;
import fr.leadrevive.outreach.repository.SmsMessageRepository;
import fr.leadrevive.outreach.repository.TenantWhatsappConfigRepository;
import fr.leadrevive.user.entity.User;
import fr.leadrevive.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InboxService {

    private final SmsConversationRepository smsConversationRepository;
    private final SmsMessageRepository smsMessageRepository;
    private final LeadRepository leadRepository;
    private final UserRepository userRepository;
    private final TwilioWhatsAppService twilioWhatsAppService;
    private final InboxNotificationService inboxNotificationService;
    private final ModerationService moderationService;
    private final TenantWhatsappConfigRepository tenantWhatsappConfigRepository;

    /**
     * Plafond hard du nombre de conversations chargees en memoire pour l'inbox.
     * Une concession active peut depasser plusieurs milliers de conversations cumulees ;
     * sans cette limite, on declenche un N+1 sur les messages + saturation heap.
     * La pagination cote frontend (filtres + recherche) couvre largement ce plafond
     * pour les flows quotidiens. Une vraie pagination offset/total sera ajoutee
     * plus tard si necessaire.
     */
    private static final int INBOX_MAX_CONVERSATIONS = 500;

    @Transactional(readOnly = true)
    public List<RepConversationInboxDto> getConversations(final Long tenantId, final Long vendeurId,
                                                           final String statut, final String modePilotage,
                                                           final Boolean nonLuSeulement, final String recherche) {
        log.info("Liste conversations inbox - tenantId: {}, vendeurId: {}", tenantId, vendeurId);

        // Get conversations for tenant, filtered by vendeur if provided.
        // Plafond hard pour eviter OOM + N+1 sur les messages (cf INBOX_MAX_CONVERSATIONS).
        final org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(0, INBOX_MAX_CONVERSATIONS);
        List<SmsConversation> conversations;
        if (vendeurId != null) {
            conversations = smsConversationRepository.findByTenantIdAndUserIdOrderByDernierMessageLeDesc(tenantId, vendeurId, pageable);
        } else {
            conversations = smsConversationRepository.findByTenantIdOrderByDernierMessageLeDesc(tenantId, pageable);
        }
        if (conversations.size() == INBOX_MAX_CONVERSATIONS) {
            log.warn("Inbox limite atteinte ({} conversations) - tenantId: {}, vendeurId: {}. " +
                    "Conversations anciennes potentiellement masquees. Envisager pagination cote front.",
                    INBOX_MAX_CONVERSATIONS, tenantId, vendeurId);
        }

        // Apply statut filter
        if (statut != null && !statut.isBlank()) {
            final StatutConversationSmsEnum statutEnum = StatutConversationSmsEnum.valueOf(statut);
            conversations = conversations.stream()
                    .filter(c -> c.getStatut() == statutEnum)
                    .toList();
        }

        // Apply mode pilotage filter
        if (modePilotage != null && !modePilotage.isBlank()) {
            final ModePilotageEnum modeEnum = ModePilotageEnum.valueOf(modePilotage);
            conversations = conversations.stream()
                    .filter(c -> c.getModePilotage() == modeEnum)
                    .toList();
        }

        // Collect lead IDs and user IDs for batch loading
        final List<Long> leadIds = conversations.stream().map(SmsConversation::getLeadId).distinct().toList();
        // filter(non-null) : une conversation demarree par le systeme (envoi auto) peut avoir
        // userId null -> on ne le passe pas a findAllById (et map.get(null) renverra null plus bas).
        final List<Long> userIds = conversations.stream()
                .map(SmsConversation::getUserId)
                .filter(java.util.Objects::nonNull)
                .distinct().toList();
        final List<String> conversationIds = conversations.stream().map(SmsConversation::getConversationId).toList();

        final Map<Long, Lead> leadsMap = leadRepository.findAllById(leadIds).stream()
                .collect(Collectors.toMap(Lead::getId, Function.identity()));
        final Map<Long, User> usersMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        // Batch load des derniers messages : 1 query au lieu de N (1 par conversation).
        // Critique pour les tenants avec 100+ conversations actives.
        final Map<String, SmsMessage> derniersMessages = conversationIds.isEmpty()
                ? Map.of()
                : smsMessageRepository.findDerniersMessages(conversationIds).stream()
                        .collect(Collectors.toMap(SmsMessage::getConversationId, Function.identity(), (a, b) -> a));

        // Build DTOs
        final List<RepConversationInboxDto> result = new ArrayList<>();
        for (final SmsConversation conv : conversations) {
            final Lead lead = leadsMap.get(conv.getLeadId());
            final User user = usersMap.get(conv.getUserId());
            final SmsMessage dernierMessage = derniersMessages.get(conv.getConversationId());

            final String dernierContenu = dernierMessage != null
                    ? (dernierMessage.getContenu().length() > 80
                        ? dernierMessage.getContenu().substring(0, 80) + "..."
                        : dernierMessage.getContenu())
                    : null;
            final String dernierDirection = dernierMessage != null ? dernierMessage.getDirection().name() : null;

            // Non lu = dernier message ENTRANT et conversation pas lue depuis
            final Boolean nonLu = dernierMessage != null
                    && dernierMessage.getDirection() == DirectionSmsEnum.ENTRANT
                    && (conv.getDerniereLectureLe() == null || dernierMessage.getCreeLe().isAfter(conv.getDerniereLectureLe()));

            // Apply non lu filter
            if (Boolean.TRUE.equals(nonLuSeulement) && !Boolean.TRUE.equals(nonLu)) {
                continue;
            }

            // Apply recherche filter (insensible casse ET accents)
            if (recherche != null && !recherche.isBlank() && lead != null) {
                final boolean matches = TexteSearchUtils.containsNormalise(lead.getNom(), recherche)
                        || TexteSearchUtils.containsNormalise(lead.getMarque(), recherche)
                        || TexteSearchUtils.containsNormalise(lead.getModele(), recherche);
                if (!matches) continue;
            }

            result.add(RepConversationInboxDto.builder()
                    .conversationId(conv.getConversationId())
                    .leadId(conv.getLeadId())
                    .nomProspect(lead != null ? lead.getNom() : "Inconnu")
                    .marque(lead != null ? lead.getMarque() : null)
                    .modele(lead != null ? lead.getModele() : null)
                    .territoire(lead != null ? lead.getTerritoire() : null)
                    .scoring(lead != null ? lead.getScoring() : null)
                    .hotnessScore(lead != null ? lead.getHotnessScore() : null)
                    .statut(conv.getStatut().name())
                    .leadStatut(lead != null && lead.getStatut() != null ? lead.getStatut().name() : null)
                    .nombreMessages(conv.getNombreMessages())
                    .dernierMessageLe(conv.getDernierMessageLe())
                    .dernierMessageContenu(dernierContenu)
                    .dernierMessageDirection(dernierDirection)
                    .messageNonLu(nonLu)
                    .userId(conv.getUserId())
                    .nomVendeur(user != null && user.getRole() == fr.leadrevive.user.entity.RoleEnum.VENDEUR
                            ? user.getPrenom() + " " + user.getNom() : null)
                    .modePilotage(conv.getModePilotage() != null ? conv.getModePilotage().name() : "IA_AUTO")
                    .canal(conv.getCanal() != null ? conv.getCanal().name() : "WHATSAPP")
                    .clientIrrite(lead != null && Boolean.TRUE.equals(lead.getClientIrrite()))
                    .build());
        }

        // Sort: non lu first, then by dernierMessageLe desc
        result.sort((a, b) -> {
            if (Boolean.TRUE.equals(a.getMessageNonLu()) && !Boolean.TRUE.equals(b.getMessageNonLu())) return -1;
            if (!Boolean.TRUE.equals(a.getMessageNonLu()) && Boolean.TRUE.equals(b.getMessageNonLu())) return 1;
            if (a.getDernierMessageLe() != null && b.getDernierMessageLe() != null) {
                return b.getDernierMessageLe().compareTo(a.getDernierMessageLe());
            }
            return 0;
        });

        return result;
    }

    @Transactional(readOnly = true)
    public RepConversationCompteursDto getCompteurs(final Long tenantId, final Long vendeurId) {
        log.info("Compteurs inbox - tenantId: {}", tenantId);
        // Compteurs via SQL plutot que chargement complet en memoire : evite
        // de charger 500 conversations + leads + users + derniers messages a chaque
        // appel (et c'est appele frequemment par la sidebar / polling 30s).
        int enCours = 0;
        int rdvObtenus = 0;
        int modeHumain = 0;
        for (final Object[] row : smsConversationRepository.compterGroupeParStatutEtMode(tenantId, vendeurId)) {
            final StatutConversationSmsEnum statut = (StatutConversationSmsEnum) row[0];
            final ModePilotageEnum mode = (ModePilotageEnum) row[1];
            final long count = ((Number) row[2]).longValue();
            if (statut == StatutConversationSmsEnum.EN_COURS) enCours += (int) count;
            if (statut == StatutConversationSmsEnum.RDV_OBTENU) rdvObtenus += (int) count;
            if (mode == ModePilotageEnum.HUMAIN) modeHumain += (int) count;
        }
        final int nonLues = (int) smsConversationRepository.compterNonLues(tenantId, vendeurId);
        return RepConversationCompteursDto.builder()
                .enCours(enCours).rdvObtenus(rdvObtenus).nonLues(nonLues).modeHumain(modeHumain).build();
    }

    @Transactional
    public void reprendreMain(final String conversationId, final Long tenantId, final Long userId) {
        log.info("Reprise en main - conversationId: {}, userId: {}", conversationId, userId);
        final SmsConversation conv = smsConversationRepository
                .findByConversationIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation non trouvee: " + conversationId));
        // Garde-fou : on n'autorise pas la reprise sur une conversation fermee
        // (STOP_DEMANDE = opt-out explicite, TERMINEE = clos, ERREUR = etat invalide).
        // Sinon : passage HUMAIN sur une opt-out = relance/envoi possible = violation RGPD.
        if (conv.getStatut() == StatutConversationSmsEnum.STOP_DEMANDE
                || conv.getStatut() == StatutConversationSmsEnum.TERMINEE
                || conv.getStatut() == StatutConversationSmsEnum.ERREUR) {
            throw new IllegalStateException("Impossible de reprendre la main : la conversation est terminée (statut "
                    + conv.getStatut() + ").");
        }
        conv.setModePilotage(ModePilotageEnum.HUMAIN);
        conv.setRepriseParUserId(userId);
        conv.setRepriseParLe(LocalDateTime.now());
        smsConversationRepository.save(conv);
    }

    @Transactional
    public void rendreIa(final String conversationId, final Long tenantId) {
        log.info("Restitution a l'IA - conversationId: {}", conversationId);
        final SmsConversation conv = smsConversationRepository
                .findByConversationIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation non trouvee: " + conversationId));
        // Meme garde-fou que reprendreMain : interdit sur conversations fermees.
        // Sans ca : conv STOP_DEMANDE -> mode IA_AUTO -> webhook entrant -> IA repond
        // au prospect qui a explicitement demande STOP. Violation RGPD.
        if (conv.getStatut() == StatutConversationSmsEnum.STOP_DEMANDE
                || conv.getStatut() == StatutConversationSmsEnum.TERMINEE
                || conv.getStatut() == StatutConversationSmsEnum.ERREUR) {
            throw new IllegalStateException("Impossible de rendre à l'IA : la conversation est terminée (statut "
                    + conv.getStatut() + ").");
        }
        conv.setModePilotage(ModePilotageEnum.IA_AUTO);
        conv.setRepriseParUserId(null);
        conv.setRepriseParLe(null);
        // Purge la relance programmee : pendant la reprise manuelle, prochaineRelanceLe
        // peut etre dans le passe. Sans ce reset, le scheduler de relance renverrait
        // immediatement un template "Je reviens vers vous" a un prospect avec qui le
        // vendeur vient d'echanger en direct. L'IA reprogrammera une relance au besoin.
        conv.setProchaineRelanceLe(null);
        smsConversationRepository.save(conv);
    }

    @Transactional
    public void marquerLue(final String conversationId, final Long tenantId) {
        log.info("Marquage lu - conversationId: {}", conversationId);
        // UPDATE cible sur derniere_lecture_le uniquement : ne charge pas l'entite et ne
        // bump donc PAS le @Version. Sans ca, ouvrir une conversation dans l'inbox pendant
        // qu'un webhook Twilio traite la reponse du prospect (appel IA ~10s tenant l'entite
        // chargee) provoquait un ObjectOptimisticLockingFailureException cote webhook et le
        // prospect ne recevait jamais de reponse. Le retour 0 = conversation absente pour ce
        // tenant -> on preserve la securite multi-tenant (IDOR) via l'exception.
        final int misesAJour = smsConversationRepository
                .updateDerniereLecture(conversationId, tenantId, LocalDateTime.now());
        if (misesAJour == 0) {
            throw new IllegalArgumentException("Conversation non trouvee: " + conversationId);
        }
    }

    @Transactional
    public void envoyerMessageManuel(final String conversationId, final String contenu,
                                      final Long tenantId, final Long userId) {
        log.info("Envoi message manuel - conversationId: {}, userId: {}", conversationId, userId);

        final SmsConversation conv = smsConversationRepository
                .findByConversationIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation non trouvee: " + conversationId));

        if (conv.getModePilotage() != ModePilotageEnum.HUMAIN) {
            throw new IllegalStateException("Passez en mode manuel d'abord");
        }

        // WhatsApp non configure : on bloque AVANT d'enregistrer le message pour eviter
        // un message fantome en statut ECHEC + retour 200 silencieux cote frontend.
        // Le GlobalExceptionHandler renvoie 422 avec un lien vers la page de config.
        final boolean whatsappActif = tenantWhatsappConfigRepository.findByTenantId(tenantId)
                .map(c -> c.getStatut() == StatutWhatsappConfigEnum.ACTIF)
                .orElse(false);
        if (!whatsappActif) {
            throw new WhatsAppNonConfigureException(tenantId);
        }

        // RGPD : pas d'envoi sortant sans preuve de consentement initial.
        leadRepository.findById(conv.getLeadId()).ifPresent(lead -> {
            if (!Boolean.TRUE.equals(lead.getConsentementContact())) {
                throw new IllegalStateException("Consentement RGPD manquant pour ce prospect");
            }
        });

        // Fenetre WhatsApp 24h : un message de session (hors template Meta-approuve) n'est
        // autorise que dans les 24h suivant le DERNIER message du prospect. Au-dela, Twilio
        // rejette (63016) et le message tombe en ECHEC sans explication claire pour le vendeur.
        // On bloque en amont avec un message metier explicite.
        final LocalDateTime dernierEntrant = smsMessageRepository
                .findDernierMessageEntrantLe(conv.getConversationId()).orElse(null);
        if (dernierEntrant == null || dernierEntrant.isBefore(LocalDateTime.now().minusHours(24))) {
            throw new IllegalStateException(
                    "Ce prospect n'a pas écrit depuis plus de 24h : WhatsApp n'autorise plus de message libre. "
                    + "Il faut attendre qu'il réponde pour pouvoir lui réécrire.");
        }

        // Moderation IA (epic 23) : verifie que le contenu respecte la Meta Business
        // Policy avant envoi. Protege la qualite WhatsApp de la WABA partagee.
        // Fail-open : si l'IA est indisponible, autorise par defaut.
        final var moderation = moderationService.verifier(tenantId, userId, conversationId, contenu);
        if (!moderation.autorise()) {
            throw new fr.leadrevive.outreach.exception.ModerationBlockedException(moderation.raison());
        }

        // Ordre calcule via SELECT MAX(ordre)+1 atomique dans la meme transaction.
        // Reduit la fenetre de race condition si un webhook entrant arrive au moment
        // ou le vendeur envoie un message manuel.
        final int ordre = smsMessageRepository.findProchainOrdre(conv.getConversationId());

        // Persiste le message EN_ATTENTE AVANT l'appel Twilio (saveAndFlush) : la ligne
        // existe pendant l'envoi. Sans ce pre-marker, le save etait fait APRES Twilio ->
        // un crash entre les deux perdait un message pourtant envoye+facture, et le vendeur
        // re-cliquait -> double envoi. On bascule ENVOYE/ECHEC juste apres l'appel.
        final SmsMessage message = new SmsMessage();
        message.setTenantId(tenantId);
        message.setConversationId(conv.getConversationId());
        message.setDirection(DirectionSmsEnum.SORTANT);
        message.setContenu(contenu);
        message.setStatut(StatutSmsEnum.EN_ATTENTE);
        message.setExpediteur(ExpediteurMessageEnum.VENDEUR);
        message.setOrdre(ordre);
        smsMessageRepository.saveAndFlush(message);

        try {
            final String twilioSid = twilioWhatsAppService.envoyerWhatsApp(conv.getNumeroProspect(), contenu, conv.getTenantId());
            message.setStatut(StatutSmsEnum.ENVOYE);
            message.setBrevoMessageId(twilioSid);
        } catch (final Exception e) {
            message.setStatut(StatutSmsEnum.ECHEC);
            log.error("Echec envoi message manuel - cause: {}", e.getMessage());
        }
        smsMessageRepository.save(message);

        conv.setNombreMessages(ordre + 1);
        conv.setDernierMessageLe(LocalDateTime.now());
        // Marquer la reprise : le vendeur a pris en charge la conversation.
        // Remet le statut en EN_COURS si la conversation etait en attente (RDV_OBTENU, PROSPECT_REFUSE)
        // car le vendeur est maintenant actif dessus.
        if (conv.getRepriseParUserId() == null) {
            conv.setRepriseParUserId(userId);
            conv.setRepriseParLe(LocalDateTime.now());
        }
        // Preservation des statuts forts : RDV_OBTENU reste RDV_OBTENU meme si le vendeur
        // continue d'echanger (le RDV est toujours pris). Sans cette exception, envoyer un
        // message manuel "pour confirmer l'horaire" faisait perdre l'etat RDV_OBTENU.
        if (conv.getStatut() != StatutConversationSmsEnum.EN_COURS
                && conv.getStatut() != StatutConversationSmsEnum.RDV_OBTENU
                && conv.getStatut() != StatutConversationSmsEnum.STOP_DEMANDE
                && conv.getStatut() != StatutConversationSmsEnum.TERMINEE) {
            conv.setStatut(StatutConversationSmsEnum.EN_COURS);
        }
        smsConversationRepository.save(conv);

        // Notifier (ciblage vendeur titulaire pour ne pas leaker le contenu PII)
        inboxNotificationService.notifierApresCommitPourVendeur(
                tenantId, conv.getUserId(), "nouveau_message",
                Map.of("conversationId", conv.getConversationId(),
                       "direction", "SORTANT",
                       "contenu", contenu,
                       "expediteur", "VENDEUR",
                       "creeLe", LocalDateTime.now().toString()));
    }

    /**
     * Réessaie l'envoi d'un message en échec.
     *
     * <p>Deux chemins distincts, selon la nature du message d'origine :</p>
     * <ul>
     *   <li><b>Template</b> (premier contact, relance, rappel — {@code contentSid} présent) :
     *       on REJOUE le template à l'identique via son Content SID + ses variables. C'est
     *       indispensable : renvoyer le texte rendu en message libre échouerait
     *       systématiquement hors fenêtre 24h (Twilio 63016). Un template Meta-approuvé est
     *       autorisé à tout moment, donc pas de contrôle de fenêtre ici.</li>
     *   <li><b>Message libre de session</b> ({@code contentSid} null) : on re-vérifie la
     *       fenêtre 24h AVANT l'appel Twilio, pour renvoyer une erreur métier claire plutôt
     *       qu'un rejet Twilio opaque si la fenêtre s'est fermée depuis l'échec initial.</li>
     * </ul>
     *
     * <p>Garde-fous communs à l'envoi initial : WhatsApp actif + consentement RGPD.</p>
     */
    @Transactional
    public void retryMessage(final String conversationId, final Long messageId, final Long tenantId) {
        log.info("Retry message - conversationId: {}, messageId: {}", conversationId, messageId);

        final SmsConversation conv = smsConversationRepository
                .findByConversationIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation non trouvée: " + conversationId));

        // Scoping tenant + conversation : un findById(messageId) nu permettrait un IDOR
        // cross-tenant (renvoyer le contenu d'un message d'un autre tenant vers le prospect
        // courant). On exige que le message appartienne A CETTE conversation ET A CE tenant.
        final SmsMessage message = smsMessageRepository
                .findByIdAndConversationIdAndTenantId(messageId, conversationId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Message non trouvé: " + messageId));

        if (message.getStatut() != StatutSmsEnum.ECHEC) {
            throw new IllegalStateException("Seuls les messages en échec peuvent être réessayés");
        }
        if (message.getDirection() != DirectionSmsEnum.SORTANT) {
            throw new IllegalStateException("Seuls les messages sortants peuvent être réessayés");
        }

        // Garde-fous communs : WhatsApp doit etre actif (sinon 422 + lien config).
        final boolean whatsappActif = tenantWhatsappConfigRepository.findByTenantId(tenantId)
                .map(c -> c.getStatut() == StatutWhatsappConfigEnum.ACTIF)
                .orElse(false);
        if (!whatsappActif) {
            throw new WhatsAppNonConfigureException(tenantId);
        }
        // RGPD : pas de renvoi sans preuve de consentement.
        leadRepository.findById(conv.getLeadId()).ifPresent(lead -> {
            if (!Boolean.TRUE.equals(lead.getConsentementContact())) {
                throw new IllegalStateException("Consentement RGPD manquant pour ce prospect");
            }
        });

        final boolean estTemplate = message.getContentSid() != null && !message.getContentSid().isBlank();

        final String twilioSid;
        try {
            if (estTemplate) {
                final Map<String, String> variables =
                        twilioWhatsAppService.deserialiserContentVariables(message.getContentVariables());
                twilioSid = twilioWhatsAppService.envoyerRelanceTemplate(
                        conv.getNumeroProspect(), message.getContentSid(), variables, conv.getTenantId());
            } else {
                final LocalDateTime dernierEntrant = smsMessageRepository
                        .findDernierMessageEntrantLe(conv.getConversationId()).orElse(null);
                if (dernierEntrant == null || dernierEntrant.isBefore(LocalDateTime.now().minusHours(24))) {
                    throw new IllegalStateException(
                            "Ce prospect n'a pas écrit depuis plus de 24h : WhatsApp n'autorise plus de message libre. "
                            + "Il faut attendre qu'il réponde pour pouvoir lui réécrire.");
                }
                twilioSid = twilioWhatsAppService.envoyerWhatsApp(
                        conv.getNumeroProspect(), message.getContenu(), conv.getTenantId());
            }
        } catch (final IllegalStateException e) {
            // Erreur metier (fenetre 24h, template introuvable...) : remontee telle quelle
            // au handler global pour un 4xx avec message clair.
            throw e;
        } catch (final Exception e) {
            // Echec cote Twilio/WhatsApp (numero non pret, compte indisponible, etc.) : on
            // remonte un IllegalStateException -> 400 avec un message clair et ACTIONNABLE
            // plutot qu'un 500 "Erreur interne" opaque. La cause technique reste dans les logs.
            log.error("Retry échoué - messageId: {}, cause: {}", messageId, e.getMessage());
            throw new IllegalStateException(
                    "Le renvoi n'a pas pu aboutir côté WhatsApp. Vérifiez l'état de votre numéro "
                    + "WhatsApp Business, puis réessayez dans quelques minutes.");
        }

        message.setStatut(StatutSmsEnum.ENVOYE);
        message.setBrevoMessageId(twilioSid);
        // Le renvoi est reparti : on efface le motif d'echec perime. Le webhook de statut
        // remettra ECHEC + nouveau motif si la livraison re-echoue.
        message.setErreur(null);
        smsMessageRepository.save(message);
        log.info("Retry réussi - messageId: {}, template: {}, nouveau sid: {}", messageId, estTemplate, twilioSid);
    }
}
