package fr.leadrevive.emailimport.service;

import fr.leadrevive.ai.entity.TenantAiConfig;
import fr.leadrevive.ai.service.TenantAiConfigService;
import fr.leadrevive.common.realtime.SseConnectionService;
import fr.leadrevive.common.realtime.dto.RealtimeEventDto;
import fr.leadrevive.emailimport.config.EmailImportConfig;
import fr.leadrevive.emailimport.entity.InboundEmailLog;
import fr.leadrevive.emailimport.entity.StatutInboundEmail;
import fr.leadrevive.emailimport.entity.TenantEmailConfig;
import fr.leadrevive.emailimport.repository.InboundEmailLogRepository;
import fr.leadrevive.emailimport.repository.TenantEmailConfigRepository;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.entity.LeadStatutEnum;
import fr.leadrevive.lead.entity.ModeImport;
import fr.leadrevive.lead.repository.LeadRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrateur du flow d'import email :
 * <ol>
 *   <li>Identification du tenant via {@code emailAddress}</li>
 *   <li>Rate limiting (compteur InboundEmailLog par heure)</li>
 *   <li>Log brut de l'email recu</li>
 *   <li>Appel parsing IA</li>
 *   <li>Deduplication par telephone (prioritaire) ou email</li>
 *   <li>Creation ou enrichissement du lead</li>
 *   <li>Mise a jour des compteurs TenantEmailConfig</li>
 * </ol>
 *
 * <p>Toutes les operations sont cross-tenant : le webhook tourne sans
 * {@code TenantContext}, le filtre Hibernate est desactive explicitement.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InboundEmailProcessingService {

    private final TenantEmailConfigRepository tenantEmailConfigRepository;
    private final InboundEmailLogRepository inboundEmailLogRepository;
    private final LeadRepository leadRepository;
    private final InboundEmailParsingService inboundEmailParsingService;
    private final EmailDeduplicationService emailDeduplicationService;
    private final EmailImportConfig emailImportConfig;
    private final EntityManager entityManager;
    private final SseConnectionService sseConnectionService;
    private final TenantAiConfigService tenantAiConfigService;

    /**
     * Traite un email entrant recu via SendGrid Inbound Parse.
     *
     * <p>Retourne toujours {@link ResultatTraitement} : le webhook doit repondre 200
     * meme en cas d'erreur metier (sinon SendGrid retry). Les rejets sont logges
     * dans {@code inbound_email_log} pour audit.</p>
     */
    @Transactional
    public ResultatTraitement traiter(final String emailTo,
                                       final String emailFrom,
                                       final String subject,
                                       final String body) {
        log.info("Reception email - to: {}, from: {}, subject: {}",
                emailTo, emailFrom, subject);

        // Fix bug-hunter #12 : disableFilter sans try/finally laissait le filtre desactive
        // sur la Session apres traitement, donc une requete HTTP utilisateur reutilisant
        // ce thread (pool Tomcat) lisait cross-tenant. Encapsule en try/finally.
        final Session session = entityManager.unwrap(Session.class);
        session.disableFilter("tenantFilter");
        try {
            // 1. Routage tenant
            final Optional<TenantEmailConfig> configOpt =
                    tenantEmailConfigRepository.findByEmailAddress(emailTo);
            if (configOpt.isEmpty()) {
                log.warn("Email recu sur adresse inconnue - to: {}", emailTo);
                return ResultatTraitement.adresseInconnue();
            }
            final TenantEmailConfig config = configOpt.get();
            if (!"ACTIVE".equalsIgnoreCase(config.getStatut())) {
                log.warn("Email recu sur adresse desactivee - tenantId: {}, to: {}",
                        config.getTenantId(), emailTo);
                return ResultatTraitement.adresseInactive();
            }

            final Long tenantId = config.getTenantId();

            // 2. Rate limiting
            final LocalDateTime ilYAUneHeure = LocalDateTime.now().minusHours(1);
            final long emailsDerniereHeure = inboundEmailLogRepository.countByTenantIdSince(tenantId, ilYAUneHeure);
            if (emailsDerniereHeure >= emailImportConfig.getMaxEmailsParHeure()) {
                log.warn("Rate limit atteint - tenantId: {}, derniere heure: {}",
                        tenantId, emailsDerniereHeure);
                sauvegarderLog(tenantId, emailFrom, subject, body, null,
                        StatutInboundEmail.REJETE_RATE_LIMIT, null, null, null,
                        "Rate limit atteint (" + emailsDerniereHeure + " emails / heure)");
                return ResultatTraitement.rateLimit();
            }

            // 3. Parsing IA
            final InboundEmailParsingService.ResultatParsing resultat =
                    inboundEmailParsingService.parser(body, subject, tenantId);

            // 4. Gestion des cas particuliers (rejet spam, confiance insuffisante, echec)
            if (resultat.statut() == InboundEmailParsingService.StatutResultat.REJET_SPAM) {
                sauvegarderLog(tenantId, emailFrom, subject, body, resultat.resultatBrut(),
                        StatutInboundEmail.REJETE_SPAM, null, null, (int) resultat.dureeMs(),
                        resultat.raisonRejet());
                mettreAJourCompteurs(config);
                return ResultatTraitement.rejetSpam(resultat.raisonRejet());
            }
            if (resultat.statut() == InboundEmailParsingService.StatutResultat.CONFIANCE_INSUFFISANTE) {
                sauvegarderLog(tenantId, emailFrom, subject, body, resultat.resultatBrut(),
                        StatutInboundEmail.PARSE_ECHEC, null, resultat.sourceDetectee(),
                        (int) resultat.dureeMs(),
                        "Confiance " + resultat.confiance() + " < " + 30);
                mettreAJourCompteurs(config);
                return ResultatTraitement.confianceInsuffisante(resultat.confiance());
            }
            if (resultat.statut() == InboundEmailParsingService.StatutResultat.ECHEC) {
                sauvegarderLog(tenantId, emailFrom, subject, body, null,
                        StatutInboundEmail.PARSE_ECHEC, null, null, (int) resultat.dureeMs(),
                        resultat.raisonRejet());
                mettreAJourCompteurs(config);
                return ResultatTraitement.echec(resultat.raisonRejet());
            }

            // 5. Deduplication
            final Optional<Lead> doublonOpt = emailDeduplicationService.chercherDoublon(
                    tenantId, resultat.telephone(), resultat.email());

            final Lead lead;
            final StatutInboundEmail statutLog;
            if (doublonOpt.isPresent()) {
                lead = enrichirLead(doublonOpt.get(), resultat, body);
                statutLog = StatutInboundEmail.LEAD_DEDUPLIQUE;
                log.info("Lead enrichi (dedup) - tenantId: {}, leadId: {}", tenantId, lead.getId());
            } else {
                lead = creerLead(tenantId, resultat, body);
                statutLog = (resultat.telephone() == null || resultat.telephone().isBlank())
                        ? StatutInboundEmail.LEAD_INCOMPLET
                        : StatutInboundEmail.LEAD_CREE;
                log.info("Lead cree par import email - tenantId: {}, leadId: {}, statut: {}",
                        tenantId, lead.getId(), statutLog);
            }

            // 6. Log de l'email + maj compteurs config
            sauvegarderLog(tenantId, emailFrom, subject, body, resultat.resultatBrut(),
                    statutLog, lead.getId(), resultat.sourceDetectee(),
                    (int) resultat.dureeMs(), null);
            mettreAJourCompteurs(config);

            // 7. Notification temps reel (toast + badge sidebar + auto-refresh)
            if (statutLog == StatutInboundEmail.LEAD_CREE || statutLog == StatutInboundEmail.LEAD_INCOMPLET) {
                sseConnectionService.broadcast(tenantId,
                        RealtimeEventDto.leadCreated(lead.getId(), lead.getNom(), lead.getSource()));
            }

            return ResultatTraitement.succes(lead.getId(), statutLog);
        } finally {
            // Re-active le filtre pour eviter une fuite cross-tenant si la session
            // est reutilisee par une requete HTTP ulterieure (sentinelle -1L).
            try {
                session.enableFilter("tenantFilter").setParameter("tenantId", -1L);
            } catch (final Exception ignore) {
                // Pas critique si la session est deja close en fin de tx.
            }
        }
    }

    /**
     * Cree un nouveau lead a partir des donnees parsees. Le consentement contact
     * est mis a true (le prospect a volontairement soumis ses coordonnees via la plateforme).
     */
    private Lead creerLead(final Long tenantId,
                            final InboundEmailParsingService.ResultatParsing resultat,
                            final String emailBody) {
        final Lead lead = new Lead();
        lead.setTenantId(tenantId);
        // Si pas de nom extrait, on utilise une valeur de secours ("Prospect Email")
        // car la colonne est NOT NULL.
        lead.setNom(formaterNomComplet(resultat.nom(), resultat.prenom()));
        lead.setTelephone(resultat.telephone());
        lead.setEmail(resultat.email());
        lead.setMarque(resultat.marque());
        lead.setModele(resultat.modele());
        lead.setSource(resultat.sourceDetectee());
        lead.setStatut(LeadStatutEnum.NOUVEAU);
        lead.setModeImport(ModeImport.EMAIL_AUTO);
        lead.setTexteSource(emailBody);
        lead.setArchive(false);
        lead.setClientIrrite(false);

        // RGPD : le prospect a soumis volontairement ses coordonnees a la plateforme,
        // qui les a transferees a la concession. On considere le consentement acquis
        // (source = REDIRECT_EMAIL pour traçabilite).
        lead.setConsentementContact(true);
        lead.setConsentementDate(LocalDateTime.now());
        lead.setConsentementSource("REDIRECT_EMAIL");

        // Donnees supplementaires extraites par l'IA (immat, km, energie, etc.)
        if (resultat.donneesSupplementaires() != null && !resultat.donneesSupplementaires().isEmpty()) {
            lead.setDonneesSupplementaires(new HashMap<>(resultat.donneesSupplementaires()));
        }

        // Garde-fou RGPD : un prospect qui s'etait desinscrit (opt-out) puis re-soumet
        // via un nouvel email ne doit JAMAIS etre recontacte automatiquement. On propage
        // l'opt-out par NUMERO (couvre aussi les leads archives du meme telephone) et on
        // n'arme pas l'envoi auto. Le lead reste cree (visible), mais silencieux.
        final boolean optOutPrealable = resultat.telephone() != null && !resultat.telephone().isBlank()
                && leadRepository.existsByTenantIdAndTelephoneAndOptOutSmsTrue(tenantId, resultat.telephone());
        if (optOutPrealable) {
            lead.setOptOutSms(true);
        }

        final Lead saved = leadRepository.save(lead);

        if (optOutPrealable) {
            log.info("Opt-out preserve sur nouveau lead email (envoi auto non arme) - tenantId: {}, leadId: {}, telephone: {}",
                    tenantId, saved.getId(), fr.leadrevive.common.util.PiiMasker.phone(resultat.telephone()));
            return saved;
        }

        // Envoi automatique du premier message WhatsApp : uniquement pour les leads
        // import email (ce service est le seul point d'accroche v1). Le scheduler
        // PremierMessageAutoJob prendra le relais une fois le delai ecoule.
        armerPremierMessageAuto(saved, tenantId);

        return saved;
    }

    /**
     * Arme l'envoi auto du premier message WhatsApp si le tenant l'a active et que le
     * lead a un telephone. Pose seulement l'horodatage ; les garde-fous (consentement,
     * opt-out, plage horaire, WhatsApp actif) sont evalues au moment de l'envoi par
     * {@code PremierMessageAutoJob}. OFF par defaut : aucun effet si non active.
     */
    private void armerPremierMessageAuto(final Lead lead, final Long tenantId) {
        if (lead.getTelephone() == null || lead.getTelephone().isBlank()) {
            return;
        }
        final TenantAiConfig config = tenantAiConfigService.getConfig(tenantId);
        if (config == null || !Boolean.TRUE.equals(config.getPremierMessageAutoActif())) {
            return;
        }
        final int delaiMinutes = config.getPremierMessageAutoDelaiMinutes() != null
                ? config.getPremierMessageAutoDelaiMinutes() : 5;
        lead.setPremierMessageAutoPrevuLe(LocalDateTime.now().plusMinutes(delaiMinutes));
        leadRepository.save(lead);
        log.info("Premier message auto arme - tenantId: {}, leadId: {}, delaiMinutes: {}",
                tenantId, lead.getId(), delaiMinutes);
    }

    /**
     * Enrichit un lead existant avec les nouvelles donnees du parsing :
     * ajoute la source dans donneesSupplementaires si differente, complete
     * les champs manquants (telephone, email, marque, modele).
     */
    private Lead enrichirLead(final Lead lead,
                               final InboundEmailParsingService.ResultatParsing resultat,
                               final String emailBody) {
        // Complete les champs manquants sans ecraser les existants
        if ((lead.getTelephone() == null || lead.getTelephone().isBlank())
                && resultat.telephone() != null) {
            lead.setTelephone(resultat.telephone());
        }
        if ((lead.getEmail() == null || lead.getEmail().isBlank())
                && resultat.email() != null) {
            lead.setEmail(resultat.email());
        }
        if ((lead.getMarque() == null || lead.getMarque().isBlank())
                && resultat.marque() != null) {
            lead.setMarque(resultat.marque());
        }
        if ((lead.getModele() == null || lead.getModele().isBlank())
                && resultat.modele() != null) {
            lead.setModele(resultat.modele());
        }

        // Ajoute la source detectee dans donneesSupplementaires.sources (liste cumulative)
        // si differente de la source principale.
        if (resultat.sourceDetectee() != null
                && !resultat.sourceDetectee().equalsIgnoreCase(lead.getSource())) {
            final Map<String, Object> donnees = lead.getDonneesSupplementaires() != null
                    ? lead.getDonneesSupplementaires() : new HashMap<>();
            donnees.merge("sourcesSupplementaires", resultat.sourceDetectee(),
                    (ancien, nouveau) -> ancien.toString().contains(nouveau.toString())
                            ? ancien : ancien + ", " + nouveau);
            lead.setDonneesSupplementaires(donnees);
        }

        // Conserve le texte source du dernier email pour reference
        lead.setTexteSource(emailBody);

        return leadRepository.save(lead);
    }

    private void sauvegarderLog(final Long tenantId,
                                 final String emailFrom,
                                 final String subject,
                                 final String body,
                                 final String resultatBrut,
                                 final StatutInboundEmail statut,
                                 final Long leadId,
                                 final String sourceDetectee,
                                 final Integer dureeParsingMs,
                                 final String raisonRejet) {
        final InboundEmailLog log = new InboundEmailLog();
        log.setTenantId(tenantId);
        log.setEmailFrom(tronquer(emailFrom, 255));
        log.setEmailSubject(tronquer(subject, 500));
        // RGPD : pour un email REJETÉ (spam / parse échec → aucun lead créé, donc aucune
        // fiche à laquelle rattacher un droit à l'oubli), on ne conserve qu'un extrait
        // tronqué, pas le corps complet en clair. Si un lead est créé, le body reste
        // (rattaché au lead, effaçable via lui) — et la purge à 30j s'applique de toute façon.
        log.setEmailBodyRaw(leadId != null ? body : tronquer(body, 200));
        log.setResultatParsing(resultatBrut);
        log.setStatut(statut);
        log.setLeadId(leadId);
        log.setSourceDetectee(tronquer(sourceDetectee, 50));
        log.setDureeParsingMs(dureeParsingMs);
        log.setRaisonRejet(tronquer(raisonRejet, 500));
        log.setCreeLe(LocalDateTime.now());
        inboundEmailLogRepository.save(log);
    }

    private void mettreAJourCompteurs(final TenantEmailConfig config) {
        config.setNbEmailsRecus((config.getNbEmailsRecus() != null ? config.getNbEmailsRecus() : 0) + 1);
        config.setDernierEmailRecuLe(LocalDateTime.now());
        tenantEmailConfigRepository.save(config);
    }

    private String formaterNomComplet(final String nom, final String prenom) {
        if (nom == null && prenom == null) return "Prospect Email";
        if (nom == null) return prenom;
        if (prenom == null) return nom;
        return prenom + " " + nom;
    }

    private String tronquer(final String valeur, final int max) {
        if (valeur == null) return null;
        return valeur.length() > max ? valeur.substring(0, max) : valeur;
    }

    /** Resultat du traitement d'un email (pour le webhook). */
    public record ResultatTraitement(StatutTraitement statut,
                                      Long leadId,
                                      StatutInboundEmail statutLog,
                                      String message) {
        public static ResultatTraitement adresseInconnue() {
            return new ResultatTraitement(StatutTraitement.ADRESSE_INCONNUE, null, null,
                    "Adresse destinataire inconnue");
        }
        public static ResultatTraitement adresseInactive() {
            return new ResultatTraitement(StatutTraitement.ADRESSE_INACTIVE, null, null,
                    "Adresse desactivee");
        }
        public static ResultatTraitement rateLimit() {
            return new ResultatTraitement(StatutTraitement.RATE_LIMIT, null,
                    StatutInboundEmail.REJETE_RATE_LIMIT, "Rate limit atteint");
        }
        public static ResultatTraitement rejetSpam(final String raison) {
            return new ResultatTraitement(StatutTraitement.REJET_SPAM, null,
                    StatutInboundEmail.REJETE_SPAM, raison);
        }
        public static ResultatTraitement confianceInsuffisante(final int confiance) {
            return new ResultatTraitement(StatutTraitement.PARSE_ECHEC, null,
                    StatutInboundEmail.PARSE_ECHEC,
                    "Confiance " + confiance + " insuffisante");
        }
        public static ResultatTraitement echec(final String raison) {
            return new ResultatTraitement(StatutTraitement.PARSE_ECHEC, null,
                    StatutInboundEmail.PARSE_ECHEC, raison);
        }
        public static ResultatTraitement succes(final Long leadId, final StatutInboundEmail statut) {
            return new ResultatTraitement(StatutTraitement.SUCCES, leadId, statut, "OK");
        }
    }

    public enum StatutTraitement {
        SUCCES,
        ADRESSE_INCONNUE,
        ADRESSE_INACTIVE,
        RATE_LIMIT,
        REJET_SPAM,
        PARSE_ECHEC
    }
}
