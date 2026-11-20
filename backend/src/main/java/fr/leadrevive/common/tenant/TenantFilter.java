package fr.leadrevive.common.tenant;

import fr.leadrevive.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Active le filtre Hibernate `tenantFilter` au debut de chaque requete HTTP
 * et nettoie le ThreadLocal {@link TenantContext} a la fin (afterCompletion)
 * pour eviter les fuites de contexte entre requetes dans le pool de threads.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantFilter implements HandlerInterceptor {

    private final EntityManager entityManager;
    private final TenantRepository tenantRepository;

    @Override
    public boolean preHandle(@NonNull final HttpServletRequest request,
                             @NonNull final HttpServletResponse response,
                             @NonNull final Object handler) {
        final Long tenantId = TenantContext.getCurrentTenantId();

        if (tenantId != null) {
            // Garde-fou : un tenant soft-deleted (actif=false) ne doit plus permettre
            // l'acces a ses donnees. Sans ce check, les users dont la concession a ete
            // supprimee gardaient acces tant que leur JWT etait valide.
            final boolean tenantActif = tenantRepository.findById(tenantId)
                    .map(t -> Boolean.TRUE.equals(t.getActif()))
                    .orElse(false);
            if (!tenantActif) {
                log.warn("Acces refuse - tenant inactif ou inexistant - tenantId: {}", tenantId);
                response.setStatus(HttpStatus.FORBIDDEN.value());
                return false;
            }
            final Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
            log.debug("Filtre tenant active - tenantId: {}", tenantId);
        }

        return true;
    }

    /**
     * Nettoie systematiquement le ThreadLocal apres chaque requete pour
     * eviter qu'un thread reutilise (pool Tomcat) garde l'ancien tenantId.
     * Critique pour la securite multi-tenant.
     */
    @Override
    public void afterCompletion(@NonNull final HttpServletRequest request,
                                @NonNull final HttpServletResponse response,
                                @NonNull final Object handler,
                                @Nullable final Exception ex) {
        TenantContext.clear();
    }
}
