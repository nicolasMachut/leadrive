package fr.leadrevive.common.exception;

import fr.leadrevive.common.ratelimit.RateLimitExceededException;
import fr.leadrevive.common.util.PiiMasker;
import fr.leadrevive.csvimport.exception.CsvParsingException;
import fr.leadrevive.csvimport.exception.ImportNotFoundException;
import fr.leadrevive.lead.exception.LeadActifExistantException;
import fr.leadrevive.lead.exception.LeadNotFoundException;
import fr.leadrevive.outreach.exception.ModerationBlockedException;
import fr.leadrevive.outreach.exception.WhatsAppNonConfigureException;
import fr.leadrevive.outreach.exception.WhatsAppOnboardingException;
import fr.leadrevive.tenant.exception.TenantNotFoundException;
import fr.leadrevive.user.exception.UserAlreadyExistsException;
import fr.leadrevive.user.exception.UserNotFoundException;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(LeadNotFoundException.class)
    public ResponseEntity<String> handleLeadNotFoundException(final LeadNotFoundException ex) {
        log.warn("Lead non trouve - id: {}", ex.getLeadId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    /**
     * Desarchivage impossible : un lead actif partage deja le telephone/email (collision
     * d'index unique V068). 409 avec message clair invitant a fusionner/modifier.
     */
    @ExceptionHandler(LeadActifExistantException.class)
    public ResponseEntity<String> handleLeadActifExistant(final LeadActifExistantException ex) {
        log.warn("Desarchivage refuse : lead actif existant - cause: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(ImportNotFoundException.class)
    public ResponseEntity<String> handleImportNotFoundException(final ImportNotFoundException ex) {
        log.warn("Import non trouve - id: {}", ex.getImportId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(CsvParsingException.class)
    public ResponseEntity<String> handleCsvParsingException(final CsvParsingException ex) {
        log.warn("Erreur parsing CSV - cause: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalStateException(final IllegalStateException ex) {
        log.warn("Erreur metier - cause: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(final IllegalArgumentException ex) {
        log.warn("Argument invalide - cause: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<String> handleTenantNotFoundException(final TenantNotFoundException ex) {
        log.warn("Tenant non trouve - id: {}", ex.getTenantId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<String> handleUserNotFoundException(final UserNotFoundException ex) {
        log.warn("Utilisateur non trouve - id: {}", ex.getUserId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<String> handleUserAlreadyExistsException(final UserAlreadyExistsException ex) {
        log.warn("Utilisateur deja existant - email: {}", PiiMasker.email(ex.getEmail()));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    /**
     * Quota de membres de l'organisation atteint cote fournisseur d'auth (membres +
     * invitations en attente). 409 avec message clair au lieu d'un 500 exposant le
     * JSON brut du fournisseur. Le mirror utilisateur cree juste avant est deja
     * rollback par le @Transactional de l'appelant.
     */
    @ExceptionHandler(fr.leadrevive.common.security.OrganizationMembershipQuotaExceededException.class)
    public ResponseEntity<String> handleMembershipQuota(
            final fr.leadrevive.common.security.OrganizationMembershipQuotaExceededException ex) {
        log.warn("Invitation refusee : quota de membres atteint");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(final MethodArgumentNotValidException ex) {
        log.warn("Erreur de validation - nombre d'erreurs: {}", ex.getBindingResult().getErrorCount());
        final Map<String, String> erreurs = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                erreurs.put(error.getField(), error.getDefaultMessage())
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(erreurs);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<String> handleRateLimit(final RateLimitExceededException ex) {
        log.warn("Rate limit depasse - cause: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ex.getMessage());
    }

    /**
     * Mauvais role / droit insuffisant → 403 (jamais 500). Sans cette regle, la regle generique
     * en bas ferait remonter une stack trace en INTERNAL_SERVER_ERROR, ce qui leak des infos
     * et confond le client.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> handleAccessDenied(final AccessDeniedException ex) {
        log.warn("Acces refuse - cause: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Acces refuse");
    }

    /**
     * Token absent / invalide / expire → 401. Spring Security le gere normalement via
     * son point d'entree, mais on ajoute un filet de securite.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<String> handleAuthentication(final AuthenticationException ex) {
        log.warn("Authentification echouee - cause: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentification requise");
    }

    // ===== Exceptions Epic 23 - WhatsApp multi-numero =====
    // Format JSON structure {code, message, ...} pour permettre au frontend
    // de proposer des actions ciblees (lien vers la page de configuration, etc.)

    /**
     * WhatsApp non configure pour le tenant courant : envoi impossible tant
     * que le manager n'a pas active sa configuration. 422 avec lien d'action.
     */
    @ExceptionHandler(WhatsAppNonConfigureException.class)
    public ResponseEntity<Map<String, Object>> handleWhatsAppNonConfigure(final WhatsAppNonConfigureException ex) {
        log.warn("Tentative envoi WhatsApp sans config - tenantId: {}", ex.getTenantId());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "code", "WHATSAPP_NON_CONFIGURE",
                "message", ex.getMessage(),
                "action", "/parametres/whatsapp/activation"
        ));
    }

    /**
     * Erreur metier pendant l'onboarding WhatsApp (numero deja pris, code
     * invalide, etc.). 400 avec code et message specifiques.
     */
    @ExceptionHandler(WhatsAppOnboardingException.class)
    public ResponseEntity<Map<String, Object>> handleWhatsAppOnboarding(final WhatsAppOnboardingException ex) {
        log.warn("Erreur onboarding WhatsApp - code: {}, message: {}", ex.getCodeErreur(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "code", ex.getCodeErreur(),
                "message", ex.getMessage()
        ));
    }

    /**
     * Message manuel bloque par la moderation IA. 422 avec raison precise
     * pour permettre la reformulation cote UI.
     */
    @ExceptionHandler(ModerationBlockedException.class)
    public ResponseEntity<Map<String, Object>> handleModerationBlocked(final ModerationBlockedException ex) {
        log.warn("Message bloque par moderation - raison: {}", ex.getRaisonModeration());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "code", "MESSAGE_BLOQUE_MODERATION",
                "message", "Votre message ne peut pas etre envoye : " + ex.getRaisonModeration()
                        + ". Reformulez et reessayez.",
                "raison", ex.getRaisonModeration()
        ));
    }

    /**
     * Conflit de modification optimiste (2 acteurs modifient la meme config
     * en parallele - typiquement 2 managers d'un meme tenant). 409 avec
     * message clair invitant a rafraichir la page.
     */
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(final OptimisticLockException ex) {
        log.warn("Conflit modification optimiste - cause: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", "CONFLIT_MODIFICATION",
                "message", "La configuration a ete modifiee par un autre utilisateur. Rafraichissez la page."
        ));
    }

    /**
     * Variante Spring de OptimisticLockException : Spring Data JPA wrappe la
     * version jakarta dans son propre type avant de la propager. Sans handler
     * dédié, le wrapper Spring tombait dans le catch-all Exception -> 500.
     * Avec V066 et @Version ajoutés sur Lead/SmsConversation/Tache, ce handler
     * devient actif et renvoie un 409 user-friendly.
     */
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleSpringOptimisticLock(
            final org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
        log.warn("Conflit modification optimiste (Spring) - entite: {}, cause: {}",
                ex.getPersistentClassName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", "CONFLIT_MODIFICATION",
                "message", "Cette donnee a ete modifiee par un autre utilisateur. Rafraichissez la page."
        ));
    }

    /**
     * Quota IA quotidien depasse pour le tenant. 429 avec message clair pour
     * que le frontend puisse afficher "Trop d'appels IA aujourd'hui, reessayez demain".
     */
    @ExceptionHandler(fr.leadrevive.ai.service.AiQuotaGuard.AiQuotaDepasseException.class)
    public ResponseEntity<Map<String, Object>> handleAiQuotaDepasse(
            final fr.leadrevive.ai.service.AiQuotaGuard.AiQuotaDepasseException ex) {
        log.warn("Quota IA depasse - cause: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                "code", "AI_QUOTA_DEPASSE",
                "message", ex.getMessage()
        ));
    }

    /**
     * ResponseStatusException porte deja son propre status (404, 403, etc.). Sans ce handler,
     * la regle generique en dessous ferait remonter du 500 a la place — masquant les 404 anonymes
     * du LeadAccessGuard et autres garde-fous de visibilite.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(final ResponseStatusException ex) {
        log.warn("Reponse explicite - status: {}, cause: {}", ex.getStatusCode().value(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(ex.getReason());
    }

    /**
     * Client SSE deconnecte pendant un send (typique : navigation, F5, fermeture
     * onglet, timeout proxy ngrok). Spring throw {@code AsyncRequestNotUsableException}
     * wrappant un {@code Broken pipe} IOException. Sans ce handler, chaque
     * deconnexion SSE logge une stack en ERROR → Sentry inonde, fausses alertes
     * pour un comportement parfaitement normal. On log en INFO (cleanup deja
     * fait par le SseConnectionService via onError) et on retourne 200 vide
     * puisque la connexion est deja morte cote client.
     */
    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> handleAsyncRequestNotUsable(
            final org.springframework.web.context.request.async.AsyncRequestNotUsableException ex) {
        log.info("Client SSE deconnecte (normal) - cause: {}", ex.getMessage());
        return ResponseEntity.ok().build();
    }

    /**
     * Timeout du long-poll async d'un stream SSE (RealtimeController, inbox). Quand aucun
     * evenement n'est emis pendant la duree du timeout servlet, Spring throw
     * {@code AsyncRequestTimeoutException}. C'est le cycle de vie NORMAL d'un SSE : le client
     * se reconnecte aussitot. Sans ce handler, l'exception tombait dans le catch-all et etait
     * loggee en ERROR avec une stack trace + "cause: null" toutes les ~6 min → Sentry inonde,
     * fausses alertes. On log en DEBUG et on retourne 200 vide.
     */
    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestTimeoutException.class)
    public ResponseEntity<Void> handleAsyncRequestTimeout(
            final org.springframework.web.context.request.async.AsyncRequestTimeoutException ex) {
        log.debug("Timeout stream SSE (normal, le client se reconnecte)");
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(final Exception ex) {
        // Broken pipe peut remonter sans wrapper Spring sur certains chemins async.
        // Meme traitement : c'est normal pour SSE, pas une vraie erreur.
        if (ex.getCause() instanceof java.io.IOException && ex.getCause().getMessage() != null
                && ex.getCause().getMessage().toLowerCase().contains("broken pipe")) {
            log.info("Client deconnecte mid-stream (normal) - cause: {}", ex.getCause().getMessage());
            return ResponseEntity.ok().build();
        }
        log.error("Erreur inattendue - cause: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur interne du serveur");
    }
}
