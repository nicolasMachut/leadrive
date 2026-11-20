package fr.leadrevive.common.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import jakarta.servlet.DispatcherType;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final String frontendUrl;
    private final String clerkJwksUri;
    private final String clerkIssuer;

    public SecurityConfig(@Value("${leadrevive.frontend-url:http://localhost:3000}") final String frontendUrl,
                          @Value("${clerk.jwks-uri:https://placeholder.clerk.accounts.dev/.well-known/jwks.json}") final String clerkJwksUri,
                          @Value("${clerk.issuer:}") final String clerkIssuer) {
        this.frontendUrl = frontendUrl;
        this.clerkJwksUri = clerkJwksUri;
        this.clerkIssuer = clerkIssuer;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http,
                                                    final ClerkJwtConverter clerkJwtConverter,
                                                    final BearerTokenQueryParamFilter bearerTokenQueryParamFilter) throws Exception {
        http
                .addFilterBefore(bearerTokenQueryParamFilter, UsernamePasswordAuthenticationFilter.class)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // CSRF desactive : API REST stateless avec Bearer token, pas de cookie d'auth
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Headers de securite OWASP : HSTS, X-Frame-Options, X-Content-Type-Options, CSP minimal
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(opts -> {})
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000L))
                        .referrerPolicy(rp -> rp.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // CSP minimal : autorise uniquement self pour le default. API JSON, pas de script.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; frame-ancestors 'none'; base-uri 'self'"))
                )
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/api/webhook/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        // Restreint Actuator au seul health check (OWASP)
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("SUPER_ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(clerkJwtConverter)));

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        // Fix securite bug-hunter : JwtDecoder Spring par defaut ne valide que la signature
        // et l'expiration. Sans validateur d'issuer, un JWT signe par un autre tenant Clerk
        // (ou par n'importe quel JWK accessible) passerait. On ajoute la validation de
        // l'issuer explicite (configure via CLERK_ISSUER, deja verifie au boot par
        // StartupConfigValidator).
        final NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(clerkJwksUri).build();
        final OAuth2TokenValidator<Jwt> defaultValidators =
                clerkIssuer != null && !clerkIssuer.isBlank()
                        ? JwtValidators.createDefaultWithIssuer(clerkIssuer)
                        : JwtValidators.createDefault();
        decoder.setJwtValidator(defaultValidators);
        return decoder;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        final CorsConfiguration configuration = new CorsConfiguration();
        // Fix securite bug-hunter : whitelist stricte. On rejette explicitement le wildcard
        // "*" (incompatible avec allowCredentials=true) et les valeurs vides. Si FRONTEND_URL
        // contient "*" ou une entree vide par erreur, on tombe sur un fail-safe (aucune
        // origine autorisee = 403 plutot que ouverture totale).
        final List<String> origines = Arrays.stream(frontendUrl.split(","))
                .map(String::trim)
                .filter(o -> !o.isBlank())
                .filter(o -> !"*".equals(o))
                .toList();
        configuration.setAllowedOrigins(origines);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "X-Source", "svix-id", "svix-signature", "svix-timestamp"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
