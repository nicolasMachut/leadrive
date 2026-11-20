package fr.leadrevive.outreach.controller;

import fr.leadrevive.outreach.service.WhatsAppConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint PUBLIC qui sert le logo d'un tenant pour qu'il soit accessible
 * par Twilio/Meta lors de la mise a jour du profil WhatsApp Business.
 *
 * <p><strong>Pourquoi public</strong> : l'API Twilio Senders v2 attend une
 * {@code profile.logo_url} HTTPS publique (fetch serveur-a-serveur cote Meta,
 * pas d'auth Bearer possible). Notre endpoint authentifie
 * {@code /api/parametres/whatsapp/profil/logo} est invisible pour Meta.</p>
 *
 * <p><strong>Securite</strong> : le logo est par definition une image
 * professionnelle deja visible a tous les prospects WhatsApp. Pas de PII,
 * pas de secret. Le tenantId etant enumerable, un attaquant pourrait scanner
 * les logos -- acceptable pour un logo business public.</p>
 *
 * <p>Cache-Control public pour permettre a Meta/Twilio de mettre en cache
 * l'image. ETag implicite via la date de modification (Spring le gere
 * automatiquement sur byte[]).</p>
 */
@RestController
@RequestMapping("/api/public/whatsapp")
@RequiredArgsConstructor
@Slf4j
public class PublicLogoController {

    private final WhatsAppConfigService onboardingService;

    @GetMapping(value = "/logo/{tenantId}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> recupererLogoPublic(@PathVariable final Long tenantId) {
        final byte[] bytes = onboardingService.recupererLogo(tenantId);
        if (bytes == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header("Cache-Control", "public, max-age=3600")
                .body(bytes);
    }
}
