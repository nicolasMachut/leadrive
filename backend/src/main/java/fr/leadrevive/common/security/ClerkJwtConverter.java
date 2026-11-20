package fr.leadrevive.common.security;

import fr.leadrevive.common.tenant.TenantContext;
import fr.leadrevive.common.util.PiiMasker;
import fr.leadrevive.user.entity.RoleEnum;
import fr.leadrevive.user.entity.StatutUtilisateurEnum;
import fr.leadrevive.user.entity.User;
import fr.leadrevive.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClerkJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;
    private final ClerkAdminClient clerkAdminClient;

    @Override
    public AbstractAuthenticationToken convert(final Jwt jwt) {
        final String clerkUserId = jwt.getSubject();
        final String clerkOrgId = jwt.getClaimAsString("org_id");
        final String orgRole = jwt.getClaimAsString("org_role");

        // Verification SUPER_ADMIN via public_metadata
        final Map<String, Object> publicMetadata = jwt.getClaim("public_metadata");
        final boolean isSuperAdmin = publicMetadata != null && Boolean.TRUE.equals(publicMetadata.get("isSuperAdmin"));

        final RoleEnum role;
        if (isSuperAdmin) {
            role = RoleEnum.SUPER_ADMIN;
        } else if ("org:manager".equals(orgRole) || "admin".equals(orgRole) || "org:admin".equals(orgRole)) {
            role = RoleEnum.MANAGER;
        } else if ("org:vendeur".equals(orgRole) || "basic_member".equals(orgRole) || "org:member".equals(orgRole)) {
            role = RoleEnum.VENDEUR;
        } else if (orgRole != null) {
            log.warn("JWT Clerk role inconnu - clerkUserId: {}, orgRole: {}", clerkUserId, orgRole);
            role = RoleEnum.VENDEUR;
        } else {
            log.warn("JWT Clerk sans role - clerkUserId: {}", clerkUserId);
            role = RoleEnum.VENDEUR;
        }

        Long userId = null;
        Long tenantId = null;

        if (role != RoleEnum.SUPER_ADMIN) {
            final Optional<User> byClerkUserId = userRepository.findByClerkUserId(clerkUserId);
            if (byClerkUserId.isPresent()) {
                final User user = byClerkUserId.get();
                // Garde-fou : un user desactive (actif=false, soft delete par
                // AdminService.supprimerUser) ne doit plus pouvoir acceder a l'API
                // meme si sa session Clerk reste valide. Sans ce check, un manager
                // desactive conserverait tous ses droits jusqu'a l'expiration JWT.
                if (Boolean.FALSE.equals(user.getActif())) {
                    log.warn("Acces refuse - user desactive - userId: {}, clerkUserId: {}", user.getId(), clerkUserId);
                    // userId/tenantId restent null => les controllers refuseront via filtre Hibernate
                    // ou via @PreAuthorize qui verifie role.
                } else {
                    userId = user.getId();
                    tenantId = user.getTenantId();
                    // MAJ derniere connexion AU PLUS une fois / 5 min : ce converter tourne sur
                    // CHAQUE requête authentifiée (y compris les polls SSE/compteurs). Écrire à
                    // chaque appel = 1 UPDATE par requête sur le chemin chaud + contention sur la
                    // ligne utilisateur (plusieurs onglets/SSE). Le throttle suffit largement.
                    final LocalDateTime derniere = user.getDerniereConnexionLe();
                    if (derniere == null || derniere.isBefore(LocalDateTime.now().minusMinutes(5))) {
                        user.setDerniereConnexionLe(LocalDateTime.now());
                        userRepository.save(user);
                    }
                }
            } else {
                // Fallback : match par email (premier login apres acceptation d'invitation).
                // L'email peut etre absent du JWT custom (template leadrevive-backend) si non
                // configure dans le dashboard Clerk. Dans ce cas, on le recupere via l'API
                // Clerk avec le clerkUserId - indispensable pour lier le mirror INVITE.
                String email = firstNonBlank(
                        jwt.getClaimAsString("email"),
                        jwt.getClaimAsString("primary_email_address")
                );
                if (email.isBlank()) {
                    try {
                        final String fetched = clerkAdminClient.getUserPrimaryEmail(clerkUserId);
                        if (fetched != null) email = fetched;
                    } catch (final Exception e) {
                        log.warn("Recuperation email Clerk echec (fallback) - clerkUserId: {}, cause: {}",
                                clerkUserId, e.getMessage());
                    }
                }
                if (!email.isBlank()) {
                    final Optional<User> byEmail = userRepository.findByEmail(email);
                    if (byEmail.isPresent()) {
                        final User user = byEmail.get();
                        // Garde-fou anti-takeover : on n'autorise la liaison automatique
                        // email -> clerkUserId QUE si le mirror est en attente (INVITE).
                        // Sinon, un attaquant qui creerait un compte Clerk avec un email
                        // deja ACTIF (ou DESACTIVE) ecraserait l'identifiant Clerk legitime.
                        // Le scenario suppose un compromise email mais Clerk verifie
                        // theoriquement la propriete de l'email : c'est de la defense
                        // en profondeur.
                        if (user.getStatut() != StatutUtilisateurEnum.INVITE) {
                            log.warn("Tentative de liaison Clerk sur user non-INVITE refusee - email: {}, statut: {}, clerkUserId: {}",
                                    PiiMasker.email(email), user.getStatut(), clerkUserId);
                        } else {
                            user.setClerkUserId(clerkUserId);
                            user.setStatut(StatutUtilisateurEnum.ACTIF);
                            user.setEmailSyncedAt(LocalDateTime.now());
                            final User updated = userRepository.save(user);
                            userId = updated.getId();
                            tenantId = updated.getTenantId();
                            log.info("Mirror utilisateur lie au clerkUserId - email: {}, clerkUserId: {}", PiiMasker.email(email), clerkUserId);
                        }
                    } else {
                        log.warn("Aucun mirror utilisateur trouve - clerkUserId: {}, email: {}, clerkOrgId: {}",
                                clerkUserId, PiiMasker.email(email), clerkOrgId);
                    }
                }
            }

            // Garde-fou final : si on n'a pas pu resoudre l'utilisateur (mirror
            // supprime, tenant desactive, user inactif...), refuser l'auth
            // explicitement pour forcer un 401. Sans ca, Spring Security accepte
            // le JWT valide + role, le frontend affiche le shell, mais aucune
            // API ne fonctionne (404 / liste vide partout) = experience cassee.
            if (userId == null || tenantId == null) {
                log.warn("Auth refusee - utilisateur non resolu (mirror supprime ou inactif) - clerkUserId: {}", clerkUserId);
                throw new org.springframework.security.authentication.DisabledException(
                        "Compte desactive ou inexistant. Reconnectez-vous.");
            }
        }

        TenantContext.setCurrentTenantId(tenantId);

        final Collection<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));

        final JwtAuthenticationToken token = new JwtAuthenticationToken(jwt, authorities, clerkUserId);
        token.setDetails(new ClerkAuthDetails(userId, tenantId, role, clerkUserId, clerkOrgId));
        return token;
    }

    private String firstNonBlank(final String... values) {
        for (final String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    public record ClerkAuthDetails(Long userId, Long tenantId, RoleEnum role, String clerkUserId, String clerkOrgId) {}
}
