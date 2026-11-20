package fr.leadrevive.lead.controller;

import fr.leadrevive.common.security.AuthService;
import fr.leadrevive.common.security.LeadAccessGuard;
import fr.leadrevive.lead.dto.RepEnvoiAutoAVenirDto;
import fr.leadrevive.lead.dto.RepHistoriqueStatutDto;
import fr.leadrevive.lead.dto.RepKanbanDto;
import fr.leadrevive.lead.dto.RepLeadDto;
import fr.leadrevive.lead.dto.RepLeadStatsDto;
import fr.leadrevive.lead.dto.RepRdvAVenirDto;
import fr.leadrevive.lead.dto.RepSuggestionDto;
import fr.leadrevive.lead.dto.RepTexteLibreDto;
import fr.leadrevive.lead.dto.ReqCreateLeadFromTexteDto;
import fr.leadrevive.lead.dto.ReqDeplacerStatutDto;
import fr.leadrevive.lead.dto.ReqReprogrammerEnvoiAutoDto;
import fr.leadrevive.lead.dto.ReqTexteLibreDto;
import fr.leadrevive.lead.dto.ReqUpdateLeadDto;
import fr.leadrevive.lead.entity.LeadStatutEnum;
import fr.leadrevive.lead.service.DeduplicationService;
import fr.leadrevive.lead.service.KanbanService;
import fr.leadrevive.lead.service.LeadService;
import fr.leadrevive.lead.service.SuggestionService;
import fr.leadrevive.lead.service.TexteLibreParsingService;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;
    private final KanbanService kanbanService;
    private final SuggestionService suggestionService;
    private final AuthService authService;
    private final TexteLibreParsingService texteLibreParsingService;
    private final DeduplicationService deduplicationService;
    private final LeadAccessGuard leadAccessGuard;

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public Page<RepLeadDto> getLeads(@RequestParam(value = "statut", required = false) final String statut,
                                      @RequestParam(value = "territoire", required = false) final String territoire,
                                      @RequestParam(value = "marque", required = false) final String marque,
                                      @RequestParam(value = "source", required = false) final String source,
                                      @RequestParam(value = "vendeurId", required = false) final Long vendeurId,
                                      @RequestParam(value = "recherche", required = false) final String recherche,
                                      @RequestParam(value = "archive", required = false, defaultValue = "false") final boolean archive,
                                      final Pageable pageable) {
        final Long tenantId = authService.getCurrentTenantId();
        // SECURITE : un vendeur ne peut voir que SES leads. On ignore tout
        // vendeurId passe en query param et on force a son userId. Un manager
        // peut continuer a filtrer par vendeurId pour voir une vue ciblee.
        final Long vendeurEffectif = "VENDEUR".equals(authService.getCurrentRole())
                ? authService.getCurrentUserId() : vendeurId;
        // archive : false (defaut) = leads actifs, true = leads archives (bascule "Actifs | Archives").
        return leadService.getLeads(tenantId, statut, territoire, marque, source, vendeurEffectif, recherche, archive, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public RepLeadDto getLead(@PathVariable("id") final Long id) {
        // SECURITE : VENDEUR ne peut acceder qu'a ses propres leads (404 anonyme sinon).
        leadAccessGuard.requireAccessibleLead(id);
        final Long tenantId = authService.getCurrentTenantId();
        return leadService.getLead(id, tenantId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public RepLeadDto updateLead(@PathVariable("id") final Long id,
                                  @RequestBody @Valid final ReqUpdateLeadDto request) {
        // SECURITE : VENDEUR ne peut modifier que ses propres leads.
        leadAccessGuard.requireAccessibleLead(id);
        // SECURITE : VENDEUR ne peut pas changer le vendeurId (s'auto-attribuer ou
        // refiler un lead a un collegue). Seul MANAGER peut reassigner.
        if (request.getVendeurId() != null) {
            leadAccessGuard.interdireSiVendeur("vendeurId");
        }
        final Long tenantId = authService.getCurrentTenantId();
        return leadService.updateLead(id, tenantId, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public ResponseEntity<Void> deleteLead(@PathVariable("id") final Long id) {
        // SECURITE : un VENDEUR ne peut archiver que SES leads (404 anonyme sinon).
        leadAccessGuard.requireAccessibleLead(id);
        final Long tenantId = authService.getCurrentTenantId();
        leadService.deleteLead(id, tenantId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Restaure un lead archivé (undo de l'archivage depuis la fiche).
     * SECURITE : un VENDEUR ne peut restaurer que SES leads (404 anonyme sinon).
     */
    @PostMapping("/{id}/restaurer")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public ResponseEntity<Void> restaurerLead(@PathVariable("id") final Long id) {
        leadAccessGuard.requireAccessibleLead(id);
        final Long tenantId = authService.getCurrentTenantId();
        leadService.restaurerLead(id, tenantId);
        return ResponseEntity.ok().build();
    }

    /**
     * Annule l'envoi automatique du premier message WhatsApp en attente pour ce lead.
     * SECURITE : un VENDEUR ne peut annuler que pour SES leads (404 anonyme sinon).
     */
    @PostMapping("/{id}/envoi-auto/annuler")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public ResponseEntity<Void> annulerEnvoiAuto(@PathVariable("id") final Long id) {
        leadAccessGuard.requireAccessibleLead(id);
        final Long tenantId = authService.getCurrentTenantId();
        leadService.annulerEnvoiAuto(id, tenantId);
        return ResponseEntity.ok().build();
    }

    /**
     * Re-arme l'envoi automatique du premier message WhatsApp (undo de l'annulation).
     * Repositionne l'echeance a la date fournie (celle prevue avant l'annulation).
     * SECURITE : un VENDEUR ne peut reprogrammer que pour SES leads (404 anonyme sinon).
     */
    @PostMapping("/{id}/envoi-auto/reprogrammer")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public ResponseEntity<Void> reprogrammerEnvoiAuto(@PathVariable("id") final Long id,
                                                      @Valid @RequestBody final ReqReprogrammerEnvoiAutoDto req) {
        leadAccessGuard.requireAccessibleLead(id);
        final Long tenantId = authService.getCurrentTenantId();
        leadService.reprogrammerEnvoiAuto(id, tenantId, req.getDateEcheance());
        return ResponseEntity.ok().build();
    }

    /**
     * Liste des envois auto du premier message a venir (dashboard / ActionCenter).
     * SECURITE : un VENDEUR ne voit QUE ses leads (ActionCenter est monte dans son
     * dashboard) ; un MANAGER voit tout le tenant. Meme pattern que getSuggestions/getRdvAVenir.
     */
    @GetMapping("/envois-auto-a-venir")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public List<RepEnvoiAutoAVenirDto> getEnvoisAutoAVenir() {
        final Long tenantId = authService.getCurrentTenantId();
        final Long vendeurEffectif = "VENDEUR".equals(authService.getCurrentRole())
                ? authService.getCurrentUserId() : null;
        return leadService.listerEnvoisAutoAVenir(tenantId, vendeurEffectif);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
    public RepLeadStatsDto getStats() {
        final Long tenantId = authService.getCurrentTenantId();
        return leadService.getStats(tenantId);
    }

    /**
     * Valeurs distinctes des filtres (marque, source) pour la page leads.
     * Evite de fetcher 1000 leads cote front pour extraire les valeurs.
     *
     * <p>Le territoire est toujours retourne pour backward-compat mais n'est
     * plus affiche cote frontend (peu pertinent pour une concession a 1-2
     * territoires). Pourra etre supprime ulterieurement.</p>
     */
    @GetMapping("/filtres")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public java.util.Map<String, java.util.List<String>> getFiltres() {
        final Long tenantId = authService.getCurrentTenantId();
        return java.util.Map.of(
                "territoires", leadService.getDistinctTerritoires(tenantId),
                "marques", leadService.getDistinctMarques(tenantId),
                "sources", leadService.getDistinctSources(tenantId));
    }

    @GetMapping("/suggestions")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public java.util.List<RepSuggestionDto> getSuggestions() {
        final Long tenantId = authService.getCurrentTenantId();
        // SECURITE : un vendeur ne recoit des suggestions QUE sur ses propres leads.
        // Un manager voit l'ensemble du tenant (vendeurId=null = aucun filtre).
        final Long vendeurId = "VENDEUR".equals(authService.getCurrentRole())
                ? authService.getCurrentUserId() : null;
        return suggestionService.getSuggestions(tenantId, vendeurId);
    }

    @GetMapping("/rdv-a-venir")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public java.util.List<RepRdvAVenirDto> getRdvAVenir() {
        final Long tenantId = authService.getCurrentTenantId();
        final String role = authService.getCurrentRole();
        final Long vendeurId = "VENDEUR".equals(role) ? authService.getCurrentUserId() : null;
        return suggestionService.getRdvAVenir(tenantId, vendeurId);
    }

    @GetMapping("/kanban")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public RepKanbanDto getKanban(@RequestParam(value = "vendeurId", required = false) final Long vendeurId,
                                   @RequestParam(value = "search", required = false) final String search,
                                   @RequestParam(value = "statut", required = false) final String statut,
                                   @RequestParam(value = "territoire", required = false) final String territoire,
                                   @RequestParam(value = "marque", required = false) final String marque,
                                   @RequestParam(value = "source", required = false) final String source) {
        final Long tenantId = authService.getCurrentTenantId();
        // SECURITE : on force vendeurId au currentUserId pour les vendeurs afin
        // qu'ils ne puissent pas acceder aux leads des autres ou aux non-assignes
        // via le query param.
        final Long vendeurEffectif = "VENDEUR".equals(authService.getCurrentRole())
                ? authService.getCurrentUserId() : vendeurId;
        return kanbanService.getKanban(tenantId, vendeurEffectif, search, statut, territoire, marque, source);
    }

    @PatchMapping("/{id}/statut")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public RepKanbanDto.KanbanCarte deplacerStatut(@PathVariable("id") final Long id,
                                                    @RequestBody final ReqDeplacerStatutDto req,
                                                    @RequestHeader(value = "X-Source", defaultValue = "SELECTBOX") final String xSource) {
        if (req.statut() == null || req.statut().isBlank()) {
            throw new IllegalArgumentException("Le statut est obligatoire");
        }
        final LeadStatutEnum nouveauStatut;
        try {
            nouveauStatut = LeadStatutEnum.valueOf(req.statut());
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("Statut invalide : " + req.statut());
        }
        // Ownership : un VENDEUR ne peut déplacer QUE ses propres leads (404 anonyme sinon).
        // Sans ce guard, un vendeur pouvait déplacer/clôturer le lead d'un collègue via
        // l'API en devinant l'id (IDOR intra-tenant). Aligné sur getLead/updateLead.
        leadAccessGuard.requireAccessibleLead(id);
        final Long tenantId = authService.getCurrentTenantId();
        final Long userId = authService.getCurrentUserId();
        final String source = "kanban".equalsIgnoreCase(xSource) ? "DRAG_DROP" : "SELECTBOX";
        return kanbanService.deplacerLead(id, tenantId, nouveauStatut, source, userId, req.commentaire());
    }

    @GetMapping("/{id}/historique-statuts")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public List<RepHistoriqueStatutDto> getHistoriqueStatuts(@PathVariable("id") final Long id) {
        // Ownership : un VENDEUR ne lit l'historique QUE de ses propres leads (404 sinon).
        leadAccessGuard.requireAccessibleLead(id);
        final Long tenantId = authService.getCurrentTenantId();
        return kanbanService.getHistorique(id, tenantId);
    }

    // ── Import texte libre ──────────────────────────────────────────────────

    @PostMapping("/parse-texte")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public RepTexteLibreDto parserTexteLibre(@RequestBody @Valid final ReqTexteLibreDto request) {
        final Long tenantId = authService.getCurrentTenantId();
        return texteLibreParsingService.parser(request.getTexte(), tenantId);
    }

    @PostMapping("/from-texte")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public ResponseEntity<RepLeadDto> creerDepuisTexte(@RequestBody @Valid final ReqCreateLeadFromTexteDto request) {
        final Long tenantId = authService.getCurrentTenantId();
        final Long userId = authService.getCurrentUserId();

        if (deduplicationService.estDoublon(tenantId, request.getEmail(), request.getTelephone())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        final RepLeadDto lead = leadService.creerLeadDepuisTexteLibre(request, tenantId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(lead);
    }
}
