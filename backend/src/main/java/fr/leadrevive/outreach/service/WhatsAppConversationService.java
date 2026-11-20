package fr.leadrevive.outreach.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.commons.text.WordUtils;
import fr.leadrevive.common.tenant.TenantContext;
import fr.leadrevive.common.util.PiiMasker;
import fr.leadrevive.ai.entity.AiLog;
import fr.leadrevive.ai.entity.AiStatutEnum;
import fr.leadrevive.ai.entity.TenantAiConfig;
import fr.leadrevive.ai.entity.TypeGenerationEnum;
import fr.leadrevive.ai.repository.AiLogRepository;
import fr.leadrevive.ai.service.AiCoutService;
import fr.leadrevive.ai.service.AiModelResolver;
import fr.leadrevive.ai.service.AiQuotaGuard;
import fr.leadrevive.ai.service.TenantAiConfigService;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.exception.LeadNotFoundException;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.tenant.repository.TenantRepository;
import fr.leadrevive.outreach.dto.RepSmsConversationDto;
import fr.leadrevive.outreach.dto.RepSmsMessageDto;
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
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/*
 * Routage des messages entrants Twilio (cf bug #16) :
 *
 *  WebhookController#twilioWhatsAppWebhook recoit From (numero prospect), Body, et optionnellement
 *  OriginalRepliedMessageSid (set par Twilio si le prospect a utilise "Reply to" sur un message precis).
 *  Resolution de la conversation cible dans traiterReponseProspect :
 *    1. Si OriginalRepliedMessageSid present → lookup direct via brevoMessageId. Match a 100%.
 *    2. Sinon → findActiveByNumeroProspectOrderByLatestOutbound : conversation active dont le dernier
 *       message SORTANT est le plus recent. Heuristique : le prospect repond a celui qui lui a ecrit
 *       en dernier. Resiste aux collisions "meme numero, plusieurs leads/conversations actives".
 *    3. Fallback findMostRecentActiveByNumeroProspect (tri sur dernierMessageLe) — uniquement si aucune
 *       conversation n'a encore de SORTANT (cas theorique).
 *
 *  Ce qui est volontairement non couvert :
 *   - Multi-tenant avec un seul numero Twilio Business partage : impossible de desambiguiser au niveau
 *     du webhook. Modele cible Twilio = un numero Business par concession; le parametre To pourrait alors
 *     etre cable sur tenant.numeroTwilioBusiness pour filtrer la lookup.
 *   - Vendeur repondant depuis son WhatsApp personnel (hors Twilio) : ne transite pas par Twilio donc invisible
 *     cote Leadrive par construction. Solution = forcer le passage par l'inbox.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppConversationService {

    private final ChatClient.Builder chatClientBuilder;
    private final AiModelResolver aiModelResolver;
    private final SmsConversationRepository smsConversationRepository;
    private final SmsMessageRepository smsMessageRepository;
    private final LeadRepository leadRepository;
    private final AiLogRepository aiLogRepository;
    private final TenantAiConfigService tenantAiConfigService;
    private final AiCoutService aiCoutService;
    private final TwilioWhatsAppService twilioWhatsAppService;
    private final InboxNotificationService inboxNotificationService;
    private final InboxEntrantWriter inboxEntrantWriter;
    private final fr.leadrevive.lead.repository.HistoriqueStatutRepository historiqueStatutRepository;
    private final ConversationAnalyseService conversationAnalyseService;
    private final AiQuotaGuard aiQuotaGuard;
    private final IaOutputFilter iaOutputFilter;
    private final TenantRepository tenantRepository;
    private final fr.leadrevive.outreach.repository.TenantWhatsappConfigRepository tenantWhatsappConfigRepository;
    private final fr.leadrevive.ai.service.AiResilientCaller aiResilientCaller;
    private final RappelRdvService rappelRdvService;
    private final fr.leadrevive.common.realtime.SseConnectionService sseConnectionService;

    @PersistenceContext
    private EntityManager entityManager;

    /** Plafond de messages par conversation pour eviter les boucles infinies et limiter le cout. */
    @Value("${leadrevive.max-messages-par-conversation:40}")
    private int maxMessagesParConversation;

    @Value("${leadrevive.relance.delai-j1-heures:24}")
    private int delaiRelanceJ1Heures;

    private String systemPromptTemplate;

    @PostConstruct
    void chargerPrompts() {
        try {
            systemPromptTemplate = new ClassPathResource("prompts/whatsapp-conversation.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
            log.info("Prompt WhatsApp charge - taille: {} caracteres", systemPromptTemplate.length());
        } catch (final IOException e) {
            throw new IllegalStateException("Impossible de charger le prompt WhatsApp", e);
        }
    }

    /**
     * Resout le nom de la concession : TenantAiConfig > Tenant.nom > fallback.
     */
    private String resoudreNomConcession(final TenantAiConfig config, final Long tenantId) {
        if (config != null && config.getNomConcession() != null && !config.getNomConcession().isBlank()) {
            return config.getNomConcession();
        }
        return tenantRepository.findById(tenantId)
                .map(t -> t.getNom())
                .orElse("la concession");
    }

    private String construireSystemPrompt(final Lead lead, final TenantAiConfig config) {
        final String nonRenseigne = "Non renseigne";
        final String nomConcession = resoudreNomConcession(config, lead.getTenantId());
        final String horaires = config != null && config.getHorairesOuverture() != null ? config.getHorairesOuverture() : nonRenseigne;
        // Adresse / telephone / site web / email : si vide, le prompt invite l'IA a
        // renvoyer vers le conseiller (jamais inventer).
        final String adresseConcession = champOuNonRenseigne(config != null ? config.getAdresseComplete() : null);
        final String telephoneConcession = champOuNonRenseigne(config != null ? config.getTelephoneConcession() : null);
        final String siteWeb = champOuNonRenseigne(config != null ? config.getSiteWeb() : null);
        final String emailContact = champOuNonRenseigne(config != null ? config.getEmailContact() : null);
        final String prenomAssistant = "Nora";

        return systemPromptTemplate
                .replace("{PRENOM_ASSISTANT}", prenomAssistant)
                .replace("{NOM_CONCESSION}", nomConcession)
                .replace("{NOM_CONCESSION_2}", nomConcession)
                .replace("{HORAIRES}", horaires)
                .replace("{ADRESSE_CONCESSION}", adresseConcession)
                .replace("{TELEPHONE_CONCESSION}", telephoneConcession)
                .replace("{SITE_WEB}", siteWeb)
                .replace("{EMAIL_CONTACT}", emailContact)
                .replace("{NOM_PROSPECT}", lead.getNom() != null ? lead.getNom() : nonRenseigne)
                .replace("{MARQUE_VEHICULE}", lead.getMarque() != null ? lead.getMarque() : nonRenseigne)
                .replace("{MODELE_VEHICULE}", lead.getModele() != null ? lead.getModele() : nonRenseigne)
                .replace("{SOURCE}", lead.getSource() != null ? lead.getSource() : nonRenseigne)
                .replace("{REPRISE}", lead.getReprise() != null && lead.getReprise() ? "Oui" : "Non")
                .replace("{ETAPE}", lead.getEtape() != null ? lead.getEtape() : nonRenseigne)
                .replace("{DONNEES_SUPPLEMENTAIRES}",
                        fr.leadrevive.common.util.DonneesSupplementairesFormatter.formater(lead.getDonneesSupplementaires()));
    }

    private static String champOuNonRenseigne(final String champ) {
        return champ != null && !champ.isBlank() ? champ : "Non renseigne";
    }

    @Transactional
    public RepSmsConversationDto demarrerConversation(final Long leadId, final String numeroDestinataire,
                                                       final Long tenantId, final Long userId) {
        log.info("Demarrage conversation WhatsApp - leadId: {}, numero: {}", leadId, PiiMasker.phone(numeroDestinataire));

        final Lead lead = leadRepository.findByIdAndTenantId(leadId, tenantId)
                .orElseThrow(() -> new LeadNotFoundException(leadId));

        if (Boolean.TRUE.equals(lead.getOptOutSms())) {
            throw new IllegalStateException("Ce lead a demande a ne plus recevoir de WhatsApp (opt-out)");
        }

        // RGPD : pas d'envoi sortant sans preuve de consentement initial.
        if (!Boolean.TRUE.equals(lead.getConsentementContact())) {
            log.warn("Envoi WhatsApp bloque - consentement manquant - leadId: {}", lead.getId());
            throw new IllegalStateException("Consentement RGPD manquant pour ce prospect (consentementContact null/false)");
        }

        final String numeroFormate = twilioWhatsAppService.formaterNumeroFr(numeroDestinataire);
        if (!twilioWhatsAppService.estNumeroValide(numeroDestinataire)) {
            throw new IllegalArgumentException("Numero invalide (format E.164 attendu : +XXXXXXXXXXX)");
        }

        smsConversationRepository.findByLeadIdAndStatut(leadId, StatutConversationSmsEnum.EN_COURS)
                .ifPresent(c -> { throw new IllegalStateException("Une conversation WhatsApp est deja en cours pour ce lead"); });

        // Ferme les anciennes conversations EN_COURS pour le meme numero (quel que soit le lead).
        // Un numero de telephone = un seul interlocuteur, pas de conversations paralleles.
        smsConversationRepository.findByTenantIdOrderByDernierMessageLeDesc(tenantId).stream()
                .filter(c -> c.getStatut() == StatutConversationSmsEnum.EN_COURS)
                .filter(c -> numeroFormate.equals(c.getNumeroProspect()))
                .forEach(c -> {
                    c.setStatut(StatutConversationSmsEnum.TERMINEE);
                    smsConversationRepository.save(c);
                    log.info("Ancienne conversation fermee (meme numero) - conversationId: {}, leadId: {}",
                            c.getConversationId(), c.getLeadId());
                });

        final TenantAiConfig config = tenantAiConfigService.getConfig(tenantId);
        final String systemPrompt = construireSystemPrompt(lead, config);
        final String nomConcession = resoudreNomConcession(config, tenantId);
        final String prenomAssistant = twilioWhatsAppService.getAssistantPrenom();
        final String vehicule = ((lead.getMarque() != null ? lead.getMarque() : "") + " " +
                (lead.getModele() != null ? lead.getModele() : "")).trim();

        // Premier message = template WhatsApp (pas d'appel IA)
        // On fetche le body du template depuis Twilio (single source of truth) et on substitue les variables
        // pour affichage dans l'inbox ET pour que l'IA ait le contexte dans l'historique.
        // Si le template n'est pas recuperable (API Twilio down, SID invalide), on echoue l'envoi
        // plutot que de risquer un historique IA incoherent.
        // Ordre des variables aligne sur le template Meta-approuve :
        // {{1}} = prenom assistant, {{2}} = nom concession, {{3}} = vehicule / projet
        final Map<String, String> variablesTemplate = Map.of(
                "1", prenomAssistant,
                "2", nomConcession,
                // Fallback sans possessif : le template dit deja "votre projet de {{3}}",
                // un fallback "votre vehicule" donnerait "votre projet de votre vehicule".
                "3", vehicule.isBlank() ? "véhicule" : vehicule
        );
        final String templateBody = twilioWhatsAppService.getTemplateBody();
        if (templateBody == null) {
            log.error("Template WhatsApp non recuperable - tenantId: {}, leadId: {}", tenantId, leadId);
            throw new IllegalStateException(
                    "Impossible de recuperer le template WhatsApp depuis Twilio. Reessayez dans quelques instants.");
        }
        final String premierMessage = twilioWhatsAppService.appliquerVariables(templateBody, variablesTemplate);

        // Creer la conversation
        final String conversationId = UUID.randomUUID().toString();
        final SmsConversation conversation = new SmsConversation();
        conversation.setTenantId(tenantId);
        conversation.setLeadId(leadId);
        conversation.setUserId(userId);
        conversation.setConversationId(conversationId);
        conversation.setStatut(StatutConversationSmsEnum.EN_COURS);
        conversation.setNumeroProspect(numeroFormate);
        conversation.setCanal(CanalConversationEnum.WHATSAPP);
        conversation.setNombreMessages(1);
        conversation.setDernierMessageLe(LocalDateTime.now());
        conversation.setSystemPrompt(systemPrompt);
        // Planifier la premiere relance automatique (J+1)
        conversation.setNombreRelances(0);
        conversation.setProchaineRelanceLe(LocalDateTime.now().plusHours(delaiRelanceJ1Heures));
        final SmsConversation savedConv = smsConversationRepository.save(conversation);

        // Envoyer le WhatsApp via template (premier contact hors fenetre 24h)
        String twilioSid;
        try {
            twilioSid = twilioWhatsAppService.envoyerWhatsAppTemplate(numeroFormate, variablesTemplate, savedConv.getTenantId());
        } catch (final Exception e) {
            smsConversationRepository.delete(savedConv);
            log.error("Echec envoi premier WhatsApp template - numero: {}, cause: {}", PiiMasker.phone(numeroFormate), e.getMessage());
            throw new RuntimeException("Echec envoi WhatsApp : " + e.getMessage(), e);
        }

        // Tracer le numero expediteur (epic 23 - multi-numero). Pour pouvoir router
        // correctement les reponses entrantes vers la bonne conversation, on
        // memorise le numero WhatsApp Business utilise pour ce premier envoi.
        tenantWhatsappConfigRepository.findByTenantId(savedConv.getTenantId())
                .ifPresent(whatsappConfig -> {
                    savedConv.setWhatsappFrom(whatsappConfig.getWhatsappFrom());
                    smsConversationRepository.save(savedConv);
                });

        final SmsMessage message = new SmsMessage();
        message.setTenantId(tenantId);
        message.setConversationId(conversationId);
        message.setDirection(DirectionSmsEnum.SORTANT);
        message.setContenu(premierMessage);
        message.setStatut(StatutSmsEnum.ENVOYE);
        message.setBrevoMessageId(twilioSid);
        // Memorise le template utilise pour pouvoir le rejouer a l'identique lors d'un
        // "Reessayer" (le premier contact est un template Meta : un retry en message libre
        // serait rejete par Twilio hors fenetre 24h).
        message.setContentSid(twilioWhatsAppService.resoudrePremierContactSid(tenantId));
        message.setContentVariables(twilioWhatsAppService.serialiserContentVariables(variablesTemplate));
        message.setOrdre(0);
        smsMessageRepository.save(message);

        // Statut lead automatique : NOUVEAU -> CONTACTE des l'envoi du premier message
        if (lead.getStatut() == fr.leadrevive.lead.entity.LeadStatutEnum.NOUVEAU) {
            final fr.leadrevive.lead.entity.HistoriqueStatut h = new fr.leadrevive.lead.entity.HistoriqueStatut();
            h.setLeadId(lead.getId());
            h.setTenantId(tenantId);
            h.setStatutPrecedent(fr.leadrevive.lead.entity.LeadStatutEnum.NOUVEAU);
            h.setStatutSuivant(fr.leadrevive.lead.entity.LeadStatutEnum.CONTACTE);
            h.setSource("IA");
            historiqueStatutRepository.save(h);
            lead.setStatut(fr.leadrevive.lead.entity.LeadStatutEnum.CONTACTE);
            leadRepository.save(lead);
            log.info("Lead passe de NOUVEAU a CONTACTE - leadId: {}", lead.getId());
        }

        // Anti double-envoi : tout premier message envoye (manuel ou auto) annule un
        // envoi automatique en attente pour ce lead. Le flag est efface ici, apres
        // l'envoi reussi, quel que soit le point d'entree.
        if (lead.getPremierMessageAutoPrevuLe() != null) {
            lead.setPremierMessageAutoPrevuLe(null);
            leadRepository.save(lead);
            log.info("Premier message auto desarme (conversation demarree) - leadId: {}", lead.getId());
        }

        loggerAppel(tenantId, leadId, systemPrompt, premierMessage);

        // Ciblage managers + vendeur assigne : le payload contient le nom du prospect (PII),
        // ne pas le broadcaster a tous les vendeurs du tenant. Si le lead n'est pas encore
        // assigne (vendeurId null), le helper diffuse a tous (managers inclus) — acceptable.
        inboxNotificationService.notifierApresCommitPourVendeur(tenantId, lead.getVendeurId(), "nouvelle_conversation",
                Map.of("conversationId", conversationId,
                       "nomProspect", lead.getNom(),
                       "marque", lead.getMarque() != null ? lead.getMarque() : "",
                       "modele", lead.getModele() != null ? lead.getModele() : ""));

        log.info("Conversation WhatsApp demarree - conversationId: {}", conversationId);

        // Temps reel : le lead vient (potentiellement) de passer NOUVEAU -> CONTACTE.
        // On previent les vues liste / Kanban ouvertes pour qu'elles se rafraichissent
        // (meme convention que lead.created, broadcast en fin de methode transactionnelle).
        sseConnectionService.broadcast(tenantId,
                fr.leadrevive.common.realtime.dto.RealtimeEventDto.leadUpdated(
                        lead.getId(), lead.getStatut() != null ? lead.getStatut().name() : null));

        return toDto(savedConv, List.of(message));
    }

    /**
     * Traite la reponse d'un prospect au webhook Twilio.
     *
     * IMPORTANT : cette methode est appelee depuis un endpoint webhook PUBLIC (pas de
     * TenantContext) et n'est PAS @Async (combinaison @Async + @Transactional + ThreadLocal
     * tenant non propage = fuite cross-tenant). On execute synchroniquement et on identifie
     * le tenant via la conversation. Le webhook Twilio attend une 200 sous 15s, le traitement
     * complet (analyse IA + reponse) prend ~3-8s — acceptable.
     */
    public void traiterReponseProspect(final String numeroProspect, final String contenu) {
        traiterReponseProspect(numeroProspect, contenu, null, null);
    }

    public void traiterReponseProspect(final String numeroProspect, final String contenu,
                                        final String originalRepliedMessageSid) {
        traiterReponseProspect(numeroProspect, contenu, originalRepliedMessageSid, null);
    }

    /**
     * Traite la reponse d'un prospect recue via le webhook Twilio.
     *
     * <p>Routage de la conversation cible (priorite decroissante) :
     * <ol>
     *   <li><strong>OriginalRepliedMessageSid</strong> (le prospect a utilise "Reply to"
     *       sur un message specifique) : lookup direct par le SID du message cite,
     *       100% fiable, fonctionne meme avec un prospect en commun a 2 tenants.</li>
     *   <li><strong>Routage par To</strong> (epic 23) : on identifie le tenant
     *       proprietaire du numero WhatsApp Business qui a recu le message, puis on
     *       cherche une conversation active (numeroProspect, tenantId). Isole les
     *       conversations entre concessions qui contactent le meme prospect.</li>
     *   <li>Pas de fallback heuristique : si le routage par To echoue, le message
     *       est logue mais pas traite (eventuellement une conversation pre-Epic 23
     *       avec whatsapp_from NULL recue sur le numero LeadRive desactive).</li>
     * </ol>
     * </p>
     *
     * <p><strong>IMPORTANT</strong> : appelee depuis un endpoint webhook PUBLIC
     * (pas de TenantContext) et n'est PAS @Async (combinaison @Async + @Transactional
     * + ThreadLocal tenant non propage = fuite cross-tenant). On execute
     * synchroniquement et on active manuellement le filtre Hibernate apres avoir
     * trouve la conversation.</p>
     *
     * <p><strong>PAS de @Transactional (incident prod 2026-07-06)</strong> : la methode
     * enchaine 2 appels IA longs (extraction ~10s + generation ~3-8s). Une transaction
     * englobante tiendrait l'entite {@code SmsConversation} chargee (version optimiste figee)
     * pendant tout ce temps ; une ecriture concurrente (marquage lu, toggle mode) bumpait le
     * {@code @Version} et le save final echouait sur {@code ObjectOptimisticLockingFailureException},
     * rollbackant TOUT le traitement -> aucune reponse envoyee au prospect. On travaille donc
     * hors TX englobante : chaque ecriture DB commit isolement (auto-commit / TX courtes des
     * beans dedies), aucun verrou long tenu pendant les appels reseau IA.</p>
     */
    public void traiterReponseProspect(final String numeroProspect, final String contenu,
                                        final String originalRepliedMessageSid,
                                        final String to) {
        log.info("Reponse prospect WhatsApp recue - numero: {}, to: {}, contenu: {}",
                PiiMasker.phone(numeroProspect), PiiMasker.phone(to), PiiMasker.content(contenu));

        SmsConversation conversation = null;

        // 1. Priorite absolue : OriginalRepliedMessageSid (reply explicite sur un message)
        if (originalRepliedMessageSid != null && !originalRepliedMessageSid.isBlank()) {
            final var msgCite = smsMessageRepository.findByBrevoMessageId(originalRepliedMessageSid);
            if (msgCite.isPresent()) {
                conversation = smsConversationRepository
                        .findByConversationId(msgCite.get().getConversationId())
                        .filter(c -> c.getStatut() != StatutConversationSmsEnum.STOP_DEMANDE
                                && c.getStatut() != StatutConversationSmsEnum.TERMINEE
                                && c.getStatut() != StatutConversationSmsEnum.ERREUR)
                        .orElse(null);
                if (conversation != null) {
                    log.info("Conversation resolue via OriginalRepliedMessageSid - conversationId: {}",
                            conversation.getConversationId());
                }
            } else {
                log.warn("OriginalRepliedMessageSid recu mais message introuvable - sid: {}", originalRepliedMessageSid);
            }
        }

        // 2. Routage par To (epic 23) : lookup cross-tenant du proprietaire du numero
        if (conversation == null && to != null && !to.isBlank()) {
            conversation = routerParNumeroTo(numeroProspect, to);
        }

        if (conversation == null) {
            log.warn("Pas de conversation WhatsApp active pour ce numero : prospect={}, to={}",
                    PiiMasker.phone(numeroProspect), PiiMasker.phone(to));
            return;
        }

        // Active manuellement le filtre Hibernate avec le tenantId de la conversation
        // (le ThreadLocal n'est pas active dans le contexte webhook).
        TenantContext.setCurrentTenantId(conversation.getTenantId());
        try {
            entityManager.unwrap(org.hibernate.Session.class)
                    .enableFilter("tenantFilter")
                    .setParameter("tenantId", conversation.getTenantId());

        // Sauvegarder le message entrant. Ordre calcule via SELECT MAX(ordre)+1 SQL
        // atomique : reduit la race condition si webhook + envoi manuel sont concurrents.
        // L'historique est charge ensuite pour reconstruire le contexte IA.
        final int ordreEntrant = smsMessageRepository.findProchainOrdre(conversation.getConversationId());
        final List<SmsMessage> historique = smsMessageRepository.findByConversationIdOrderByOrdre(conversation.getConversationId());

        // Persiste le message entrant dans une transaction PROPRE (REQUIRES_NEW, via
        // bean dedie) qui commit immediatement : la donnee est durable des le retour,
        // sans attendre l'analyse + la reponse IA (2-5s) qui restent dans la transaction
        // courante. On notifie l'inbox JUSTE APRES (SSE synchrone, pas via afterCommit)
        // pour que le message du prospect s'affiche des sa reception. Corrige le bug ou
        // l'entrant n'apparaissait qu'au moment de la reponse IA (voire pas du tout en
        // temps reel : un afterCommit enregistre dans la transaction interne ne partait pas).
        final SmsMessage msgEntrantSave =
                inboxEntrantWriter.persister(conversation, contenu, ordreEntrant);

        // Notification inbox immediate : ciblage vendeur titulaire (managers + le VENDEUR
        // de la conversation), pas tous les vendeurs du tenant. L'entrant est deja committe
        // (REQUIRES_NEW ci-dessus) -> notify direct coherent, pas besoin d'afterCommit.
        inboxNotificationService.notifierPourVendeur(
                conversation.getTenantId(), conversation.getUserId(), "nouveau_message",
                Map.of("conversationId", conversation.getConversationId(),
                       "direction", "ENTRANT",
                       "contenu", contenu,
                       "creeLe", LocalDateTime.now().toString()));

        // Reset du compteur de relances : le prospect a repondu, on arrete les relances automatiques
        if (conversation.getNombreRelances() != null && conversation.getNombreRelances() > 0) {
            log.info("Prospect a repondu apres {} relance(s) - reset compteur - conversationId: {}",
                    conversation.getNombreRelances(), conversation.getConversationId());
        }
        conversation.setNombreRelances(0);
        conversation.setProchaineRelanceLe(null);
        conversation.setDerniereRelanceLe(null);
        // save explicite : plus de @Transactional englobante -> le dirty checking ne
        // persiste plus automatiquement ces resets au commit. Sans ce save, un prospect
        // qui repond continuerait de recevoir les relances automatiques programmees.
        smsConversationRepository.save(conversation);

        // RGPD : la reponse spontanee du prospect vaut consentement explicite. On le capture si absent.
        leadRepository.findById(conversation.getLeadId()).ifPresent(leadProspect -> {
            if (!Boolean.TRUE.equals(leadProspect.getConsentementContact())) {
                leadProspect.setConsentementContact(true);
                leadProspect.setConsentementDate(LocalDateTime.now());
                leadProspect.setConsentementSource("whatsapp_reply");
                leadRepository.save(leadProspect);
                log.info("Consentement RGPD capture (reponse WhatsApp) - leadId: {}", leadProspect.getId());
            }
        });

        // (Le message entrant a deja ete persiste ET notifie a l'inbox par
        // inboxEntrantWriter ci-dessus, dans sa propre transaction -> SSE immediat.)

        // RGPD : court-circuit STOP textuel AVANT l'appel IA. Sans ca, l'appel
        // Claude (2-5s) laisse une fenetre pendant laquelle un scheduler de relance
        // peut envoyer un nouveau message a un prospect qui vient de dire STOP.
        // On bascule immediatement le statut, on coupe l'opt-out lead, on annule
        // les rappels RDV planifies puis on return.
        if (contenu != null && estDemandeStop(contenu)) {
            log.info("STOP textuel detecte - court-circuit IA - conversationId: {}", conversation.getConversationId());
            conversation.setStatut(StatutConversationSmsEnum.STOP_DEMANDE);
            smsConversationRepository.save(conversation);
            leadRepository.findById(conversation.getLeadId()).ifPresent(lead -> {
                lead.setOptOutSms(true);
                leadRepository.save(lead);
                rappelRdvService.annulerRappelsNonEnvoyes(lead.getId());
            });
            return;
        }

        // Quota IA journalier : chaque message entrant déclenche 2 appels Claude
        // (extraction + génération). Sans ce garde-fou, un prospect (ou un tiers connaissant
        // le numéro) qui envoie des centaines de messages fait exploser le coût IA. Si le
        // quota est atteint, on NE traite PAS via l'IA : bascule HUMAIN + alerte vendeur,
        // sans throw (sinon le webhook Twilio échoue → retry en boucle).
        try {
            aiQuotaGuard.verifierQuotaJournalier(conversation.getTenantId());
        } catch (final AiQuotaGuard.AiQuotaDepasseException e) {
            log.warn("Quota IA journalier atteint - bascule HUMAIN sans reponse IA - conversationId: {}, tenantId: {}",
                    conversation.getConversationId(), conversation.getTenantId());
            if (conversation.getModePilotage() != ModePilotageEnum.HUMAIN) {
                conversation.setModePilotage(ModePilotageEnum.HUMAIN);
                smsConversationRepository.save(conversation);
                inboxNotificationService.notifierApresCommitPourVendeur(
                        conversation.getTenantId(), conversation.getUserId(), "handoff_humain",
                        Map.of("conversationId", conversation.getConversationId(),
                               "raison", "Quota IA journalier atteint, à traiter manuellement"));
            }
            return;
        }

        // Analyse IA structuree + actions automatiques (statut, opt-out, handoff, RDV).
        // La conversation peut etre fermee (STOP/REFUS) ou basculee en mode HUMAIN (handoff).
        // Filet de securite : l'analyse est un ENRICHISSEMENT, pas un prerequis a la reponse.
        // Si elle echoue (conflit optimiste persistant, extraction IA KO), on log et on
        // CONTINUE : le prospect doit recevoir une reponse quoi qu'il arrive (l'incident prod
        // 2026-07-06 = prospect laisse sans reponse suite a un conflit optimiste dans l'analyse).
        try {
            conversationAnalyseService.analyserEtAgir(conversation, msgEntrantSave);
        } catch (final Exception e) {
            log.error("Analyse IA non appliquee (on repond quand meme au prospect) - conversationId: {}, cause: {}",
                    conversation.getConversationId(), PiiMasker.content(e.getMessage()));
        }

        // Recharger la conversation : son statut/mode peut avoir change suite a l'analyse
        final SmsConversation conversationApresAnalyse = smsConversationRepository
                .findByConversationId(conversation.getConversationId())
                .orElse(conversation);

        // Si la conversation a ete fermee par l'analyse (STOP/REFUS), on s'arrete la
        if (conversationApresAnalyse.getStatut() != StatutConversationSmsEnum.EN_COURS
                && conversationApresAnalyse.getStatut() != StatutConversationSmsEnum.RDV_OBTENU) {
            log.info("Conversation fermee suite a l'analyse - statut: {}", conversationApresAnalyse.getStatut());
            return;
        }

        // Verifier le mode de pilotage (peut avoir bascule en HUMAIN suite a un handoff)
        if (conversationApresAnalyse.getModePilotage() == ModePilotageEnum.HUMAIN) {
            log.info("Conversation en mode HUMAIN - pas de reponse IA - conversationId: {}", conversationApresAnalyse.getConversationId());
            conversationApresAnalyse.setNombreMessages(ordreEntrant + 1);
            conversationApresAnalyse.setDernierMessageLe(LocalDateTime.now());
            smsConversationRepository.save(conversationApresAnalyse);
            return;
        }

        // Garde-fou max-messages : si on a atteint le plafond, on bascule en HUMAIN
        // au lieu de continuer la boucle IA. Protege contre les couts incontroles et les conversations infinies.
        if (ordreEntrant + 1 >= maxMessagesParConversation) {
            log.warn("Plafond de messages atteint ({}/{}) - bascule en HUMAIN - conversationId: {}",
                    ordreEntrant + 1, maxMessagesParConversation, conversationApresAnalyse.getConversationId());
            conversationApresAnalyse.setModePilotage(ModePilotageEnum.HUMAIN);
            conversationApresAnalyse.setNombreMessages(ordreEntrant + 1);
            conversationApresAnalyse.setDernierMessageLe(LocalDateTime.now());
            smsConversationRepository.save(conversationApresAnalyse);
            // Message systeme visible dans la conversation
            insererMessageSysteme(conversationApresAnalyse,
                    "Plafond de " + maxMessagesParConversation + " messages atteint. Un conseiller va prendre le relais.");
            inboxNotificationService.notifierApresCommitPourVendeur(
                    conversationApresAnalyse.getTenantId(), conversationApresAnalyse.getUserId(), "handoff_humain",
                    Map.of("conversationId", conversationApresAnalyse.getConversationId(),
                           "raison", "Plafond de " + maxMessagesParConversation + " messages atteint"));
            return;
        }

        // Construire le contexte IA — system prompt stocke a la creation de la conversation
        String systemPrompt = conversationApresAnalyse.getSystemPrompt();
        if (systemPrompt == null || systemPrompt.isBlank()) {
            // Fallback : regenere si conversation creee avant le refactor
            final Lead lead = leadRepository.findById(conversationApresAnalyse.getLeadId()).orElse(null);
            if (lead == null) return;
            final TenantAiConfig config = tenantAiConfigService.getConfig(conversationApresAnalyse.getTenantId());
            systemPrompt = construireSystemPrompt(lead, config);
        }

        final List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        for (final SmsMessage sms : historique) {
            if (sms.getDirection() == DirectionSmsEnum.SORTANT) {
                messages.add(new AssistantMessage(sms.getContenu()));
            } else {
                messages.add(new UserMessage(sms.getContenu()));
            }
        }
        messages.add(new UserMessage(contenu));

        // Generer la reponse IA via AiResilientCaller (Resilience4j @Retry + @CircuitBreaker).
        // 3 tentatives backoff exponentiel (1s, 2s, 4s) sur erreurs transitoires.
        // Si toutes les tentatives echouent OU si le circuit est ouvert : fallback metier
        // (message d'attente au prospect + bascule HUMAIN + alerte SSE vendeur).
        final ChatClient chatClient = chatClientBuilder.build();
        final String reponseIa;
        try {
            reponseIa = aiResilientCaller.executer(() -> chatClient.prompt().messages(messages)
                    .options(aiModelResolver.getOptions(TypeGenerationEnum.WHATSAPP_CONVERSATION))
                    .call().content());
        } catch (final Exception e) {
            log.error("Appel IA echoue apres retry/circuit breaker - bascule HUMAIN + fallback - conversationId: {}, cause: {}",
                    conversationApresAnalyse.getConversationId(), PiiMasker.content(e.getMessage()));
            envoyerFallbackEtBasculerHumain(conversationApresAnalyse, ordreEntrant);
            return;
        }

        // Filtre de sortie IA : si l'IA mentionne un prix, un taux, ou s'engage commercialement,
        // on ne l'envoie PAS et on bascule en HUMAIN. Le vendeur reprend la main.
        // Conformite IA Act + protection juridique de la concession.
        final List<String> violations = iaOutputFilter.verifier(reponseIa);
        if (!violations.isEmpty()) {
            log.warn("Filtre IA bloque - bascule HUMAIN - conversationId: {}, violations: {}",
                    conversationApresAnalyse.getConversationId(), violations);
            conversationApresAnalyse.setModePilotage(ModePilotageEnum.HUMAIN);
            conversationApresAnalyse.setNombreMessages(ordreEntrant + 1);
            conversationApresAnalyse.setDernierMessageLe(LocalDateTime.now());
            smsConversationRepository.save(conversationApresAnalyse);
            inboxNotificationService.notifierApresCommitPourVendeur(
                    conversationApresAnalyse.getTenantId(), conversationApresAnalyse.getUserId(), "handoff_humain",
                    Map.of("conversationId", conversationApresAnalyse.getConversationId(),
                           "raison", "Message IA bloque par filtre : " + String.join(", ", violations)));
            return;
        }

        // [CORRECTION #13] Detection du tag [ALERTE_RAPPEL_IMMEDIAT] dans la reponse IA.
        // Si present : on le retire du message envoye au prospect et on notifie le vendeur en urgence.
        final boolean alerteRappelImmediat = reponseIa.contains("[ALERTE_RAPPEL_IMMEDIAT]");
        final String reponseNettoyee = reponseIa.replace("[ALERTE_RAPPEL_IMMEDIAT]", "").trim();

        if (alerteRappelImmediat) {
            log.warn("ALERTE RAPPEL IMMEDIAT detectee - conversationId: {}, leadId: {}",
                    conversationApresAnalyse.getConversationId(), conversationApresAnalyse.getLeadId());
            // Enrichissement payload : le bandeau global cross-page (<GlobalAlertesRappelBanner />)
            // n'a pas acces a la liste des conversations cote front, donc le backend pousse
            // nom/marque/modele directement dans l'event pour que le vendeur ait l'info utile
            // dans la notif navigateur + le banner.
            final Lead lead = leadRepository.findById(conversationApresAnalyse.getLeadId()).orElse(null);
            final java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("conversationId", conversationApresAnalyse.getConversationId());
            payload.put("leadId", conversationApresAnalyse.getLeadId());
            payload.put("message", "Le prospect demande a etre rappele IMMEDIATEMENT");
            if (lead != null) {
                payload.put("nomProspect", lead.getNom() != null ? lead.getNom() : "");
                payload.put("marque", lead.getMarque() != null ? lead.getMarque() : "");
                payload.put("modele", lead.getModele() != null ? lead.getModele() : "");
            }
            inboxNotificationService.notifierApresCommitPourVendeur(
                    conversationApresAnalyse.getTenantId(), conversationApresAnalyse.getUserId(), "alerte_rappel_immediat", payload);
        }

        // Envoyer la reponse — pas de controle horaire ici car le prospect vient de repondre
        // (il est eveille et attend une reponse). Le controle horaire ne s'applique qu'au
        // premier contact sortant (template) pour ne pas reveiller les gens.
        StatutSmsEnum statutReponse;
        String twilioSid = null;
        try {
            twilioSid = twilioWhatsAppService.envoyerWhatsApp(conversationApresAnalyse.getNumeroProspect(), reponseNettoyee, conversationApresAnalyse.getTenantId());
            statutReponse = StatutSmsEnum.ENVOYE;
        } catch (final Exception e) {
            statutReponse = StatutSmsEnum.ECHEC;
            log.error("Echec envoi reponse WhatsApp - cause: {}", e.getMessage());
        }

        // IMPORTANT (anti double-envoi) : la reponse est DEJA partie au prospect (action externe
        // irreversible). A partir d'ici, plus AUCUNE ecriture ne doit faire echouer le webhook.
        persisterReponseSortanteBestEffort(conversationApresAnalyse, reponseNettoyee, statutReponse,
                twilioSid, ordreEntrant, contenu);
        log.info("Reponse WhatsApp envoyee - conversationId: {}", conversationApresAnalyse.getConversationId());
        } finally {
            // SECURITE : cleanup obligatoire du filtre Hibernate ET du ThreadLocal.
            // Sans le disableFilter, le filtre reste actif sur la Session et peut polluer
            // les requetes suivantes du meme thread Tomcat (fuite cross-tenant garantie
            // sous charge webhook).
            try {
                entityManager.unwrap(org.hibernate.Session.class).disableFilter("tenantFilter");
            } catch (final Exception e) {
                log.warn("Echec disableFilter en finally - non bloquant: {}", e.getMessage());
            }
            TenantContext.clear();
        }
    }

    /**
     * Routage par {@code To} (epic 23 - multi-numero) : identifie le tenant proprietaire
     * du numero WhatsApp Business qui a recu le message, puis cherche une conversation
     * active pour le prospect dans CE tenant uniquement.
     *
     * <p>Le filtre Hibernate tenantFilter est desactive temporairement parce que le webhook
     * n'a pas de TenantContext. Une fois la conversation trouvee, l'appelant principal
     * reactive le filtre avec le tenantId de la conversation pour la suite du traitement.</p>
     *
     * <p>Securite : verifie que le {@code conversation.tenant_id} correspond bien au
     * {@code config.tenant_id} - protege contre une eventuelle reattribution sale d'un
     * numero entre tenants (cf. tenant_whatsapp_number_history).</p>
     */
    private SmsConversation routerParNumeroTo(final String numeroProspect, final String numeroTo) {
        final var session = entityManager.unwrap(org.hibernate.Session.class);
        session.disableFilter("tenantFilter");

        final var configOpt = tenantWhatsappConfigRepository.findByWhatsappFrom(numeroTo);
        if (configOpt.isEmpty()) {
            log.warn("Routage webhook : aucune config WhatsApp ne possede ce numero - to: {}",
                    PiiMasker.phone(numeroTo));
            return null;
        }
        final Long tenantIdProprietaire = configOpt.get().getTenantId();

        final var conversationOpt = smsConversationRepository
                .findActiveByNumeroProspectAndTenantId(numeroProspect, tenantIdProprietaire);
        if (conversationOpt.isEmpty()) {
            // Detection du cas frequent "prospect a fait STOP puis envoie un nouveau
            // message" : on cherche les conversations FERMEES pour ce numero+tenant.
            // Si on en trouve, on alerte le vendeur via SSE pour qu'il decide quoi faire
            // (typiquement : reactiver le consentement RGPD apres confirmation orale).
            final var fermees = smsConversationRepository
                    .findAllByNumeroProspectAndTenantId(numeroProspect, tenantIdProprietaire);
            if (!fermees.isEmpty()) {
                final SmsConversation derniere = fermees.get(0);
                log.error("Webhook recu sur conversation fermee - prospect: {}, tenant: {}, statut: {}",
                        PiiMasker.phone(numeroProspect), tenantIdProprietaire, derniere.getStatut());
                inboxNotificationService.notifierApresCommit(tenantIdProprietaire, "message_apres_fermeture",
                        java.util.Map.of(
                                "conversationId", derniere.getConversationId(),
                                "leadId", String.valueOf(derniere.getLeadId()),
                                "statut", derniere.getStatut().name(),
                                "contenuApercu", "Le prospect a envoye un nouveau message apres cloture."));
            } else {
                log.warn("Routage webhook : aucune conversation pour ce prospect dans ce tenant - prospect: {}, tenant: {}",
                        PiiMasker.phone(numeroProspect), tenantIdProprietaire);
            }
            return null;
        }

        final SmsConversation conversation = conversationOpt.get();
        // Defense en profondeur : on s'assure que la conversation appartient bien au
        // tenant identifie par le numero To (devrait toujours etre vrai vu la query,
        // mais double-check explicite contre un eventuel bug de routage).
        if (!tenantIdProprietaire.equals(conversation.getTenantId())) {
            log.error("Routage webhook : mismatch tenant_id (anti-fuite) - to: {}, tenant attendu: {}, tenant conversation: {}",
                    PiiMasker.phone(numeroTo), tenantIdProprietaire, conversation.getTenantId());
            return null;
        }

        log.info("Conversation resolue via To (epic 23) - conversationId: {}, tenant: {}",
                conversation.getConversationId(), tenantIdProprietaire);
        return conversation;
    }

    /**
     * Persiste la reponse IA sortante + bookkeeping + notification, en BEST-EFFORT.
     *
     * <p>Appelee APRES l'envoi effectif du message au prospect (action externe irreversible).
     * Toute exception ici (notamment {@code ObjectOptimisticLockingFailureException} si la
     * version de la conversation a bouge pendant la generation IA de 3-8s : marquage lu residuel,
     * toggle mode, relance) est AVALEE en WARN et ne remonte JAMAIS : sinon elle atteindrait le
     * WebhookController -> retrait idempotence -> 500 -> retry Twilio -> le prospect recevrait un
     * 2e message. Le bookkeeping conversation passe par un UPDATE cible qui ne bump pas
     * {@code @Version} (ne peut pas lever de conflit optimiste). Package-private pour le test.</p>
     */
    void persisterReponseSortanteBestEffort(final SmsConversation conversation, final String reponseNettoyee,
                                            final StatutSmsEnum statutReponse, final String twilioSid,
                                            final int ordreEntrant, final String contenuEntrant) {
        try {
            final SmsMessage msgSortant = new SmsMessage();
            msgSortant.setTenantId(conversation.getTenantId());
            msgSortant.setConversationId(conversation.getConversationId());
            msgSortant.setDirection(DirectionSmsEnum.SORTANT);
            msgSortant.setContenu(reponseNettoyee);
            msgSortant.setStatut(statutReponse);
            msgSortant.setBrevoMessageId(twilioSid);
            msgSortant.setExpediteur(ExpediteurMessageEnum.IA);
            msgSortant.setOrdre(ordreEntrant + 1);
            smsMessageRepository.save(msgSortant);

            // Bookkeeping via UPDATE cible : ne bump pas @Version, ne peut pas lever de conflit optimiste.
            smsConversationRepository.updateBookkeepingApresEnvoi(
                    conversation.getConversationId(), ordreEntrant + 2, LocalDateTime.now());

            inboxNotificationService.notifierApresCommitPourVendeur(
                    conversation.getTenantId(), conversation.getUserId(), "nouveau_message",
                    Map.of("conversationId", conversation.getConversationId(),
                           "direction", "SORTANT",
                           "contenu", reponseNettoyee,
                           "expediteur", "IA",
                           "creeLe", LocalDateTime.now().toString()));

            loggerAppel(conversation.getTenantId(), conversation.getLeadId(), contenuEntrant, reponseNettoyee);
        } catch (final Exception e) {
            // Le prospect a deja recu sa reponse : on ne propage pas (sinon retry Twilio = double-envoi).
            log.warn("Echec bookkeeping post-envoi (reponse deja envoyee au prospect, pas de retry) - conversationId: {}, cause: {}",
                    conversation.getConversationId(), PiiMasker.content(e.getMessage()));
        }
    }

    /**
     * Fallback envoye au prospect quand Anthropic est indisponible (529, timeout) apres retry.
     * On envoie un message d'attente, on bascule en HUMAIN et on alerte le vendeur via SSE.
     */
    private void envoyerFallbackEtBasculerHumain(final SmsConversation conv, final int ordreEntrant) {
        final String fallback = "Merci pour votre message ! Un conseiller va vous recontacter tres rapidement.";
        StatutSmsEnum statutFallback;
        String twilioSid = null;
        try {
            twilioSid = twilioWhatsAppService.envoyerWhatsApp(conv.getNumeroProspect(), fallback, conv.getTenantId());
            statutFallback = StatutSmsEnum.ENVOYE;
        } catch (final Exception e) {
            statutFallback = StatutSmsEnum.ECHEC;
            log.error("Echec envoi fallback WhatsApp - cause: {}", e.getMessage());
        }

        // Le fallback est DEJA parti au prospect (action externe irreversible) : a partir d'ici,
        // aucune ecriture ne doit faire echouer le webhook (sinon retry Twilio -> double fallback).
        // On persiste en best-effort et on avale toute erreur (dont un conflit optimiste sur conv)
        // en WARN. Le passage en HUMAIN est important mais pas au point de risquer un double-envoi.
        try {
            final SmsMessage msgFallback = new SmsMessage();
            msgFallback.setTenantId(conv.getTenantId());
            msgFallback.setConversationId(conv.getConversationId());
            msgFallback.setDirection(DirectionSmsEnum.SORTANT);
            msgFallback.setContenu(fallback);
            msgFallback.setStatut(statutFallback);
            msgFallback.setBrevoMessageId(twilioSid);
            msgFallback.setExpediteur(ExpediteurMessageEnum.SYSTEME);
            msgFallback.setOrdre(ordreEntrant + 1);
            smsMessageRepository.save(msgFallback);

            // UPDATE cible du mode + bookkeeping : ne bump pas @Version, pas de conflit optimiste.
            smsConversationRepository.updateModeEtBookkeepingApresEnvoi(
                    conv.getConversationId(), ModePilotageEnum.HUMAIN, ordreEntrant + 2, LocalDateTime.now());

            inboxNotificationService.notifierApresCommitPourVendeur(
                    conv.getTenantId(), conv.getUserId(), "handoff_humain",
                    Map.of("conversationId", conv.getConversationId(),
                           "raison", "Service IA temporairement indisponible - intervention vendeur necessaire"));
        } catch (final Exception e) {
            log.warn("Echec bookkeeping post-fallback (fallback deja envoye, pas de retry) - conversationId: {}, cause: {}",
                    conv.getConversationId(), PiiMasker.content(e.getMessage()));
        }
    }

    @Transactional(readOnly = true)
    public RepSmsConversationDto getConversation(final String conversationId, final Long tenantId) {
        final SmsConversation conv = smsConversationRepository
                .findByConversationIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation non trouvee: " + conversationId));
        final List<SmsMessage> messages = smsMessageRepository.findByConversationIdOrderByOrdre(conversationId);
        return toDto(conv, messages);
    }

    @Transactional(readOnly = true)
    public List<RepSmsConversationDto> getConversationsLead(final Long leadId, final Long tenantId) {
        return smsConversationRepository.findByLeadIdAndTenantIdOrderByCreeLe(leadId, tenantId).stream()
                .map(c -> RepSmsConversationDto.builder()
                        .id(c.getId())
                        .conversationId(c.getConversationId())
                        .statut(c.getStatut().name())
                        .nombreMessages(c.getNombreMessages())
                        .dernierMessageLe(c.getDernierMessageLe())
                        .creeLe(c.getCreeLe())
                        .build())
                .toList();
    }

    /**
     * Job quotidien : envoie les WhatsApp en attente.
     *
     * <p>Fix bug-hunter #13 : @Transactional retire du scheduler. Tenir une TX
     * pendant N appels Twilio (200ms-2s chacun) saturait le pool Hikari pour le
     * trafic HTTP utilisateur, et un fail tardif rollbackait toute la boucle.
     * Chaque item est traite isolement via {@link #traiterMessageEnAttente}.</p>
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "Europe/Paris")
    @SchedulerLock(name = "envoyerMessagesWhatsAppEnAttente", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void envoyerMessagesEnAttente() {
        log.info("Envoi des WhatsApp en attente");
        final List<SmsMessage> enAttente = smsMessageRepository.findByStatut(StatutSmsEnum.EN_ATTENTE);
        for (final SmsMessage sms : enAttente) {
            try {
                traiterMessageEnAttente(sms.getId());
            } catch (final Exception e) {
                log.error("Erreur envoi WhatsApp en attente - smsId: {}, cause: {}",
                        sms.getId(), e.getMessage(), e);
            }
        }
        log.info("WhatsApp en attente traites - nombre: {}", enAttente.size());
    }

    /**
     * Traite un message en attente isolement (TX dediee).
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void traiterMessageEnAttente(final Long smsId) {
        final SmsMessage sms = smsMessageRepository.findById(smsId).orElse(null);
        if (sms == null || sms.getStatut() != StatutSmsEnum.EN_ATTENTE) return;
        final SmsConversation conv = smsConversationRepository.findByConversationId(sms.getConversationId()).orElse(null);
        if (conv == null || conv.getStatut() != StatutConversationSmsEnum.EN_COURS) return;
        try {
            final String twilioSid = twilioWhatsAppService.envoyerWhatsApp(conv.getNumeroProspect(), sms.getContenu(), conv.getTenantId());
            sms.setStatut(StatutSmsEnum.ENVOYE);
            sms.setBrevoMessageId(twilioSid);
        } catch (final Exception e) {
            sms.setStatut(StatutSmsEnum.ECHEC);
            sms.setErreur(e.getMessage());
        }
        smsMessageRepository.save(sms);
    }

    /**
     * Capitalise chaque mot d'un nom ("martin karim" → "Martin Karim").
     * Gere aussi les noms composes avec tirets ("jean-pierre" → "Jean-Pierre").
     * Retourne "Bonjour" en fallback si le nom est vide.
     * Package-private pour permettre les tests unitaires.
     */
    String capitalize(final String nom) {
        if (nom == null || nom.isBlank()) return "Bonjour";
        return WordUtils.capitalizeFully(nom.trim(), ' ', '-');
    }

    private void insererMessageSysteme(final SmsConversation conversation, final String contenu) {
        // Ordre via SELECT MAX(ordre)+1 atomique (anti race condition).
        final int ordre = smsMessageRepository.findProchainOrdre(conversation.getConversationId());
        final SmsMessage msg = new SmsMessage();
        msg.setTenantId(conversation.getTenantId());
        msg.setConversationId(conversation.getConversationId());
        msg.setDirection(DirectionSmsEnum.SORTANT);
        msg.setContenu(contenu);
        msg.setStatut(StatutSmsEnum.RECU);
        msg.setExpediteur(ExpediteurMessageEnum.SYSTEME);
        msg.setOrdre(ordre);
        smsMessageRepository.save(msg);
    }

    private void loggerAppel(final Long tenantId, final Long leadId, final String prompt, final String reponse) {
        final AiLog aiLog = new AiLog();
        aiLog.setTenantId(tenantId);
        aiLog.setLeadId(leadId);
        aiLog.setTypeGeneration(TypeGenerationEnum.WHATSAPP_CONVERSATION);
        aiLog.setPrompt(prompt.length() > 500 ? prompt.substring(0, 500) + "..." : prompt);
        aiLog.setReponse(reponse);
        final String modele = aiModelResolver.getModelName(TypeGenerationEnum.WHATSAPP_CONVERSATION);
        aiLog.setModele(modele);
        aiLog.setStatut(AiStatutEnum.SUCCES);
        aiLog.setCoutEstime(aiCoutService.calculerCout(modele, prompt.length() / 4, reponse.length() / 4));
        aiLogRepository.save(aiLog);
    }

    /**
     * Detecte une demande explicite de STOP / desabonnement / opt-out cote prospect.
     * Court-circuit IA : si l'utilisateur envoie "STOP", "ARRETE", "DESABONNE",
     * "UNSUBSCRIBE" (avec ou sans accents, eventuellement entoure de ponctuation),
     * on bascule immediatement sans payer l'appel Claude.
     */
    private boolean estDemandeStop(final String contenu) {
        final String normalise = java.text.Normalizer.normalize(contenu.trim().toUpperCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[^A-Z ]", " ")
                .trim();
        if (normalise.isEmpty()) return false;
        // Mots-cles "purs" (le message ne contient que le mot, eventuellement avec espaces)
        return normalise.equals("STOP")
                || normalise.equals("ARRETE")
                || normalise.equals("ARRETER")
                || normalise.equals("ARRETEZ")
                || normalise.equals("UNSUBSCRIBE")
                || normalise.equals("DESABONNE")
                || normalise.equals("DESABONNER")
                || normalise.equals("DESABONNEMENT")
                || normalise.equals("DESINSCRIPTION")
                || normalise.equals("NE PLUS M ECRIRE")
                || normalise.equals("STOP SMS")
                || normalise.equals("STOP MESSAGES");
    }

    private RepSmsConversationDto toDto(final SmsConversation conv, final List<SmsMessage> messages) {
        return RepSmsConversationDto.builder()
                .id(conv.getId())
                .conversationId(conv.getConversationId())
                .statut(conv.getStatut().name())
                .numeroProspect(conv.getNumeroProspect())
                .nombreMessages(conv.getNombreMessages())
                .dernierMessageLe(conv.getDernierMessageLe())
                .creeLe(conv.getCreeLe())
                .messages(messages.stream().map(m -> RepSmsMessageDto.builder()
                        .id(m.getId())
                        .direction(m.getDirection().name())
                        .expediteur(m.getExpediteur() != null ? m.getExpediteur().name() : null)
                        .contenu(m.getContenu())
                        .statut(m.getStatut().name())
                        .motifEchec(m.getStatut() == StatutSmsEnum.ECHEC
                                ? fr.leadrevive.outreach.util.WhatsAppErreurMessage.lisible(m.getErreur())
                                : null)
                        .ordre(m.getOrdre())
                        .creeLe(m.getCreeLe())
                        .build()).toList())
                .build();
    }
}
