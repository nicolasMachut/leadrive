package fr.leadrevive.user.service;

import fr.leadrevive.common.util.PiiMasker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    public void envoyerInvitation(final String email, final String prenom, final String lienInvitation) {
        // PII : on ne log ni l'email en clair, ni le token d'invitation (qui permettrait
        // a quiconque ayant acces aux logs de prendre le controle du compte). Cf RGPD art. 5(1)(f).
        log.info("EMAIL INVITATION [MOCK] - to: {}, prenom: {}, lien: [redacted]",
                PiiMasker.email(email), PiiMasker.name(prenom));
    }

    public void envoyerResetPassword(final String email, final String lienReset) {
        // Idem : le lien contient un token de reset, jamais en clair dans les logs.
        log.info("EMAIL RESET [MOCK] - to: {}, lien: [redacted]", PiiMasker.email(email));
    }

    // ====================================================================
    // Notifications WhatsApp Business (epic 23)
    // ====================================================================
    // Note : ces methodes restent au stade de log structure. Le projet utilisera
    // un vrai provider (Sendgrid, AWS SES, etc.) post-MVP. Les events sont
    // identifiables dans les logs pour configurer des alertes Sentry.

    public void notifierActivationWhatsApp(final Long tenantId) {
        log.info("EMAIL WHATSAPP ACTIVATION [MOCK] - tenantId: {}", tenantId);
    }

    public void notifierRejetMeta(final Long tenantId, final String raison) {
        log.warn("EMAIL WHATSAPP REJET META [MOCK] - tenantId: {}, raison: {}", tenantId, raison);
    }

    public void alerterAdminQualityRatingJaune(final Long tenantId) {
        log.warn("ALERTE ADMIN [MOCK] - quality_rating JAUNE - tenantId: {}", tenantId);
    }

    public void alerterAdminQualityRatingRouge(final Long tenantId) {
        log.error("ALERTE ADMIN [MOCK] - quality_rating ROUGE - tenantId: {}", tenantId);
    }

    public void alerterAdminValidationLongue(final Long tenantId) {
        log.warn("ALERTE ADMIN [MOCK] - validation Meta > 48h - tenantId: {}", tenantId);
    }
}
