package fr.leadrevive.common.security;

import fr.leadrevive.common.util.PiiMasker;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ClerkAdminClient {

    private final RestClient restClient;
    private final String apiKey;

    public ClerkAdminClient(@Value("${clerk.secret-key:}") final String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.clerk.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @SuppressWarnings("unchecked")
    @Retry(name = "clerkCall")
    @CircuitBreaker(name = "clerkCall")
    public String createOrganization(final String name, final String createdByClerkUserId) {
        log.info("Creation organization Clerk - nom: {}", name);
        final Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        if (createdByClerkUserId != null) {
            body.put("created_by", createdByClerkUserId);
        }
        final Map<String, Object> response = restClient.post()
                .uri("/organizations")
                .body(body)
                .retrieve()
                .body(Map.class);
        if (response == null) {
            throw new RuntimeException("Reponse Clerk vide");
        }
        return (String) response.get("id");
    }

    @Retry(name = "clerkCall")
    @CircuitBreaker(name = "clerkCall")
    public void inviteUserToOrganization(final String clerkOrgId, final String email, final String role,
                                         final String redirectUrl) {
        log.info("Invitation Clerk - orgId: {}, email: {}, role: {}", clerkOrgId, PiiMasker.email(email), role);
        final Map<String, Object> body = new HashMap<>();
        body.put("email_address", email);
        body.put("role", role);
        if (redirectUrl != null && !redirectUrl.isBlank()) {
            body.put("redirect_url", redirectUrl + "/login/accept-invitation");
        }
        try {
            restClient.post()
                    .uri("/organizations/" + clerkOrgId + "/invitations")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (final RestClientResponseException e) {
            // Quota de membres atteint (membres + invitations en attente) : condition
            // permanente, on la traduit en exception metier claire plutot que de laisser
            // remonter le 403 + JSON brut de Clerk jusqu'a l'utilisateur.
            if (e.getResponseBodyAsString().contains("organization_membership_quota_exceeded")) {
                log.warn("Invitation refusee : quota de membres atteint - orgId: {}", clerkOrgId);
                throw new OrganizationMembershipQuotaExceededException(
                        "Cet établissement a atteint sa limite de membres (invitations en attente comprises). "
                                + "Retirez un membre ou annulez une invitation en attente avant d'en inviter un nouveau.");
            }
            throw e;
        }
    }

    public void inviteSuperAdmin(final String email) {
        log.info("Invitation SUPER_ADMIN Clerk - email: {}", PiiMasker.email(email));
        final Map<String, Object> body = new HashMap<>();
        body.put("email_address", email);
        body.put("public_metadata", Map.of("isSuperAdmin", true));
        restClient.post()
                .uri("/invitations")
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listOrganizations() {
        final Object response = restClient.get()
                .uri("/organizations?limit=500")
                .retrieve()
                .body(Object.class);
        log.info("Liste organizations Clerk - response type: {}", response != null ? response.getClass().getSimpleName() : "null");
        if (response instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        if (response instanceof Map<?, ?> map && map.get("data") instanceof List<?> data) {
            return (List<Map<String, Object>>) data;
        }
        log.warn("Format de reponse Clerk inattendu : {}", response);
        return List.of();
    }

    @SuppressWarnings("unchecked")
    public long countUsers() {
        log.info("Comptage users Clerk");
        final Object response = restClient.get()
                .uri("/users/count")
                .retrieve()
                .body(Object.class);
        if (response instanceof Map<?, ?> map && map.get("total_count") instanceof Number n) {
            return n.longValue();
        }
        if (response instanceof Number n) return n.longValue();
        return 0L;
    }

    @SuppressWarnings("unchecked")
    public String getOrganizationName(final String clerkOrgId) {
        log.info("Recuperation nom organization Clerk - orgId: {}", clerkOrgId);
        final Map<String, Object> response = restClient.get()
                .uri("/organizations/" + clerkOrgId)
                .retrieve()
                .body(Map.class);
        if (response == null) return null;
        return (String) response.get("name");
    }

    @SuppressWarnings("unchecked")
    public void revokeInvitationByEmail(final String clerkOrgId, final String email) {
        log.info("Revocation invitation Clerk par email - orgId: {}, email: {}", clerkOrgId, PiiMasker.email(email));
        // Lister les invitations pending et trouver celle qui matche l'email
        final Map<String, Object> response = restClient.get()
                .uri("/organizations/" + clerkOrgId + "/invitations?status=pending&limit=100")
                .retrieve()
                .body(Map.class);
        if (response == null) return;
        final Object data = response.get("data");
        if (!(data instanceof List<?> list)) return;
        for (final Object item : list) {
            if (item instanceof Map<?, ?> inv) {
                final String invEmail = (String) inv.get("email_address");
                final String invId = (String) inv.get("id");
                if (email.equalsIgnoreCase(invEmail) && invId != null) {
                    log.info("Revocation invitation Clerk - invitationId: {}", invId);
                    restClient.post()
                            .uri("/organizations/" + clerkOrgId + "/invitations/" + invId + "/revoke")
                            .retrieve()
                            .toBodilessEntity();
                    return;
                }
            }
        }
        log.warn("Aucune invitation pending trouvee pour email: {} dans org: {}", PiiMasker.email(email), clerkOrgId);
    }

    @Retry(name = "clerkCall")
    @CircuitBreaker(name = "clerkCall")
    public void removeMemberFromOrganization(final String clerkOrgId, final String clerkUserId) {
        log.info("Retrait membre org Clerk - orgId: {}, userId: {}", clerkOrgId, clerkUserId);
        restClient.delete()
                .uri("/organizations/" + clerkOrgId + "/memberships/" + clerkUserId)
                .retrieve()
                .toBodilessEntity();
    }

    @Retry(name = "clerkCall")
    @CircuitBreaker(name = "clerkCall")
    public void deleteOrganization(final String clerkOrgId) {
        log.info("Suppression organization Clerk - orgId: {}", clerkOrgId);
        restClient.delete()
                .uri("/organizations/" + clerkOrgId)
                .retrieve()
                .toBodilessEntity();
    }

    public void deactivateUser(final String clerkUserId) {
        log.info("Desactivation user Clerk - userId: {}", clerkUserId);
        restClient.post()
                .uri("/users/" + clerkUserId + "/lock")
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Suppression definitive du compte Clerk. Differe de {@link #deactivateUser}
     * (lock) qui garde le compte. A utiliser quand on veut effacer le user pour
     * de bon (suppression de concession, RGPD droit a l'oubli).
     */
    @Retry(name = "clerkCall")
    @CircuitBreaker(name = "clerkCall")
    public void deleteUser(final String clerkUserId) {
        log.info("Suppression user Clerk - userId: {}", clerkUserId);
        restClient.delete()
                .uri("/users/" + clerkUserId)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Recupere l'adresse email primaire d'un user Clerk. Sert de fallback dans
     * {@link ClerkJwtConverter} quand le JWT ne contient pas l'email comme claim
     * (le template Clerk peut etre configure sans). Sans ca, un manager fraichement
     * invite ne peut pas etre lie a son mirror INVITE -> DisabledException -> 401.
     */
    @SuppressWarnings("unchecked")
    public String getUserPrimaryEmail(final String clerkUserId) {
        log.info("Recuperation email user Clerk - userId: {}", clerkUserId);
        final Map<String, Object> response = restClient.get()
                .uri("/users/" + clerkUserId)
                .retrieve()
                .body(Map.class);
        if (response == null) return null;
        final Object primaryId = response.get("primary_email_address_id");
        final Object emailAddresses = response.get("email_addresses");
        if (!(emailAddresses instanceof List<?> list)) return null;
        for (final Object item : list) {
            if (item instanceof Map<?, ?> emailObj) {
                final Object id = emailObj.get("id");
                if (primaryId != null && primaryId.equals(id)) {
                    return (String) emailObj.get("email_address");
                }
            }
        }
        // Pas de primary identifie, retourne le premier email trouve
        for (final Object item : list) {
            if (item instanceof Map<?, ?> emailObj) {
                final String addr = (String) emailObj.get("email_address");
                if (addr != null && !addr.isBlank()) return addr;
            }
        }
        return null;
    }
}
