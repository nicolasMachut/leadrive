package fr.leadrevive.tache.controller;

import fr.leadrevive.common.security.AuthService;
import fr.leadrevive.common.security.LeadAccessGuard;
import fr.leadrevive.tache.dto.RepCalendrierDto;
import fr.leadrevive.tache.dto.RepMaJourneeDto;
import fr.leadrevive.tache.dto.RepTacheDto;
import fr.leadrevive.tache.dto.ReqStatutTacheDto;
import fr.leadrevive.tache.dto.ReqTacheDto;
import fr.leadrevive.tache.entity.StatutTacheEnum;
import fr.leadrevive.tache.service.TacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class TacheController {

    private final TacheService tacheService;
    private final AuthService authService;
    private final LeadAccessGuard leadAccessGuard;

    @PostMapping("/api/taches")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public RepTacheDto creerTache(@RequestBody final ReqTacheDto req) {
        // SECURITE : VENDEUR ne peut creer une tache que sur SES leads (404 anonyme sinon).
        if (req.leadId() != null) {
            leadAccessGuard.requireAccessibleLead(req.leadId());
        }
        final Long tenantId = authService.getCurrentTenantId();
        final Long vendeurId = authService.getCurrentUserId();
        return tacheService.creerTache(req, tenantId, vendeurId, "MANUELLE");
    }

    @PutMapping("/api/taches/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public RepTacheDto modifierTache(@PathVariable("id") final Long id,
                                      @RequestBody final ReqTacheDto req) {
        // SECURITE : si on (re)rattache la tache a un lead, VENDEUR doit etre titulaire
        // de CE lead (404 anonyme sinon). Sans ce guard, un vendeur pouvait pointer sa
        // tache vers le lead d'un collegue et en lire le nom via le DTO retourne (IDOR).
        if (req.leadId() != null) {
            leadAccessGuard.requireAccessibleLead(req.leadId());
        }
        final Long tenantId = authService.getCurrentTenantId();
        return tacheService.modifierTache(id, tenantId, req);
    }

    @PatchMapping("/api/taches/{id}/statut")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public RepTacheDto changerStatut(@PathVariable("id") final Long id,
                                      @RequestBody final ReqStatutTacheDto req) {
        final Long tenantId = authService.getCurrentTenantId();
        return tacheService.changerStatut(id, tenantId, StatutTacheEnum.valueOf(req.statut()));
    }

    /**
     * UNDO d'un "Effectue"/"Abandonner" sur une tache : la remet A_FAIRE (et un-cascade
     * le lead a RDV_PRIS si c'est un RDV lie). Ownership tache verifiee dans le service.
     */
    @PostMapping("/api/taches/{id}/rouvrir")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public RepTacheDto rouvrirTache(@PathVariable("id") final Long id) {
        final Long tenantId = authService.getCurrentTenantId();
        return tacheService.rouvrirTache(id, tenantId);
    }

    @PatchMapping("/api/taches/{id}/reporter")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public RepTacheDto reporterTache(@PathVariable("id") final Long id,
                                      @RequestBody final java.util.Map<String, String> body) {
        final Long tenantId = authService.getCurrentTenantId();
        final java.time.LocalDateTime nouvelleDateEcheance = java.time.LocalDateTime.parse(body.get("dateEcheance"));
        return tacheService.reporterTache(id, tenantId, nouvelleDateEcheance);
    }

    @DeleteMapping("/api/taches/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public ResponseEntity<Void> supprimerTache(@PathVariable("id") final Long id) {
        final Long tenantId = authService.getCurrentTenantId();
        tacheService.supprimerTache(id, tenantId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/taches/ma-journee")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public RepMaJourneeDto getMaJournee(
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date) {
        final Long tenantId = authService.getCurrentTenantId();
        final boolean isManager = "MANAGER".equals(authService.getCurrentRole());
        final Long vendeurId = isManager ? null : authService.getCurrentUserId();
        final LocalDate dateEffective = date != null ? date : LocalDate.now();
        return tacheService.getMaJournee(tenantId, vendeurId, dateEffective);
    }

    @GetMapping("/api/taches/calendrier")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public RepCalendrierDto getCalendrier(
            @RequestParam(value = "mois", required = false) final Integer mois,
            @RequestParam(value = "annee", required = false) final Integer annee,
            @RequestParam(value = "vendeurId", required = false) final Long vendeurId) {
        final Long tenantId = authService.getCurrentTenantId();
        final boolean isManager = "MANAGER".equals(authService.getCurrentRole());
        // SECURITE : un vendeur ne peut voir que SON calendrier. On ignore tout
        // vendeurId passe en query param et on force a son userId. Un manager
        // peut filtrer par vendeurId pour voir le calendrier d'un de ses vendeurs.
        final Long vendeurEffectif = isManager
                ? vendeurId
                : authService.getCurrentUserId();
        final int moisEffectif = mois != null ? mois : LocalDate.now().getMonthValue();
        final int anneeEffective = annee != null ? annee : LocalDate.now().getYear();
        return tacheService.getCalendrier(tenantId, vendeurEffectif, moisEffectif, anneeEffective);
    }

    @GetMapping("/api/leads/{leadId}/taches")
    @PreAuthorize("hasAnyRole('MANAGER', 'VENDEUR')")
    public List<RepTacheDto> getTachesParLead(@PathVariable("leadId") final Long leadId) {
        // SECURITE : VENDEUR ne peut lire les taches que de SES leads (404 anonyme sinon).
        leadAccessGuard.requireAccessibleLead(leadId);
        final Long tenantId = authService.getCurrentTenantId();
        return tacheService.getTachesParLead(leadId, tenantId);
    }
}
