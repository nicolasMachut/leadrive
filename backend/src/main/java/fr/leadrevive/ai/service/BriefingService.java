package fr.leadrevive.ai.service;

import fr.leadrevive.ai.dto.RepBriefingDto;
import fr.leadrevive.ai.entity.AiLog;
import fr.leadrevive.ai.entity.AiStatutEnum;
import fr.leadrevive.ai.entity.BriefingQuotidien;
import fr.leadrevive.ai.entity.TypeGenerationEnum;
import fr.leadrevive.ai.repository.AiLogRepository;
import fr.leadrevive.ai.repository.BriefingQuotidienRepository;
import fr.leadrevive.feedback.service.FeedbackRdvService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BriefingService {

    private final ChatClient.Builder chatClientBuilder;
    private final AiModelResolver aiModelResolver;
    private final AiResilientCaller aiResilientCaller;
    private final BriefingQuotidienRepository briefingRepository;
    private final BriefingDataService briefingDataService;
    private final AiLogRepository aiLogRepository;
    private final AiCoutService aiCoutService;
    private final FeedbackRdvService feedbackRdvService;

    private String managerTemplate;
    private String vendeurTemplate;

    @PostConstruct
    void chargerPrompts() {
        try {
            managerTemplate = new ClassPathResource("prompts/briefing-manager.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
            vendeurTemplate = new ClassPathResource("prompts/briefing-vendeur.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
            log.info("Prompts briefing charges");
        } catch (final IOException e) {
            throw new IllegalStateException("Impossible de charger les prompts briefing", e);
        }
    }

    @Transactional
    public RepBriefingDto getBriefingDuJour(final Long tenantId, final Long userId) {
        log.info("Recuperation briefing du jour - tenantId: {}, userId: {}", tenantId, userId);
        final LocalDate aujourdHui = LocalDate.now();

        final BriefingQuotidien existant;
        if (userId != null) {
            existant = briefingRepository.findByTenantIdAndDateBriefingAndUserId(tenantId, aujourdHui, userId).orElse(null);
        } else {
            existant = briefingRepository.findByTenantIdAndDateBriefingAndUserIdIsNull(tenantId, aujourdHui).orElse(null);
        }

        if (existant != null) {
            return toDto(existant);
        }

        // Generate on the fly
        return generer(tenantId, userId);
    }

    @Transactional
    public RepBriefingDto generer(final Long tenantId, final Long userId) {
        log.info("Generation briefing - tenantId: {}, userId: {}", tenantId, userId);

        final BriefingDataService.BriefingData data = briefingDataService.collecterDonnees(tenantId, userId);

        final String prompt;
        if (userId != null) {
            // Vendeur briefing
            final String prospectsList = data.prospectsPrioritaires().stream()
                    .map(p -> "- " + p.nom() + " (" + p.marque() + " " + p.modele() + ") : " + p.raison())
                    .collect(Collectors.joining("\n"));
            prompt = vendeurTemplate
                    .replace("{NOM_CONCESSION}", data.nomConcession())
                    .replace("{PRENOM_VENDEUR}", data.prenomManager())
                    .replace("{CONVOS_HIER}", String.valueOf(data.conversationsLanceesHier()))
                    .replace("{RDV_HIER}", String.valueOf(data.rdvObtenuSHier()))
                    .replace("{MESSAGES_RECUS_HIER}", String.valueOf(data.messagesRecusHier()))
                    .replace("{PROSPECTS_PRIORITAIRES}", prospectsList)
                    .replace("{RDV_SEMAINE}", String.valueOf(data.totalRdvSemaine()))
                    .replace("{TAUX_CONVERSION_RDV}", String.valueOf(data.tauxConversionRdv()))
                    .replace("{MARQUE_TOP}", data.marqueTopSemaine() != null ? data.marqueTopSemaine() : "N/A")
                    .replace("{LEADS_SEMAINE}", String.valueOf(data.nombreLeadsSemaine()));
        } else {
            // Manager briefing
            final String vendeursList = data.topVendeurs().stream()
                    .map(v -> "- " + v.prenom() + " : " + v.rdvObtenus() + " RDV, " + v.conversationsLancees() + " conversations")
                    .collect(Collectors.joining("\n"));
            final String prospectsList = data.prospectsPrioritaires().stream()
                    .map(p -> "- " + p.nom() + " (" + p.marque() + " " + p.modele() + ") : " + p.raison())
                    .collect(Collectors.joining("\n"));
            prompt = managerTemplate
                    .replace("{NOM_CONCESSION}", data.nomConcession())
                    .replace("{PRENOM_MANAGER}", data.prenomManager())
                    .replace("{LEADS_IMPORTES_HIER}", String.valueOf(data.leadsImportesHier()))
                    .replace("{CONVOS_HIER}", String.valueOf(data.conversationsLanceesHier()))
                    .replace("{RDV_HIER}", String.valueOf(data.rdvObtenuSHier()))
                    .replace("{VENTES_HIER}", String.valueOf(data.ventesConfirmeesHier()))
                    .replace("{MESSAGES_RECUS_HIER}", String.valueOf(data.messagesRecusHier()))
                    .replace("{MESSAGES_NON_REPONDUS}", String.valueOf(data.messagesNonRepondus()))
                    .replace("{CONVOS_SEMAINE}", String.valueOf(data.totalConversationsSemaine()))
                    .replace("{RDV_SEMAINE}", String.valueOf(data.totalRdvSemaine()))
                    .replace("{VENTES_SEMAINE}", String.valueOf(data.totalVentesSemaine()))
                    .replace("{CA_SEMAINE}", data.caGenereSemaine().toPlainString())
                    .replace("{LEADS_NON_CONTACTES}", String.valueOf(data.leadsNonContactes()))
                    .replace("{TAUX_REPONSE}", String.valueOf(data.tauxReponseWhatsApp()))
                    .replace("{TAUX_CONVERSION_RDV}", String.valueOf(data.tauxConversionRdv()))
                    .replace("{MARQUE_TOP}", data.marqueTopSemaine() != null ? data.marqueTopSemaine() : "N/A")
                    .replace("{LEADS_MARQUE_TOP}", String.valueOf(data.nombreLeadsMarqueTop()))
                    .replace("{TERRITOIRE_TOP}", data.territoireTopSemaine() != null ? data.territoireTopSemaine() : "N/A")
                    .replace("{VENDEURS_LIST}", vendeursList)
                    .replace("{PROSPECTS_PRIORITAIRES}", prospectsList);
        }

        final AnthropicChatOptions aiOptions = aiModelResolver.getOptions(TypeGenerationEnum.BRIEFING_QUOTIDIEN);
        final ChatClient chatClient = chatClientBuilder.build();
        final String promptFinal = prompt;
        final String contenuIa;
        try {
            contenuIa = aiResilientCaller.executer(() ->
                    chatClient.prompt().user(promptFinal).options(aiOptions).call().content());
        } catch (final Exception e) {
            // Si Anthropic est indisponible apres retry/circuit-breaker, on log et on retourne
            // un briefing degrade SANS le sauvegarder en base (sinon le prochain fetch retourne
            // ce briefing partiel). Le frontend peut afficher un message + bouton "Reessayer".
            log.warn("Generation briefing IA indisponible - tenantId: {}, cause: {}",
                    tenantId, e.getMessage());
            return RepBriefingDto.builder()
                    .id(null)
                    .dateBriefing(LocalDate.now())
                    .contenu(null)
                    .tokensPrompt(0)
                    .tokensReponse(0)
                    .coutEstime(java.math.BigDecimal.ZERO)
                    .creeLe(LocalDateTime.now())
                    .erreurGeneration("Le briefing IA est temporairement indisponible. Réessayez dans un instant.")
                    .build();
        }

        // Section bonus : insights des feedbacks vendeurs (si presents)
        final String insightsFeedback = feedbackRdvService.genererInsightsBriefing(tenantId);
        final String contenu = (contenuIa != null && insightsFeedback != null)
                ? contenuIa + "\n\n[FEEDBACK_INSIGHTS]\n" + insightsFeedback
                : contenuIa;

        // Save
        final BriefingQuotidien briefing = new BriefingQuotidien();
        briefing.setTenantId(tenantId);
        briefing.setUserId(userId);
        briefing.setDateBriefing(LocalDate.now());
        briefing.setContenu(contenu);
        briefing.setDonneesContexte(prompt.length() > 2000 ? prompt.substring(0, 2000) : prompt);
        briefing.setTokensPrompt(prompt.length() / 4);
        briefing.setTokensReponse(contenu != null ? contenu.length() / 4 : 0);
        briefing.setCoutEstime(aiCoutService.calculerCout(aiOptions.getModel(), prompt.length() / 4, contenu != null ? contenu.length() / 4 : 0));
        final BriefingQuotidien saved = briefingRepository.save(briefing);

        // Log AI call
        final AiLog aiLog = new AiLog();
        aiLog.setTenantId(tenantId);
        aiLog.setTypeGeneration(TypeGenerationEnum.BRIEFING_QUOTIDIEN);
        aiLog.setPrompt(prompt.length() > 500 ? prompt.substring(0, 500) + "..." : prompt);
        aiLog.setReponse(contenu);
        aiLog.setModele(aiOptions.getModel());
        aiLog.setStatut(AiStatutEnum.SUCCES);
        aiLog.setCoutEstime(saved.getCoutEstime());
        aiLogRepository.save(aiLog);

        log.info("Briefing genere - tenantId: {}, taille: {} chars", tenantId, contenu != null ? contenu.length() : 0);
        return toDto(saved);
    }

    /**
     * Invalide le cache du briefing du jour pour tout le tenant.
     */
    @Transactional
    public void invaliderBriefingDuJour(final Long tenantId) {
        log.info("Invalidation briefing du jour - tenantId: {}", tenantId);
        briefingRepository.deleteByTenantIdAndDateBriefing(tenantId, LocalDate.now());
    }

    @Transactional
    public RepBriefingDto regenerer(final Long tenantId, final Long userId) {
        log.info("Regeneration briefing - tenantId: {}", tenantId);
        if (userId != null) {
            briefingRepository.deleteByTenantIdAndDateBriefingAndUserId(tenantId, LocalDate.now(), userId);
        } else {
            briefingRepository.deleteByTenantIdAndDateBriefingAndUserIdIsNull(tenantId, LocalDate.now());
        }
        return generer(tenantId, userId);
    }

    private RepBriefingDto toDto(final BriefingQuotidien briefing) {
        return RepBriefingDto.builder()
                .id(briefing.getId())
                .dateBriefing(briefing.getDateBriefing())
                .contenu(briefing.getContenu())
                .tokensPrompt(briefing.getTokensPrompt())
                .tokensReponse(briefing.getTokensReponse())
                .coutEstime(briefing.getCoutEstime())
                .creeLe(briefing.getCreeLe())
                .build();
    }
}
