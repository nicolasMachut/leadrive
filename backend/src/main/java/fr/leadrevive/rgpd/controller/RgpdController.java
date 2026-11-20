package fr.leadrevive.rgpd.controller;

import fr.leadrevive.common.security.AuthService;
import fr.leadrevive.rgpd.service.RgpdService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/rgpd")
@RequiredArgsConstructor
public class RgpdController {

    private final RgpdService rgpdService;
    private final AuthService authService;

    /**
     * Supprime toutes les donnees d'un prospect (Art. 17 RGPD).
     * Recherche par telephone et/ou email. Au moins un des deux doit etre fourni.
     */
    @DeleteMapping("/prospect")
    @PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> supprimerProspect(
            @RequestParam(value = "telephone", required = false) final String telephone,
            @RequestParam(value = "email", required = false) final String email) {
        if ((telephone == null || telephone.isBlank()) && (email == null || email.isBlank())) {
            return ResponseEntity.badRequest().body(Map.of("erreur", "telephone ou email requis"));
        }
        final Long tenantId = authService.getCurrentTenantId();
        final int count = rgpdService.supprimerDonneesProspect(tenantId, telephone, email);
        // RGPD Art. 17 : on distingue "supprime" (200, count>=1) de "non trouve" (404).
        // Sans cette distinction, un audit CNIL "pouvez-vous prouver que ce prospect n'existe pas ?"
        // est ambigu : 200/0 peut signifier suppression OU absence.
        if (count == 0) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                    .body(Map.of("erreur", "Aucun prospect trouvé avec ces identifiants",
                                  "leadsSupprimes", 0));
        }
        return ResponseEntity.ok(Map.of("leadsSupprimes", count));
    }

    /**
     * Exporte les donnees d'un prospect (Art. 20 RGPD — portabilite).
     */
    @GetMapping("/prospect/export")
    @PreAuthorize("hasAnyRole('MANAGER', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Object>> exporterProspect(
            @RequestParam(value = "telephone", required = false) final String telephone,
            @RequestParam(value = "email", required = false) final String email) {
        if ((telephone == null || telephone.isBlank()) && (email == null || email.isBlank())) {
            return ResponseEntity.badRequest().body(Map.of("erreur", "telephone ou email requis"));
        }
        final Long tenantId = authService.getCurrentTenantId();
        final Map<String, Object> export = rgpdService.exporterDonneesProspect(tenantId, telephone, email);
        @SuppressWarnings("unchecked")
        final java.util.List<Object> leads = (java.util.List<Object>) export.getOrDefault("leads", java.util.Collections.emptyList());
        if (leads.isEmpty()) {
            // Pas de prospect trouve => 404. Permet un audit CNIL clair sur les
            // demandes d'acces (Art. 15) infructueuses.
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                    .body(Map.of("erreur", "Aucun prospect trouvé avec ces identifiants"));
        }
        return ResponseEntity.ok(export);
    }
}
