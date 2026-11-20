package fr.leadrevive.common.admin;

import fr.leadrevive.common.admin.dto.RepAdminDashboardDto;
import fr.leadrevive.common.admin.dto.RepConcessionSimpleDto;
import fr.leadrevive.common.admin.dto.RepUsageIaDto;
import fr.leadrevive.common.admin.dto.ReqCreateConcessionDto;
import fr.leadrevive.common.admin.dto.ReqInviterManagerDto;
import fr.leadrevive.common.admin.dto.ReqInviterVendeurDto;
import fr.leadrevive.user.dto.RepImpactDesactivationDto;
import fr.leadrevive.user.dto.RepVendeurDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/dashboard")
    public RepAdminDashboardDto dashboard() {
        return adminService.getDashboard();
    }

    @GetMapping("/concessions")
    public List<RepConcessionSimpleDto> listerConcessions() {
        return adminService.listerConcessions();
    }

    @GetMapping("/concessions/{id}/details")
    public fr.leadrevive.common.admin.dto.RepConcessionDetailDto getDetailsConcession(@PathVariable final Long id) {
        return adminService.getDetailsConcession(id);
    }

    // Endpoint supprime : le re-push profil WhatsApp via Twilio Senders API n'est
    // plus pertinent depuis le sunset LEGACY. Le profil Meta Business est gere
    // directement par le manager via Facebook Embedded Signup.

    /**
     * Force le rafraichissement du statut WhatsApp depuis Twilio.
     * Utile pour diagnostiquer un sender bloque en ERREUR / OFFLINE :
     * l'API renvoie offline_reasons[] qui explique pourquoi.
     */
    @PostMapping("/concessions/{id}/synchroniser-statut-twilio")
    public ResponseEntity<Void> synchroniserStatutTwilio(@PathVariable final Long id) {
        adminService.synchroniserStatutTwilio(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Liste les configs WhatsApp en statut ERREUR. Outil de support proactif.
     */
    @GetMapping("/onboardings-bloques")
    public List<fr.leadrevive.common.admin.dto.RepConcessionDetailDto.OnboardingBloqueDto>
            listerOnboardingsBloques() {
        return adminService.listerOnboardingsBloques();
    }

    @PostMapping("/concessions")
    public ResponseEntity<Void> creerConcession(@RequestBody @Valid final ReqCreateConcessionDto request) {
        adminService.creerConcession(request);
        return ResponseEntity.status(201).build();
    }

    @DeleteMapping("/concessions/{id}")
    public ResponseEntity<Void> supprimerConcession(@PathVariable final Long id) {
        adminService.supprimerConcession(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/concessions/{id}/managers")
    public List<RepVendeurDto> listerManagers(@PathVariable final Long id) {
        return adminService.listerManagers(id);
    }

    @PostMapping("/concessions/{id}/managers")
    public ResponseEntity<Void> inviterManager(@PathVariable final Long id,
                                                @RequestBody @Valid final ReqInviterManagerDto request) {
        adminService.inviterManager(id, request);
        return ResponseEntity.status(201).build();
    }

    @DeleteMapping("/concessions/{id}/managers/{userId}")
    public ResponseEntity<String> supprimerManager(@PathVariable final Long id,
                                                    @PathVariable final Long userId) {
        final String erreur = adminService.supprimerManager(id, userId);
        if (erreur != null) {
            return ResponseEntity.badRequest().body(erreur);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/concessions/{id}/utilisateurs")
    public List<RepVendeurDto> listerUtilisateurs(@PathVariable final Long id) {
        return adminService.listerUtilisateurs(id);
    }

    @PostMapping("/concessions/{id}/vendeurs")
    public ResponseEntity<Void> inviterVendeur(@PathVariable final Long id,
                                                @RequestBody @Valid final ReqInviterVendeurDto request) {
        adminService.inviterVendeur(id, request);
        return ResponseEntity.status(201).build();
    }

    /**
     * Preview de l'impact d'une desactivation : compteurs leads / convs / taches
     * + destinataire (1er manager actif). Endpoint dedie pour permettre au front
     * d'afficher une modal de confirmation precise avant le DELETE.
     */
    @GetMapping("/concessions/{id}/vendeurs/{userId}/impact-desactivation")
    public RepImpactDesactivationDto impactDesactivationVendeur(@PathVariable final Long id,
                                                                  @PathVariable final Long userId) {
        return adminService.calculerImpactVendeur(id, userId);
    }

    @DeleteMapping("/concessions/{id}/vendeurs/{userId}")
    public ResponseEntity<String> supprimerVendeur(@PathVariable final Long id,
                                                    @PathVariable final Long userId,
                                                    @RequestParam(defaultValue = "false") final boolean reassigner) {
        final String erreur = adminService.supprimerVendeur(id, userId, reassigner);
        if (erreur != null) {
            return ResponseEntity.badRequest().body(erreur);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/monitoring/usage-ia")
    public List<RepUsageIaDto> usageIa(@RequestParam(defaultValue = "30") final int jours) {
        return adminService.usageIa(jours);
    }
}
