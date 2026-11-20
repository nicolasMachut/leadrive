package fr.leadrevive.outreach.service;

import fr.leadrevive.ai.entity.AiLog;
import fr.leadrevive.ai.entity.AiStatutEnum;
import fr.leadrevive.ai.entity.TypeGenerationEnum;
import fr.leadrevive.ai.repository.AiLogRepository;
import fr.leadrevive.ai.service.AiCoutService;
import fr.leadrevive.ai.service.AiModelResolver;
import fr.leadrevive.lead.entity.HistoriqueStatut;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.entity.LeadStatutEnum;
import fr.leadrevive.lead.repository.HistoriqueStatutRepository;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.tache.service.TacheIAService;
import fr.leadrevive.outreach.dto.ExtractionAnalyseDto;
import fr.leadrevive.outreach.dto.RepConversationAnalyseDto;
import fr.leadrevive.outreach.entity.ConversationAnalyse;
import fr.leadrevive.outreach.entity.DirectionSmsEnum;
import fr.leadrevive.outreach.entity.ExpediteurMessageEnum;
import fr.leadrevive.outreach.entity.IntentionEnum;
import fr.leadrevive.outreach.entity.StatutSmsEnum;
import fr.leadrevive.outreach.entity.ModePilotageEnum;
import fr.leadrevive.outreach.entity.SmsConversation;
import fr.leadrevive.outreach.entity.SmsMessage;
import fr.leadrevive.outreach.entity.StatutConversationSmsEnum;
import fr.leadrevive.outreach.repository.ConversationAnalyseRepository;
import fr.leadrevive.outreach.repository.SmsConversationRepository;
import fr.leadrevive.outreach.repository.SmsMessageRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service d'analyse IA des messages entrants WhatsApp.
 * Apres chaque reponse du prospect, extrait l'intention + signaux + objections en JSON structure,
 * puis declenche les actions automatiques :
 * - update statut de la conversation (RDV_OBTENU, STOP_DEMANDE, PROSPECT_REFUSE)
 * - update statut du lead et opt-out si STOP/REFUS
 * - bascule en mode HUMAIN si handoff necessaire
 * - notification SSE temps reel vers l'inbox
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationAnalyseService {

    private final ChatClient.Builder chatClientBuilder;
    private final AiModelResolver aiModelResolver;
    private final fr.leadrevive.ai.service.AiResilientCaller aiResilientCaller;
    private final ConversationAnalyseRepository conversationAnalyseRepository;
    private final SmsConversationRepository smsConversationRepository;
    private final SmsMessageRepository smsMessageRepository;
    private final LeadRepository leadRepository;
    private final AiLogRepository aiLogRepository;
    private final AiCoutService aiCoutService;
    private final InboxNotificationService inboxNotificationService;
    private final TacheIAService tacheIAService;
    private final RappelRdvService rappelRdvService;
    private final HistoriqueStatutRepository historiqueStatutRepository;
    private final fr.leadrevive.ai.service.BriefingService briefingService;
    // Transaction programmatique (REQUIRES_NEW) pour la transaction COURTE d'application de
    // l'analyse. On utilise un TransactionTemplate plutot qu'une self-injection @Transactional :
    // pas de self-reference -> AUCUN risque de dependance circulaire au demarrage (incident prod
    // 2026-07-06 : self-injection @Lazy non propagee au jar -> cycle inboxController -> ... ->
    // conversationAnalyseService -> self -> APPLICATION FAILED TO START), et pas de dependance au
    // proxy AOP (le retry ouvre une vraie nouvelle TX a chaque tentative).
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

    private String promptTemplate;
    private org.springframework.transaction.support.TransactionTemplate txTemplateRequiresNew;

    @PostConstruct
    void chargerPrompt() {
        this.txTemplateRequiresNew = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        this.txTemplateRequiresNew.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            promptTemplate = new ClassPathResource("prompts/whatsapp-extraction.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
            log.info("Prompt extraction WhatsApp charge - taille: {} caracteres", promptTemplate.length());
        } catch (final IOException e) {
            throw new IllegalStateException("Impossible de charger le prompt d'extraction", e);
        }
    }

    /** Nombre de tentatives d'application des actions en cas de conflit optimiste concurrent. */
    private static final int MAX_TENTATIVES_OPTIMISTIC_LOCK = 3;

    /**
     * Analyse le dernier message entrant d'une conversation et persiste le resultat.
     * Declenche ensuite les actions automatiques associees a l'intention detectee.
     *
     * <p><strong>IMPORTANT (perf + robustesse)</strong> : cette methode n'est
     * VOLONTAIREMENT PAS {@code @Transactional}. L'appel IA Anthropic ({@link #extraireAnalyse})
     * dure ~10s et ne doit JAMAIS s'executer dans une transaction tenant une entite
     * {@code SmsConversation} chargee : pendant ces 10s, une ecriture concurrente (ex.
     * marquage lu par un manager qui ouvre l'inbox) modifie la ligne et fait echouer le
     * {@code save} final sur {@code ObjectOptimisticLockingFailureException} -> le prospect
     * ne recevait plus jamais de reponse (incident prod 2026-07-06). On isole donc l'appel
     * IA (hors TX), puis on applique les actions dans une transaction COURTE qui RECHARGE la
     * conversation fraiche depuis la DB, avec retry sur conflit optimiste.</p>
     *
     * @param conversation la conversation concernee (deja persistee, utilisee en lecture seule ici)
     * @param messageEntrant le message entrant qu'on analyse
     */
    public RepConversationAnalyseDto analyserEtAgir(final SmsConversation conversation, final SmsMessage messageEntrant) {
        log.info("Analyse IA conversation - conversationId: {}, messageId: {}",
                conversation.getConversationId(), messageEntrant.getId());

        // 1. Appel IA HORS transaction (~10s) : aucune entite optimistic-locked tenue.
        final ExtractionAnalyseDto extraction = extraireAnalyse(conversation, messageEntrant);
        if (extraction == null) {
            log.warn("Extraction IA echouee - conversationId: {}", conversation.getConversationId());
            return null;
        }

        // 2. Application des actions en transaction COURTE, avec rechargement de la
        //    conversation fraiche + retry sur conflit optimiste concurrent.
        return appliquerAnalyseAvecRetry(conversation.getConversationId(), messageEntrant, extraction);
    }

    /**
     * Applique les actions de l'analyse dans une transaction courte, en rechargeant la
     * conversation fraiche a chaque tentative. Retry borne sur
     * {@link org.springframework.orm.ObjectOptimisticLockingFailureException} : si une
     * ecriture concurrente (marquage lu, toggle mode, relance) a bump le {@code @Version}
     * entre le rechargement et le commit, on rejoue avec l'etat le plus recent.
     */
    // package-private pour permettre le test unitaire du retry sur conflit optimiste
    RepConversationAnalyseDto appliquerAnalyseAvecRetry(final String conversationId,
                                                        final SmsMessage messageEntrant,
                                                        final ExtractionAnalyseDto extraction) {
        org.springframework.orm.ObjectOptimisticLockingFailureException derniereErreur = null;
        for (int tentative = 1; tentative <= MAX_TENTATIVES_OPTIMISTIC_LOCK; tentative++) {
            try {
                // Transaction COURTE dediee (REQUIRES_NEW) via TransactionTemplate : le conflit
                // optimiste est leve au commit, donc DANS execute(), et remonte ici pour le retry.
                return txTemplateRequiresNew.execute(status ->
                        appliquerAnalyse(conversationId, messageEntrant, extraction));
            } catch (final org.springframework.orm.ObjectOptimisticLockingFailureException e) {
                derniereErreur = e;
                log.warn("Conflit optimiste sur application analyse - tentative {}/{} - conversationId: {}",
                        tentative, MAX_TENTATIVES_OPTIMISTIC_LOCK, conversationId);
            }
        }
        log.error("Echec application analyse apres {} tentatives (conflit optimiste persistant) - conversationId: {}, cause: {}",
                MAX_TENTATIVES_OPTIMISTIC_LOCK, conversationId,
                derniereErreur != null ? derniereErreur.getMessage() : "inconnue");
        // On propage : l'appelant (traiterReponseProspect) doit savoir que l'analyse n'a pas
        // ete appliquee pour ne pas construire une reponse IA sur un etat incoherent, mais il
        // continue neanmoins a repondre au prospect (l'analyse est un enrichissement, pas un
        // prerequis a la reponse).
        throw derniereErreur;
    }

    /**
     * Recharge la conversation fraiche, persiste l'analyse et applique les actions automatiques.
     * Toujours appelee DANS la transaction courte ouverte par {@link #appliquerAnalyseAvecRetry}
     * (TransactionTemplate REQUIRES_NEW) : pas d'annotation {@code @Transactional} ici, donc pas
     * de self-invocation via proxy, donc pas de self-reference, donc pas de dependance circulaire.
     */
    // package-private pour permettre au test de retry de la stubber (spy)
    RepConversationAnalyseDto appliquerAnalyse(final String conversationId,
                                                      final SmsMessage messageEntrant,
                                                      final ExtractionAnalyseDto extraction) {
        final SmsConversation conversation = smsConversationRepository
                .findByConversationId(conversationId)
                .orElse(null);
        if (conversation == null) {
            log.warn("Conversation introuvable au moment d'appliquer l'analyse - conversationId: {}", conversationId);
            return null;
        }

        final ConversationAnalyse analyse = persisterAnalyse(conversation, messageEntrant, extraction);
        appliquerActions(conversation, analyse);
        notifierFront(conversation, analyse);

        return toDto(analyse);
    }

    private ExtractionAnalyseDto extraireAnalyse(final SmsConversation conversation, final SmsMessage messageEntrant) {
        final long debut = System.currentTimeMillis();

        // Construire l'historique recent (10 derniers messages max)
        final List<SmsMessage> historique = smsMessageRepository
                .findByConversationIdOrderByOrdre(conversation.getConversationId());
        final String historiqueStr = historique.stream()
                .skip(Math.max(0, historique.size() - 10))
                .map(m -> (m.getDirection() == DirectionSmsEnum.SORTANT ? "[ASSISTANT] " : "[PROSPECT] ") + m.getContenu())
                .collect(Collectors.joining("\n"));

        // Contexte temporel + RDV en cours : indispensable pour resoudre correctement
        // les expressions relatives ("vers 15h" = aujourd'hui si posterieur a maintenant,
        // ou meme jour que le RDV en cours), et pour distinguer un RE-PLANNING d'un RDV
        // deja acte (→ changement confirme) d'une negociation neuve.
        final java.time.LocalDateTime maintenant = java.time.LocalDateTime.now();
        final java.time.LocalDateTime rdvEnCours = tacheIAService.dateRdvEnCours(conversation.getLeadId());
        final String rdvExistantStr = rdvEnCours != null
                ? rdvEnCours.format(java.time.format.DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy 'a' HH:mm", java.util.Locale.FRENCH))
                    + " (ISO " + rdvEnCours.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")) + ")"
                : "aucun RDV en cours";

        final String prompt = promptTemplate
                .replace("{HISTORIQUE}", historiqueStr)
                .replace("{MESSAGE}", messageEntrant.getContenu())
                .replace("{DATE_JOUR}", maintenant.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .replace("{HEURE_JOUR}", maintenant.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
                .replace("{RDV_EXISTANT}", rdvExistantStr);

        final AnthropicChatOptions aiOptions = aiModelResolver.getOptions(TypeGenerationEnum.WHATSAPP_EXTRACTION);
        final AiLog aiLog = new AiLog();
        aiLog.setTenantId(conversation.getTenantId());
        aiLog.setLeadId(conversation.getLeadId());
        aiLog.setTypeGeneration(TypeGenerationEnum.WHATSAPP_EXTRACTION);
        aiLog.setPrompt(prompt.length() > 2000 ? prompt.substring(0, 2000) + "..." : prompt);
        aiLog.setModele(aiOptions.getModel());

        try {
            final ChatClient chatClient = chatClientBuilder.build();
            final BeanOutputConverter<ExtractionAnalyseDto> converter = new BeanOutputConverter<>(ExtractionAnalyseDto.class);

            final org.springframework.ai.chat.model.ChatResponse chatResponse = aiResilientCaller.executer(() ->
                    chatClient.prompt()
                            .user(prompt + "\n\n" + converter.getFormat())
                            .options(aiOptions)
                            .call()
                            .chatResponse());

            final String reponse = chatResponse.getResult().getOutput().getText();
            final ExtractionAnalyseDto extraction = converter.convert(reponse);

            final long duree = System.currentTimeMillis() - debut;
            final Integer tokensInput = chatResponse.getMetadata().getUsage() != null
                    ? (int) chatResponse.getMetadata().getUsage().getPromptTokens() : null;
            final Integer tokensOutput = chatResponse.getMetadata().getUsage() != null
                    ? (int) chatResponse.getMetadata().getUsage().getCompletionTokens() : null;

            aiLog.setReponse(reponse);
            aiLog.setTokensPrompt(tokensInput);
            aiLog.setTokensReponse(tokensOutput);
            aiLog.setCoutEstime(aiCoutService.calculerCout(aiOptions.getModel(), tokensInput, tokensOutput));
            aiLog.setDureeMs(duree);
            aiLog.setStatut(AiStatutEnum.SUCCES);
            aiLogRepository.save(aiLog);

            log.info("Extraction IA reussie - intention: {}, handoff: {}, duree: {}ms",
                    extraction != null ? extraction.getIntention() : "null",
                    extraction != null ? extraction.getBesoinHandoff() : "null",
                    duree);
            return extraction;

        } catch (final Exception e) {
            aiLog.setStatut(AiStatutEnum.ERREUR);
            aiLog.setErreur(e.getMessage() != null && e.getMessage().length() > 500
                    ? e.getMessage().substring(0, 500) : e.getMessage());
            aiLog.setDureeMs(System.currentTimeMillis() - debut);
            aiLogRepository.save(aiLog);
            log.error("Echec extraction IA - cause: {}", e.getMessage());
            return null;
        }
    }

    private ConversationAnalyse persisterAnalyse(final SmsConversation conversation,
                                                  final SmsMessage messageEntrant,
                                                  final ExtractionAnalyseDto extraction) {
        final ConversationAnalyse analyse = new ConversationAnalyse();
        analyse.setTenantId(conversation.getTenantId());
        analyse.setConversationId(conversation.getConversationId());
        analyse.setSmsMessageId(messageEntrant.getId());
        analyse.setIntention(parseIntention(extraction.getIntention()));
        analyse.setSignauxAchat(extraction.getSignauxAchat());
        analyse.setObjections(extraction.getObjections());
        analyse.setBesoinHandoff(Boolean.TRUE.equals(extraction.getBesoinHandoff()));
        analyse.setRaisonHandoff(extraction.getRaisonHandoff());
        analyse.setResumeVendeur(extraction.getResumeVendeur());
        analyse.setTonProspect(extraction.getTonProspect());
        analyse.setQuestionEnAttente(extraction.getQuestionEnAttente());
        analyse.setRdvPropose(Boolean.TRUE.equals(extraction.getRdvPropose()));
        analyse.setRdvConfirme(Boolean.TRUE.equals(extraction.getRdvConfirme()));
        analyse.setRdvCreneau(extraction.getRdvCreneau());
        if (extraction.getRdvDateHeure() != null && !extraction.getRdvDateHeure().isBlank()) {
            try {
                analyse.setRdvDateHeure(java.time.LocalDateTime.parse(extraction.getRdvDateHeure()));
            } catch (final java.time.format.DateTimeParseException e) {
                log.warn("Format rdvDateHeure invalide ignore - valeur: {}", extraction.getRdvDateHeure());
            }
        }
        analyse.setDelaiProjet(extraction.getDelaiProjet());
        analyse.setMontantBudget(extraction.getMontantBudget());
        analyse.setApportPrevu(extraction.getApportPrevu());
        analyse.setModeFinancement(extraction.getModeFinancement());
        analyse.setAccordBanque(extraction.getAccordBanque());
        analyse.setRepriseConditionneAchat(extraction.getRepriseConditionneAchat());
        analyse.setDemandeEssai(extraction.getDemandeEssai());
        analyse.setRaisonContact(extraction.getRaisonContact());
        return conversationAnalyseRepository.save(analyse);
    }

    private IntentionEnum parseIntention(final String value) {
        if (value == null || value.isBlank()) return IntentionEnum.AUTRE;
        try {
            return IntentionEnum.valueOf(value.trim().toUpperCase());
        } catch (final IllegalArgumentException e) {
            return IntentionEnum.AUTRE;
        }
    }

    /**
     * Applique les actions automatiques en fonction de l'intention detectee.
     *
     * Pipeline lead.statut : NOUVEAU -> CONTACTE -> EN_COURS -> RDV_PRIS -> TERMINE
     * - intention=RDV   : lead -> RDV_PRIS (sauf si deja TERMINE), conversation -> RDV_OBTENU
     * - intention=REFUS : lead -> TERMINE, conversation -> PROSPECT_REFUSE
     * - intention=STOP  : lead -> TERMINE + optOutSms, conversation -> STOP_DEMANDE
     * - intention=AUTRE : si lead etait CONTACTE, il passe a EN_COURS (le prospect a repondu)
     * - handoff         : bascule mode_pilotage en HUMAIN
     * - hotness_score   : recalcule a chaque analyse (cf appliquerHotness)
     */
    private void appliquerActions(final SmsConversation conversation, final ConversationAnalyse analyse) {
        boolean conversationModifiee = false;

        switch (analyse.getIntention()) {
            case STOP -> {
                conversation.setStatut(StatutConversationSmsEnum.STOP_DEMANDE);
                conversationModifiee = true;
                leadRepository.findById(conversation.getLeadId()).ifPresent(lead -> {
                    lead.setOptOutSms(true);
                    final LeadStatutEnum ancien = lead.getStatut();
                    if (ancien != LeadStatutEnum.TERMINE) {
                        lead.setStatut(LeadStatutEnum.TERMINE);
                        tracerChangementStatut(lead, ancien, LeadStatutEnum.TERMINE);
                    }
                    appliquerHotness(lead, analyse);
                    leadRepository.save(lead);
                    rappelRdvService.annulerRappelsNonEnvoyes(lead.getId());
                    // Si un RDV avait ete pris precedemment, l'annuler du planning vendeur.
                    tacheIAService.annulerRdvSiExiste(lead.getId(), "Prospect a demande STOP");
                    log.info("Lead STOP - opt-out + statut TERMINE - leadId: {}", lead.getId());
                });
            }
            case REFUS -> {
                conversation.setStatut(StatutConversationSmsEnum.PROSPECT_REFUSE);
                conversationModifiee = true;
                leadRepository.findById(conversation.getLeadId()).ifPresent(lead -> {
                    final LeadStatutEnum ancien = lead.getStatut();
                    if (ancien != LeadStatutEnum.TERMINE) {
                        lead.setStatut(LeadStatutEnum.TERMINE);
                        tracerChangementStatut(lead, ancien, LeadStatutEnum.TERMINE);
                    }
                    appliquerHotness(lead, analyse);
                    leadRepository.save(lead);
                    // Cas critique : prospect avait accepte un RDV puis change d'avis.
                    // Sans cette annulation, le RDV reste dans le planning du vendeur et
                    // declenche des rappels WhatsApp ("Vous avez rendez-vous demain" alors
                    // que le prospect a deja dit non).
                    tacheIAService.annulerRdvSiExiste(lead.getId(), "Prospect a change d'avis (refus)");
                    rappelRdvService.annulerRappelsNonEnvoyes(lead.getId());
                    log.info("Lead REFUS - statut TERMINE - leadId: {}", lead.getId());
                });
            }
            case RDV -> {
                // Validation des dates RDV : refuse les dates dans le passé (l'IA peut
                // mal calculer relative time) ou trop lointaines (>180j = probable
                // hallucination IA sur une formulation vague).
                final java.time.LocalDateTime rdvDate = analyse.getRdvDateHeure();
                final boolean rdvDatePasse = rdvDate != null && rdvDate.isBefore(LocalDateTime.now().minusMinutes(5));
                final boolean rdvDateTropLoin = rdvDate != null && rdvDate.isAfter(LocalDateTime.now().plusDays(180));
                if (rdvDatePasse || rdvDateTropLoin) {
                    log.warn("RDV detecte avec date suspecte (passe ou >180j) - ignore - leadId: {}, date: {}",
                            conversation.getLeadId(), rdvDate);
                    leadRepository.findById(conversation.getLeadId()).ifPresent(lead -> {
                        appliquerHotness(lead, analyse);
                        leadRepository.save(lead);
                    });
                    break;
                }
                if (Boolean.TRUE.equals(analyse.getRdvConfirme())) {
                    // Garde-fou : rdvConfirme=true SANS rdvDateHeure = incoherence IA.
                    // On ne marque pas RDV_OBTENU sinon le lead est dit "RDV pris" mais
                    // sans tache dans le planning = vendeur perdu.
                    if (rdvDate == null) {
                        log.warn("rdvConfirme=true mais rdvDateHeure null - incoherence IA - leadId: {}",
                                conversation.getLeadId());
                        leadRepository.findById(conversation.getLeadId()).ifPresent(lead -> {
                            appliquerHotness(lead, analyse);
                            leadRepository.save(lead);
                        });
                        break;
                    }
                    // RDV explicitement confirme par le prospect → creer le RDV
                    if (conversation.getRdvProposeLe() == null) {
                        conversation.setRdvProposeLe(LocalDateTime.now());
                    }
                    conversation.setStatut(StatutConversationSmsEnum.RDV_OBTENU);
                    conversationModifiee = true;
                    leadRepository.findById(conversation.getLeadId()).ifPresent(lead -> {
                        final LeadStatutEnum ancien = lead.getStatut();
                        // On accepte la remontee TERMINE -> RDV_PRIS : si le prospect a
                        // refuse puis change d'avis, le lead doit refleter le nouveau RDV.
                        if (ancien != LeadStatutEnum.RDV_PRIS) {
                            lead.setStatut(LeadStatutEnum.RDV_PRIS);
                            tracerChangementStatut(lead, ancien, LeadStatutEnum.RDV_PRIS);
                        }
                        appliquerHotness(lead, analyse);
                        leadRepository.save(lead);
                        // Creer une tache RDV dans le calendrier du vendeur + planifier les rappels WhatsApp
                        final fr.leadrevive.tache.entity.Tache tacheRdv = tacheIAService.onRdvDetecte(
                                lead.getId(), lead.getTenantId(), rdvDate, conversation.getUserId());
                        if (tacheRdv != null) {
                            rappelRdvService.planifierRappels(tacheRdv, lead, conversation);
                        }
                        log.info("Lead RDV confirme - leadId: {}", lead.getId());
                    });
                } else {
                    // RDV propose mais pas encore confirme → ne pas creer le RDV, juste mettre a jour le hotness
                    log.info("RDV propose mais non confirme - conversationId: {}", conversation.getConversationId());
                    leadRepository.findById(conversation.getLeadId()).ifPresent(lead -> {
                        appliquerHotness(lead, analyse);
                        leadRepository.save(lead);
                    });
                }
            }
            case AUTRE -> {
                leadRepository.findById(conversation.getLeadId()).ifPresent(lead -> {
                    if (lead.getStatut() == LeadStatutEnum.CONTACTE) {
                        tracerChangementStatut(lead, LeadStatutEnum.CONTACTE, LeadStatutEnum.EN_COURS);
                        lead.setStatut(LeadStatutEnum.EN_COURS);
                        log.info("Lead passe de CONTACTE a EN_COURS - leadId: {}", lead.getId());
                    }
                    appliquerHotness(lead, analyse);
                    leadRepository.save(lead);
                });
            }
        }

        if (Boolean.TRUE.equals(analyse.getBesoinHandoff()) && conversation.getModePilotage() != ModePilotageEnum.HUMAIN) {
            conversation.setModePilotage(ModePilotageEnum.HUMAIN);
            conversationModifiee = true;
            // Message systeme visible dans la conversation pour que le vendeur comprenne pourquoi
            insererMessageSysteme(conversation,
                    "L'IA a passe la main" + (analyse.getRaisonHandoff() != null
                            ? " : " + analyse.getRaisonHandoff()
                            : ". La conversation necessite une intervention humaine."));
            log.info("Conversation basculee en mode HUMAIN - conversationId: {}, raison: {}",
                    conversation.getConversationId(), analyse.getRaisonHandoff());
            inboxNotificationService.notifierApresCommitPourVendeur(
                    conversation.getTenantId(), conversation.getUserId(), "handoff_humain",
                    Map.of("conversationId", conversation.getConversationId(),
                            "raison", analyse.getRaisonHandoff() != null ? analyse.getRaisonHandoff() : "Intervention humaine necessaire"));
        }

        // Flag client irrite : prospect mecontent + handoff = lead sensible. Affiche un badge rouge dans l'inbox.
        if (Boolean.TRUE.equals(analyse.getBesoinHandoff()) && "mecontent".equalsIgnoreCase(analyse.getTonProspect())) {
            leadRepository.findById(conversation.getLeadId()).ifPresent(lead -> {
                if (!Boolean.TRUE.equals(lead.getClientIrrite())) {
                    lead.setClientIrrite(true);
                    leadRepository.save(lead);
                    log.warn("Lead marque client irrite - leadId: {}, raison: {}",
                            lead.getId(), analyse.getRaisonHandoff());
                }
            });
        }

        if (conversationModifiee) {
            smsConversationRepository.save(conversation);
        }
    }

    /**
     * Signaux d'achat consideres comme "forts" (boost +0.10 chacun).
     */
    private static final Set<String> SIGNAUX_FORTS = Set.of(
            "budget_mentionne", "urgence_forte", "financement_ok", "essai_demande");

    /**
     * Calcule et applique le hotness_score sur le lead.
     * Formule :
     * - base = scoring CSV (si present, sinon 0.5)
     * - +0.30 si intention RDV confirme
     * - +0.10 si intention RDV propose mais non confirme
     * - +0.10 par signal "fort"
     * - +0.05 par signal "faible"
     * - -0.10 par objection
     * - -0.50 si STOP / REFUS (probabilite d'achat ~ nulle)
     * - +0.20 si besoinHandoff (le prospect est "actif" et necessite intervention humaine)
     * - clamp [0, 1]
     */
    // package-private pour permettre les tests unitaires
    void appliquerHotness(final fr.leadrevive.lead.entity.Lead lead, final ConversationAnalyse analyse) {
        BigDecimal score = lead.getScoring() != null ? lead.getScoring() : new BigDecimal("0.50");

        switch (analyse.getIntention()) {
            case RDV -> {
                if (Boolean.TRUE.equals(analyse.getRdvConfirme())) {
                    score = score.add(new BigDecimal("0.30"));
                } else {
                    score = score.add(new BigDecimal("0.10"));
                }
            }
            case STOP, REFUS -> score = score.subtract(new BigDecimal("0.50"));
            case AUTRE -> { /* pas d'impact direct */ }
        }

        if (analyse.getSignauxAchat() != null) {
            for (final String signal : analyse.getSignauxAchat()) {
                final BigDecimal boost = SIGNAUX_FORTS.contains(signal) ? new BigDecimal("0.10") : new BigDecimal("0.05");
                score = score.add(boost);
            }
        }

        if (analyse.getObjections() != null) {
            score = score.subtract(new BigDecimal("0.10").multiply(BigDecimal.valueOf(analyse.getObjections().size())));
        }

        if (Boolean.TRUE.equals(analyse.getBesoinHandoff())) {
            score = score.add(new BigDecimal("0.20"));
        }

        // Clamp [0, 1]
        if (score.compareTo(BigDecimal.ZERO) < 0) score = BigDecimal.ZERO;
        if (score.compareTo(BigDecimal.ONE) > 0) score = BigDecimal.ONE;
        score = score.setScale(2, RoundingMode.HALF_UP);

        lead.setHotnessScore(score);
        lead.setHotnessCalculeLe(LocalDateTime.now());
        log.info("Hotness recalcule - leadId: {}, score: {}, intention: {}",
                lead.getId(), score, analyse.getIntention());
    }

    /**
     * Insere un message systeme visible dans la conversation (pas envoye sur WhatsApp).
     * Sert a informer le vendeur d'un evenement automatique (handoff, max-messages, etc.).
     */
    private void insererMessageSysteme(final SmsConversation conversation, final String contenu) {
        // Ordre via SELECT MAX(ordre)+1 atomique (minimise la race condition avec
        // un autre INSERT concurrent sur la meme conversation).
        final int ordre = smsMessageRepository.findProchainOrdre(conversation.getConversationId());

        final SmsMessage msgSysteme = new SmsMessage();
        msgSysteme.setTenantId(conversation.getTenantId());
        msgSysteme.setConversationId(conversation.getConversationId());
        msgSysteme.setDirection(DirectionSmsEnum.SORTANT);
        msgSysteme.setContenu(contenu);
        msgSysteme.setStatut(StatutSmsEnum.RECU);
        msgSysteme.setExpediteur(ExpediteurMessageEnum.SYSTEME);
        msgSysteme.setOrdre(ordre);
        smsMessageRepository.save(msgSysteme);

        inboxNotificationService.notifierApresCommitPourVendeur(
                conversation.getTenantId(), conversation.getUserId(), "nouveau_message",
                Map.of("conversationId", conversation.getConversationId(),
                       "direction", "SYSTEME",
                       "contenu", contenu,
                       "creeLe", java.time.LocalDateTime.now().toString()));
    }

    private void notifierFront(final SmsConversation conversation, final ConversationAnalyse analyse) {
        inboxNotificationService.notifierApresCommitPourVendeur(
                conversation.getTenantId(), conversation.getUserId(), "analyse_mise_a_jour",
                Map.of(
                        "conversationId", conversation.getConversationId(),
                        "intention", analyse.getIntention().name(),
                        "besoinHandoff", analyse.getBesoinHandoff(),
                        "rdvPropose", analyse.getRdvPropose()
                ));
    }

    private void tracerChangementStatut(final Lead lead, final LeadStatutEnum ancien, final LeadStatutEnum nouveau) {
        if (ancien == nouveau) return;
        final HistoriqueStatut h = new HistoriqueStatut();
        h.setLeadId(lead.getId());
        h.setTenantId(lead.getTenantId());
        h.setStatutPrecedent(ancien);
        h.setStatutSuivant(nouveau);
        h.setSource("IA");
        historiqueStatutRepository.save(h);
        // Invalider le briefing du jour — les données ont changé
        try {
            briefingService.invaliderBriefingDuJour(lead.getTenantId());
        } catch (final Exception e) {
            log.warn("Echec invalidation briefing - tenantId: {}, cause: {}", lead.getTenantId(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public RepConversationAnalyseDto getDerniereAnalyse(final Long tenantId, final String conversationId) {
        final Optional<ConversationAnalyse> opt = conversationAnalyseRepository
                .findDerniereByTenantIdAndConversationId(tenantId, conversationId);
        if (opt.isEmpty()) {
            return null;
        }
        // Source de verite de l'agenda = la tache RDV en cours du lead (mutable), pas
        // l'extraction IA figee. Apres un report, seule la tache est a jour : on expose
        // sa date pour eviter d'afficher une date perimee "Passe" dans le panneau.
        final LocalDateTime rdvTacheDateHeure = smsConversationRepository
                .findByConversationIdAndTenantId(conversationId, tenantId)
                .map(conversation -> tacheIAService.dateRdvEnCours(conversation.getLeadId()))
                .orElse(null);
        return toDto(opt.get(), rdvTacheDateHeure);
    }

    private RepConversationAnalyseDto toDto(final ConversationAnalyse a) {
        return toDto(a, null);
    }

    private RepConversationAnalyseDto toDto(final ConversationAnalyse a, final LocalDateTime rdvTacheDateHeure) {
        return RepConversationAnalyseDto.builder()
                .id(a.getId())
                .conversationId(a.getConversationId())
                .intention(a.getIntention().name())
                .signauxAchat(a.getSignauxAchat())
                .objections(a.getObjections())
                .besoinHandoff(a.getBesoinHandoff())
                .raisonHandoff(a.getRaisonHandoff())
                .resumeVendeur(a.getResumeVendeur())
                .tonProspect(a.getTonProspect())
                .questionEnAttente(a.getQuestionEnAttente())
                .rdvPropose(a.getRdvPropose())
                .rdvConfirme(a.getRdvConfirme())
                .rdvCreneau(a.getRdvCreneau())
                .rdvDateHeure(a.getRdvDateHeure())
                .rdvTacheDateHeure(rdvTacheDateHeure)
                .delaiProjet(a.getDelaiProjet())
                .montantBudget(a.getMontantBudget())
                .apportPrevu(a.getApportPrevu())
                .modeFinancement(a.getModeFinancement())
                .accordBanque(a.getAccordBanque())
                .repriseConditionneAchat(a.getRepriseConditionneAchat())
                .creeLe(a.getCreeLe())
                .build();
    }
}
