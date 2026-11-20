package fr.leadrevive.tache.service;

import fr.leadrevive.common.security.AuthService;
import fr.leadrevive.lead.entity.Lead;
import fr.leadrevive.lead.entity.LeadStatutEnum;
import fr.leadrevive.lead.exception.LeadNotFoundException;
import fr.leadrevive.lead.repository.LeadRepository;
import fr.leadrevive.outreach.service.RappelRdvService;
import fr.leadrevive.tache.dto.RepCalendrierDto;
import fr.leadrevive.tache.dto.RepMaJourneeDto;
import fr.leadrevive.tache.dto.RepTacheDto;
import fr.leadrevive.tache.dto.ReqTacheDto;
import fr.leadrevive.tache.entity.PrioriteTacheEnum;
import fr.leadrevive.tache.entity.StatutTacheEnum;
import fr.leadrevive.tache.entity.Tache;
import fr.leadrevive.tache.entity.TypeTacheEnum;
import fr.leadrevive.tache.repository.TacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TacheService {

    private final TacheRepository tacheRepository;
    private final LeadRepository leadRepository;
    private final RappelRdvService rappelRdvService;
    private final AuthService authService;

    @Transactional
    public RepTacheDto creerTache(final ReqTacheDto req, final Long tenantId, final Long vendeurId, final String source) {
        log.info("Creation tache - titre: {}, tenantId: {}, vendeurId: {}", req.titre(), tenantId, vendeurId);

        final Tache tache = new Tache();
        tache.setTenantId(tenantId);
        tache.setVendeurId(vendeurId);
        tache.setLeadId(req.leadId());
        tache.setTitre(req.titre());
        tache.setDescription(req.description());
        tache.setTypeTache(TypeTacheEnum.valueOf(req.typeTache()));
        tache.setDateEcheance(req.dateEcheance());
        tache.setDureeMinutes(req.dureeMinutes());
        tache.setPriorite(req.priorite() != null ? PrioriteTacheEnum.valueOf(req.priorite()) : PrioriteTacheEnum.NORMALE);
        tache.setStatut(StatutTacheEnum.A_FAIRE);
        tache.setSource(source);
        tache.setRappelMinutesAvant(req.rappelMinutesAvant());
        tache.setRappelEnvoye(false);

        final Tache sauvegardee = tacheRepository.save(tache);
        log.info("Tache creee - id: {}, titre: {}", sauvegardee.getId(), sauvegardee.getTitre());

        return toDto(sauvegardee);
    }

    @Transactional
    public RepTacheDto modifierTache(final Long tacheId, final Long tenantId, final ReqTacheDto req) {
        log.info("Modification tache - id: {}, tenantId: {}", tacheId, tenantId);

        final Tache tache = getTacheSecurisee(tacheId, tenantId);
        final boolean dateChange = tache.getDateEcheance() != null && !tache.getDateEcheance().equals(req.dateEcheance());

        // Garde-fou : un RDV rattache a un lead ne doit PAS etre detache (leadId -> null).
        // La cascade de statut lead et les rappels J-1 dependent de ce lien ; le nullifier
        // (via un req.leadId() null) casserait ces mecanismes silencieusement. On conserve
        // alors l'ancien leadId. Le detachement reste possible pour les taches non-RDV.
        final boolean estRdvRattache = tache.getTypeTache() == TypeTacheEnum.RDV && tache.getLeadId() != null;
        if (req.leadId() == null && estRdvRattache) {
            log.warn("Detachement d'un RDV de son lead ignore - tacheId: {}", tacheId);
        } else {
            tache.setLeadId(req.leadId());
        }
        tache.setTitre(req.titre());
        tache.setDescription(req.description());
        tache.setTypeTache(TypeTacheEnum.valueOf(req.typeTache()));
        tache.setDateEcheance(req.dateEcheance());
        tache.setDureeMinutes(req.dureeMinutes());
        tache.setPriorite(req.priorite() != null ? PrioriteTacheEnum.valueOf(req.priorite()) : PrioriteTacheEnum.NORMALE);
        tache.setRappelMinutesAvant(req.rappelMinutesAvant());

        final Tache sauvegardee = tacheRepository.save(tache);

        // Si la date d'un RDV change, on annule les anciens rappels (envoi WhatsApp J-1 typiquement).
        // Sans ca, le rappel partait a l'ancienne date et le prospect recevait "RDV demain" alors
        // que c'est dans 5 jours. La re-planification doit etre faite par le caller (besoin lead+conversation).
        if (dateChange && tache.getTypeTache() == TypeTacheEnum.RDV && tache.getLeadId() != null) {
            rappelRdvService.annulerRappelsNonEnvoyes(tache.getLeadId());
            log.info("Date RDV modifiee - rappels annules - leadId: {}", tache.getLeadId());
        }

        log.info("Tache modifiee - id: {}", sauvegardee.getId());
        return toDto(sauvegardee);
    }

    @Transactional
    public RepTacheDto changerStatut(final Long tacheId, final Long tenantId, final StatutTacheEnum statut) {
        log.info("Changement statut tache - id: {}, statut: {}", tacheId, statut);

        final Tache tache = getTacheSecurisee(tacheId, tenantId);
        // Anti tache-orpheline : le lead lié doit toujours appartenir au vendeur courant.
        verifierAccesLeadCourantSiRdv(tache, tenantId);

        // Idempotence : deja dans le statut cible -> no-op (pas de bump de version,
        // pas de re-cascade). Neutralise un double-appel (ex. slide-up feedback qui
        // declenchait 2 PATCH concurrents -> OptimisticLockException + faux toast d'echec).
        if (tache.getStatut() == statut) {
            log.info("Statut tache deja a {} - no-op - id: {}", statut, tacheId);
            return toDto(tache);
        }

        // Garde-fou : on ne marque pas "effectué" un RDV encore prévu dans le futur
        // (marge 1h pour les RDV un peu en avance). Sinon on gonfle les RDV effectués
        // et la cascade clôturerait le lead (TERMINE) alors que le RDV n'a pas eu lieu.
        if (statut == StatutTacheEnum.TERMINEE
                && tache.getTypeTache() == TypeTacheEnum.RDV
                && tache.getDateEcheance() != null
                && tache.getDateEcheance().isAfter(LocalDateTime.now().plusHours(1))) {
            throw new IllegalStateException("Ce RDV est prévu dans le futur : il ne peut pas encore être marqué effectué.");
        }

        tache.setStatut(statut);

        if (statut == StatutTacheEnum.TERMINEE) {
            tache.setDateTerminee(LocalDateTime.now());
        } else {
            tache.setDateTerminee(null);
        }

        final Tache sauvegardee = tacheRepository.save(tache);
        log.info("Statut tache change - id: {}, statut: {}", sauvegardee.getId(), statut);

        // Cascade : si c'est une tache RDV, synchroniser le statut du lead
        if (tache.getTypeTache() == TypeTacheEnum.RDV && tache.getLeadId() != null) {
            cascaderStatutLead(tache, statut);
            // Si le RDV est marque TERMINEE ou ANNULEE, annuler les rappels J-1 encore en attente
            // (sinon le prospect recoit "Rappel demain" pour un RDV deja annule/effectue).
            if (statut == StatutTacheEnum.TERMINEE || statut == StatutTacheEnum.ANNULEE) {
                rappelRdvService.annulerRappelsNonEnvoyes(tache.getLeadId());
            }
        }

        return toDto(sauvegardee);
    }

    /**
     * Annule un RDV dans le cadre d'un abandon avec motif. Garde-fou : verifie que
     * la tache (rdvId) appartient BIEN au lead cible, sinon 404. Sans ce controle,
     * un payload forge {@code {leadId: L1, rdvId: R2}} tracerait le motif sur L1
     * mais annulerait la tache d'un autre lead L2 (du meme vendeur) via la cascade.
     * Delegue ensuite a {@link #changerStatut} (annulation + cascade + rappels).
     */
    @Transactional
    public void annulerRdvDeLead(final Long rdvId, final Long tenantId, final Long leadId) {
        final Tache tache = getTacheSecurisee(rdvId, tenantId);
        if (leadId != null && !leadId.equals(tache.getLeadId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "RDV introuvable pour ce lead");
        }
        changerStatut(rdvId, tenantId, StatutTacheEnum.ANNULEE);
    }

    /**

     * Rouvre une tache close par erreur (UNDO d'un "Effectue"/"Abandonner") : remet la
     * tache A_FAIRE. Si c'est un RDV lie a un lead, un-cascade le lead a RDV_PRIS (il
     * avait ete passe TERMINE par "Effectue" ou EN_COURS par "Abandonner"). Pour une
     * tache non-RDV (Appel, Relance...), on ne touche que la tache. On ne touche JAMAIS
     * a historique_statut (l'evenement facturable IA→RDV_PRIS reste, il n'a jamais disparu).
     */
    @Transactional
    public RepTacheDto rouvrirTache(final Long tacheId, final Long tenantId) {
        log.info("Reouverture tache (undo) - id: {}", tacheId);
        final Tache tache = getTacheSecurisee(tacheId, tenantId);
        // Anti tache-orpheline : le lead lié doit toujours appartenir au vendeur courant.
        verifierAccesLeadCourantSiRdv(tache, tenantId);
        tache.setStatut(StatutTacheEnum.A_FAIRE);
        tache.setDateTerminee(null);
        final Tache sauvegardee = tacheRepository.save(tache);
        if (tache.getTypeTache() == TypeTacheEnum.RDV && tache.getLeadId() != null) {
            leadRepository.findByIdAndTenantId(tache.getLeadId(), tenantId).ifPresent(lead -> {
                if (lead.getStatut() != LeadStatutEnum.RDV_PRIS) {
                    log.info("Undo RDV → lead RDV_PRIS - leadId: {}", lead.getId());
                    lead.setStatut(LeadStatutEnum.RDV_PRIS);
                    leadRepository.save(lead);
                }
            });
        }
        return toDto(sauvegardee);
    }

    /**
     * UNDO scopé au lead : rouvre le RDV en vérifiant d'abord qu'il appartient BIEN au
     * {@code leadId} fourni (sinon 404). Sans ce contrôle, un payload forgé
     * {@code {leadId: L1, rdvId: R2}} rouvrirait le RDV d'un autre lead. Aligné sur
     * {@link #annulerRdvDeLead}.
     */
    @Transactional
    public RepTacheDto rouvrirTacheDeLead(final Long rdvId, final Long tenantId, final Long leadId) {
        final Tache tache = getTacheSecurisee(rdvId, tenantId);
        if (leadId != null && !leadId.equals(tache.getLeadId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "RDV introuvable pour ce lead");
        }
        return rouvrirTache(rdvId, tenantId);
    }

    /**
     * Synchronise le statut du lead quand une tache RDV change de statut.
     * TERMINEE → lead passe a TERMINE (RDV effectue)
     * ANNULEE → lead revient a EN_COURS (RDV abandonne, le lead reste actif)
     */
    private void cascaderStatutLead(final Tache tache, final StatutTacheEnum statutTache) {
        leadRepository.findByIdAndTenantId(tache.getLeadId(), tache.getTenantId()).ifPresent(lead -> {
            if (statutTache == StatutTacheEnum.TERMINEE && lead.getStatut() != LeadStatutEnum.TERMINE) {
                log.info("Cascade RDV TERMINEE → lead TERMINE - leadId: {}", lead.getId());
                lead.setStatut(LeadStatutEnum.TERMINE);
                leadRepository.save(lead);
            } else if (statutTache == StatutTacheEnum.ANNULEE && lead.getStatut() == LeadStatutEnum.RDV_PRIS) {
                log.info("Cascade RDV ANNULEE → lead EN_COURS - leadId: {}", lead.getId());
                lead.setStatut(LeadStatutEnum.EN_COURS);
                leadRepository.save(lead);
            }
        });
    }

    @Transactional
    public RepTacheDto reporterTache(final Long tacheId, final Long tenantId, final LocalDateTime nouvelleDateEcheance) {
        log.info("Report tache - id: {}, nouvelleDateEcheance: {}", tacheId, nouvelleDateEcheance);

        final Tache tache = getTacheSecurisee(tacheId, tenantId);
        // Anti tache-orpheline : le lead lié doit toujours appartenir au vendeur courant.
        verifierAccesLeadCourantSiRdv(tache, tenantId);
        tache.setDateEcheance(nouvelleDateEcheance);

        final Tache sauvegardee = tacheRepository.save(tache);
        // Si c'est un RDV reporte, annuler les rappels J-1 planifies a l'ancienne date.
        if (tache.getTypeTache() == TypeTacheEnum.RDV && tache.getLeadId() != null) {
            rappelRdvService.annulerRappelsNonEnvoyes(tache.getLeadId());
            log.info("RDV reporte - rappels J-1 annules - leadId: {}", tache.getLeadId());
        }
        log.info("Tache reportee - id: {}, nouvelleDateEcheance: {}", sauvegardee.getId(), nouvelleDateEcheance);

        return toDto(sauvegardee);
    }

    @Transactional
    public void supprimerTache(final Long tacheId, final Long tenantId) {
        log.info("Suppression tache - id: {}, tenantId: {}", tacheId, tenantId);

        final Tache tache = getTacheSecurisee(tacheId, tenantId);
        tacheRepository.delete(tache);

        log.info("Tache supprimee - id: {}", tacheId);
    }

    @Transactional(readOnly = true)
    public RepMaJourneeDto getMaJournee(final Long tenantId, final Long vendeurId, final LocalDate date) {
        log.info("Recuperation journee - tenantId: {}, vendeurId: {}, date: {}", tenantId, vendeurId, date);

        final LocalDateTime maintenant = LocalDateTime.now();
        final LocalDateTime debutJour = date.atStartOfDay();
        final LocalDateTime finJour = date.atTime(LocalTime.MAX);
        final LocalDateTime debutDemain = date.plusDays(1).atStartOfDay();
        final LocalDateTime finDemain = date.plusDays(1).atTime(LocalTime.MAX);

        // En retard = taches A_FAIRE dont l'echeance est passee (date+heure < maintenant)
        final List<Tache> tachesEnRetardRaw = vendeurId != null
                ? tacheRepository.findByTenantIdAndVendeurIdAndStatutAndDateEcheanceBeforeOrderByDateEcheanceAsc(tenantId, vendeurId, StatutTacheEnum.A_FAIRE, maintenant)
                : tacheRepository.findByTenantIdAndStatutAndDateEcheanceBeforeOrderByDateEcheanceAsc(tenantId, StatutTacheEnum.A_FAIRE, maintenant);

        final List<Tache> tachesAujourdhui = vendeurId != null
                ? tacheRepository.findByTenantIdAndVendeurIdAndDateEcheanceBetweenOrderByDateEcheanceAsc(tenantId, vendeurId, debutJour, finJour)
                : tacheRepository.findByTenantIdAndDateEcheanceBetweenOrderByDateEcheanceAsc(tenantId, debutJour, finJour);

        final List<Tache> tachesDemainRaw = vendeurId != null
                ? tacheRepository.findByTenantIdAndVendeurIdAndDateEcheanceBetweenOrderByDateEcheanceAsc(tenantId, vendeurId, debutDemain, finDemain)
                : tacheRepository.findByTenantIdAndDateEcheanceBetweenOrderByDateEcheanceAsc(tenantId, debutDemain, finDemain);

        // Une seule requete lead pour les 3 listes (anti N+1).
        final List<Tache> toutesTaches = new ArrayList<>(tachesEnRetardRaw);
        toutesTaches.addAll(tachesAujourdhui);
        toutesTaches.addAll(tachesDemainRaw);
        final Map<Long, Lead> leadsParId = chargerLeadsParId(toutesTaches, tenantId);

        final List<RepTacheDto> tachesEnRetard = tachesEnRetardRaw.stream()
                .map(t -> toDto(t, leadsParId))
                .toList();

        // Exclure les taches en retard (deja affichees dans la section "En retard")
        final Set<Long> idsEnRetard = tachesEnRetard.stream()
                .map(RepTacheDto::getId)
                .collect(Collectors.toSet());

        final List<RepTacheDto> aujourdhui = tachesAujourdhui.stream()
                .map(t -> toDto(t, leadsParId))
                .filter(t -> !idsEnRetard.contains(t.getId()))
                .toList();

        final List<RepTacheDto> demain = tachesDemainRaw.stream()
                .map(t -> toDto(t, leadsParId))
                .toList();

        final int terminees = (int) tachesAujourdhui.stream()
                .filter(t -> t.getStatut() == StatutTacheEnum.TERMINEE)
                .count();

        return RepMaJourneeDto.builder()
                .date(date)
                .enRetard(tachesEnRetard)
                .aujourdhui(aujourdhui)
                .demain(demain)
                .totalAujourdhui(aujourdhui.size())
                .terminees(terminees)
                .nbEnRetard(tachesEnRetard.size())
                .build();
    }

    @Transactional(readOnly = true)
    public RepCalendrierDto getCalendrier(final Long tenantId, final Long vendeurId, final int mois, final int annee) {
        log.info("Recuperation calendrier - tenantId: {}, vendeurId: {}, mois: {}, annee: {}", tenantId, vendeurId, mois, annee);

        final LocalDate premierJour = LocalDate.of(annee, mois, 1);
        final LocalDate dernierJour = premierJour.withDayOfMonth(premierJour.lengthOfMonth());
        final LocalDateTime debut = premierJour.atStartOfDay();
        final LocalDateTime fin = dernierJour.atTime(LocalTime.MAX);

        final List<Tache> tachesDuMois = vendeurId != null
                ? tacheRepository.findByTenantIdAndVendeurIdAndDateEcheanceBetweenOrderByDateEcheanceAsc(tenantId, vendeurId, debut, fin)
                : tacheRepository.findByTenantIdAndDateEcheanceBetweenOrderByDateEcheanceAsc(tenantId, debut, fin);

        final Map<Long, Lead> leadsParId = chargerLeadsParId(tachesDuMois, tenantId);

        final Map<LocalDate, List<Tache>> parJour = new LinkedHashMap<>();
        for (final Tache tache : tachesDuMois) {
            final LocalDate jour = tache.getDateEcheance().toLocalDate();
            parJour.computeIfAbsent(jour, k -> new ArrayList<>()).add(tache);
        }

        final List<RepCalendrierDto.JourCalendrier> jours = new ArrayList<>();
        for (final Map.Entry<LocalDate, List<Tache>> entry : parJour.entrySet()) {
            final List<Tache> tachesJour = entry.getValue();
            final int nombreTotal = tachesJour.size();
            final List<RepTacheDto> tachesLimitees = tachesJour.stream()
                    .limit(5)
                    .map(t -> toDto(t, leadsParId))
                    .toList();

            jours.add(RepCalendrierDto.JourCalendrier.builder()
                    .date(entry.getKey())
                    .taches(tachesLimitees)
                    .nombreTotal(nombreTotal)
                    .build());
        }

        return RepCalendrierDto.builder()
                .mois(mois)
                .annee(annee)
                .jours(jours)
                .build();
    }

    @Transactional(readOnly = true)
    public List<RepTacheDto> getTachesParLead(final Long leadId, final Long tenantId) {
        log.info("Recuperation taches par lead - leadId: {}, tenantId: {}", leadId, tenantId);
        // Vérifier que le lead appartient au tenant
        leadRepository.findByIdAndTenantId(leadId, tenantId)
                .orElseThrow(() -> new LeadNotFoundException(leadId));
        final List<Tache> taches = tacheRepository.findByLeadIdOrderByDateEcheanceDesc(leadId);
        final Map<Long, Lead> leadsParId = chargerLeadsParId(taches, tenantId);
        return taches.stream()
                .map(t -> toDto(t, leadsParId))
                .toList();
    }

    /**
     * Garde-fou anti tâche-orpheline : le {@code vendeurId} d'une tâche RDV n'est PAS
     * resynchronisé quand le lead est réassigné. Un ancien vendeur (dont l'id est encore
     * sur la tâche) passerait donc {@link #getTacheSecurisee} et pourrait, via la cascade,
     * clôturer / rouvrir / reporter le lead d'un COLLÈGUE (lead qu'il ne peut plus voir).
     * On vérifie donc que, pour un VENDEUR, le lead lié appartient TOUJOURS à l'utilisateur
     * courant (404 anonyme sinon). Les MANAGER (vue d'équipe) bypassent.
     */
    private void verifierAccesLeadCourantSiRdv(final Tache tache, final Long tenantId) {
        if (tache.getLeadId() == null) return;
        if (!"VENDEUR".equals(authService.getCurrentRole())) return;
        final Long currentUserId = authService.getCurrentUserId();
        leadRepository.findByIdAndTenantId(tache.getLeadId(), tenantId).ifPresent(lead -> {
            if (lead.getVendeurId() != null && !lead.getVendeurId().equals(currentUserId)) {
                log.warn("Acces refuse : tache liee a un lead qui n'appartient plus au vendeur courant - tacheId: {}, leadId: {}",
                        tache.getId(), tache.getLeadId());
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Tache introuvable");
            }
        });
    }

    private Tache getTacheSecurisee(final Long tacheId, final Long tenantId) {
        // SECURITE : 404 anonyme dans tous les cas d'acces refuse pour ne pas
        // confirmer l'existence d'une tache d'un autre tenant ou d'un autre vendeur
        // (oracle d'enumeration).
        final Tache tache = tacheRepository.findById(tacheId)
                .filter(t -> tenantId != null && tenantId.equals(t.getTenantId()))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Tache introuvable"));

        // Un VENDEUR ne peut acceder qu'a SES taches.
        if ("VENDEUR".equals(authService.getCurrentRole())) {
            final Long currentUserId = authService.getCurrentUserId();
            if (currentUserId == null || !currentUserId.equals(tache.getVendeurId())) {
                log.warn("Acces refuse a une tache non-assignee au vendeur courant - tacheId: {}, vendeurTache: {}, userCourant: {}",
                        tacheId, tache.getVendeurId(), currentUserId);
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Tache introuvable");
            }
        }
        // Garde-fou ownership : un VENDEUR ne peut toucher qu'aux taches qu'il a creees
        // ou qui lui sont assignees (vendeurId). Les MANAGER bypassent (vue d'equipe).
        // Sans ce check, vendeur A pouvait PUT /api/taches/{id_B} et modifier les RDV de B.
        // Fix bug-hunter #1 : IllegalStateException leaké l'existence de la tache (oracle).
        // On renvoie 404 anonyme comme le check principal au-dessus.
        final String role = authService.getCurrentRole();
        if ("VENDEUR".equals(role)) {
            final Long currentUserId = authService.getCurrentUserId();
            if (currentUserId != null && tache.getVendeurId() != null
                    && !tache.getVendeurId().equals(currentUserId)) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Tache introuvable");
            }
        }
        return tache;
    }

    /**
     * Usage UNITAIRE (creation/modification/changement de statut d'une seule tache) :
     * resout le lead en 1 requete. Pour les LISTES, utiliser {@link #toDto(Tache, Map)}
     * avec une map pre-chargee en batch via {@link #chargerLeadsParId(List, Long)}
     * (sinon N+1 : 1 requete lead par tache).
     */
    private RepTacheDto toDto(final Tache tache) {
        final Map<Long, Lead> leadsParId = tache.getLeadId() != null
                ? leadRepository.findByIdInAndTenantId(List.of(tache.getLeadId()), tache.getTenantId())
                        .stream().collect(Collectors.toMap(Lead::getId, l -> l))
                : Map.of();
        return toDto(tache, leadsParId);
    }

    /** Charge en UNE seule requete les leads references par une liste de taches (anti N+1). */
    private Map<Long, Lead> chargerLeadsParId(final List<Tache> taches, final Long tenantId) {
        final List<Long> leadIds = taches.stream()
                .map(Tache::getLeadId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (leadIds.isEmpty()) return Map.of();
        return leadRepository.findByIdInAndTenantId(leadIds, tenantId).stream()
                .collect(Collectors.toMap(Lead::getId, l -> l));
    }

    private RepTacheDto toDto(final Tache tache, final Map<Long, Lead> leadsParId) {
        final LocalDateTime maintenant = LocalDateTime.now();
        final boolean estEnRetard = tache.getStatut() == StatutTacheEnum.A_FAIRE
                && tache.getDateEcheance().isBefore(maintenant);

        String leadNom = null;
        String leadStatut = null;
        if (tache.getLeadId() != null) {
            final Lead lead = leadsParId.get(tache.getLeadId());
            if (lead != null) {
                leadNom = lead.getNom();
                leadStatut = lead.getStatut() != null ? lead.getStatut().getValeur() : null;
            }
        }

        final String tempsRestant = calculerTempsRestant(tache.getDateEcheance(), maintenant, estEnRetard);

        return RepTacheDto.builder()
                .id(tache.getId())
                .titre(tache.getTitre())
                .description(tache.getDescription())
                .typeTache(tache.getTypeTache().getValeur())
                .dateEcheance(tache.getDateEcheance())
                .dureeMinutes(tache.getDureeMinutes())
                .priorite(tache.getPriorite().getValeur())
                .statut(tache.getStatut().getValeur())
                .source(tache.getSource())
                .rappelMinutesAvant(tache.getRappelMinutesAvant())
                .dateTerminee(tache.getDateTerminee())
                .leadId(tache.getLeadId())
                .leadNom(leadNom)
                .leadStatut(leadStatut)
                .vendeurId(tache.getVendeurId())
                .enRetard(estEnRetard)
                .tempsRestant(tempsRestant)
                .build();
    }

    private String calculerTempsRestant(final LocalDateTime dateEcheance, final LocalDateTime maintenant,
                                         final boolean estEnRetard) {
        if (estEnRetard) {
            final long joursRetard = ChronoUnit.DAYS.between(dateEcheance, maintenant);
            if (joursRetard >= 1) {
                return "en retard de " + joursRetard + "j";
            }
            final long heuresRetard = ChronoUnit.HOURS.between(dateEcheance, maintenant);
            if (heuresRetard >= 1) {
                return "en retard de " + heuresRetard + "h";
            }
            final long minutesRetard = ChronoUnit.MINUTES.between(dateEcheance, maintenant);
            return "en retard de " + minutesRetard + "min";
        }

        final long joursAvant = ChronoUnit.DAYS.between(maintenant, dateEcheance);
        if (joursAvant > 1) {
            return "dans " + joursAvant + "j";
        }
        if (joursAvant == 1) {
            return "demain " + dateEcheance.getHour() + "h";
        }

        final long heuresAvant = ChronoUnit.HOURS.between(maintenant, dateEcheance);
        if (heuresAvant >= 1) {
            return "dans " + heuresAvant + "h";
        }

        final long minutesAvant = ChronoUnit.MINUTES.between(maintenant, dateEcheance);
        return "dans " + minutesAvant + "min";
    }
}
