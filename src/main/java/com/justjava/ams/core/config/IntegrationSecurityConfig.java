package com.justjava.ams.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

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

    @Value("${integration.security.connect-timeout:3000}")
    private int connectTimeoutMillis;

    @Value("${integration.security.read-timeout:5000}")
    private int readTimeoutMillis;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
                        .requestMatchers(HttpMethod.GET,
                                "/api/integrations/organizations/*/payroll-journals/by-key")
                        .hasAuthority("SCOPE_payroll-journal:read")
                        .requestMatchers(HttpMethod.POST,
                                "/api/integrations/organizations/*/payroll-journals")
                        .hasAuthority("SCOPE_payroll-journal:post")
                        .requestMatchers(HttpMethod.GET, "/api/integrations/organizations/**")
                        .hasAuthority("SCOPE_payroll-journal:post")
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(integrationAuthenticationEntryPoint())
                        .accessDeniedHandler(integrationAccessDeniedHandler()))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(integrationAuthenticationEntryPoint())
                        .jwt(jwt -> jwt.decoder(integrationJwtDecoder)));
        http.addFilterBefore(identityProviderFailureFilter(), BearerTokenAuthenticationFilter.class);
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
            try {
                return current.decode(token);
            } catch (JwtException ex) {
                if (isIdentityProviderUnavailable(ex)) {
                    log.warn("Integration identity provider is unavailable; token rejected");
                }
                throw ex;
            }
        }
    }

    private JwtDecoder buildDecoder() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMillis));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMillis));
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        Map<?, ?> metadata = restTemplate.getForObject(
                issuerUri + "/.well-known/openid-configuration", Map.class);
        if (metadata == null || !issuerUri.equals(metadata.get("issuer"))
                || !(metadata.get("jwks_uri") instanceof String jwksUri) || jwksUri.isBlank()) {
            throw new JwtException("Issuer metadata is invalid");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .restOperations(restTemplate)
                .build();
        decoder.setJwtValidator(tokenValidator(issuerUri, requiredAudience));
        return decoder;
    }

    private AuthenticationEntryPoint integrationAuthenticationEntryPoint() {
        return (request, response, exception) -> {
            if (isIdentityProviderUnavailable(exception)) {
                writeSecurityError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "IDENTITY_PROVIDER_UNAVAILABLE", "Identity provider is temporarily unavailable", true);
                response.setHeader("Retry-After", "5");
            } else {
                writeSecurityError(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "UNAUTHORIZED", "Authentication is required", false);
            }
        };
    }

    private AccessDeniedHandler integrationAccessDeniedHandler() {
        return (request, response, exception) -> writeSecurityError(response,
                HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "Access denied", false);
    }

    private OncePerRequestFilter identityProviderFailureFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                    throws ServletException, IOException {
                try {
                    filterChain.doFilter(request, response);
                } catch (AuthenticationException exception) {
                    if (!isIdentityProviderUnavailable(exception)) {
                        throw exception;
                    }
                    writeIdentityProviderUnavailable(response);
                }
            }
        };
    }

    private void writeIdentityProviderUnavailable(HttpServletResponse response) throws IOException {
        writeSecurityError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                "IDENTITY_PROVIDER_UNAVAILABLE", "Identity provider is temporarily unavailable", true);
        response.setHeader("Retry-After", "5");
    }

    private void writeSecurityError(
            HttpServletResponse response, int status, String code, String message, boolean retryable) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of(
                "code", code,
                "message", message,
                "retryable", retryable,
                "details", List.of()));
    }

    private boolean isIdentityProviderUnavailable(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ResourceAccessException
                    || cause instanceof ConnectException
                    || cause instanceof SocketTimeoutException
                    || cause instanceof UnknownHostException
                    || cause instanceof NoRouteToHostException) {
                return true;
            }
        }
        return false;
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