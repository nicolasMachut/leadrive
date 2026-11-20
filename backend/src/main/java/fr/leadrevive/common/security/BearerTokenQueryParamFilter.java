package fr.leadrevive.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Si la requete cible {@code /api/realtime/**} et ne contient pas de header
 * Authorization, on copie le JWT recu via le query param {@code ?token=...}
 * dans le header Authorization. Permet l'auth des connexions SSE puisque
 * l'API native {@code EventSource} du navigateur ne supporte pas les headers
 * custom.
 *
 * <p>Pas de log du token (sensible). Le query param reste visible dans les
 * logs access du proxy — on accepte ce compromis MVP : le token est court (60s
 * pour les JWT custom Clerk).</p>
 */
@Component
public class BearerTokenQueryParamFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                     final HttpServletResponse response,
                                     final FilterChain filterChain) throws ServletException, IOException {
        final String path = request.getRequestURI();
        if (path != null && path.startsWith("/api/realtime/")
                && request.getHeader("Authorization") == null) {
            final String token = request.getParameter("token");
            if (token != null && !token.isBlank()) {
                filterChain.doFilter(new TokenAsHeaderRequestWrapper(request, token), response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Wrapper qui injecte le header Authorization a partir du token query param.
     * Les autres headers sont preserves a l'identique.
     */
    private static final class TokenAsHeaderRequestWrapper extends HttpServletRequestWrapper {
        private final String authHeader;

        TokenAsHeaderRequestWrapper(final HttpServletRequest request, final String token) {
            super(request);
            this.authHeader = "Bearer " + token;
        }

        @Override
        public String getHeader(final String name) {
            if ("Authorization".equalsIgnoreCase(name)) return authHeader;
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(final String name) {
            if ("Authorization".equalsIgnoreCase(name)) {
                return Collections.enumeration(Collections.singletonList(authHeader));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            final Enumeration<String> original = super.getHeaderNames();
            final java.util.List<String> noms = new java.util.ArrayList<>();
            boolean hasAuth = false;
            while (original.hasMoreElements()) {
                final String n = original.nextElement();
                noms.add(n);
                if ("Authorization".equalsIgnoreCase(n)) hasAuth = true;
            }
            if (!hasAuth) noms.add("Authorization");
            return Collections.enumeration(noms);
        }
    }
}
