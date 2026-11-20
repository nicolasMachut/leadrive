package fr.leadrevive.lead.service;

import fr.leadrevive.lead.dto.RepHistoriqueStatutDto;
import fr.leadrevive.lead.dto.RepKanbanDto;
import fr.leadrevive.lead.dto.RepKanbanDto.KanbanCarte;
import fr.leadrevive.lead.dto.RepKanbanDto.KanbanColonne;
import fr.leadrevive.lead.entity.HistoriqueStatut;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.entity.LeadStatutConfig;
import fr.leadrevive.lead.entity.LeadStatutEnum;
import fr.leadrevive.lead.exception.LeadNotFoundException;
import fr.leadrevive.lead.repository.HistoriqueStatutRepository;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.tache.entity.StatutTacheEnum;
import fr.leadrevive.tache.entity.TypeTacheEnum;
import fr.leadrevive.tache.repository.TacheRepository;
import fr.leadrevive.user.entity.User;
import fr.leadrevive.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KanbanService {

    private final LeadRepository leadRepository;
    private final HistoriqueStatutRepository historiqueStatutRepository;
    private final UserRepository userRepository;
    private final TacheRepository tacheRepository;

    private static final int MAX_CARTES_PAR_COLONNE = 50;

    /** Colonnes du Kanban — couleurs centralisées dans LeadStatutConfig. */
    private static final List<ColonneConfig> COLONNES = List.of(
            LeadStatutEnum.NOUVEAU, LeadStatutEnum.CONTACTE, LeadStatutEnum.EN_COURS,
            LeadStatutEnum.RDV_PRIS, LeadStatutEnum.TERMINE
    ).stream().map(s -> {
        final LeadStatutConfig.Config c = LeadStatutConfig.CONFIGS.get(s);
        return new ColonneConfig(s, c.label(), c.couleur());
    }).toList();

    record ColonneConfig(LeadStatutEnum statut, String label, String couleur) {}

    // ── Kanban Board ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RepKanbanDto getKanban(final Long tenantId, final Long vendeurId, final String recherche,
                                    final String statut, final String territoire, final String marque,
                                    final String source) {
        log.info("Chargement Kanban - tenantId: {}, vendeurId: {}, statut: {}", tenantId, vendeurId, statut);

        // Scoping archive + vendeur poussé en SQL (au lieu de charger TOUTE la table
        // du tenant puis filtrer en RAM = OOM/latence à l'échelle). Les filtres
        // restants (recherche/statut/territoire/marque/source) s'appliquent ensuite
        // sur l'ensemble déjà réduit aux leads actifs du périmètre.
        List<Lead> leads;
        if (vendeurId == null) {
            leads = leadRepository.findByTenantIdAndArchiveFalse(tenantId);
        } else if (vendeurId.equals(0L)) {
            leads = leadRepository.findByTenantIdAndVendeurIdIsNullAndArchiveFalse(tenantId);
        } else {
            leads = leadRepository.findByTenantIdAndVendeurIdAndArchiveFalse(tenantId, vendeurId);
        }
        if (recherche != null && !recherche.isBlank()) {
            final String search = recherche.toLowerCase();
            leads = leads.stream().filter(l ->
                    (l.getNom() != null && l.getNom().toLowerCase().contains(search))
                    || (l.getEmail() != null && l.getEmail().toLowerCase().contains(search))
                    || (l.getTelephone() != null && l.getTelephone().contains(search))
            ).toList();
        }
        if (statut != null && !statut.isBlank()) {
            final LeadStatutEnum filtreStatut = LeadStatutEnum.valueOf(statut);
            leads = leads.stream().filter(l -> l.getStatut() == filtreStatut).toList();
        }
        if (territoire != null && !territoire.isBlank()) {
            leads = leads.stream().filter(l -> territoire.equalsIgnoreCase(l.getTerritoire())).toList();
        }
        if (marque != null && !marque.isBlank()) {
            leads = leads.stream().filter(l -> marque.equalsIgnoreCase(l.getMarque())).toList();
        }
        if (source != null && !source.isBlank()) {
            leads = leads.stream().filter(l -> source.equalsIgnoreCase(l.getSource())).toList();
        }

        final Map<LeadStatutEnum, List<Lead>> parStatut = leads.stream()
                .collect(Collectors.groupingBy(Lead::getStatut, LinkedHashMap::new, Collectors.toList()));

        // Pre-charge en batch les prochains RDV pour la colonne RDV_PRIS (evite un
        // N+1 lors du tri par date de RDV imminent). Si aucun lead en RDV_PRIS,
        // skip le query.
        final Map<Long, LocalDateTime> prochainRdvParLead = chargerProchainRdvParLead(
                parStatut.getOrDefault(LeadStatutEnum.RDV_PRIS, List.of()));

        // Batch-load des vendeurs (1 requête WHERE id IN) au lieu d'un findById par carte
        // (N+1 : jusqu'à 250 SELECT par chargement Kanban).
        final List<Long> vendeurIds = leads.stream()
                .map(Lead::getVendeurId).filter(Objects::nonNull).distinct().toList();
        final Map<Long, User> vendeursParId = vendeurIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(vendeurIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        final List<KanbanColonne> colonnes = new ArrayList<>();
        int totalLeads = 0;

        for (final ColonneConfig config : COLONNES) {
            final List<Lead> leadsStatut = parStatut.getOrDefault(config.statut(), List.of());
            final int count = leadsStatut.size();
            totalLeads += count;

            final Comparator<Lead> tri = triParColonne(config.statut(), prochainRdvParLead);
            final List<Lead> triees = leadsStatut.stream()
                    .sorted(tri)
                    .limit(MAX_CARTES_PAR_COLONNE)
                    .toList();

            colonnes.add(KanbanColonne.builder()
                    .statut(config.statut().name())
                    .label(config.label())
                    .couleur(config.couleur())
                    .leads(triees.stream().map(l -> toCarte(l, vendeursParId)).toList())
                    .count(count)
                    .build());
        }

        return RepKanbanDto.builder()
                .colonnes(colonnes)
                .totalLeads(totalLeads)
                .build();
    }

    // ── Déplacer un lead ───────────────────────────────────────────────────

    @Transactional
    public KanbanCarte deplacerLead(final Long leadId, final Long tenantId,
                                     final LeadStatutEnum nouveauStatut, final String source,
                                     final Long userId, final String commentaire) {
        log.info("Déplacement lead - id: {}, vers: {}, source: {}", leadId, nouveauStatut, source);

        final Lead lead = leadRepository.findByIdAndTenantId(leadId, tenantId)
                .orElseThrow(() -> new LeadNotFoundException(leadId));

        final LeadStatutEnum ancien = lead.getStatut();

        // Toute transition manuelle est autorisee : l'endpoint deplacerLead n'est
        // appele que via le kanban (drag-drop) ou le selectbox de la fiche prospect.
        // Le user assume sa correction (ex : remettre en NOUVEAU apres une erreur).
        // Seule restriction : X -> X (no-op).
        if (nouveauStatut == ancien) {
            return toCarte(lead);
        }

        // Mettre à jour le lead
        lead.setStatut(nouveauStatut);
        lead.setStatutModifieLe(LocalDateTime.now());
        lead.setStatutModifiePar(userId != null ? "USER" : "IA");
        leadRepository.save(lead);

        // Créer l'historique
        final HistoriqueStatut historique = new HistoriqueStatut();
        historique.setLeadId(leadId);
        historique.setTenantId(tenantId);
        historique.setStatutPrecedent(ancien);
        historique.setStatutSuivant(nouveauStatut);
        historique.setSource(source);
        historique.setUserId(userId);
        historique.setCommentaire(commentaire);
        historiqueStatutRepository.save(historique);

        log.info("Lead déplacé - id: {}, {} → {}", leadId, ancien, nouveauStatut);
        return toCarte(lead);
    }

    // ── Historique ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RepHistoriqueStatutDto> getHistorique(final Long leadId, final Long tenantId) {
        // Vérifier que le lead appartient au tenant
        leadRepository.findByIdAndTenantId(leadId, tenantId)
                .orElseThrow(() -> new LeadNotFoundException(leadId));
        return historiqueStatutRepository.findByLeadIdOrderByCreeLeDesc(leadId).stream()
                .map(h -> RepHistoriqueStatutDto.builder()
                        .statutPrecedent(h.getStatutPrecedent() != null ? h.getStatutPrecedent().name() : null)
                        .statutSuivant(h.getStatutSuivant().name())
                        .source(h.getSource())
                        .commentaire(h.getCommentaire())
                        .date(h.getCreeLe())
                        .build())
                .toList();
    }

    // ── Helpers privés ─────────────────────────────────────────────────────

    /**
     * Tri adapte par colonne, optimise pour l'action vendeur :
     * <ul>
     *   <li><b>NOUVEAU</b> : date de creation desc — les nouveaux arrivants
     *       sont mis en avant (regle des 15 min, module Vitesse).</li>
     *   <li><b>CONTACTE / EN_COURS</b> : date de dernier contact <em>asc</em> —
     *       les conversations dormantes (anciennes) remontent en haut pour etre
     *       relancees en priorite. Les recentes (toujours actives) descendent.</li>
     *   <li><b>RDV_PRIS</b> : date du <em>prochain</em> RDV asc — le RDV imminent
     *       en haut. Tombe sur le statut_modifie_le si pas de tache RDV future
     *       (cas edge : statut RDV_PRIS sans tache liee).</li>
     *   <li><b>TERMINE</b> : date de fin desc — historique recent en haut.</li>
     * </ul>
     */
    private Comparator<Lead> triParColonne(final LeadStatutEnum statut,
                                            final Map<Long, LocalDateTime> prochainRdvParLead) {
        return switch (statut) {
            case NOUVEAU -> Comparator.comparing(Lead::getCreeLe, Comparator.nullsLast(Comparator.reverseOrder()));
            case CONTACTE, EN_COURS -> Comparator.comparing(
                    (Lead l) -> l.getStatutModifieLe() != null ? l.getStatutModifieLe() : l.getModifieLe(),
                    Comparator.nullsFirst(Comparator.naturalOrder()));
            case RDV_PRIS -> Comparator.comparing(
                    (Lead l) -> prochainRdvParLead.getOrDefault(l.getId(),
                            l.getStatutModifieLe() != null ? l.getStatutModifieLe() : l.getModifieLe()),
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case TERMINE -> Comparator.comparing(
                    (Lead l) -> l.getStatutModifieLe() != null ? l.getStatutModifieLe() : l.getModifieLe(),
                    Comparator.nullsLast(Comparator.reverseOrder()));
            default -> Comparator.comparing(Lead::getCreeLe, Comparator.nullsLast(Comparator.reverseOrder()));
        };
    }

    /**
     * Construit la map leadId -> date du prochain RDV (A_FAIRE ou EN_COURS, dans
     * le futur) pour les leads passes en argument. Un seul query batch, pas de N+1.
     * Si plusieurs RDV pour le meme lead, on garde le plus proche dans le temps
     * (query ORDER BY date_echeance ASC, on prend le premier rencontre par leadId).
     */
    private Map<Long, LocalDateTime> chargerProchainRdvParLead(final List<Lead> leadsRdvPris) {
        if (leadsRdvPris.isEmpty()) return Map.of();
        final List<Long> leadIds = leadsRdvPris.stream().map(Lead::getId).toList();
        final var taches = tacheRepository
                .findByLeadIdInAndTypeTacheAndStatutInAndDateEcheanceAfterOrderByDateEcheanceAsc(
                        leadIds,
                        TypeTacheEnum.RDV,
                        List.of(StatutTacheEnum.A_FAIRE, StatutTacheEnum.EN_COURS),
                        LocalDateTime.now());
        final Map<Long, LocalDateTime> resultat = new LinkedHashMap<>();
        for (final var t : taches) {
            // Premiere occurrence = plus tot (query triee ASC) -> ne pas ecraser.
            resultat.putIfAbsent(t.getLeadId(), t.getDateEcheance());
        }
        return resultat;
    }

    private int scoreEffectif(final Lead lead) {
        if (lead.getHotnessScore() != null) {
            return lead.getHotnessScore().multiply(BigDecimal.valueOf(100)).intValue();
        }
        if (lead.getScoring() != null) {
            return lead.getScoring().multiply(BigDecimal.valueOf(100)).intValue();
        }
        return 0;
    }

    /** Usage unitaire (deplacerLead) : résout le vendeur en 1 requête puis délègue. */
    private KanbanCarte toCarte(final Lead lead) {
        final Map<Long, User> v = lead.getVendeurId() != null
                ? userRepository.findAllById(List.of(lead.getVendeurId())).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()))
                : Map.of();
        return toCarte(lead, v);
    }

    private KanbanCarte toCarte(final Lead lead, final Map<Long, User> vendeursParId) {
        final int score = scoreEffectif(lead);
        final LocalDateTime dernierContact = lead.getStatutModifieLe() != null
                ? lead.getStatutModifieLe() : lead.getModifieLe();
        final String tempsRelatif = dernierContact != null ? tempsRelatif(dernierContact) : null;

        final String priorite = calculerPriorite(lead, score);
        final List<String> tags = calculerTags(lead, score);

        final String vehicule = (lead.getMarque() != null || lead.getModele() != null)
                ? ((lead.getMarque() != null ? lead.getMarque() : "") + " " + (lead.getModele() != null ? lead.getModele() : "")).trim()
                : null;

        String vendeurNom = null;
        Long vendeurIdEffectif = lead.getVendeurId();
        if (vendeurIdEffectif != null) {
            final User vendeur = vendeursParId.get(vendeurIdEffectif);
            if (vendeur != null && vendeur.getRole() == fr.leadrevive.user.entity.RoleEnum.VENDEUR) {
                vendeurNom = vendeur.getPrenom() + " " + vendeur.getNom();
            } else {
                // Le vendeurId pointe vers un manager ou un user inexistant — traiter comme non assigné
                vendeurIdEffectif = null;
            }
        }

        return KanbanCarte.builder()
                .id(lead.getId())
                .nom(lead.getNom())
                .telephone(masquerTelephone(lead.getTelephone()))
                .email(masquerEmail(lead.getEmail()))
                .vehiculeInteret(vehicule)
                .scoreIA(score)
                .dateCreation(lead.getCreeLe())
                .dernierContact(dernierContact)
                .vendeurId(vendeurIdEffectif)
                .vendeurNom(vendeurNom)
                .sourceOrigine(lead.getSource())
                .statut(lead.getStatut().name())
                .tempsDepuisDernierContact(tempsRelatif)
                .priorite(priorite)
                .tags(tags)
                .kilometrage(lead.getDonneesSupplementaires() != null && lead.getDonneesSupplementaires().get("kilometrage") instanceof Number
                        ? ((Number) lead.getDonneesSupplementaires().get("kilometrage")).intValue() : null)
                .energie(lead.getDonneesSupplementaires() != null && lead.getDonneesSupplementaires().get("energie") instanceof String
                        ? (String) lead.getDonneesSupplementaires().get("energie") : null)
                .build();
    }

    private String calculerPriorite(final Lead lead, final int score) {
        if (score >= 70) return "HAUTE";
        final LocalDateTime dernierContact = lead.getStatutModifieLe() != null
                ? lead.getStatutModifieLe() : lead.getModifieLe();
        if (dernierContact != null
                && Duration.between(dernierContact, LocalDateTime.now()).toHours() > 48
                && lead.getStatut() != LeadStatutEnum.TERMINE) {
            return "HAUTE";
        }
        if (score < 30 && lead.getStatut() == LeadStatutEnum.NOUVEAU) return "BASSE";
        return "MOYENNE";
    }

    private List<String> calculerTags(final Lead lead, final int score) {
        final List<String> tags = new ArrayList<>();
        if (score >= 80) tags.add("Chaud");

        return tags;
    }

    private String tempsRelatif(final LocalDateTime date) {
        final Duration duree = Duration.between(date, LocalDateTime.now());
        // Affiche "a l'instant" plutot que "il y a 0 min" pour les actions
        // tres recentes (moins d'1 minute ecoulee).
        if (duree.toMinutes() < 1) return "a l'instant";
        if (duree.toMinutes() < 60) return "il y a " + duree.toMinutes() + " min";
        if (duree.toHours() < 24) return "il y a " + duree.toHours() + "h";
        if (duree.toDays() < 30) return "il y a " + duree.toDays() + "j";
        return "il y a " + (duree.toDays() / 30) + " mois";
    }

    private String masquerTelephone(final String tel) {
        if (tel == null || tel.length() < 6) return tel;
        return tel.substring(0, 3) + " ** ** " + tel.substring(tel.length() - 4);
    }

    private String masquerEmail(final String email) {
        if (email == null || !email.contains("@")) return email;
        final String[] parts = email.split("@");
        final String debut = parts[0].length() > 1 ? parts[0].substring(0, 1) + "***" : parts[0];
        return debut + "@" + parts[1];
    }
}
