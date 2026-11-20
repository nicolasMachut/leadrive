package fr.leadrevive.ai.service;

import fr.leadrevive.ai.dto.RepFichePreparationDto;
import fr.leadrevive.ai.entity.AiLog;
import fr.leadrevive.ai.entity.AiStatutEnum;
import fr.leadrevive.ai.entity.TenantAiConfig;
import fr.leadrevive.ai.entity.TypeGenerationEnum;
import fr.leadrevive.ai.repository.AiLogRepository;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.exception.LeadNotFoundException;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.outreach.entity.ConversationAnalyse;
import fr.leadrevive.outreach.entity.DirectionSmsEnum;
import fr.leadrevive.outreach.entity.SmsConversation;
import fr.leadrevive.outreach.entity.SmsMessage;
import fr.leadrevive.outreach.repository.ConversationAnalyseRepository;
import fr.leadrevive.outreach.repository.SmsConversationRepository;
import fr.leadrevive.outreach.repository.SmsMessageRepository;
import fr.leadrevive.tenant.repository.TenantRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FichePreparationService {

    private final ChatClient.Builder chatClientBuilder;
    private final AiModelResolver aiModelResolver;
    private final AiResilientCaller aiResilientCaller;
    private final LeadRepository leadRepository;
    private final AiLogRepository aiLogRepository;
    private final TenantAiConfigService tenantAiConfigService;
    private final AiCoutService aiCoutService;
    private final ConversationAnalyseRepository conversationAnalyseRepository;
    private final SmsConversationRepository smsConversationRepository;
    private final SmsMessageRepository smsMessageRepository;
    private final AiQuotaGuard aiQuotaGuard;
    private final TenantRepository tenantRepository;

    private String promptTemplate;

    @PostConstruct
    void chargerPromptTemplate() {
        try {
            final ClassPathResource resource = new ClassPathResource("prompts/fiche-preparation.txt");
            promptTemplate = resource.getContentAsString(StandardCharsets.UTF_8);
            log.info("Prompt template fiche-preparation charge - taille: {} caracteres", promptTemplate.length());
        } catch (final IOException e) {
            throw new IllegalStateException("Impossible de charger le prompt template fiche-preparation.txt", e);
        }
    }

    /**
     * Retourne la fiche cachee si elle existe et que les donnees n'ont pas change.
     * Retourne null si pas de cache ou si les donnees ont change (le front doit appeler genererFiche).
     */
    /**
     * Retourne la fiche cachee + un flag indiquant si elle est perimee.
     * - fiche jamais generee → null
     * - fiche a jour → cached=true, stale=false
     * - fiche perimee (donnees ont change) → cached=true, stale=true (le front affiche l'ancienne + regenere)
     */
    @Transactional(readOnly = true)
    public RepFichePreparationDto getFicheSiExiste(final Long leadId, final Long tenantId) {
        final Lead lead = leadRepository.findByIdAndTenantId(leadId, tenantId)
                .orElseThrow(() -> new LeadNotFoundException(leadId));

        if (lead.getFichePreparation() == null) {
            return null; // Jamais generee
        }

        final String hashActuel = calculerHash(lead);
        final boolean stale = !hashActuel.equals(lead.getFicheHash());

        log.info("Fiche preparation cache - leadId: {}, stale: {}", leadId, stale);
        return RepFichePreparationDto.builder()
                .leadId(leadId)
                .fiche(lead.getFichePreparation())
                .cached(true)
                .stale(stale)
                .build();
    }

    @Transactional
    @Retry(name = "aiCall")
    @CircuitBreaker(name = "aiCall", fallbackMethod = "fichePreparationFallback")
    public RepFichePreparationDto genererFiche(final Long leadId, final Long tenantId) {
        log.info("Generation fiche de preparation - leadId: {}, tenantId: {}", leadId, tenantId);

        // Quota IA : bloque les usages massifs qui dériveraient le budget Anthropic.
        aiQuotaGuard.verifierQuotaJournalier(tenantId);

        final Lead lead = leadRepository.findByIdAndTenantId(leadId, tenantId)
                .orElseThrow(() -> new LeadNotFoundException(leadId));

        // Protection tokens : 2 niveaux de cache.
        // 1. Hash identique : on retourne la fiche existante (cas nominal).
        // 2. Anti-thrashing : si la fiche a moins de 5 minutes, on la garde meme si un
        //    message vient d'arriver (sinon : 10 vendeurs qui ouvrent la fiche d'un meme
        //    lead juste apres un message du prospect declenchent 10 regenerations IA).
        if (lead.getFichePreparation() != null && lead.getFicheHash() != null) {
            final String hashActuel = calculerHash(lead);
            final boolean hashIdentique = hashActuel.equals(lead.getFicheHash());
            final boolean ficheRecente = lead.getFicheGenereeLe() != null
                    && lead.getFicheGenereeLe().isAfter(java.time.LocalDateTime.now().minusMinutes(5));
            if (hashIdentique || ficheRecente) {
                log.info("Fiche fraiche reutilisee - leadId: {}, hashIdentique: {}, ficheRecente: {}",
                        leadId, hashIdentique, ficheRecente);
                return RepFichePreparationDto.builder()
                        .leadId(leadId)
                        .fiche(lead.getFichePreparation())
                        .cached(true)
                        .build();
            }
        }

        final TenantAiConfig config = tenantAiConfigService.getConfig(tenantId);
        final String prompt = construirePrompt(lead, config);

        final long debut = System.currentTimeMillis();
        final AiLog aiLog = new AiLog();
        aiLog.setTenantId(tenantId);
        aiLog.setLeadId(leadId);
        aiLog.setTypeGeneration(TypeGenerationEnum.FICHE_PREPARATION);
        aiLog.setPrompt(prompt.length() > 2000 ? prompt.substring(0, 2000) + "..." : prompt);
        final AnthropicChatOptions aiOptions = aiModelResolver.getOptions(TypeGenerationEnum.FICHE_PREPARATION);
        aiLog.setModele(aiOptions.getModel());

        try {
            final ChatClient chatClient = chatClientBuilder.build();

            final org.springframework.ai.chat.model.ChatResponse chatResponse = aiResilientCaller.executer(() ->
                    chatClient.prompt()
                            .user(prompt)
                            .options(aiOptions)
                            .call()
                            .chatResponse());

            final String reponse = chatResponse.getResult().getOutput().getText();
            final long duree = System.currentTimeMillis() - debut;

            final Integer tokensInput = chatResponse.getMetadata().getUsage() != null
                    ? (int) chatResponse.getMetadata().getUsage().getPromptTokens() : null;
            final Integer tokensOutput = chatResponse.getMetadata().getUsage() != null
                    ? (int) chatResponse.getMetadata().getUsage().getCompletionTokens() : null;

            final BigDecimal cout = aiCoutService.calculerCout(aiOptions.getModel(), tokensInput, tokensOutput);

            aiLog.setReponse(reponse);
            aiLog.setTokensPrompt(tokensInput);
            aiLog.setTokensReponse(tokensOutput);
            aiLog.setCoutEstime(cout);
            aiLog.setDureeMs(duree);
            aiLog.setStatut(AiStatutEnum.SUCCES);
            aiLogRepository.save(aiLog);

            // Persister le cache sur le lead via UPDATE cible : ne bump PAS le @Version.
            // La TX tient le lead pendant l'appel IA (~10s) ; un save(lead) classique entrait
            // en conflit optimiste avec toute mutation concurrente du lead (webhook prospect,
            // drag&drop Kanban) -> fiche perdue. L'UPDATE cible ne touche que le cache fiche.
            final LocalDateTime genereeLe = LocalDateTime.now();
            final String hash = calculerHash(lead);
            leadRepository.updateCacheFiche(leadId, reponse, genereeLe, hash);

            log.info("Fiche generee et cachee - leadId: {}, tokens: {}/{}, cout: {}, duree: {}ms",
                    leadId, tokensInput, tokensOutput, cout, duree);

            return RepFichePreparationDto.builder()
                    .idAiLog(aiLog.getId())
                    .leadId(leadId)
                    .fiche(reponse)
                    .tokensPrompt(tokensInput)
                    .tokensReponse(tokensOutput)
                    .coutEstime(cout)
                    .dureeMs(duree)
                    .cached(false)
                    .build();

        } catch (final Exception e) {
            final long duree = System.currentTimeMillis() - debut;
            aiLog.setDureeMs(duree);
            aiLog.setStatut(duree > 30000 ? AiStatutEnum.TIMEOUT : AiStatutEnum.ERREUR);
            aiLog.setErreur(e.getMessage());
            aiLogRepository.save(aiLog);
            log.error("Erreur generation fiche - leadId: {}, cause: {}", leadId, e.getMessage());
            throw e;
        }
    }

    private RepFichePreparationDto fichePreparationFallback(final Long leadId, final Long tenantId, final Throwable t) {
        log.warn("Fallback fiche preparation - leadId: {}, cause: {}", leadId, t.getMessage());
        return RepFichePreparationDto.builder()
                .leadId(leadId)
                .fiche("Le service de generation IA est temporairement indisponible. Veuillez reessayer dans quelques instants.")
                .build();
    }

    /**
     * Hash SHA-256 des donnees qui influencent la fiche. Si ce hash change,
     * la fiche est perimee et doit etre regeneree.
     */
    private String calculerHash(final Lead lead) {
        final ContexteConversation contexte = chargerContexteConversation(lead);
        final String donnees = String.join("|",
                str(lead.getNom()), str(lead.getMarque()), str(lead.getModele()),
                str(lead.getStatut() != null ? lead.getStatut().name() : null),
                str(lead.getSource()), str(lead.getReprise()),
                contexte.transcript(), contexte.resume(),
                contexte.budget(), contexte.delaiProjet(),
                contexte.demandeEssai(), contexte.raisonContact());
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(donnees.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponible", e);
        }
    }

    private String str(final Object value) {
        return value != null ? value.toString() : "";
    }

    private String construirePrompt(final Lead lead, final TenantAiConfig config) {
        final String nonRenseigne = "Non renseigne";

        final String nomConcession = resoudreNomConcession(config, lead.getTenantId());
        final String horairesOuverture = config != null && config.getHorairesOuverture() != null ? config.getHorairesOuverture() : nonRenseigne;
        final ContexteConversation contexte = chargerContexteConversation(lead);

        return promptTemplate
                .replace("{NOM_CONCESSION}", nomConcession)
                .replace("{HORAIRES}", horairesOuverture)
                .replace("{NOM_PROSPECT}", lead.getNom() != null ? lead.getNom() : nonRenseigne)
                .replace("{MARQUE_VEHICULE}", lead.getMarque() != null ? lead.getMarque() : nonRenseigne)
                .replace("{MODELE_VEHICULE}", lead.getModele() != null ? lead.getModele() : nonRenseigne)
                .replace("{SOURCE}", lead.getSource() != null ? lead.getSource() : nonRenseigne)
                .replace("{CRENEAU_RAPPEL}", contexte.creneauRappel())
                .replace("{CONSEILLER}", nonRenseigne)
                .replace("{DELAI_PROJET}", contexte.delaiProjet())
                .replace("{MODELE}", lead.getModele() != null ? lead.getModele() : nonRenseigne)
                .replace("{REPRISE}", lead.getReprise() != null && lead.getReprise() ? "Oui" : "Non")
                .replace("{REPRISE_CONDITIONNE}", contexte.repriseConditionneAchat())
                .replace("{BUDGET}", contexte.budget())
                .replace("{APPORT}", contexte.apport())
                .replace("{MODE_FINANCEMENT}", contexte.modeFinancement())
                .replace("{ACCORD_BANQUE}", contexte.accordBanque())
                .replace("{SIGNAUX}", contexte.signaux())
                .replace("{OBJECTIONS}", contexte.objections())
                .replace("{TRANSCRIPT}", contexte.transcript())
                .replace("{RESUME}", contexte.resume())
                .replace("{TON_PROSPECT}", contexte.tonProspect())
                .replace("{QUESTION_EN_ATTENTE}", contexte.questionEnAttente())
                .replace("{DEMANDE_ESSAI}", contexte.demandeEssai())
                .replace("{RAISON_CONTACT}", contexte.raisonContact())
                .replace("{DONNEES_SUPPLEMENTAIRES}",
                        fr.leadrevive.common.util.DonneesSupplementairesFormatter.formater(lead.getDonneesSupplementaires()));
    }

    private String resoudreNomConcession(final TenantAiConfig config, final Long tenantId) {
        if (config != null && config.getNomConcession() != null && !config.getNomConcession().isBlank()) {
            return config.getNomConcession();
        }
        return tenantRepository.findById(tenantId)
                .map(t -> t.getNom())
                .orElse("la concession");
    }

    private ContexteConversation chargerContexteConversation(final Lead lead) {
        final String aucun = "Aucune information collectee a date";
        final String aucuneConv = "Aucune conversation WhatsApp pour ce lead.";

        final List<SmsConversation> conversations = smsConversationRepository
                .findByLeadIdAndTenantIdOrderByCreeLe(lead.getId(), lead.getTenantId());
        if (conversations.isEmpty()) {
            return new ContexteConversation(aucun, aucun, aucun, aucun, aucun, aucun, aucun,
                    "Aucun signal detecte (pas de conversation).",
                    "Aucune objection detectee.",
                    aucuneConv,
                    "Pas encore d'analyse IA.",
                    "Non detecte",
                    "Aucune question en attente",
                    aucun,
                    aucun);
        }

        final SmsConversation derniereConv = conversations.get(conversations.size() - 1);

        final ConversationAnalyse analyse = conversationAnalyseRepository
                .findDerniereByTenantIdAndConversationId(lead.getTenantId(), derniereConv.getConversationId())
                .orElse(null);

        final List<SmsMessage> messages = smsMessageRepository
                .findByConversationIdOrderByOrdre(derniereConv.getConversationId());
        final String transcript = messages.isEmpty() ? aucuneConv :
                messages.stream()
                        .skip(Math.max(0, messages.size() - 10))
                        .map(m -> (m.getDirection() == DirectionSmsEnum.SORTANT ? "[ASSISTANT] " : "[PROSPECT] ") + m.getContenu())
                        .collect(Collectors.joining("\n"));

        if (analyse == null) {
            return new ContexteConversation(aucun, aucun, aucun, aucun, aucun, aucun, aucun,
                    "Aucun signal encore detecte.",
                    "Aucune objection encore detectee.",
                    transcript,
                    "Le prospect n'a pas encore repondu.",
                    "Non detecte",
                    "Aucune question en attente",
                    aucun,
                    aucun);
        }

        return new ContexteConversation(
                analyse.getRdvCreneau() != null ? analyse.getRdvCreneau() : aucun,
                analyse.getDelaiProjet() != null ? analyse.getDelaiProjet() : aucun,
                analyse.getRepriseConditionneAchat() == null ? aucun
                        : (analyse.getRepriseConditionneAchat() ? "Oui" : "Non"),
                analyse.getMontantBudget() != null ? analyse.getMontantBudget() : aucun,
                analyse.getApportPrevu() != null ? analyse.getApportPrevu() : aucun,
                analyse.getModeFinancement() != null ? analyse.getModeFinancement() : aucun,
                analyse.getAccordBanque() != null ? analyse.getAccordBanque() : aucun,
                formatListe(analyse.getSignauxAchat(), "Aucun signal detecte."),
                formatListe(analyse.getObjections(), "Aucune objection detectee."),
                transcript,
                analyse.getResumeVendeur() != null ? analyse.getResumeVendeur() : "Pas de resume disponible.",
                analyse.getTonProspect() != null ? analyse.getTonProspect() : "Non detecte",
                analyse.getQuestionEnAttente() != null ? analyse.getQuestionEnAttente() : "Aucune question en attente",
                analyse.getDemandeEssai() != null && analyse.getDemandeEssai() ? "true" : "false",
                analyse.getRaisonContact() != null ? analyse.getRaisonContact() : aucun
        );
    }

    private String formatListe(final List<String> elements, final String fallback) {
        if (elements == null || elements.isEmpty()) return fallback;
        return elements.stream().map(s -> "- " + s).collect(Collectors.joining("\n"));
    }

    private record ContexteConversation(
            String creneauRappel,
            String delaiProjet,
            String repriseConditionneAchat,
            String budget,
            String apport,
            String modeFinancement,
            String accordBanque,
            String signaux,
            String objections,
            String transcript,
            String resume,
            String tonProspect,
            String questionEnAttente,
            String demandeEssai,
            String raisonContact
    ) {}
}
