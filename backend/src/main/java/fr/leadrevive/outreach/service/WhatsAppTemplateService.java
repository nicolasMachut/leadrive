package fr.leadrevive.outreach.service;

import fr.leadrevive.outreach.config.TwilioConfig;
import fr.leadrevive.outreach.entity.StatutTemplateEnum;
import fr.leadrevive.outreach.entity.TenantWhatsappConfig;
import fr.leadrevive.outreach.entity.TenantWhatsappTemplate;
import fr.leadrevive.outreach.integration.TwilioContentApiClient;
import fr.leadrevive.outreach.repository.TenantWhatsappConfigRepository;
import fr.leadrevive.outreach.repository.TenantWhatsappTemplateRepository;
import fr.leadrevive.outreach.template.WhatsAppTemplateLoader;
import fr.leadrevive.outreach.template.WhatsAppTemplateLoader.LoadedTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Soumission et suivi des templates WhatsApp Meta pour un tenant (epic 25).
 *
 * <p>Cycle d'un template par tenant :</p>
 * <ol>
 *   <li>Creation locale en {@link StatutTemplateEnum#EN_ATTENTE_SOUMISSION}
 *       (a partir du YAML versionne dans {@code resources/whatsapp-templates/}).</li>
 *   <li>Soumission a Twilio Content API ({@link #soumettreTous(Long)}) -> SID
 *       Twilio recupere, statut local passe a {@link StatutTemplateEnum#SOUMIS}.</li>
 *   <li>Polling Meta ({@link #raffraichirStatuts(Long)}) -> {@link StatutTemplateEnum#APPROUVE}
 *       ou {@link StatutTemplateEnum#REJETE} avec raison.</li>
 * </ol>
 *
 * <p><strong>Dry-run</strong> : en {@code twilio.dry-run=true}, on simule
 * l'approbation Meta instantanement. C'est utile pour le QA local sans
 * connexion reseau a Meta.</p>
 *
 * <p><strong>Idempotence</strong> : appeler {@link #soumettreTous(Long)} plusieurs
 * fois est sans effet pour les templates deja APPROUVE / SOUMIS. Seuls les
 * EN_ATTENTE_SOUMISSION ou REJETE (avec back-off expire) sont re-pousses.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppTemplateService {

    private final WhatsAppTemplateLoader templateLoader;
    private final TenantWhatsappTemplateRepository templateRepository;
    private final TwilioConfig twilioConfig;
    // Dependances utilisees uniquement en mode reel (Twilio Content API + lookup
    // credentials sub-account du tenant). En dry-run, jamais appelees.
    private final TwilioContentApiClient contentApiClient;
    private final TenantWhatsappConfigRepository configRepository;

    /**
     * Cree localement les entrees BDD pour tous les templates YAML qui ne sont
     * pas encore presents pour ce tenant. Idempotent : si une entree existe
     * deja, on la laisse en l'etat.
     */
    @Transactional
    public void initialiserTemplatesPourTenant(final Long tenantId) {
        final List<LoadedTemplate> tous = templateLoader.tousLesTemplates();
        for (final LoadedTemplate t : tous) {
            final String name = t.definition().name();
            final boolean existe = templateRepository
                    .findByTenantIdAndTemplateName(tenantId, name)
                    .isPresent();
            if (existe) {
                continue;
            }
            final TenantWhatsappTemplate entry = new TenantWhatsappTemplate();
            entry.setTenantId(tenantId);
            entry.setTemplateName(name);
            entry.setStatut(StatutTemplateEnum.EN_ATTENTE_SOUMISSION);
            entry.setYamlContentHash(t.contentHashSha256());
            templateRepository.save(entry);
            log.info("Template initialise pour tenant - tenantId: {}, template: {}", tenantId, name);
        }
    }

    /**
     * Reinitialise tous les templates d'un tenant a l'etat EN_ATTENTE_SOUMISSION
     * (SID vide). Appele a la deconnexion WhatsApp : une approbation Meta est liee
     * au WABA, donc apres un changement de compte les anciennes approbations sont
     * caduques. Les templates seront re-soumis a la reconnexion (soumettreTous).
     */
    @Transactional
    public void reinitialiserTemplates(final Long tenantId) {
        final List<TenantWhatsappTemplate> tous = templateRepository.findAllByTenantId(tenantId);
        for (final TenantWhatsappTemplate t : tous) {
            t.setStatut(StatutTemplateEnum.EN_ATTENTE_SOUMISSION);
            t.setTemplateSid(null);
            t.setMetaRejectReason(null);
            t.setSoumisLe(null);
            t.setProchaineTentativeLe(null);
            t.setNbTentatives(0);
            templateRepository.save(t);
        }
        log.info("Templates reinitialises (deconnexion WhatsApp) - tenantId: {}, count: {}",
                tenantId, tous.size());
    }

    /**
     * Pre-cree les Content Twilio des le demarrage du flow (avant l'Embedded
     * Signup), pour raccourcir le delai d'activation : a la connexion Meta,
     * {@link #soumettreUn} n'aura plus qu'un seul appel (approbation) au lieu de
     * deux (creation + approbation).
     *
     * <p>Best-effort et idempotent : un template qui a deja un SID est saute ;
     * un echec sur un template n'empeche pas les autres ({@link #soumettreTous}
     * recreera le Content en fallback si besoin). Le statut reste
     * {@link StatutTemplateEnum#EN_ATTENTE_SOUMISSION} : la soumission Meta a lieu
     * dans {@code connecterMeta}.</p>
     */
    @Transactional
    public void preCreerContentsTwilio(final Long tenantId) {
        if (twilioConfig.isDryRun()) {
            return;
        }
        final TenantWhatsappConfig config = configRepository.findByTenantId(tenantId).orElse(null);
        if (config == null || config.getTwilioSubAccountSid() == null
                || config.getTwilioSubAuthToken() == null) {
            log.warn("Pre-creation Content ignoree : sub-account absent - tenantId: {}", tenantId);
            return;
        }
        int reussis = 0;
        int echecs = 0;
        for (final TenantWhatsappTemplate template
                : templateRepository.findAllByTenantIdAndStatut(tenantId, StatutTemplateEnum.EN_ATTENTE_SOUMISSION)) {
            if (template.getTemplateSid() != null && !template.getTemplateSid().isBlank()) {
                continue; // deja pre-cree (idempotence)
            }
            final LoadedTemplate loaded = templateLoader.parNom(template.getTemplateName());
            if (loaded == null) {
                continue;
            }
            try {
                final TwilioContentApiClient.ContentResponse content = contentApiClient.creerContent(
                        config.getTwilioSubAccountSid(),
                        config.getTwilioSubAuthToken(),
                        loaded.definition());
                // Statut inchange (EN_ATTENTE_SOUMISSION) : la soumission Meta viendra
                // dans connecterMeta via soumettreTous.
                // saveAndFlush : persiste le SID immediatement pour reduire la fenetre ou
                // le Content existe cote Twilio mais pas en base (crash -> Content orphelin
                // + recreation d'un doublon au prochain run).
                template.setTemplateSid(content.sid());
                templateRepository.saveAndFlush(template);
                reussis++;
            } catch (final Exception ex) {
                log.warn("Echec pre-creation Content - tenantId: {}, template: {}, cause: {}",
                        tenantId, template.getTemplateName(), ex.getMessage());
                echecs++;
            }
        }
        log.info("Content templates pre-crees - tenantId: {}, reussis: {}, echecs: {}",
                tenantId, reussis, echecs);
    }

    /**
     * Soumet a Twilio Content API tous les templates eligibles pour ce tenant
     * (statut EN_ATTENTE_SOUMISSION ou REJETE avec back-off expire).
     *
     * <p>En dry-run, marque immediatement les templates comme APPROUVE.</p>
     */
    @Transactional
    public ResultatSoumission soumettreTous(final Long tenantId) {
        // Reprendre aussi les templates REJETE dont le back-off est expiré : sinon le
        // bouton "Relancer la soumission" ne resoumet JAMAIS un rejet (il ne voyait que
        // EN_ATTENTE_SOUMISSION) → l'établissement n'activait jamais l'envoi.
        final LocalDateTime maintenant = LocalDateTime.now();
        for (final TenantWhatsappTemplate rejete
                : templateRepository.findAllByTenantIdAndStatut(tenantId, StatutTemplateEnum.REJETE)) {
            if (rejete.getProchaineTentativeLe() == null
                    || !rejete.getProchaineTentativeLe().isAfter(maintenant)) {
                rejete.setStatut(StatutTemplateEnum.EN_ATTENTE_SOUMISSION);
                rejete.setTemplateSid(null);
                templateRepository.save(rejete);
            }
        }
        final List<TenantWhatsappTemplate> aSoumettre =
                templateRepository.findAllByTenantIdAndStatut(tenantId, StatutTemplateEnum.EN_ATTENTE_SOUMISSION);
        int soumis = 0;
        int echecs = 0;
        for (final TenantWhatsappTemplate template : aSoumettre) {
            try {
                soumettreUn(template, false);
                soumis++;
            } catch (final Exception ex) {
                log.warn("Echec soumission template - tenantId: {}, template: {}, cause: {}",
                        tenantId, template.getTemplateName(), ex.getMessage());
                marquerEchecSoumission(template, ex);
                echecs++;
            }
        }
        log.info("Soumission templates - tenantId: {}, soumis: {}, echecs: {}",
                tenantId, soumis, echecs);
        return new ResultatSoumission(soumis, echecs, aSoumettre.size());
    }

    /**
     * Bouton admin "Re-soumettre" : force la recreation complete de TOUS les
     * templates du tenant, quel que soit leur statut (SOUMIS / APPROUVE / REJETE /
     * EN_ATTENTE_SOUMISSION). Utile apres un changement de YAML (categorie, texte) :
     * chaque template repart de zero (SID vide -> nouveau Content Twilio + nouvelle
     * soumission Meta). Reinitialisation + soumission dans une seule transaction.
     *
     * <p>Distinct de {@link #soumettreTous(Long)} (qui ignore les SOUMIS/APPROUVE)
     * et de {@link #reinitialiserTemplates(Long)} (deconnexion, sans re-soumission).</p>
     */
    @Transactional
    public ResultatSoumission resoumettreTous(final Long tenantId) {
        // 1. Re-synchronise la liste des templates du tenant avec les YAML courants :
        //    cree les manquants (ex: un tenant onboarde avant l'ajout/renommage d'un
        //    template n'a pas la ligne correspondante). Idempotent.
        initialiserTemplatesPourTenant(tenantId);
        // 2. Reinitialise chaque template, en PURGEANT les orphelins : une ligne dont
        //    le YAML n'existe plus (ex: anciens noms relance_j2/j5 renommes en j1/j3)
        //    ne peut pas etre soumise (loader.parNom == null -> "Template introuvable")
        //    et resterait bloquee "en attente". On la supprime pour repartir propre.
        final List<TenantWhatsappTemplate> tous = templateRepository.findAllByTenantId(tenantId);
        int purges = 0;
        final List<TenantWhatsappTemplate> cibles = new java.util.ArrayList<>();
        for (final TenantWhatsappTemplate t : tous) {
            if (templateLoader.parNom(t.getTemplateName()) == null) {
                templateRepository.delete(t);
                purges++;
                log.warn("Template orphelin purge (YAML absent) - tenantId: {}, name: {}",
                        tenantId, t.getTemplateName());
                continue;
            }
            // On NE nulle PAS le SID (swap atomique en fin de soumettreUn -> pas de coupure).
            t.setStatut(StatutTemplateEnum.EN_ATTENTE_SOUMISSION);
            t.setMetaRejectReason(null);
            t.setSoumisLe(null);
            t.setProchaineTentativeLe(null);
            t.setNbTentatives(0);
            templateRepository.save(t);
            cibles.add(t);
        }
        // Soumission forcee (nouveau Content) de chaque cible, isolee par template
        // (un echec n'empeche pas les autres, cause persistee via marquerEchecSoumission).
        int soumis = 0;
        int echecs = 0;
        for (final TenantWhatsappTemplate t : cibles) {
            try {
                soumettreUn(t, true);
                soumis++;
            } catch (final Exception ex) {
                log.warn("Echec re-soumission forcee - tenantId: {}, name: {}, cause: {}",
                        tenantId, t.getTemplateName(), ex.getMessage());
                marquerEchecSoumission(t, ex);
                echecs++;
            }
        }
        log.info("Re-soumission forcee de tous les templates - tenantId: {}, soumis: {}, echecs: {}, orphelins purges: {}",
                tenantId, soumis, echecs, purges);
        return new ResultatSoumission(soumis, echecs, cibles.size());
    }

    /**
     * Re-soumet UN seul template (bouton admin par ligne). Reinitialise puis soumet
     * ce template sans toucher aux autres — utile pour relancer un modele en erreur
     * ou rejete sans perturber les modeles deja approuves qui servent a l'envoi.
     *
     * <p>Si le template est orphelin (YAML absent, ex: ancien nom renomme), la ligne
     * est purgee (on ne peut pas le re-soumettre, son YAML n'existe plus).</p>
     */
    @Transactional
    public ResultatSoumission resoumettreUn(final Long tenantId, final String templateName) {
        final TenantWhatsappTemplate template = templateRepository
                .findByTenantIdAndTemplateName(tenantId, templateName)
                .orElseThrow(() -> new IllegalStateException(
                        "Template introuvable pour tenantId=" + tenantId + ", name=" + templateName));
        if (templateLoader.parNom(templateName) == null) {
            templateRepository.delete(template);
            log.warn("Template orphelin purge (re-soumission unitaire) - tenantId: {}, name: {}",
                    tenantId, templateName);
            return new ResultatSoumission(0, 0, 0);
        }
        // On NE nulle PAS le SID : l'ancien Content approuve reste actif pour l'envoi
        // jusqu'au swap atomique en fin de soumettreUn (evite une coupure d'envoi).
        template.setStatut(StatutTemplateEnum.EN_ATTENTE_SOUMISSION);
        template.setMetaRejectReason(null);
        template.setSoumisLe(null);
        template.setProchaineTentativeLe(null);
        template.setNbTentatives(0);
        templateRepository.save(template);
        try {
            soumettreUn(template, true);
            return new ResultatSoumission(1, 0, 1);
        } catch (final Exception ex) {
            log.warn("Echec re-soumission unitaire - tenantId: {}, name: {}, cause: {}",
                    tenantId, templateName, ex.getMessage());
            marquerEchecSoumission(template, ex);
            return new ResultatSoumission(0, 1, 1);
        }
    }

    /**
     * Marque un template en echec de soumission : back-off exponentiel + cause
     * persistee dans {@code metaRejectReason} (visible dans l'UI admin, sinon l'echec
     * est silencieux). Le statut reste EN_ATTENTE_SOUMISSION (re-tentable).
     */
    private void marquerEchecSoumission(final TenantWhatsappTemplate template, final Exception ex) {
        template.setNbTentatives(template.getNbTentatives() + 1);
        template.setProchaineTentativeLe(LocalDateTime.now()
                .plusMinutes(Math.min(60L, (long) Math.pow(2, template.getNbTentatives()))));
        final String cause = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        template.setMetaRejectReason(cause.length() > 500 ? cause.substring(0, 500) : cause);
        templateRepository.save(template);
    }

    /**
     * Soumet un template. En dry-run, simule l'approbation immediate.
     *
     * @param forcerNouveauContent si true, cree TOUJOURS un nouveau Content Twilio
     *   (re-soumission apres changement de YAML) au lieu de reutiliser le SID existant.
     *   Le SID du template n'est ecrase qu'a la fin, en cas de succes : l'ancien Content
     *   approuve reste actif pour l'envoi tant que la nouvelle soumission n'a pas abouti
     *   (pas de fenetre de coupure).
     */
    private void soumettreUn(final TenantWhatsappTemplate template, final boolean forcerNouveauContent) {
        final LoadedTemplate loaded = templateLoader.parNom(template.getTemplateName());
        if (loaded == null) {
            throw new IllegalStateException(
                    "Template introuvable dans le classpath: " + template.getTemplateName());
        }
        if (twilioConfig.isDryRun()) {
            // Simulation : on attribue un SID factice et on passe directement
            // a APPROUVE pour debloquer le QA local.
            template.setTemplateSid("HX_dryrun_" + template.getTemplateName());
            template.setStatut(StatutTemplateEnum.APPROUVE);
            template.setMetaRejectReason(null);
            template.setSoumisLe(LocalDateTime.now());
            template.setDateDerniereSynchro(LocalDateTime.now());
            template.setYamlContentHash(loaded.contentHashSha256());
            templateRepository.save(template);
            log.info("DRY-RUN : template auto-approuve - tenantId: {}, name: {}",
                    template.getTenantId(), template.getTemplateName());
            return;
        }
        // Mode reel : appel Twilio Content API avec credentials sub-account du tenant.
        final TenantWhatsappConfig config = configRepository.findByTenantId(template.getTenantId())
                .orElseThrow(() -> new IllegalStateException(
                        "Aucune config WhatsApp pour tenantId=" + template.getTenantId()));
        if (config.getTwilioSubAccountSid() == null || config.getTwilioSubAuthToken() == null) {
            throw new IllegalStateException(
                    "Sub-account Twilio absent pour tenantId=" + template.getTenantId()
                    + " - lance le flow Self Sign-up avant de soumettre les templates.");
        }
        // 1. Content : reutiliser celui pre-cree dans demarrer() s'il existe (on saute
        //    creerContent -> 1 seul appel, soumission plus rapide). Sinon on le cree
        //    maintenant (fallback si la pre-creation a echoue). Le token est dechiffre
        //    par EncryptedStringConverter a la lecture et ne reste en memoire que le
        //    temps du call.
        final String ancienSid = template.getTemplateSid();
        String contentSid = ancienSid;
        if (forcerNouveauContent || contentSid == null || contentSid.isBlank()) {
            contentSid = contentApiClient.creerContent(
                    config.getTwilioSubAccountSid(),
                    config.getTwilioSubAuthToken(),
                    loaded.definition()
            ).sid();
        }
        // 2. Soumettre pour approbation Meta. Si le Content pre-cree est devenu
        //    invalide (ex: 404 apres changement de compte / expiration), on le recree
        //    puis on resoumet une fois.
        try {
            contentApiClient.soumettreApprobationWhatsApp(
                    config.getTwilioSubAccountSid(),
                    config.getTwilioSubAuthToken(),
                    contentSid,
                    loaded.definition().name(),
                    loaded.definition().category()
            );
        } catch (final Exception ex) {
            log.warn("Soumission Meta echouee (Content peut-etre perime), re-creation - "
                    + "tenantId: {}, name: {}, cause: {}",
                    template.getTenantId(), template.getTemplateName(), ex.getMessage());
            contentSid = contentApiClient.creerContent(
                    config.getTwilioSubAccountSid(),
                    config.getTwilioSubAuthToken(),
                    loaded.definition()
            ).sid();
            contentApiClient.soumettreApprobationWhatsApp(
                    config.getTwilioSubAccountSid(),
                    config.getTwilioSubAuthToken(),
                    contentSid,
                    loaded.definition().name(),
                    loaded.definition().category()
            );
        }
        // 3. Persister l'etat SOUMIS.
        template.setTemplateSid(contentSid);
        template.setStatut(StatutTemplateEnum.SOUMIS);
        // Effacer le motif de rejet de la tentative precedente : le template vient
        // d'etre re-soumis (nouveau Content, pending cote Twilio). Sans ce reset, le
        // front continue d'afficher l'ancien rejet sur un template "Soumis" -> message
        // contradictoire et trompeur pour le manager.
        template.setMetaRejectReason(null);
        template.setSoumisLe(LocalDateTime.now());
        template.setDateDerniereSynchro(LocalDateTime.now());
        template.setYamlContentHash(loaded.contentHashSha256());
        templateRepository.save(template);
        log.info("Template soumis a Meta - tenantId: {}, name: {}, contentSid: {}",
                template.getTenantId(), template.getTemplateName(), contentSid);
    }

    /**
     * Polling : pour chaque template en SOUMIS, demande a Twilio/Meta le statut
     * d'approbation. En dry-run, no-op (les templates passent directement a
     * APPROUVE lors de la soumission).
     */
    @Transactional
    public void raffraichirStatuts(final Long tenantId) {
        if (twilioConfig.isDryRun()) {
            return;
        }
        final List<TenantWhatsappTemplate> soumis =
                templateRepository.findAllByTenantIdAndStatut(tenantId, StatutTemplateEnum.SOUMIS);
        if (soumis.isEmpty()) {
            return;
        }
        final TenantWhatsappConfig config = configRepository.findByTenantId(tenantId)
                .orElse(null);
        if (config == null || config.getTwilioSubAccountSid() == null
                || config.getTwilioSubAuthToken() == null) {
            log.warn("Polling impossible : credentials sub-account manquants - tenantId: {}", tenantId);
            return;
        }
        for (final TenantWhatsappTemplate template : soumis) {
            if (template.getTemplateSid() == null) {
                continue;
            }
            try {
                final TwilioContentApiClient.ApprovalStatus approval = contentApiClient
                        .recupererApprobations(
                                config.getTwilioSubAccountSid(),
                                config.getTwilioSubAuthToken(),
                                template.getTemplateSid());
                template.setDateDerniereSynchro(LocalDateTime.now());
                if (approval.approuve()) {
                    template.setStatut(StatutTemplateEnum.APPROUVE);
                    template.setMetaRejectReason(null);
                    log.info("Template approuve par Meta - tenantId: {}, name: {}",
                            tenantId, template.getTemplateName());
                } else if (approval.rejete()) {
                    template.setStatut(StatutTemplateEnum.REJETE);
                    template.setMetaRejectReason(approval.rejectionReason());
                    log.warn("Template rejete par Meta - tenantId: {}, name: {}, raison: {}",
                            tenantId, template.getTemplateName(), approval.rejectionReason());
                } else if (template.getMetaRejectReason() != null) {
                    // Etat d'attente (received / pending) : aucun rejet en cours. On efface
                    // le motif de rejet perime d'une tentative precedente, sinon le front
                    // continue d'afficher une erreur sur un template "Soumis" -> message
                    // contradictoire et trompeur. Nettoie aussi les lignes deja en base.
                    template.setMetaRejectReason(null);
                    log.info("Motif de rejet perime efface (template de nouveau en attente) "
                            + "- tenantId: {}, name: {}", tenantId, template.getTemplateName());
                }
                templateRepository.save(template);
            } catch (final Exception ex) {
                log.warn("Echec polling Meta - tenantId: {}, name: {}, cause: {}",
                        tenantId, template.getTemplateName(), ex.getMessage());
            }
        }
    }

    /**
     * Tous les templates approuves sont-ils prets pour ce tenant ?
     * Utilise pour bloquer ou autoriser l'envoi de messages.
     */
    public boolean tousLesTemplatesPrets(final Long tenantId, final TenantWhatsappConfig config) {
        if (config == null) {
            return false;
        }
        // NB : les lignes orphelines (YAML absent, ex: ancien nom renomme) sont traitees
        // a la source (migration V072 de renommage + purge dans resoumettreTous), pas ici :
        // coupler ce controle a templateLoader alourdirait ce chemin chaud.
        final List<TenantWhatsappTemplate> tous = templateRepository.findAllByTenantId(tenantId);
        if (tous.isEmpty()) {
            return false;
        }
        return tous.stream().allMatch(t -> t.getStatut() == StatutTemplateEnum.APPROUVE);
    }

    /**
     * Liste les templates en attente / rejetes pour ce tenant. Utilise par l'UI
     * d'activation pour afficher la timeline.
     */
    public List<TenantWhatsappTemplate> templatesNonPrets(final Long tenantId) {
        return templateRepository.findAllByTenantId(tenantId).stream()
                .filter(t -> t.getStatut() != StatutTemplateEnum.APPROUVE)
                .toList();
    }

    public List<TenantWhatsappTemplate> tousLesTemplatesDuTenant(final Long tenantId) {
        return templateRepository.findAllByTenantId(tenantId);
    }

    public record ResultatSoumission(int soumis, int echecs, int total) {
    }
}
