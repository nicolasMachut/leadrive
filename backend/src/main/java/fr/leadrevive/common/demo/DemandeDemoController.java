package fr.leadrevive.common.demo;

import fr.leadrevive.common.ratelimit.RateLimitExceededException;
import fr.leadrevive.common.ratelimit.RateLimiter;
import fr.leadrevive.common.util.PiiMasker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class DemandeDemoController {

    private final DemandeDemoRepository demandeDemoRepository;
    private final RateLimiter rateLimiter;

    // Endpoint PUBLIC — pas d'auth
    @PostMapping("/api/public/demande-demo")
    public ResponseEntity<Void> demanderDemo(@RequestBody @Valid final ReqDemandeDemoDto request,
                                              final HttpServletRequest httpRequest) {
        // Rate limit par IP : 5 demandes/min/IP. Protege contre le spam.
        final String ip = clientIp(httpRequest);
        if (!rateLimiter.tryAcquire("demande-demo:" + ip, 5)) {
            throw new RateLimitExceededException("Trop de demandes en peu de temps. Reessayez dans 1 minute.");
        }

        // Anti-doublon applicatif : retour 200 idempotent si l'email a deja une
        // demande NOUVEAU. Evite le 500 si l'attaquant VPN bypasse le rate limit
        // (la contrainte UNIQUE en BDD - V054 - rattrappe en derniere ligne, mais
        // un 200 idempotent est plus propre cote UX que d'exposer un conflit BDD).
        if (demandeDemoRepository.existsByEmailAndStatut(request.getEmail(), "NOUVEAU")) {
            log.info("Demande demo deja existante (idempotent) - email: {}", PiiMasker.email(request.getEmail()));
            return ResponseEntity.status(200).build();
        }

        log.info("Nouvelle demande de demo - nom: {}, concession: {}, email: {}, ip: {}",
                PiiMasker.name(request.getNom()), request.getConcession(),
                PiiMasker.email(request.getEmail()), ip);

        final DemandeDemo demande = new DemandeDemo();
        demande.setNom(request.getNom());
        demande.setConcession(request.getConcession());
        demande.setEmail(request.getEmail());
        demande.setTelephone(request.getTelephone());
        demande.setVolumeLeads(request.getVolumeLeads());
        demandeDemoRepository.save(demande);

        return ResponseEntity.status(201).build();
    }

    // Endpoint SUPER_ADMIN — lister les demandes
    @GetMapping("/api/admin/demandes-demo")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<DemandeDemo> listerDemandes() {
        return demandeDemoRepository.findTop500ByOrderByCreeLe();
    }

    /** Recupere l'IP cliente en gerant le reverse proxy (Railway). */
    private String clientIp(final HttpServletRequest request) {
        final String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Getter
    @Setter
    public static class ReqDemandeDemoDto {
        @NotBlank private String nom;
        @NotBlank private String concession;
        @NotBlank @Email private String email;
        private String telephone;
        private String volumeLeads;
    }
}
