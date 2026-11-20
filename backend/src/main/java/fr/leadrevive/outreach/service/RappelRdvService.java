package fr.leadrevive.outreach.service;

import fr.leadrevive.ai.entity.TenantAiConfig;
import fr.leadrevive.ai.service.TenantAiConfigService;
import fr.leadrevive.common.util.PiiMasker;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.outreach.entity.DirectionSmsEnum;
import fr.leadrevive.outreach.entity.ExpediteurMessageEnum;
import fr.leadrevive.outreach.entity.RappelRdv;
import fr.leadrevive.outreach.entity.SmsConversation;
import fr.leadrevive.outreach.entity.SmsMessage;
import fr.leadrevive.outreach.entity.StatutSmsEnum;
import fr.leadrevive.outreach.entity.TenantWhatsappTemplate;
import fr.leadrevive.outreach.entity.TypeRappelEnum;
import fr.leadrevive.outreach.repository.RappelRdvRepository;
import fr.leadrevive.outreach.repository.TenantWhatsappTemplateRepository;
import fr.leadrevive.outreach.repository.SmsConversationRepository;
import fr.leadrevive.outreach.repository.SmsMessageRepository;
import fr.leadrevive.tache.entity.Tache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Planifie et envoie le rappel WhatsApp JOUR_J (1h avant le RDV) pour les RDV confirmes.
 * Le rappel est envoye via un template WhatsApp pre-approuve par Meta.
 *
 * Note : le rappel "veille a 18h" a ete supprime — un seul rappel suffit (1h avant).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RappelRdvService {

    @Value("${leadrevive.rappel-rdv.enabled:false}")
    private boolean rappelEnabled;

    private final RappelRdvRepository rappelRdvRepository;
    private final SmsConversationRepository smsConversationRepository;
    private final SmsMessageRepository smsMessageRepository;
    private final LeadRepository leadRepository;
    private final TwilioWhatsAppService twilioWhatsAppService;
    private final TenantWhatsappTemplateRepository tenantWhatsappTemplateRepository;
    private final TenantAiConfigService tenantAiConfigService;
    private final InboxNotificationService inboxNotificationService;

    /**
     * Planifie le rappel JOUR_J (1h avant le RDV) pour un RDV confirme.
     * Appele depuis ConversationAnalyseService quand rdvConfirme == true.
     *
     * @param tacheRdv la tache RDV creee par TacheIAService
     * @param lead le lead concerne
     * @param conversation la conversation WhatsApp (pour le numero de destination)
     */
    @Transactional
    public void planifierRappels(final Tache tacheRdv, final Lead lead, final SmsConversation conversation) {
        if (!rappelEnabled) {
            log.debug("Rappels RDV desactives - aucune planification");
            return;
        }

        final LocalDateTime dateRdv = tacheRdv.getDateEcheance();
        final LocalDateTime jourJ = dateRdv.minusHours(1);
        if (!jourJ.isAfter(LocalDateTime.now())) {
            log.info("RDV trop proche - rappel JOUR_J non planifie - leadId: {}, dateRdv: {}",
                    lead.getId(), dateRdv);
            return;
        }

        // Idempotence : si un rappel non envoye existe deja pour cette tache, on resynchronise
        // sa date d'envoi au lieu d'en creer un nouveau. Evite l'envoi multiple lorsque l'IA
        // reconfirme le meme RDV sur plusieurs messages successifs du prospect.
        final Optional<RappelRdv> existant = rappelRdvRepository
                .findFirstByTacheIdAndTypeRappelAndEnvoyeFalseOrderByIdAsc(tacheRdv.getId(), TypeRappelEnum.JOUR_J);
        if (existant.isPresent()) {
            final RappelRdv rappel = existant.get();
            if (!jourJ.equals(rappel.getEnvoiPrevuAt())) {
                log.info("Rappel JOUR_J deja planifie - resynchronisation - leadId: {}, ancien: {}, nouveau: {}",
                        lead.getId(), rappel.getEnvoiPrevuAt(), jourJ);
                rappel.setEnvoiPrevuAt(jourJ);
                rappelRdvRepository.save(rappel);
            } else {
                log.debug("Rappel JOUR_J deja planifie pour le meme creneau - leadId: {}, envoi prevu: {}",
                        lead.getId(), jourJ);
            }
            return;
        }

        log.info("Planification rappel JOUR_J - leadId: {}, dateRdv: {}, conversationId: {}",
                lead.getId(), dateRdv, conversation.getConversationId());
        creerRappel(tacheRdv, lead, conversation, TypeRappelEnum.JOUR_J, jourJ);
    }

    /**
     * Job toutes les 5 minutes — envoie les rappels dus.
     *
     * <p>Fix bug-hunter #13 : @Transactional retire du scheduler. Tenir une seule TX
     * pendant N appels Twilio (200ms-2s chacun) saturait le pool Hikari pour le trafic
     * HTTP utilisateur, et un fail tardif rollbackait toute la boucle (perte du marker
     * d'idempotence "envoye=true" pose plus tot). Chaque item commit isolement via
     * {@code saveAndFlush} dans le bloc try/catch.</p>
     */
    @Scheduled(fixedRate = 300_000)
    @SchedulerLock(name = "envoyerRappelsRdvDus", lockAtMostFor = "PT4M", lockAtLeastFor = "PT1M")
    public void envoyerRappelsDus() {
        if (!rappelEnabled) return;

        final List<RappelRdv> dus = rappelRdvRepository.findByEnvoyeFalseAndEnvoiPrevuAtBefore(LocalDateTime.now());
        if (dus.isEmpty()) return;

        log.info("Rappels RDV dus a envoyer - nombre: {}", dus.size());
        int envoyees = 0;
        int erreurs = 0;

        for (final RappelRdv rappel : dus) {
            try {
                traiterRappel(rappel);
                envoyees++;
            } catch (final Exception e) {
                erreurs++;
                // Fix bug-hunter #10 : log.error avec stack trace pour ne pas masquer
                // le contexte de l'echec (Twilio 5xx, lead introuvable, etc.).
                log.error("Erreur envoi rappel RDV - rappelId: {}, cause: {}",
                        rappel.getId(), e.getMessage(), e);
            }
        }

        log.info("Rappels RDV termines - envoyes: {}, erreurs: {}", envoyees, erreurs);
    }

    /**
     * Traite un rappel isole. TX dediee (REQUIRES_NEW) pour ne pas tenir la
     * connexion Hikari pendant toute la boucle (cf. envoyerRappelsDus).
     * Note : appele via {@code this} dans la boucle, le proxy AOP ne s'active
     * pas pour les appels intra-bean ; on conserve l'annotation pour expliciter
     * l'intention (et si self-injection est ajoutee plus tard).
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void traiterRappel(final RappelRdv rappel) {
        // Verifier opt-out avant envoi
        final Lead lead = leadRepository.findById(rappel.getLeadId()).orElse(null);
        if (lead != null && Boolean.TRUE.equals(lead.getOptOutSms())) {
            log.info("Lead opt-out, rappel annule - leadId: {}", rappel.getLeadId());
            rappel.setEnvoye(true);
            rappel.setEnvoyeAt(LocalDateTime.now());
            rappelRdvRepository.save(rappel);
            return;
        }

        // IDEMPOTENCE : pre-marker envoye=true + flush AVANT l'envoi Twilio.
        // Sans ca, un crash entre l'envoi et le save = re-envoi du rappel au
        // prochain tick (toutes les 5 min) avec un nouveau message identique
        // au prospect. Pattern "fail-closed" : on accepte le risque rarissime
        // de ne pas envoyer pour eliminer le risque (frequent en multi-replica)
        // d'envoyer deux fois.
        rappel.setEnvoye(true);
        rappel.setEnvoyeAt(LocalDateTime.now());
        rappelRdvRepository.saveAndFlush(rappel);

        envoyerTemplate(rappel);
    }

    /**
     * Annule les rappels non envoyes pour un lead (appele lors d'un STOP ou annulation).
     */
    @Transactional
    public void annulerRappelsNonEnvoyes(final Long leadId) {
        final List<RappelRdv> rappels = rappelRdvRepository.findByLeadIdAndEnvoyeFalse(leadId);
        if (!rappels.isEmpty()) {
            rappelRdvRepository.deleteAll(rappels);
            log.info("Rappels annules pour lead - leadId: {}, nombre: {}", leadId, rappels.size());
        }
    }

    private void envoyerTemplate(final RappelRdv rappel) {
        final SmsConversation conversation = smsConversationRepository.findById(rappel.getSmsConversationId()).orElse(null);
        if (conversation == null) {
            log.warn("Conversation introuvable pour rappel - rappelId: {}", rappel.getId());
            return;
        }

        final Lead lead = leadRepository.findById(rappel.getLeadId()).orElse(null);
        if (lead == null) {
            log.warn("Lead introuvable pour rappel - rappelId: {}", rappel.getId());
            return;
        }

        // Recuperer le nom de la concession
        final TenantAiConfig config = tenantAiConfigService.getConfig(rappel.getTenantId());
        final String nomConcession = config != null && config.getNomConcession() != null
                ? config.getNomConcession() : "notre concession";

        // Variables alignees sur le template Meta-approuve :
        // {{1}} = prenom assistant, {{2}} = nom concession
        // Le template indique "vous appelle dans environ 1 heure" — pas besoin d'heure exacte.
        final String templateSid = tenantWhatsappTemplateRepository
                .findByTenantIdAndTemplateName(rappel.getTenantId(), "leadrive_rappel_rdv")
                .map(TenantWhatsappTemplate::getTemplateSid)
                .orElse(null);
        final String prenomAssistant = twilioWhatsAppService.getAssistantPrenom();
        final Map<String, String> variables = Map.of(
                "1", prenomAssistant,
                "2", nomConcession
        );
        final String contenuReconstitue = "Bonjour, c'est " + prenomAssistant
                + " de " + nomConcession + ".\n\n"
                + "Notre conseiller vous appelle dans environ 1 heure comme convenu."
                + " Si un imprévu s'est glissé dans votre journée, répondez-moi et on décale facilement.";

        if (templateSid == null || templateSid.isBlank()) {
            log.warn("Template rappel JOUR_J non configure - rappelId: {}", rappel.getId());
            return;
        }

        // Envoyer le template
        final String twilioSid = twilioWhatsAppService.envoyerRelanceTemplate(
                conversation.getNumeroProspect(), templateSid, variables, conversation.getTenantId());

        // Sauvegarder le message dans la conversation
        // Ordre via SELECT MAX(ordre)+1 atomique (anti race condition).
        final int ordre = smsMessageRepository.findProchainOrdre(conversation.getConversationId());
        final SmsMessage message = new SmsMessage();
        message.setTenantId(conversation.getTenantId());
        message.setConversationId(conversation.getConversationId());
        message.setDirection(DirectionSmsEnum.SORTANT);
        message.setContenu(contenuReconstitue);
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
                       "contenu", contenuReconstitue,
                       "creeLe", LocalDateTime.now().toString()));

        log.info("Rappel RDV JOUR_J envoye - leadId: {}, destinataire: {}, concession: {}",
                rappel.getLeadId(), PiiMasker.phone(conversation.getNumeroProspect()), nomConcession);
    }

    private void creerRappel(final Tache tache, final Lead lead, final SmsConversation conversation,
                              final TypeRappelEnum type, final LocalDateTime envoiPrevu) {
        final RappelRdv rappel = new RappelRdv();
        rappel.setTenantId(lead.getTenantId());
        rappel.setTacheId(tache.getId());
        rappel.setSmsConversationId(conversation.getId());
        rappel.setLeadId(lead.getId());
        rappel.setTypeRappel(type);
        rappel.setEnvoiPrevuAt(envoiPrevu);
        rappel.setEnvoye(false);
        rappelRdvRepository.save(rappel);

        log.info("Rappel planifie - type: {}, envoi prevu: {}, leadId: {}", type, envoiPrevu, lead.getId());
    }

}
