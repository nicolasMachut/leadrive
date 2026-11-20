package fr.leadrevive.feedback.service;

import fr.leadrevive.feedback.dto.RepFeedbackRdvDto;
import fr.leadrevive.feedback.dto.RepImpactFeedbackDto;
import fr.leadrevive.feedback.dto.RepStatsPrecisionDto;
import fr.leadrevive.feedback.dto.ReqAbandonRdvDto;
import fr.leadrevive.feedback.dto.ReqFeedbackRdvDto;
import fr.leadrevive.feedback.entity.FeedbackRdv;
import fr.leadrevive.feedback.entity.QualiteQualificationEnum;
import fr.leadrevive.feedback.entity.RaisonAbandonEnum;
import fr.leadrevive.feedback.entity.SuitePrevueEnum;
import fr.leadrevive.feedback.repository.FeedbackRdvRepository;
import fr.leadrevive.common.security.AuthService;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.tache.service.TacheIAService;
import fr.leadrevive.tache.service.TacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackRdvService {

    private final FeedbackRdvRepository feedbackRdvRepository;
    private final LeadRepository leadRepository;
    private final TacheIAService tacheIAService;
    private final TacheService tacheService;
    private final AuthService authService;

    /**
     * Verifie que le lead cible appartient au tenant courant ET, pour un VENDEUR,
     * qu'il en est le proprietaire. Sans ce controle, un feedback/abandon pouvait
     * etre pose sur le lead d'un autre vendeur (voire d'un autre tenant si rdvId
     * absent → aucune 2e barriere via changerStatut). 404 anonyme (pas d'oracle).
     */
    private void verifierAccesLead(final Long leadId, final Long tenantId, final Long vendeurId) {
        final Lead lead = leadRepository.findByIdAndTenantId(leadId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead introuvable"));
        if ("VENDEUR".equals(authService.getCurrentRole())
                && lead.getVendeurId() != null
                && !lead.getVendeurId().equals(vendeurId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead introuvable");
        }
    }

    private static final int[][] PALIERS = {
            {0, 4},
            {5, 14},
            {15, 29},
            {30, 49},
            {50, Integer.MAX_VALUE}
    };
    private static final String[] PALIERS_EMOJI = {"\uD83C\uDF31", "\uD83C\uDF3F", "\uD83C\uDF33", "\u2B50", "\uD83C\uDFC6"};
    private static final String[] PALIERS_NOM = {"Premiers pas", "En croissance", "Fiable", "Expert", "Maître"};
    private static final String[] PALIERS_DESC = {
            "Votre IA commence à apprendre",
            "Votre IA prend ses marques",
            "Votre IA vous connaît bien",
            "Votre IA est affûtée",
            "Votre IA est redoutable"
    };

    private static final int SEUIL_PRECISION_VISIBLE = 20;
    private static final int IMPACT_DELAI_JOURS = 7;

    @Transactional
    public RepFeedbackRdvDto enregistrerFeedback(final ReqFeedbackRdvDto request, final Long tenantId, final Long vendeurId) {
        log.info("Enregistrement feedback RDV - tenantId: {}, leadId: {}, qualite: {}, suite: {}",
                tenantId, request.leadId(), request.qualiteAppel(), request.suitePrevue());

        // Ownership : le lead doit appartenir au tenant (et au vendeur si VENDEUR).
        verifierAccesLead(request.leadId(), tenantId, vendeurId);

        // Anti-doublon : un meme lead ne peut avoir qu'un seul feedback de cloture.
        // Sans ca, le vendeur pouvait rejouer le slide-up et fausser le calcul de
        // precision IA (2 avis contradictoires sur le meme RDV = stats biaisees).
        feedbackRdvRepository
                .findFirstByLeadIdAndTenantIdAndQualiteAppelIsNotNullOrderByCreeLe(request.leadId(), tenantId)
                .ifPresent(existant -> {
                    throw new IllegalStateException(
                            "Un feedback a deja ete enregistre pour ce RDV (id: " + existant.getId() + ").");
                });

        final long avantTotal = feedbackRdvRepository.countByTenantIdAndQualiteAppelIsNotNull(tenantId);

        final FeedbackRdv feedback = new FeedbackRdv();
        feedback.setTenantId(tenantId);
        feedback.setLeadId(request.leadId());
        feedback.setRdvId(request.rdvId());
        feedback.setVendeurId(vendeurId);
        feedback.setQualiteAppel(QualiteQualificationEnum.valueOf(request.qualiteAppel()));
        final SuitePrevueEnum suite = SuitePrevueEnum.valueOf(request.suitePrevue());
        feedback.setSuitePrevue(suite);
        feedbackRdvRepository.save(feedback);

        // Si le vendeur declare une vente en cours, on cree une tache "Confirmer la
        // vente" dans son planning. Sans cela, le feedback est enregistre mais aucune
        // vente n'apparait en BDD => stats CA biaisees + workflow casse.
        if (suite == SuitePrevueEnum.VENTE_EN_COURS) {
            tacheIAService.onVenteEnCoursDeclaree(request.leadId(), tenantId, vendeurId);
        }

        final int total = (int) (avantTotal + 1);
        final int palierIndex = trouverPalierIndex(total);
        final int palierIndexAvant = trouverPalierIndex((int) avantTotal);
        final boolean nouveauPalier = palierIndex > palierIndexAvant;

        final boolean precisionVisible = total >= SEUIL_PRECISION_VISIBLE;
        final int precision = precisionVisible ? calculerPrecisionIa(tenantId) : 0;

        return RepFeedbackRdvDto.builder()
                .feedbackId(feedback.getId())
                .totalFeedbacks(total)
                .precisionIa(precision)
                .precisionVisible(precisionVisible)
                .palierActuel(formaterPalier(palierIndex))
                .palierSuivant(palierIndex < PALIERS.length - 1 ? formaterPalier(palierIndex + 1) : null)
                .feedbacksRestantsPalier(palierIndex < PALIERS.length - 1 ? PALIERS[palierIndex + 1][0] - total : 0)
                .nouveauPalier(nouveauPalier)
                .build();
    }

    @Transactional
    public void enregistrerAbandon(final ReqAbandonRdvDto request, final Long tenantId, final Long vendeurId) {
        log.info("Enregistrement abandon RDV - tenantId: {}, leadId: {}, raison: {}",
                tenantId, request.leadId(), request.raisonAbandon());

        // Ownership : le lead doit appartenir au tenant (et au vendeur si VENDEUR).
        // Indispensable ICI car si rdvId est null, changerStatut() n'est pas appele
        // et ne fournit donc aucune 2e barriere de securite.
        verifierAccesLead(request.leadId(), tenantId, vendeurId);

        // Idempotence / anti-doublon : si un abandon existe deja pour ce lead
        // (double-clic, deux onglets, retry), on ne recree pas de motif et on ne
        // re-annule pas la tache (deja ANNULEE). Sinon on gonfle les stats d'abandon.
        if (feedbackRdvRepository
                .findFirstByLeadIdAndTenantIdAndRaisonAbandonIsNotNullOrderByCreeLe(request.leadId(), tenantId)
                .isPresent()) {
            log.info("Abandon RDV deja enregistre pour ce lead, no-op - tenantId: {}, leadId: {}",
                    tenantId, request.leadId());
            return;
        }

        final FeedbackRdv feedback = new FeedbackRdv();
        feedback.setTenantId(tenantId);
        feedback.setLeadId(request.leadId());
        feedback.setRdvId(request.rdvId());
        feedback.setVendeurId(vendeurId);
        feedback.setRaisonAbandon(RaisonAbandonEnum.valueOf(request.raisonAbandon()));
        feedbackRdvRepository.save(feedback);

        // Abandon ATOMIQUE : dans la meme transaction, on annule aussi la tache RDV.
        // Avant, le front devait chainer 2 appels (POST /abandon PUIS PATCH ANNULEE) ;
        // si le 2e echouait on avait un motif enregistre mais un RDV toujours en retard
        // (feedback orphelin). changerStatut(ANNULEE) fait l'annulation + la cascade lead
        // (RDV_PRIS -> EN_COURS) + l'annulation des rappels J-1 en attente. On ne l'appelle
        // que si un rdvId est fourni (un abandon peut viser un lead sans tache liee).
        if (request.rdvId() != null) {
            // Garde-fou : la tache RDV doit appartenir au leadId (evite qu'un payload
            // forge annule le RDV d'un autre lead du meme vendeur).
            tacheService.annulerRdvDeLead(request.rdvId(), tenantId, request.leadId());
        }
    }

    /**
     * UNDO d'une action RDV (Effectue ou Abandonner) : supprime le feedback cree pour
     * ce RDV et rouvre la tache (A_FAIRE + lead RDV_PRIS). Declenche par le bouton
     * "Annuler" du toast, dans les secondes qui suivent l'action.
     */
    @Transactional
    public void annulerDerniereAction(final Long leadId, final Long rdvId, final Long tenantId, final Long vendeurId) {
        log.info("Annulation derniere action RDV (undo) - tenantId: {}, leadId: {}, rdvId: {}",
                tenantId, leadId, rdvId);
        verifierAccesLead(leadId, tenantId, vendeurId);
        // Garde-fou : la tache (rdvId) doit appartenir AU leadId ciblé. rouvrirTacheDeLead
        // valide rdvId ∈ leadId (404 sinon) AVANT toute mutation. Sans ce contrôle, un
        // payload forgé {leadId: L1(sien), rdvId: R2(autre lead)} aurait supprimé le
        // feedback + rouvert le RDV d'un autre lead. On rouvre d'abord (donc on valide),
        // puis on supprime le feedback seulement si la validation est passée.
        tacheService.rouvrirTacheDeLead(rdvId, tenantId, leadId);
        feedbackRdvRepository.deleteByRdvIdAndTenantId(rdvId, tenantId);
    }

    @Transactional(readOnly = true)
    public int calculerPrecisionIa(final Long tenantId) {
        final long positifs = feedbackRdvRepository.countByTenantIdAndQualiteAppelIn(
                tenantId, List.of(QualiteQualificationEnum.CHAUD, QualiteQualificationEnum.OK));
        final long total = feedbackRdvRepository.countByTenantIdAndQualiteAppelIsNotNull(tenantId);
        if (total == 0) return 0;
        return (int) Math.round((double) positifs / total * 100);
    }

    @Transactional(readOnly = true)
    public RepStatsPrecisionDto getStatsPrecision(final Long tenantId) {
        log.info("Recuperation stats precision IA - tenantId: {}", tenantId);
        final int total = (int) feedbackRdvRepository.countByTenantIdAndQualiteAppelIsNotNull(tenantId);
        final boolean precisionVisible = total >= SEUIL_PRECISION_VISIBLE;
        final int palierIndex = trouverPalierIndex(total);

        return RepStatsPrecisionDto.builder()
                .totalFeedbacks(total)
                .precisionVisible(precisionVisible)
                .precisionIa(precisionVisible ? calculerPrecisionIa(tenantId) : 0)
                .palierActuel(formaterPalier(palierIndex))
                .palierDescription(PALIERS_DESC[palierIndex])
                .palierSuivant(palierIndex < PALIERS.length - 1 ? formaterPalier(palierIndex + 1) : null)
                .feedbacksRestantsPalier(palierIndex < PALIERS.length - 1 ? PALIERS[palierIndex + 1][0] - total : 0)
                .build();
    }

    @Transactional(readOnly = true)
    public List<RepImpactFeedbackDto> getImpactsEnAttente(final Long tenantId, final Long vendeurId) {
        final LocalDateTime seuil = LocalDateTime.now().minusDays(IMPACT_DELAI_JOURS);
        final List<FeedbackRdv> impacts = feedbackRdvRepository.findImpactsEnAttente(tenantId, vendeurId, seuil);
        return impacts.stream()
                .map(f -> {
                    final String nom = leadRepository.findByIdAndTenantId(f.getLeadId(), tenantId)
                            .map(Lead::getNom)
                            .orElse("ce prospect");
                    return RepImpactFeedbackDto.builder()
                            .feedbackId(f.getId())
                            .leadId(f.getLeadId())
                            .nomProspect(nom)
                            .build();
                })
                .toList();
    }

    /**
     * Genere une section "insights de vos retours" pour le briefing manager,
     * uniquement si la concession a des feedbacks. Retourne null sinon.
     */
    @Transactional(readOnly = true)
    public String genererInsightsBriefing(final Long tenantId) {
        final long total = feedbackRdvRepository.countByTenantIdAndQualiteAppelIsNotNull(tenantId);
        if (total == 0) return null;

        final long rate = feedbackRdvRepository.countByTenantIdAndQualiteAppel(tenantId, QualiteQualificationEnum.RATE);
        final long totalSuite = feedbackRdvRepository.countByTenantIdAndSuitePrevueIsNotNull(tenantId);
        final long perdu = feedbackRdvRepository.countByTenantIdAndSuitePrevue(tenantId, SuitePrevueEnum.PERDU);

        final int tauxRate = (int) Math.round((double) rate / total * 100);
        final int tauxBienQualifie = 100 - tauxRate;
        final int tauxPerdu = totalSuite > 0 ? (int) Math.round((double) perdu / totalSuite * 100) : 0;

        final StringBuilder sb = new StringBuilder();
        sb.append("Sur les ").append(total).append(" derniers RDV avec retour vendeur, ")
                .append(tauxBienQualifie).append("% étaient bien qualifiés par l'IA.");

        if (tauxRate > 30) {
            sb.append(" Attention : ").append(tauxRate)
                    .append("% des RDV ont été signalés comme mal qualifiés. L'IA a besoin de plus de contexte sur vos critères.");
        }
        if (totalSuite >= 10 && tauxPerdu > 30) {
            sb.append(" ").append(tauxPerdu)
                    .append("% des RDV aboutissent à un \"Perdu\" — vérifiez les critères de scoring.");
        }
        return sb.toString();
    }

    @Transactional
    public void marquerImpactVu(final Long feedbackId, final Long tenantId) {
        log.info("Marquage impact feedback vu - feedbackId: {}, tenantId: {}", feedbackId, tenantId);
        // SECURITE : 404 anonyme si cross-tenant pour ne pas laisser silencieusement
        // l'attaquant pinguer des IDs et constater "no-op" (oracle).
        final var feedback = feedbackRdvRepository.findById(feedbackId)
                .filter(f -> tenantId != null && tenantId.equals(f.getTenantId()))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Feedback introuvable"));
        feedback.setImpactNotifie(true);
        feedbackRdvRepository.save(feedback);
    }

    private int trouverPalierIndex(final int total) {
        for (int i = 0; i < PALIERS.length; i++) {
            if (total >= PALIERS[i][0] && total <= PALIERS[i][1]) {
                return i;
            }
        }
        return 0;
    }

    private String formaterPalier(final int index) {
        return PALIERS_EMOJI[index] + " " + PALIERS_NOM[index];
    }
}
