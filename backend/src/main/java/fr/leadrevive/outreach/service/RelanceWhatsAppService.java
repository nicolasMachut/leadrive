package fr.leadrevive.outreach.service;

import fr.leadrevive.ai.entity.TenantAiConfig;
import fr.leadrevive.ai.service.TenantAiConfigService;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.outreach.entity.DirectionSmsEnum;
import fr.leadrevive.outreach.entity.ExpediteurMessageEnum;
import fr.leadrevive.outreach.entity.SmsConversation;
import fr.leadrevive.outreach.entity.SmsMessage;
import fr.leadrevive.outreach.entity.StatutSmsEnum;
import fr.leadrevive.outreach.entity.TenantWhatsappTemplate;
import fr.leadrevive.outreach.repository.SmsConversationRepository;
import fr.leadrevive.outreach.repository.SmsMessageRepository;
import fr.leadrevive.outreach.repository.TenantWhatsappTemplateRepository;
import fr.leadrevive.tache.service.TacheIAService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service de relance automatique WhatsApp.
 * Envoie des templates de relance aux prospects qui n'ont pas repondu
 * au premier message (J+1) puis a la premiere relance (J+3).
 * Apres le max de relances, cree une tache manuelle pour le vendeur.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RelanceWhatsAppService {

    private final SmsConversationRepository smsConversationRepository;
    private final SmsMessageRepository smsMessageRepository;
    private final TwilioWhatsAppService twilioWhatsAppService;
    private final TenantWhatsappTemplateRepository tenantWhatsappTemplateRepository;
    private final InboxNotificationService inboxNotificationService;
    private final TacheIAService tacheIAService;
    private final LeadRepository leadRepository;
    private final TenantAiConfigService tenantAiConfigService;

    @Value("${leadrevive.relance.enabled:true}")
    private boolean relanceEnabled;

    @Value("${leadrevive.relance.max-relances:2}")
    private int maxRelances;

    @Value("${leadrevive.relance.delai-j1-heures:24}")
    private int delaiJ1Heures;

    @Value("${leadrevive.relance.delai-j3-heures:72}")
    private int delaiJ3Heures;

    /**
     * Ticks d'envoi des relances en heures de bureau : 9h30, 12h00, 14h30 et 17h00
     * (Europe/Paris), sauf le dimanche. Deux crons car les minutes different (30 vs 00)
     * selon l'heure.
     *
     * <p>Verifier plusieurs fois par jour (au lieu d'un seul tick a 9h30) reduit fortement
     * le decalage : la relance J+1 est due a "heure du 1er message + 24h". Avec un unique
     * tick a 9h30, un 1er message envoye a 9h40 attendait le surlendemain 9h30 (~48h) ;
     * avec ces creneaux il part des J+1 12h00 (~26h).</p>
     */
    @Scheduled(cron = "${leadrevive.relance.cron-matin-aprem:0 30 9,14 * * *}", zone = "Europe/Paris")
    @SchedulerLock(name = "envoyerRelancesWhatsApp", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    public void tickRelancesMatinApresMidi() {
        envoyerRelances();
    }

    @Scheduled(cron = "${leadrevive.relance.cron-midi-soir:0 0 12,17 * * *}", zone = "Europe/Paris")
    @SchedulerLock(name = "envoyerRelancesWhatsApp", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    public void tickRelancesMidiSoir() {
        envoyerRelances();
    }

    /** Corps commun : envoie toutes les relances eligibles (appele par les ticks planifies). */
    public void envoyerRelances() {
        if (!relanceEnabled) {
            log.debug("Relances desactivees - aucune action");
            return;
        }

        // Pas de relance le dimanche
        if (LocalDateTime.now().getDayOfWeek() == DayOfWeek.SUNDAY) {
            log.debug("Dimanche - relances reportees");
            return;
        }

        final LocalDateTime maintenant = LocalDateTime.now();
        final List<SmsConversation> eligibles = smsConversationRepository.findConversationsEligiblesRelance(maintenant);

        if (eligibles.isEmpty()) {
            log.debug("Aucune conversation eligible pour relance");
            return;
        }

        log.info("Relances WhatsApp - {} conversations eligibles", eligibles.size());
        int envoyees = 0;
        int erreurs = 0;

        for (final SmsConversation conversation : eligibles) {
            try {
                traiterRelance(conversation);
                envoyees++;
            } catch (final Exception e) {
                erreurs++;
                log.error("Echec relance - conversationId: {}, cause: {}", conversation.getConversationId(), e.getMessage());
            }
        }

        log.info("Relances terminees - envoyees: {}, erreurs: {}", envoyees, erreurs);
    }

    /**
     * Traite la relance pour une conversation donnee.
     * Envoie le template adapte (J+1 ou J+3) ou cree une tache si max atteint.
     *
     * <p>@Transactional REQUIRES_NEW : isoler chaque envoi pour ne pas tenir une
     * connexion Hikari pendant toute la boucle (saturation pool = 503 sur le trafic
     * utilisateur). Note : le proxy AOP n'est activable que pour les appels
     * inter-beans ; ici l'appel via {@code this} dans envoyerRelances ne declenche
     * pas le proxy, mais on conserve l'annotation pour expliciter l'intention.</p>
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void traiterRelance(final SmsConversation conversation) {
        final int nombreRelances = conversation.getNombreRelances() != null ? conversation.getNombreRelances() : 0;

        if (nombreRelances >= maxRelances) {
            // Max atteint : creer tache pour le vendeur et arreter les relances
            conversation.setProchaineRelanceLe(null);
            smsConversationRepository.save(conversation);
            tacheIAService.onMaxRelancesAtteint(conversation.getLeadId(), conversation.getTenantId(), conversation.getUserId());

            inboxNotificationService.notifierApresCommitPourVendeur(
                    conversation.getTenantId(), conversation.getUserId(), "relance_max_atteint",
                    Map.of("conversationId", conversation.getConversationId(),
                           "nombreRelances", String.valueOf(nombreRelances)));
            log.info("Max relances atteint - conversationId: {}, nombreRelances: {}", conversation.getConversationId(), nombreRelances);
            return;
        }

        // Determiner le template a utiliser (resolution per-tenant)
        final String templateName = nombreRelances == 0
                ? "leadrive_relance_j1"
                : "leadrive_relance_j3";
        final String templateSid = tenantWhatsappTemplateRepository
                .findByTenantIdAndTemplateName(conversation.getTenantId(), templateName)
                .map(TenantWhatsappTemplate::getTemplateSid)
                .orElse(null);

        if (templateSid == null || templateSid.isBlank()) {
            log.warn("Template {} non trouve pour tenantId={} - conversationId: {}",
                    templateName, conversation.getTenantId(), conversation.getConversationId());
            return;
        }

        // Construire les variables du template
        final Map<String, String> variables = construireVariables(conversation);
        if (variables == null) {
            log.warn("Variables relance incompletes - conversationId: {}", conversation.getConversationId());
            return;
        }

        // Verifier que le lead n'est pas opt-out
        final Lead lead = leadRepository.findById(conversation.getLeadId()).orElse(null);
        if (lead != null && Boolean.TRUE.equals(lead.getOptOutSms())) {
            log.info("Lead opt-out, relance annulee - leadId: {}", conversation.getLeadId());
            conversation.setProchaineRelanceLe(null);
            smsConversationRepository.save(conversation);
            return;
        }

        // IDEMPOTENCE : pre-incrementer nombreRelances + persister AVANT l'appel Twilio.
        // Sans ca, si le pod crash entre l'envoi Twilio et le save BDD, le job tournera
        // au prochain tick avec nombreRelances inchange et enverra une 2eme relance
        // identique (Twilio facturee 2x, image marque cassee cote prospect).
        // Pattern "fail-closed" : mieux ne pas envoyer du tout (cas extreme) que d'envoyer
        // deux fois.
        conversation.setNombreRelances(nombreRelances + 1);
        conversation.setDerniereRelanceLe(LocalDateTime.now());
        conversation.setDernierMessageLe(LocalDateTime.now());
        conversation.setNombreMessages((conversation.getNombreMessages() != null ? conversation.getNombreMessages() : 0) + 1);
        if (nombreRelances + 1 >= maxRelances) {
            conversation.setProchaineRelanceLe(null);
        } else {
            // Garde : si la config inverse les delais (J3 < J1), eviter un reschedule
            // dans le passe (la 2e relance partirait sans espacement au prochain tick).
            conversation.setProchaineRelanceLe(
                    LocalDateTime.now().plusHours(Math.max(1, delaiJ3Heures - delaiJ1Heures)));
        }
        smsConversationRepository.saveAndFlush(conversation);

        // Envoyer le template (apres la prise du slot d'idempotence)
        final String twilioSid = twilioWhatsAppService.envoyerRelanceTemplate(
                conversation.getNumeroProspect(), templateSid, variables, conversation.getTenantId());

        // Sauvegarder le message envoye (la trace du contenu, pas le slot d'idempotence)
        final String contenu = construireContenuRelance(variables, nombreRelances);
        final int ordre = smsMessageRepository.findProchainOrdre(conversation.getConversationId());

        final SmsMessage message = new SmsMessage();
        message.setTenantId(conversation.getTenantId());
        message.setConversationId(conversation.getConversationId());
        message.setDirection(DirectionSmsEnum.SORTANT);
        message.setContenu(contenu);
        message.setStatut(StatutSmsEnum.ENVOYE);
        message.setBrevoMessageId(twilioSid);
        message.setExpediteur(ExpediteurMessageEnum.IA);
        // Memorise le template (pour rejeu fidele lors d'un "Reessayer").
        message.setContentSid(templateSid);
        message.setContentVariables(twilioWhatsAppService.serialiserContentVariables(variables));
        message.setOrdre(ordre);
        smsMessageRepository.save(message);

        // Notifier le front (ciblage vendeur titulaire pour ne pas leaker le contenu PII)
        inboxNotificationService.notifierApresCommitPourVendeur(
                conversation.getTenantId(), conversation.getUserId(), "nouveau_message",
                Map.of("conversationId", conversation.getConversationId(),
                       "direction", "SORTANT",
                       "contenu", contenu,
                       "creeLe", LocalDateTime.now().toString()));

        log.info("Relance {} envoyee - conversationId: {}, leadId: {}",
                nombreRelances + 1, conversation.getConversationId(), conversation.getLeadId());
    }

    /**
     * Construit les variables du template de relance, alignees sur les templates Meta-approuves.
     * {{1}} = prenom assistant, {{2}} = nom concession, {{3}} = vehicule / projet
     */
    private Map<String, String> construireVariables(final SmsConversation conversation) {
        final Lead lead = leadRepository.findById(conversation.getLeadId()).orElse(null);
        if (lead == null) return null;

        final TenantAiConfig config = tenantAiConfigService.getConfig(conversation.getTenantId());
        final String nomConcession = config != null && config.getNomConcession() != null
                ? config.getNomConcession() : "notre concession";

        final String vehicule = ((lead.getMarque() != null ? lead.getMarque() : "")
                + " " + (lead.getModele() != null ? lead.getModele() : "")).trim();

        return Map.of(
                "1", twilioWhatsAppService.getAssistantPrenom(),
                "2", nomConcession,
                // Fallback sans possessif : les templates disent "votre projet de {{3}}",
                // un fallback "votre projet automobile" donnerait un doublon ("projet de
                // votre projet automobile").
                "3", vehicule.isBlank() ? "véhicule" : vehicule
        );
    }

    /**
     * Reconstruit le texte lisible du message pour le sauvegarder en base
     * (les templates Twilio ne renvoient pas le body resolu).
     * Doit refleter exactement le texte des templates Meta-approuves.
     */
    private String construireContenuRelance(final Map<String, String> variables, final int numeroRelance) {
        final String assistant = variables.get("1");
        final String concession = variables.get("2");
        final String projet = variables.get("3");
        if (numeroRelance == 0) {
            // Template J+1
            return "Bonjour, c'est " + assistant + " de " + concession + ".\n\n"
                    + "Je reviens vers vous au sujet de votre projet de " + projet + ".\n\n"
                    + "Notre conseiller a des disponibilités cette semaine pour vous présenter les meilleures conditions."
                    + " Quel créneau vous arrangerait ?";
        } else {
            // Template J+3
            return "Bonjour, c'est " + assistant + " de " + concession + ".\n\n"
                    + "Je reviens une dernière fois au sujet de votre projet de " + projet + "."
                    + " Notre conseiller peut vous rappeler en 5 minutes pour répondre à vos questions."
                    + " Souhaitez-vous qu'on organise ça ?";
        }
    }
}
