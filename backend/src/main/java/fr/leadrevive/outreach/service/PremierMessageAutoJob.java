package fr.leadrevive.outreach.service;

import fr.leadrevive.ai.entity.TenantAiConfig;
import fr.leadrevive.ai.service.TenantAiConfigService;
import fr.leadrevive.common.util.PiiMasker;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.outreach.exception.WhatsAppNonConfigureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Envoi automatique du premier message WhatsApp pour les leads recus par import email.
 *
 * <p>Un lead est "arme" par {@code InboundEmailProcessingService} (flag
 * {@code lead.premierMessageAutoPrevuLe}) si le tenant a active la fonctionnalite. Ce job
 * balaye periodiquement les leads dont l'echeance est passee et qui sont encore au statut
 * NOUVEAU, puis declenche l'envoi via {@code WhatsAppConversationService.demarrerConversation}
 * (qui porte tous les garde-fous : opt-out, consentement RGPD, numero valide, WhatsApp actif).</p>
 *
 * <p><strong>Multi-tenant</strong> : execute hors requete HTTP, donc le filtre Hibernate
 * tenantFilter n'est pas actif. La requete balaye tous les tenants (comme
 * {@code RelanceWhatsAppService}), et la config est resolue par tenant du lead.</p>
 *
 * <p><strong>Idempotence</strong> : ShedLock empeche 2 replicas de tourner en parallele.
 * Un envoi reussi efface le flag ET passe le lead en CONTACTE (via demarrerConversation),
 * donc il ne sera pas re-selectionne. Sur echec transitoire, le flag reste et l'envoi sera
 * retente au prochain tick (borne par l'expiration 24h).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PremierMessageAutoJob {

    /** Au-dela de cette anciennete, un lead arme non envoye est abandonne (flag efface). */
    private static final long EXPIRATION_HEURES = 24;

    private final LeadRepository leadRepository;
    private final TenantAiConfigService tenantAiConfigService;
    private final WhatsAppConversationService whatsAppConversationService;

    /**
     * Job planifie : toutes les minutes, envoie les premiers messages auto eligibles.
     * (Intervalle court pour minimiser la latence entre l'echeance armee et l'envoi reel ;
     * le job est leger - une requete indexee, retour immediat si aucun lead eligible.)
     * Pas de @Transactional englobante (chaque lead est traite isolement pour ne pas tenir
     * une connexion Hikari pendant les appels Twilio, cf. RelanceWhatsAppService).
     */
    @Scheduled(fixedDelay = 60000)
    @SchedulerLock(name = "envoyerPremierMessageAuto", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void envoyerPremiersMessagesAuto() {
        final LocalDateTime maintenant = LocalDateTime.now();
        final List<Lead> eligibles = leadRepository.findEligiblesPremierMessageAuto(maintenant);
        if (eligibles.isEmpty()) {
            return;
        }
        log.info("Premier message auto - leads eligibles: {}", eligibles.size());
        int envoyes = 0;
        int reportes = 0;
        int abandonnes = 0;
        for (final Lead lead : eligibles) {
            try {
                final ResultatEnvoi resultat = traiterLead(lead, maintenant);
                switch (resultat) {
                    case ENVOYE -> envoyes++;
                    case REPORTE -> reportes++;
                    case ABANDONNE -> abandonnes++;
                }
            } catch (final Exception e) {
                // Un echec sur un lead ne doit jamais casser le job pour les autres.
                log.error("Erreur envoi premier message auto - leadId: {}, cause: {}",
                        lead.getId(), PiiMasker.content(e.getMessage()));
            }
        }
        log.info("Premier message auto termine - envoyes: {}, reportes: {}, abandonnes: {}",
                envoyes, reportes, abandonnes);
    }

    private enum ResultatEnvoi { ENVOYE, REPORTE, ABANDONNE }

    /**
     * Traite un lead arme. Ordre des controles : config coupee -> expiration -> plage horaire -> envoi.
     */
    private ResultatEnvoi traiterLead(final Lead lead, final LocalDateTime maintenant) {
        final Long tenantId = lead.getTenantId();
        final TenantAiConfig config = tenantAiConfigService.getConfig(tenantId);

        // Config coupee entre-temps : on desarme proprement.
        if (config == null || !Boolean.TRUE.equals(config.getPremierMessageAutoActif())) {
            desarmer(lead, "config desactivee");
            return ResultatEnvoi.ABANDONNE;
        }

        // Expiration : lead arme depuis plus de 24h -> abandon (flag efface, pas d'envoi).
        if (lead.getPremierMessageAutoPrevuLe() != null
                && lead.getPremierMessageAutoPrevuLe().isBefore(maintenant.minusHours(EXPIRATION_HEURES))) {
            desarmer(lead, "expire (>" + EXPIRATION_HEURES + "h)");
            return ResultatEnvoi.ABANDONNE;
        }

        // Plage horaire : hors plage -> on SKIP sans desarmer (sera repris a l'ouverture).
        final int debut = config.getPremierMessageAutoFenetreDebutHeure() != null
                ? config.getPremierMessageAutoFenetreDebutHeure() : 8;
        final int fin = config.getPremierMessageAutoFenetreFinHeure() != null
                ? config.getPremierMessageAutoFenetreFinHeure() : 20;
        final int heure = maintenant.getHour();
        if (heure < debut || heure >= fin) {
            log.debug("Premier message auto hors plage [{}h-{}h[ (h={}) - reporte - leadId: {}",
                    debut, fin, heure, lead.getId());
            return ResultatEnvoi.REPORTE;
        }

        // Envoi. demarrerConversation porte tous les garde-fous et efface le flag en cas de succes.
        // On attribue la conversation au vendeur titulaire du lead (lead.vendeurId) : ainsi ce
        // vendeur y accede dans l'inbox et recoit la notif ciblee (pas de diffusion a tout le
        // tenant). userId reste null seulement si le lead n'est pas encore assigne (cas legitime,
        // colonne user_id nullable depuis V074).
        try {
            whatsAppConversationService.demarrerConversation(lead.getId(), lead.getTelephone(), tenantId, lead.getVendeurId());
            log.info("Premier message auto envoye - tenantId: {}, leadId: {}", tenantId, lead.getId());
            return ResultatEnvoi.ENVOYE;
        } catch (final WhatsAppNonConfigureException e) {
            // Transitoire : WhatsApp pas encore actif. On laisse le flag pour retry (borne 24h).
            log.warn("Premier message auto non envoye (WhatsApp non configure) - retry ulterieur - tenantId: {}, leadId: {}",
                    tenantId, lead.getId());
            return ResultatEnvoi.REPORTE;
        } catch (final IllegalArgumentException e) {
            // Numero invalide = garde-fou PERMANENT : inutile de reessayer, on desarme.
            desarmer(lead, "numero invalide");
            return ResultatEnvoi.ABANDONNE;
        } catch (final IllegalStateException e) {
            // IllegalStateException regroupe des cas PERMANENTS (opt-out, consentement manquant)
            // et TRANSITOIRES (template Twilio non recuperable, sub-account absent). On desarme
            // seulement sur un garde-fou permanent ; sinon on laisse le flag pour retry.
            if (estGardeFouPermanent(e.getMessage())) {
                desarmer(lead, "garde-fou permanent: " + e.getMessage());
                return ResultatEnvoi.ABANDONNE;
            }
            log.warn("Premier message auto non envoye (erreur transitoire) - retry ulterieur - tenantId: {}, leadId: {}, cause: {}",
                    tenantId, lead.getId(), PiiMasker.content(e.getMessage()));
            return ResultatEnvoi.REPORTE;
        } catch (final RuntimeException e) {
            // Echec Twilio / WhatsApp non configure / autre erreur reseau = transitoire :
            // on laisse le flag pour retry (l'expiration 24h borne le nombre de tentatives).
            log.warn("Premier message auto non envoye (erreur transitoire) - retry ulterieur - tenantId: {}, leadId: {}, cause: {}",
                    tenantId, lead.getId(), PiiMasker.content(e.getMessage()));
            return ResultatEnvoi.REPORTE;
        }
    }

    /**
     * Garde-fous PERMANENTS : opt-out RGPD, consentement manquant. Ces cas ne se resolvent
     * pas tout seuls -> on desarme (jamais de retry). Les autres IllegalStateException
     * (template Twilio indisponible, sub-account absent, conversation deja en cours) sont
     * transitoires ou benignes -> retry / no-op.
     */
    private boolean estGardeFouPermanent(final String message) {
        if (message == null) {
            return false;
        }
        final String m = message.toLowerCase();
        return m.contains("opt-out") || m.contains("consentement");
    }

    private void desarmer(final Lead lead, final String raison) {
        lead.setPremierMessageAutoPrevuLe(null);
        leadRepository.save(lead);
        log.info("Premier message auto desarme ({}) - leadId: {}", raison, lead.getId());
    }
}
