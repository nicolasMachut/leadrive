package fr.leadrevive.common.security;

import fr.leadrevive.common.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AuthService {

    public Long getCurrentUserId() {
        final ClerkJwtConverter.ClerkAuthDetails details = getDetails();
        return details != null ? details.userId() : null;
    }

    public Long getCurrentTenantId() {
        final Long fromContext = TenantContext.getCurrentTenantId();
        if (fromContext != null) {
            return fromContext;
        }
        final ClerkJwtConverter.ClerkAuthDetails details = getDetails();
        return details != null ? details.tenantId() : null;
    }

    public String getCurrentRole() {
        final ClerkJwtConverter.ClerkAuthDetails details = getDetails();
        return details != null && details.role() != null ? details.role().name() : null;
    }

    public String getCurrentClerkUserId() {
        final ClerkJwtConverter.ClerkAuthDetails details = getDetails();
        return details != null ? details.clerkUserId() : null;
    }

    private ClerkJwtConverter.ClerkAuthDetails getDetails() {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            final Object details = jwtAuth.getDetails();
            if (details instanceof ClerkJwtConverter.ClerkAuthDetails clerkDetails) {
                return clerkDetails;
            }
        }
        return null;
    }
}
