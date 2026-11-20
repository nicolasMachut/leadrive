package fr.leadrevive.outreach.controller;

import fr.leadrevive.common.security.AuthService;
import fr.leadrevive.outreach.service.WhatsAppConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sert le logo PNG WhatsApp Business du tenant courant.
 *
 * <p>Utilise par le composant {@code TenantLogoImage} cote front
 * (sidebar, header) pour afficher le logo du tenant.</p>
 */
@RestController
@RequestMapping("/api/parametres/whatsapp")
@RequiredArgsConstructor
@Slf4j
public class WhatsAppLogoController {

    private final WhatsAppConfigService onboardingService;
    private final AuthService authService;

    /**
     * Retourne le logo PNG du tenant courant, ou 204 No Content si pas de logo
     * uploade. On renvoie 204 (et non 404) car la route existe : l'absence de
     * logo est un etat normal, pas une erreur. Cela evite que le navigateur
     * loggue une erreur rouge en console a chaque page sans logo. Accessible a
     * tous les utilisateurs du tenant pour l'afficher dans la sidebar / header.
     */
    @GetMapping(value = "/profil/logo", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> recupererLogo() {
        final Long tenantId = authService.getCurrentTenantId();
        // Defense en profondeur : si le tenant est null (user mirror supprime,
        // tenant desactive, etc.), pas de logo a renvoyer.
        if (tenantId == null) {
            return ResponseEntity.noContent().build();
        }
        final byte[] bytes = onboardingService.recupererLogo(tenantId);
        if (bytes == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok()
                .header("Cache-Control", "private, max-age=300")
                .body(bytes);
    }
}
