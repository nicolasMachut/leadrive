package fr.leadrevive.lead.service;

import fr.leadrevive.ai.entity.TenantAiConfig;
import fr.leadrevive.ai.service.TenantAiConfigService;
import fr.leadrevive.lead.dto.RepEnvoiAutoAVenirDto;
import fr.leadrevive.lead.dto.RepLeadDto;
import fr.leadrevive.lead.dto.RepLeadStatsDto;
import fr.leadrevive.lead.dto.ReqCreateLeadFromTexteDto;
import fr.leadrevive.lead.dto.ReqUpdateLeadDto;
import fr.leadrevive.common.realtime.SseConnectionService;
import fr.leadrevive.common.realtime.dto.RealtimeEventDto;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.entity.LeadStatutEnum;
import fr.leadrevive.lead.entity.ModeImport;
import fr.leadrevive.lead.exception.LeadActifExistantException;
import fr.leadrevive.lead.exception.LeadNotFoundException;
import fr.leadrevive.lead.mapper.LeadMapper;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.outreach.service.PremierMessageAutoFenetre;
import fr.leadrevive.user.entity.RoleEnum;
import fr.leadrevive.user.entity.User;
import fr.leadrevive.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeadService {

    private final LeadRepository leadRepository;
    private final LeadMapper leadMapper;
    private final SseConnectionService sseConnectionService;
    private final UserRepository userRepository;
    private final TenantAiConfigService tenantAiConfigService;

    /** Defauts de plage horaire d'envoi si la config est absente ou incomplete (aligne PremierMessageAutoJob). */
    private static final int PLAGE_DEBUT_DEFAUT = 8;
    private static final int PLAGE_FIN_DEFAUT = 20;

    /** Champs autorises au tri de la liste (whitelist). "statut" est exclu : trier sur
     *  l'enum donne l'ordre alphabetique (pas l'ordre du pipeline) = trompeur ; la vue
     *  Kanban est la vue ordonnee par pipeline. */
    private static final Set<String> CHAMPS_TRI_AUTORISES = Set.of("nom", "creeLe", "scoring", "hotnessScore", "id");
    private static final int TAILLE_PAGE_MAX = 100;

    /**
     * Assainit le Pageable issu du client :
     *  - borne la taille de page (anti `?size=100000`),
     *  - whitelist les champs de tri (anti tri sur colonnes TEXT lourdes / statut alphabetique),
     *  - applique NULLS LAST sur les scores (leads sans score en fin de tri desc),
     *  - ajoute un tie-breaker stable `id` pour une pagination deterministe (anti doublon/saut).
     */
    private Pageable sanitizePageable(final Pageable pageable) {
        final int taille = Math.min(pageable.getPageSize(), TAILLE_PAGE_MAX);
        final List<Sort.Order> ordres = new ArrayList<>();
        for (final Sort.Order o : pageable.getSort()) {
            if (!CHAMPS_TRI_AUTORISES.contains(o.getProperty())) {
                continue;
            }
            ordres.add(("scoring".equals(o.getProperty()) || "hotnessScore".equals(o.getProperty()))
                    ? o.nullsLast() : o);
        }
        ordres.add(Sort.Order.asc("id")); // tie-breaker stable
        return PageRequest.of(pageable.getPageNumber(), taille, Sort.by(ordres));
    }

    @Transactional(readOnly = true)
    public Page<RepLeadDto> getLeads(final Long tenantId,
                                      final String statut,
                                      final String territoire,
                                      final String marque,
                                      final String source,
                                      final Long vendeurId,
                                      final String recherche,
                                      final boolean archive,
                                      final Pageable pageable) {
        log.info("Recuperation des leads - tenantId: {}, statut: {}, source: {}, archive: {}", tenantId, statut, source, archive);
        final java.util.List<LeadStatutEnum> statuts;
        if (statut != null && !statut.isBlank()) {
            statuts = java.util.Arrays.stream(statut.split(","))
                    .map(String::trim)
                    .map(LeadStatutEnum::valueOf)
                    .toList();
        } else {
            statuts = null;
        }
        final Page<Lead> page = leadRepository.findByTenantIdWithFilters(
                tenantId, archive, statuts, territoire, marque, source, vendeurId, recherche, sanitizePageable(pageable));
        log.info("Leads recuperes - nombre: {}", page.getTotalElements());
        // Enrichissement de l'heure d'envoi effective sur CHAQUE ligne : sinon le badge
        // liste reaffiche l'echeance brute (libelle "hors plage" trompeur persistant).
        // Config resolue UNE SEULE FOIS (tous les leads d'une page = meme tenant) : pas
        // de getConfig par ligne (anti N+1).
        final int debut = plageDebut(tenantId);
        final int fin = plageFin(tenantId);
        final LocalDateTime maintenant = LocalDateTime.now();
        return page.map(l -> {
            final RepLeadDto dto = leadMapper.toDto(l);
            enrichirEnvoiEffectif(dto, debut, fin, maintenant);
            return dto;
        });
    }

    @Transactional(readOnly = true)
    public RepLeadDto getLead(final Long id, final Long tenantId) {
        log.info("Recuperation du lead - idLead: {}, tenantId: {}", id, tenantId);
        final Lead lead = leadRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new LeadNotFoundException(id));
        final RepLeadDto dto = leadMapper.toDto(lead);
        enrichirEnvoiEffectif(dto, plageDebut(tenantId), plageFin(tenantId), LocalDateTime.now());
        return dto;
    }

    /** Heure de debut de plage d'envoi configuree pour le tenant (defaut si absente). */
    private int plageDebut(final Long tenantId) {
        final TenantAiConfig config = tenantAiConfigService.getConfig(tenantId);
        return config != null && config.getPremierMessageAutoFenetreDebutHeure() != null
                ? config.getPremierMessageAutoFenetreDebutHeure() : PLAGE_DEBUT_DEFAUT;
    }

    /** Heure de fin de plage d'envoi configuree pour le tenant (defaut si absente). */
    private int plageFin(final Long tenantId) {
        final TenantAiConfig config = tenantAiConfigService.getConfig(tenantId);
        return config != null && config.getPremierMessageAutoFenetreFinHeure() != null
                ? config.getPremierMessageAutoFenetreFinHeure() : PLAGE_FIN_DEFAUT;
    }

    /**
     * Renseigne les champs derives {@code premierMessageAutoEffectifLe} / {@code premierMessageAutoHorsPlage}
     * a partir de l'echeance brute et de la plage horaire (debut/fin) deja resolue par l'appelant.
     * Court-circuit si aucun envoi n'est arme (laisse effectifLe null et horsPlage false).
     *
     * <p>La plage est passee en parametre (et non resolue ici) pour eviter un appel
     * {@code getConfig} par ligne quand on enrichit une liste : l'appelant resout la config
     * une seule fois pour tout le lot (meme tenant).</p>
     */
    private void enrichirEnvoiEffectif(final RepLeadDto dto, final int debut, final int fin,
                                       final LocalDateTime maintenant) {
        final LocalDateTime prevuLe = dto.getPremierMessageAutoPrevuLe();
        if (prevuLe == null) {
            return;
        }
        final PremierMessageAutoFenetre.ResultatFenetre res =
                PremierMessageAutoFenetre.calculer(prevuLe, debut, fin, maintenant);
        dto.setPremierMessageAutoEffectifLe(res.effectifLe());
        dto.setPremierMessageAutoHorsPlage(res.horsPlage());
    }

    @Transactional
    public RepLeadDto updateLead(final Long id, final Long tenantId, final ReqUpdateLeadDto request) {
        log.info("Mise a jour du lead - idLead: {}, tenantId: {}", id, tenantId);
        final Lead lead = leadRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new LeadNotFoundException(id));

        // Le statut NE se change PAS ici : il doit passer par PATCH /api/leads/{id}/statut
        // (KanbanService.deplacerLead) qui trace la transition dans historique_statut.
        // Sinon une transition faite via PUT serait invisible dans l'historique (cohérence
        // Kanban/stats cassée). On refuse explicitement plutôt que d'ignorer en silence.
        if (request.getStatut() != null && !request.getStatut().equals(lead.getStatut().name())) {
            throw new IllegalArgumentException(
                    "Le statut d'un lead se modifie via son suivi de statut, pas par cette action.");
        }
        // Vendeur : la cible doit être un VENDEUR actif du même établissement, sinon le lead
        // serait assigné à un id fantôme (autre tenant / manager / inexistant) et disparaîtrait
        // des vues et stats par vendeur.
        if (request.getVendeurId() != null) {
            final User vendeur = userRepository.findById(request.getVendeurId())
                    .filter(u -> tenantId != null && tenantId.equals(u.getTenantId()))
                    .filter(u -> u.getRole() == RoleEnum.VENDEUR)
                    .filter(u -> Boolean.TRUE.equals(u.getActif()))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Vendeur invalide : la cible doit etre un vendeur actif de l'etablissement."));
            lead.setVendeurId(vendeur.getId());
        }
        if (request.getNom() != null) {
            lead.setNom(request.getNom());
        }
        if (request.getTelephone() != null) {
            lead.setTelephone(request.getTelephone());
        }
        if (request.getEmail() != null) {
            lead.setEmail(request.getEmail());
        }
        if (request.getEtape() != null) {
            lead.setEtape(request.getEtape());
        }

        final Lead saved = leadRepository.save(lead);
        log.info("Lead mis a jour - idLead: {}", id);
        return leadMapper.toDto(saved);
    }

    @Transactional
    public RepLeadDto creerLeadDepuisTexteLibre(final ReqCreateLeadFromTexteDto donnees,
                                                  final Long tenantId, final Long vendeurId) {
        log.info("Création lead depuis texte libre - tenantId: {}", tenantId);
        final Lead lead = new Lead();
        lead.setTenantId(tenantId);
        final String nomComplet = ((donnees.getPrenom() != null ? donnees.getPrenom() + " " : "") +
                (donnees.getNom() != null ? donnees.getNom() : "")).trim();
        lead.setNom(nomComplet.isBlank() ? "Inconnu" : nomComplet);
        lead.setTelephone(donnees.getTelephone());
        lead.setEmail(donnees.getEmail());
        lead.setMarque(donnees.getMarque());
        lead.setModele(donnees.getModele());
        lead.setSource(donnees.getSourceDetectee());
        lead.setStatut(LeadStatutEnum.NOUVEAU);
        lead.setModeImport(ModeImport.COLLER_TEXTE);
        lead.setTexteSource(donnees.getTexteOriginal());
        lead.setDonneesSupplementaires(donnees.getDonneesSupplementaires() != null ? donnees.getDonneesSupplementaires() : Map.of());
        // RGPD : meme logique que CsvImportService — la concession importe le lead dans
        // son parcours commercial, consentement implicite. La source permet l'audit.
        lead.setConsentementContact(true);
        lead.setConsentementDate(java.time.LocalDateTime.now());
        lead.setConsentementSource("texte_libre");
        if (vendeurId != null) {
            lead.setVendeurId(vendeurId);
        }
        final Lead saved = leadRepository.save(lead);
        log.info("Lead créé depuis texte libre - idLead: {}", saved.getId());
        sseConnectionService.broadcast(tenantId,
                RealtimeEventDto.leadCreated(saved.getId(), saved.getNom(), saved.getSource()));
        return leadMapper.toDto(saved);
    }

    /**
     * Annule un envoi auto du premier message WhatsApp en attente (efface le flag).
     * Ownership deja verifie en amont par le controller (LeadAccessGuard). Idempotent :
     * ne fait rien si aucun envoi n'est arme.
     */
    @Transactional
    public void annulerEnvoiAuto(final Long id, final Long tenantId) {
        log.info("Annulation envoi auto premier message - idLead: {}, tenantId: {}", id, tenantId);
        final Lead lead = leadRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new LeadNotFoundException(id));
        if (lead.getPremierMessageAutoPrevuLe() != null) {
            lead.setPremierMessageAutoPrevuLe(null);
            leadRepository.save(lead);
        }
    }


    /**
     * Re-arme un envoi auto du premier message WhatsApp (undo de l'annulation).
     * Repositionne le flag {@code premierMessageAutoPrevuLe} a la date d'echeance
     * fournie (celle qui etait prevue avant l'annulation). Ownership deja verifie
     * en amont par le controller (LeadAccessGuard).
     *
     * <p>Garde-fous : le lead doit encore etre au statut NOUVEAU (sinon il a deja
     * ete contacte / avance dans le pipeline, re-armer un premier message auto
     * n'a plus de sens) et la date d'echeance ne doit pas etre nulle.</p>
     */
    @Transactional
    public void reprogrammerEnvoiAuto(final Long id, final Long tenantId,
                                      final java.time.LocalDateTime dateEcheance) {
        log.info("Reprogrammation envoi auto premier message - idLead: {}, tenantId: {}", id, tenantId);
        if (dateEcheance == null) {
            throw new IllegalArgumentException("La date d'echeance est obligatoire pour reprogrammer l'envoi.");
        }
        final Lead lead = leadRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new LeadNotFoundException(id));
        if (lead.getStatut() != LeadStatutEnum.NOUVEAU) {
            throw new IllegalStateException(
                    "Impossible de reprogrammer l'envoi automatique : ce lead a deja ete contacte.");
        }
        lead.setPremierMessageAutoPrevuLe(dateEcheance);
        leadRepository.save(lead);
    }

    /**
     * Liste legere des envois auto du premier message a venir (leads import email armes,
     * encore au statut NOUVEAU), triee par echeance. Scope vendeur : si {@code vendeurId}
     * est non nul (appelant VENDEUR), ne retourne que SES leads ; null (MANAGER) = tout le tenant.
     */
    @Transactional(readOnly = true)
    public List<RepEnvoiAutoAVenirDto> listerEnvoisAutoAVenir(final Long tenantId, final Long vendeurId) {
        // Config resolue une seule fois : tous les leads listes appartiennent au meme tenant.
        final int debut = plageDebut(tenantId);
        final int fin = plageFin(tenantId);
        final LocalDateTime maintenant = LocalDateTime.now();
        return leadRepository.findEnvoisAutoAVenir(tenantId, vendeurId).stream()
                .map(l -> {
                    final PremierMessageAutoFenetre.ResultatFenetre res = PremierMessageAutoFenetre.calculer(
                            l.getPremierMessageAutoPrevuLe(), debut, fin, maintenant);
                    return RepEnvoiAutoAVenirDto.builder()
                            .idLead(l.getId())
                            .nom(l.getNom())
                            .premierMessageAutoPrevuLe(l.getPremierMessageAutoPrevuLe())
                            .premierMessageAutoEffectifLe(res.effectifLe())
                            .premierMessageAutoHorsPlage(res.horsPlage())
                            .build();
                })
                .toList();
    }

    @Transactional
    public void deleteLead(final Long id, final Long tenantId) {
        log.info("Archivage du lead - idLead: {}, tenantId: {}", id, tenantId);
        final Lead lead = leadRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new LeadNotFoundException(id));
        lead.setArchive(true);
        // On efface l'envoi auto en attente : un lead archive est exclu du scheduler,
        // le message ne partira jamais. Sans ca, le badge "auto" reste affiche (fantome)
        // sur un lead qui ne sera jamais contacte.
        lead.setPremierMessageAutoPrevuLe(null);
        leadRepository.save(lead);
        log.info("Lead archivé - idLead: {}", id);
    }

    /**
     * Restaure un lead archivé (undo de l'archivage). Ownership déjà vérifiée en
     * amont par le controller (LeadAccessGuard). {@code findByIdAndTenantId} ne filtre
     * pas sur {@code archive}, donc un lead archivé reste retrouvable. Idempotent : ne
     * fait rien si le lead est déjà actif.
     *
     * <p>Garde-fou collision : la dedup ignore les archives, donc un lead ACTIF avec le
     * meme telephone/email a pu etre cree entre-temps. Desarchiver violerait alors l'index
     * unique {@code uq_lead_tenant_telephone_actif} / {@code uq_lead_tenant_email_actif}
     * (V068) -> 500 opaque. On pre-check et on remonte une 409 avec message clair.</p>
     */
    @Transactional
    public void restaurerLead(final Long id, final Long tenantId) {
        log.info("Restauration du lead - idLead: {}, tenantId: {}", id, tenantId);
        final Lead lead = leadRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new LeadNotFoundException(id));
        if (Boolean.TRUE.equals(lead.getArchive())) {
            if (lead.getTelephone() != null && !lead.getTelephone().isBlank()
                    && leadRepository.existsByTenantIdAndTelephoneAndArchiveFalseAndIdNot(
                            tenantId, lead.getTelephone(), id)) {
                throw new LeadActifExistantException(
                        "Un prospect actif avec le même numéro existe déjà. "
                                + "Fusionnez ou modifiez-le avant de désarchiver celui-ci.");
            }
            if (lead.getEmail() != null && !lead.getEmail().isBlank()
                    && leadRepository.existsByTenantIdAndEmailAndArchiveFalseAndIdNot(
                            tenantId, lead.getEmail(), id)) {
                throw new LeadActifExistantException(
                        "Un prospect actif avec le même email existe déjà. "
                                + "Fusionnez ou modifiez-le avant de désarchiver celui-ci.");
            }
            lead.setArchive(false);
            leadRepository.save(lead);
        }
        log.info("Lead restauré - idLead: {}", id);
    }

    @Transactional(readOnly = true)
    public java.util.List<String> getDistinctTerritoires(final Long tenantId) {
        return leadRepository.findDistinctTerritoiresByTenantId(tenantId);
    }

    @Transactional(readOnly = true)
    public java.util.List<String> getDistinctMarques(final Long tenantId) {
        return leadRepository.findDistinctMarquesByTenantId(tenantId);
    }

    @Transactional(readOnly = true)
    public java.util.List<String> getDistinctSources(final Long tenantId) {
        return leadRepository.findDistinctSourcesByTenantId(tenantId);
    }

    @Transactional(readOnly = true)
    public RepLeadStatsDto getStats(final Long tenantId) {
        log.info("Recuperation des stats leads - tenantId: {}", tenantId);

        final Map<String, Long> parStatut = new LinkedHashMap<>();
        leadRepository.countByTenantIdGroupByStatut(tenantId)
                .forEach(row -> parStatut.put(row[0].toString(), (Long) row[1]));

        final Map<String, Long> parTerritoire = new LinkedHashMap<>();
        leadRepository.countByTenantIdGroupByTerritoire(tenantId)
                .forEach(row -> parTerritoire.put(row[0] != null ? row[0].toString() : "Non defini", (Long) row[1]));

        final Map<String, Long> parMarque = new LinkedHashMap<>();
        leadRepository.countByTenantIdGroupByMarque(tenantId)
                .forEach(row -> parMarque.put(row[0] != null ? row[0].toString() : "Non defini", (Long) row[1]));

        final Long total = parStatut.values().stream().mapToLong(Long::longValue).sum();

        log.info("Stats leads recuperees - total: {}", total);

        return RepLeadStatsDto.builder()
                .parStatut(parStatut)
                .parTerritoire(parTerritoire)
                .parMarque(parMarque)
                .total(total)
                .build();
    }
}
