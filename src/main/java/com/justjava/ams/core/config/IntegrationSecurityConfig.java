package com.justjava.ams.core.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;

/**
 * - STAGE 1 (D1): bearer-token security for service-to-service calls.
 * - Applies ONLY to /api/integrations/**, EXCEPT /api/integrations/webhooks/**.
 * - The existing webhook endpoints predate Stage 1 and keep their session-based behaviour,
 * - so they stay with Oauth2SecurityConfig. All other paths also stay with Oauth2SecurityConfig.
 */
@Slf4j
@Configuration
public class IntegrationSecurityConfig {

    private static final String INTEGRATION_PATH = "/api/integrations/**";

    // Existing (pre-Stage 1) endpoints that must keep the login-session behaviour.
    private static final String WEBHOOK_PATH = "/api/integrations/webhooks/**";

    // Defaults to the AMS realm issuer already configured for login. No secrets here.
    @Value("${integration.security.issuer-uri:${spring.security.oauth2.client.provider.keycloak.issuer-uri}}")
    private String issuerUri;

    @Value("${integration.security.audience:ams-api}")
    private String requiredAudience;

    @Bean
    @Order(1)
    public SecurityFilterChain integrationSecurityFilterChain(HttpSecurity http,
                                                              JwtDecoder integrationJwtDecoder) throws Exception {
        RequestMatcher integrationOnly = new AndRequestMatcher(
                PathPatternRequestMatcher.withDefaults().matcher(INTEGRATION_PATH),
                new NegatedRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher(WEBHOOK_PATH)));

        http.securityMatcher(integrationOnly)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(CsrfConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        // Stage 1 scaffolding: the access check needs the post scope.
                        // Stage 2 endpoints will add their own scope rules here.
                        .requestMatchers("/api/integrations/organizations/**")
                        .hasAuthority("SCOPE_payroll-journal:post")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(integrationJwtDecoder)));
        return http.build();
    }

    @Bean
    public JwtDecoder integrationJwtDecoder() {
        return new LazyJwtDecoder();
    }

    /**
     * - Contacts the issuer only when the first token arrives, so the application can start
     * - even if Keycloak is briefly unreachable. If the issuer cannot be reached, the token is
     * - rejected (never accepted) and the next request tries again.
     */
    private final class LazyJwtDecoder implements JwtDecoder {
        private volatile JwtDecoder delegate;

        @Override
        public Jwt decode(String token) throws JwtException {
            JwtDecoder current = delegate;
            if (current == null) {
                synchronized (this) {
                    current = delegate;
                    if (current == null) {
                        try {
                            current = buildDecoder();
                        } catch (RuntimeException e) {
                            log.warn("Integration issuer configuration could not be loaded; token rejected");
                            throw new JwtException("Unable to load issuer configuration", e);
                        }
                        delegate = current;
                    }
                }
            }
            return current.decode(token);
        }
    }

    private JwtDecoder buildDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        decoder.setJwtValidator(tokenValidator(issuerUri, requiredAudience));
        return decoder;
    }

    /** Issuer + expiry + required audience. Package-private and static so tests can call it directly. */
    static OAuth2TokenValidator<Jwt> tokenValidator(String issuerUri, String requiredAudience) {
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri); // issuer + expiry
        OAuth2TokenValidator<Jwt> withAudience = jwt -> {
            List<String> audience = jwt.getAudience();
            if (audience != null && audience.contains(requiredAudience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Required audience is missing", null));
        };
        return new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience);
    }
}