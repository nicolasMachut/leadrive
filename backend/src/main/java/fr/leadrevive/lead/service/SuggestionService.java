package fr.leadrevive.lead.service;

import fr.leadrevive.lead.dto.RepRdvAVenirDto;
import fr.leadrevive.lead.dto.RepSuggestionDto;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.entity.LeadStatutEnum;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.outreach.entity.ConversationAnalyse;
import fr.leadrevive.outreach.entity.StatutConversationSmsEnum;
import fr.leadrevive.outreach.repository.ConversationAnalyseRepository;
import fr.leadrevive.outreach.repository.SmsConversationRepository;
import fr.leadrevive.tache.entity.StatutTacheEnum;
import fr.leadrevive.tache.entity.TypeTacheEnum;
import fr.leadrevive.tache.repository.TacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Genere des suggestions de leads a contacter.
 * Regles hardcodees pour le MVP :
 * - Leads NOUVEAU jamais contactes (pas de conversation active)
 * - Leads CONTACTE sans reponse depuis longtemps
 * - Tries par scoring decroissant
 * - Opt-out exclus
 * - Max 20 suggestions
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SuggestionService {

    private static final int MAX_SUGGESTIONS = 20;

    private final LeadRepository leadRepository;
    private final SmsConversationRepository smsConversationRepository;
    private final ConversationAnalyseRepository conversationAnalyseRepository;
    private final fr.leadrevive.user.repository.UserRepository userRepository;
    private final TacheRepository tacheRepository;

    @Transactional(readOnly = true)
    public List<RepSuggestionDto> getSuggestions(final Long tenantId, final Long vendeurId) {
        log.info("Generation des suggestions - tenantId: {}, vendeurId: {}", tenantId, vendeurId);

        // Filtre vendeur strict : un vendeur ne voit QUE ses leads. Les
        // non-assignes ne remontent pas dans ses suggestions.
        // Surface operationnelle (suggestions d'action) : on exclut les archives des la requete,
        // coherent avec getRdvAVenir (qui filtrait deja !getArchive()). Un lead archive ne doit
        // plus generer de suggestion.
        final List<Lead> tousLesLeads = leadRepository.findByTenantIdAndArchiveFalse(tenantId).stream()
                .filter(l -> vendeurId == null || (l.getVendeurId() != null && vendeurId.equals(l.getVendeurId())))
                .toList();

        // IDs des leads qui ont deja une conversation active (EN_COURS, RDV_OBTENU)
        final Set<Long> leadsAvecConversationActive = smsConversationRepository
                .findByTenantIdOrderByDernierMessageLeDesc(tenantId).stream()
                .filter(c -> c.getStatut() == StatutConversationSmsEnum.EN_COURS
                        || c.getStatut() == StatutConversationSmsEnum.RDV_OBTENU)
                .map(c -> c.getLeadId())
                .collect(Collectors.toSet());

        final List<RepSuggestionDto> suggestions = new ArrayList<>();

        for (final Lead lead : tousLesLeads) {
            // Exclure les opt-out
            if (Boolean.TRUE.equals(lead.getOptOutSms())) continue;

            // Exclure les leads termines
            if (lead.getStatut() == LeadStatutEnum.TERMINE) continue;

            // Exclure les leads avec une conversation active
            if (leadsAvecConversationActive.contains(lead.getId())) continue;

            final String raison;
            final String priorite;

            if (lead.getStatut() == LeadStatutEnum.NOUVEAU) {
                // Jamais contacte
                final BigDecimal score = effectiveScore(lead);
                if (score.compareTo(new BigDecimal("0.70")) >= 0) {
                    raison = "Lead a fort potentiel jamais contacte (scoring " + score + ")";
                    priorite = "HAUTE";
                } else if (score.compareTo(new BigDecimal("0.40")) >= 0) {
                    raison = "Lead non contacte avec un scoring correct";
                    priorite = "MOYENNE";
                } else {
                    raison = "Lead a contacter";
                    priorite = "BASSE";
                }
            } else if (lead.getStatut() == LeadStatutEnum.CONTACTE) {
                // Contacte mais pas de conversation active — probablement pas de reponse
                raison = "Contacte sans reponse — relance recommandee";
                priorite = "MOYENNE";
            } else if (lead.getStatut() == LeadStatutEnum.EN_COURS) {
                // En cours mais sans conversation active — conversation terminee sans conclusion
                raison = "Conversation interrompue — a relancer";
                priorite = "HAUTE";
            } else if (lead.getStatut() == LeadStatutEnum.RDV_PRIS) {
                // RDV pris mais plus de conversation — suivi post-RDV
                raison = "RDV pris — suivi a effectuer";
                priorite = "BASSE";
            } else {
                continue;
            }

            suggestions.add(RepSuggestionDto.builder()
                    .idLead(lead.getId())
                    .nom(lead.getNom())
                    .telephone(lead.getTelephone())
                    .marque(lead.getMarque())
                    .modele(lead.getModele())
                    .territoire(lead.getTerritoire())
                    .source(lead.getSource())
                    .scoring(lead.getScoring())
                    .hotnessScore(lead.getHotnessScore())
                    .statut(lead.getStatut().name())
                    .creeLe(lead.getCreeLe())
                    .raison(raison)
                    .priorite(priorite)
                    .build());
        }

        // Trier : HAUTE d'abord, puis scoring decroissant
        suggestions.sort(Comparator
                .comparingInt((RepSuggestionDto s) -> prioriteOrdre(s.getPriorite()))
                .thenComparing(s -> effectiveScoreDto(s), Comparator.reverseOrder()));

        final List<RepSuggestionDto> result = suggestions.stream().limit(MAX_SUGGESTIONS).toList();
        log.info("Suggestions generees - tenantId: {}, total: {}, retournees: {}",
                tenantId, suggestions.size(), result.size());
        return result;
    }

    private BigDecimal effectiveScore(final Lead lead) {
        if (lead.getHotnessScore() != null) return lead.getHotnessScore();
        if (lead.getScoring() != null) return lead.getScoring();
        return BigDecimal.ZERO;
    }

    private BigDecimal effectiveScoreDto(final RepSuggestionDto s) {
        if (s.getHotnessScore() != null) return s.getHotnessScore();
        if (s.getScoring() != null) return s.getScoring();
        return BigDecimal.ZERO;
    }

    private int prioriteOrdre(final String priorite) {
        return switch (priorite) {
            case "HAUTE" -> 0;
            case "MOYENNE" -> 1;
            default -> 2;
        };
    }

    /**
     * Retourne les leads en RDV_PRIS avec le creneau et resume extraits par l'IA.
     */
    @Transactional(readOnly = true)
    public List<RepRdvAVenirDto> getRdvAVenir(final Long tenantId, final Long vendeurId) {
        log.info("Recherche RDV a venir - tenantId: {}, vendeurId: {}", tenantId, vendeurId);
        List<Lead> leads = leadRepository.findByTenantId(tenantId).stream()
                .filter(l -> l.getStatut() == LeadStatutEnum.RDV_PRIS)
                .filter(l -> !Boolean.TRUE.equals(l.getArchive()))
                .toList();
        // Filtre vendeur strict : un vendeur ne voit QUE les leads qui lui sont
        // assignes. Les leads non-assignes (vendeurId=null) sont reserves aux
        // managers qui doivent les redistribuer manuellement.
        if (vendeurId != null) {
            leads = leads.stream()
                    .filter(l -> l.getVendeurId() != null && vendeurId.equals(l.getVendeurId()))
                    .toList();
        }

        return leads.stream().map(l -> {
            // Derniere conversation du lead → derniere analyse avec rdvCreneau
            final var convs = smsConversationRepository.findByLeadIdAndTenantIdOrderByCreeLe(l.getId(), tenantId);
            String creneau = null;
            String resume = null;
            LocalDateTime rdvDateHeure = null;
            for (int i = convs.size() - 1; i >= 0 && rdvDateHeure == null; i--) {
                final List<ConversationAnalyse> analyses = conversationAnalyseRepository
                        .findByConversationIdOrderByCreeLe(convs.get(i).getConversationId());
                for (int j = analyses.size() - 1; j >= 0; j--) {
                    final ConversationAnalyse a = analyses.get(j);
                    if (rdvDateHeure == null && a.getRdvDateHeure() != null) {
                        rdvDateHeure = a.getRdvDateHeure();
                        creneau = a.getRdvCreneau();
                    }
                    if (resume == null && a.getResumeVendeur() != null) {
                        resume = a.getResumeVendeur();
                    }
                    if (rdvDateHeure != null && resume != null) break;
                }
            }
            // Formater le créneau à partir de la date absolue si disponible
            final String creneauAffiche = rdvDateHeure != null
                    ? formaterCreneauRelatif(rdvDateHeure)
                    : creneau;
            // Résoudre le nom du vendeur assigné
            String vendeurNom = null;
            if (l.getVendeurId() != null) {
                vendeurNom = userRepository.findById(l.getVendeurId())
                        .map(u -> u.getPrenom() + " " + u.getNom())
                        .orElse(null);
            }
            // Trouver la tache RDV associee au lead — source de verite pour l'heure du RDV
            final var tacheOpt = tacheRepository.findFirstByLeadIdAndTypeTacheAndStatutOrderByCreeLe(
                    l.getId(), TypeTacheEnum.RDV, StatutTacheEnum.A_FAIRE);
            final Long tacheId = tacheOpt.map(fr.leadrevive.tache.entity.Tache::getId).orElse(null);
            // Si la tache existe, son dateEcheance est la source de verite (pas l'extraction IA qui peut diverger)
            final LocalDateTime rdvDateHeureFinale = tacheOpt
                    .map(fr.leadrevive.tache.entity.Tache::getDateEcheance)
                    .orElse(rdvDateHeure);
            final String creneauFinal = rdvDateHeureFinale != null
                    ? formaterCreneauRelatif(rdvDateHeureFinale)
                    : creneauAffiche;
            return RepRdvAVenirDto.builder()
                    .idLead(l.getId())
                    .tacheId(tacheId)
                    .nom(l.getNom())
                    .marque(l.getMarque())
                    .modele(l.getModele())
                    .vendeurNom(vendeurNom)
                    .creneau(creneauFinal)
                    .rdvDateHeure(rdvDateHeureFinale)
                    .resumeVendeur(resume)
                    .build();
        })
        .sorted((a, b) -> {
            if (a.getRdvDateHeure() == null && b.getRdvDateHeure() == null) return 0;
            if (a.getRdvDateHeure() == null) return 1;
            if (b.getRdvDateHeure() == null) return 1;
            return a.getRdvDateHeure().compareTo(b.getRdvDateHeure());
        })
        .toList();
    }

    /**
     * Formate une date/heure absolue en texte relatif français.
     * Ex: "Aujourd'hui à 14h", "Demain à 10h", "Vendredi 18 à 18h"
     */
    private String formaterCreneauRelatif(final LocalDateTime dateHeure) {
        final LocalDate aujourdHui = LocalDate.now();
        final LocalDate dateRdv = dateHeure.toLocalDate();
        final long joursEcart = ChronoUnit.DAYS.between(aujourdHui, dateRdv);
        final String heure = dateHeure.getHour() + "h" + (dateHeure.getMinute() > 0 ? String.format("%02d", dateHeure.getMinute()) : "");

        if (joursEcart == 0) return "Aujourd'hui à " + heure;
        if (joursEcart == 1) return "Demain à " + heure;
        if (joursEcart == -1) return "Hier à " + heure;
        if (joursEcart < -1) return "Passé (" + dateRdv.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")) + ")";

        final String jourSemaine = dateRdv.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.FRENCH);
        final String jourCapitalise = jourSemaine.substring(0, 1).toUpperCase() + jourSemaine.substring(1);
        return jourCapitalise + " " + dateRdv.getDayOfMonth() + " à " + heure;
    }
}
