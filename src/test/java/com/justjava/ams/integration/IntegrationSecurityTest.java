package com.justjava.ams.integration;

import com.justjava.ams.core.config.AuthenticationManager;
import com.justjava.ams.core.config.IntegrationSecurityConfig;
import com.justjava.ams.integration.controller.IntegrationAccessCheckController;
import com.justjava.ams.integration.controller.IntegrationWhoAmIController;
import com.justjava.ams.integration.service.ClientEntitlementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * STAGE 1 tests for the integration security chain (D1) and the access-check endpoint (D2).
 * No Keycloak or database needed: the decoder and the entitlement service are replaced by fakes.
 */
@WebMvcTest({IntegrationWhoAmIController.class, IntegrationAccessCheckController.class})
@Import(IntegrationSecurityConfig.class)
@TestPropertySource(properties = {
        "integration.security.issuer-uri=https://test-issuer.invalid/realms/ams",
        "integration.security.audience=ams-api"
})
class IntegrationSecurityTest {

    private static final String ACCESS_CHECK = "/api/integrations/organizations/7/access-check";

    @Autowired
    private MockMvc mockMvc;

    // GlobalControllerAdvice needs this bean; a fake is enough for these tests.
    @MockitoBean
    private AuthenticationManager authenticationManager;

    // Replaces the real decoder so no network call is made.
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientEntitlementService entitlementService;

    @Test
    void noToken_isRejectedWith401() throws Exception {
        mockMvc.perform(get("/api/integrations/whoami"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidToken_isRejectedWith401() throws Exception {
        when(jwtDecoder.decode(any())).thenThrow(new BadJwtException("bad token"));

        mockMvc.perform(get("/api/integrations/whoami")
                        .header("Authorization", "Bearer this-is-not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validToken_returnsClientIdAndScopes() throws Exception {
        mockMvc.perform(get("/api/integrations/whoami")
                        .with(jwt().jwt(j -> j
                                .claim("azp", "jjeip-ams")
                                .claim("scope", "payroll-journal:post payroll-journal:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("jjeip-ams"))
                .andExpect(jsonPath("$.scopes[0]").value("payroll-journal:post"));
    }

    @Test
    void accessCheck_withoutRequiredScope_isForbidden() throws Exception {
        mockMvc.perform(get(ACCESS_CHECK)
                        .with(jwt().jwt(j -> j
                                .claim("azp", "jjeip-ams")
                                .claim("scope", "payroll-journal:read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void accessCheck_withScopeButNotEntitled_isForbidden() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access to the requested organization is not permitted"))
                .when(entitlementService).assertEntitled(any(), eq(7L));

        mockMvc.perform(get(ACCESS_CHECK)
                        .with(jwt().jwt(j -> j
                                .claim("azp", "jjeip-ams")
                                .claim("scope", "payroll-journal:post"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void accessCheck_withScopeAndEntitled_returnsNoContent() throws Exception {
        doNothing().when(entitlementService).assertEntitled(any(), eq(7L));

        mockMvc.perform(get(ACCESS_CHECK)
                        .with(jwt().jwt(j -> j
                                .claim("azp", "jjeip-ams")
                                .claim("scope", "payroll-journal:post"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void loggedInBrowserSession_cannotCallIntegrationPaths() throws Exception {
        // Simulates a user who is logged in through the browser: the login is stored in the HTTP session.
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "ui-user", null, AuthorityUtils.createAuthorityList("ROLE_USER")));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        mockMvc.perform(get("/api/integrations/whoami").session(session))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(ACCESS_CHECK).session(session))
                .andExpect(status().isUnauthorized());
    }
}