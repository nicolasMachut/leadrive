package fr.leadrevive.user.controller;

import fr.leadrevive.common.security.AuthService;
import fr.leadrevive.user.dto.RepMeDto;
import fr.leadrevive.user.dto.ReqOnboardingDto;
import fr.leadrevive.user.entity.User;
import fr.leadrevive.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class MeController {

    private final AuthService authService;
    private final UserRepository userRepository;

    @PatchMapping("/onboarding")
    @Transactional
    public RepMeDto updateOnboarding(@RequestBody final ReqOnboardingDto request) {
        final Long userId = authService.getCurrentUserId();
        if (userId == null) {
            throw new org.springframework.security.authentication.DisabledException(
                    "Compte introuvable. Reconnectez-vous.");
        }
        final User u = userRepository.findById(userId)
                .orElseThrow(() -> new org.springframework.security.authentication.DisabledException(
                        "Compte introuvable. Reconnectez-vous."));
        log.info("Mise a jour onboarding - utilisateur: {}, etape: {}, termine: {}",
                userId, request.getOnboardingEtape(), request.getOnboardingTermine());
        if (request.getOnboardingTermine() != null) {
            u.setOnboardingTermine(request.getOnboardingTermine());
        }
        if (request.getOnboardingEtape() != null) {
            u.setOnboardingEtape(request.getOnboardingEtape());
        }
        final User saved = userRepository.save(u);
        return RepMeDto.builder()
                .id(saved.getId())
                .email(saved.getEmail())
                .prenom(saved.getPrenom())
                .nom(saved.getNom())
                .role(saved.getRole().name())
                .tenantId(saved.getTenantId())
                .onboardingTermine(saved.getOnboardingTermine())
                .onboardingEtape(saved.getOnboardingEtape())
                .build();
    }

    @GetMapping
    public RepMeDto me() {
        // SUPER_ADMIN : pas de mirror local (par design - opere en cross-tenant).
        // On renvoie un DTO minimal pour que le frontend puisse lire le role et
        // rediriger vers /admin sans tomber dans un 401 (compte valide cote Clerk).
        final String role = authService.getCurrentRole();
        if ("SUPER_ADMIN".equals(role)) {
            return RepMeDto.builder().role("SUPER_ADMIN").build();
        }

        final Long userId = authService.getCurrentUserId();
        // Mirror utilisateur introuvable = probleme d'auth (compte supprime, tenant
        // desactive), pas une ressource "manquante". On renvoie 401 (via
        // DisabledException) pour que le frontend useApiClient detecte et redirige
        // vers /login proprement. Sans ca, le frontend recoit 404 et l'utilisateur
        // se retrouve coince dans une coquille vide.
        if (userId == null) {
            log.warn("Mirror utilisateur introuvable pour le JWT courant - 401");
            throw new org.springframework.security.authentication.DisabledException(
                    "Compte introuvable. Reconnectez-vous.");
        }
        final User u = userRepository.findById(userId)
                .orElseThrow(() -> new org.springframework.security.authentication.DisabledException(
                        "Compte introuvable. Reconnectez-vous."));
        return RepMeDto.builder()
                .id(u.getId())
                .email(u.getEmail())
                .prenom(u.getPrenom())
                .nom(u.getNom())
                .role(u.getRole().name())
                .tenantId(u.getTenantId())
                .onboardingTermine(u.getOnboardingTermine())
                .onboardingEtape(u.getOnboardingEtape())
                .build();
    }
}
