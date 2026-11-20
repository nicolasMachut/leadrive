package fr.leadrevive.rgpd.service;

import fr.leadrevive.ai.repository.AiLogRepository;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.repository.HistoriqueStatutRepository;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.outreach.repository.ConversationAnalyseRepository;
import fr.leadrevive.outreach.repository.SmsConversationRepository;
import fr.leadrevive.outreach.repository.SmsMessageRepository;
import fr.leadrevive.tache.repository.TacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service RGPD : suppression et export des donnees personnelles d'un prospect.
 * Art. 17 (droit a l'effacement) et Art. 20 (portabilite).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RgpdService {

    private final LeadRepository leadRepository;
    private final SmsConversationRepository smsConversationRepository;
    private final SmsMessageRepository smsMessageRepository;
    private final ConversationAnalyseRepository conversationAnalyseRepository;
    private final HistoriqueStatutRepository historiqueStatutRepository;
    private final TacheRepository tacheRepository;
    private final AiLogRepository aiLogRepository;

    /**
     * Supprime toutes les donnees d'un prospect identifie par son telephone ou email.
     * Cascade : taches, historique statut, messages, analyses, conversations, AI logs, lead.
     */
    @Transactional
    public int supprimerDonneesProspect(final Long tenantId, final String telephone, final String email) {
        log.info("RGPD suppression demandee - tenantId: {}, telephone: {}, email: {}", tenantId,
                telephone != null ? telephone.substring(0, Math.min(4, telephone.length())) + "***" : null,
                email != null ? email.substring(0, Math.min(3, email.length())) + "***" : null);

        final List<Lead> leads = leadRepository.findByTenantId(tenantId).stream()
                .filter(l -> (telephone != null && telephone.equals(l.getTelephone()))
                        || (email != null && email.equalsIgnoreCase(l.getEmail())))
                .toList();

        if (leads.isEmpty()) {
            log.info("RGPD suppression - aucun lead trouve");
            return 0;
        }

        int count = 0;
        for (final Lead lead : leads) {
            final Long leadId = lead.getId();

            // Supprimer les taches liees
            tacheRepository.findByLeadIdOrderByDateEcheanceDesc(leadId)
                    .forEach(tacheRepository::delete);

            // Supprimer l'historique de statut
            historiqueStatutRepository.findByLeadIdOrderByCreeLeDesc(leadId)
                    .forEach(historiqueStatutRepository::delete);

            // Supprimer les conversations + messages + analyses
            smsConversationRepository.findByLeadIdAndTenantIdOrderByCreeLe(leadId, tenantId)
                    .forEach(conv -> {
                        conversationAnalyseRepository.findByConversationIdOrderByCreeLe(conv.getConversationId())
                                .forEach(conversationAnalyseRepository::delete);
                        smsMessageRepository.findByConversationIdOrderByOrdre(conv.getConversationId())
                                .forEach(smsMessageRepository::delete);
                        smsConversationRepository.delete(conv);
                    });

            // Supprimer les AI logs lies au lead (DELETE cible, pas findAll() qui chargerait
            // toute la table ai_log en memoire => OOM en prod).
            aiLogRepository.deleteByLeadIdAndTenantId(leadId, tenantId);

            // Supprimer le lead
            leadRepository.delete(lead);
            count++;
        }

        log.info("RGPD suppression terminee - {} leads supprimes avec toutes leurs donnees", count);
        return count;
    }

    /**
     * Exporte TOUTES les donnees d'un prospect (Art. 20 portabilite).
     * Inclut : lead complet + conversations + messages + analyses IA + historique statut + taches.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> exporterDonneesProspect(final Long tenantId, final String telephone, final String email) {
        log.info("RGPD export demande - tenantId: {}", tenantId);

        final List<Lead> leads = leadRepository.findByTenantId(tenantId).stream()
                .filter(l -> (telephone != null && telephone.equals(l.getTelephone()))
                        || (email != null && email.equalsIgnoreCase(l.getEmail())))
                .toList();

        final Map<String, Object> export = new LinkedHashMap<>();
        export.put("genereLe", java.time.LocalDateTime.now().toString());
        export.put("tenantId", tenantId);

        export.put("leads", leads.stream().map(l -> {
            final Map<String, Object> leadExport = new LinkedHashMap<>();
            leadExport.put("id", l.getId());
            leadExport.put("nom", strOuVide(l.getNom()));
            leadExport.put("telephone", strOuVide(l.getTelephone()));
            leadExport.put("email", strOuVide(l.getEmail()));
            leadExport.put("marque", strOuVide(l.getMarque()));
            leadExport.put("modele", strOuVide(l.getModele()));
            leadExport.put("source", strOuVide(l.getSource()));
            leadExport.put("territoire", strOuVide(l.getTerritoire()));
            leadExport.put("etape", strOuVide(l.getEtape()));
            leadExport.put("statut", l.getStatut().name());
            leadExport.put("scoring", l.getScoring());
            leadExport.put("hotnessScore", l.getHotnessScore());
            leadExport.put("optOutSms", l.getOptOutSms());
            leadExport.put("consentementContact", l.getConsentementContact());
            leadExport.put("consentementDate", l.getConsentementDate() != null ? l.getConsentementDate().toString() : null);
            leadExport.put("consentementSource", l.getConsentementSource());
            leadExport.put("creeLe", l.getCreeLe() != null ? l.getCreeLe().toString() : null);
            leadExport.put("donneesSupplementaires", l.getDonneesSupplementaires());

            // Conversations + messages + analyses
            leadExport.put("conversations", smsConversationRepository
                    .findByLeadIdAndTenantIdOrderByCreeLe(l.getId(), tenantId).stream()
                    .map(conv -> {
                        final Map<String, Object> convExport = new LinkedHashMap<>();
                        convExport.put("conversationId", conv.getConversationId());
                        convExport.put("statut", conv.getStatut().name());
                        convExport.put("modePilotage", conv.getModePilotage() != null ? conv.getModePilotage().name() : null);
                        convExport.put("creeLe", conv.getCreeLe() != null ? conv.getCreeLe().toString() : null);
                        convExport.put("messages", smsMessageRepository
                                .findByConversationIdOrderByOrdre(conv.getConversationId()).stream()
                                .map(m -> Map.of(
                                        "ordre", m.getOrdre(),
                                        "direction", m.getDirection().name(),
                                        "expediteur", m.getExpediteur() != null ? m.getExpediteur().name() : "",
                                        "contenu", strOuVide(m.getContenu()),
                                        "statut", m.getStatut().name(),
                                        "creeLe", m.getCreeLe() != null ? m.getCreeLe().toString() : ""
                                )).toList());
                        convExport.put("analyses", conversationAnalyseRepository
                                .findByConversationIdOrderByCreeLe(conv.getConversationId()).stream()
                                .map(a -> {
                                    final Map<String, Object> aExport = new LinkedHashMap<>();
                                    aExport.put("intention", a.getIntention() != null ? a.getIntention().name() : null);
                                    aExport.put("signauxAchat", a.getSignauxAchat());
                                    aExport.put("objections", a.getObjections());
                                    aExport.put("besoinHandoff", a.getBesoinHandoff());
                                    aExport.put("raisonHandoff", a.getRaisonHandoff());
                                    aExport.put("resumeVendeur", a.getResumeVendeur());
                                    aExport.put("tonProspect", a.getTonProspect());
                                    aExport.put("rdvCreneau", a.getRdvCreneau());
                                    aExport.put("creeLe", a.getCreeLe() != null ? a.getCreeLe().toString() : null);
                                    return aExport;
                                }).toList());
                        return convExport;
                    }).toList());

            // Historique de statut
            leadExport.put("historiqueStatut", historiqueStatutRepository
                    .findByLeadIdOrderByCreeLeDesc(l.getId()).stream()
                    .map(h -> Map.of(
                            "statutPrecedent", h.getStatutPrecedent() != null ? h.getStatutPrecedent().name() : "",
                            "statutSuivant", h.getStatutSuivant().name(),
                            "source", strOuVide(h.getSource()),
                            "creeLe", h.getCreeLe() != null ? h.getCreeLe().toString() : ""
                    )).toList());

            // Taches
            leadExport.put("taches", tacheRepository
                    .findByLeadIdOrderByDateEcheanceDesc(l.getId()).stream()
                    .map(t -> Map.of(
                            "type", t.getTypeTache() != null ? t.getTypeTache().name() : "",
                            "statut", t.getStatut() != null ? t.getStatut().name() : "",
                            "dateEcheance", t.getDateEcheance() != null ? t.getDateEcheance().toString() : "",
                            "creeLe", t.getCreeLe() != null ? t.getCreeLe().toString() : ""
                    )).toList());

            return leadExport;
        }).toList());

        return export;
    }

    private static String strOuVide(final String s) {
        return s != null ? s : "";
    }
}
