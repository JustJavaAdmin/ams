package com.justjava.ams.integration;

import com.justjava.ams.core.config.AuthenticationManager;
import com.justjava.ams.core.config.IntegrationSecurityConfig;
import com.justjava.ams.integration.controller.IntegrationAccessCheckController;
import com.justjava.ams.integration.controller.IntegrationWhoAmIController;
import com.justjava.ams.integration.service.ClientEntitlementService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPairGenerator;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({IntegrationWhoAmIController.class, IntegrationAccessCheckController.class})
@Import(IntegrationSecurityConfig.class)
class IntegrationJwksRecoveryTest {
    private static final int METADATA_PORT = IntegrationJwksTestSupport.freePort();
    private static final int JWKS_PORT = IntegrationJwksTestSupport.freePort();
    private static final String ISSUER = "http://127.0.0.1:" + METADATA_PORT + "/realms/ams";
    private static final HttpServer METADATA =
            IntegrationJwksTestSupport.metadataServer(METADATA_PORT, ISSUER, JWKS_PORT);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private ClientEntitlementService entitlementService;

    @DynamicPropertySource
    static void integrationSecurityProperties(DynamicPropertyRegistry registry) {
        registry.add("integration.security.issuer-uri", () -> ISSUER);
        registry.add("integration.security.audience", () -> "ams-api");
        registry.add("integration.security.connect-timeout", () -> "3000");
        registry.add("integration.security.read-timeout", () -> "5000");
    }

    @AfterAll
    static void stopMetadata() {
        METADATA.stop(0);
    }

    @Test
    void connectionFailureFailsClosedAndNextRequestRecoversWithoutRestart() throws Exception {
        String token = IntegrationJwksTestSupport.token(
                ISSUER, IntegrationJwksTestSupport.SIGNING_KEY, IntegrationJwksTestSupport.KEY_ID);

        mockMvc.perform(get("/api/integrations/whoami").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.code").value("IDENTITY_PROVIDER_UNAVAILABLE"));

        mockMvc.perform(get("/api/integrations/organizations/1/access-check")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable());
        verify(entitlementService, never()).assertEntitled(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        HttpServer keys = IntegrationJwksTestSupport.jwksServer(JWKS_PORT, 0);
        try {
            mockMvc.perform(get("/api/integrations/whoami").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.clientId").value("jjeip-ams"));

            var wrongKey = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            String invalidToken = IntegrationJwksTestSupport.token(ISSUER, wrongKey, IntegrationJwksTestSupport.KEY_ID);
            mockMvc.perform(get("/api/integrations/whoami").header("Authorization", "Bearer " + invalidToken))
                    .andExpect(status().isUnauthorized());
            String wrongAudience = IntegrationJwksTestSupport.token(
                    ISSUER, IntegrationJwksTestSupport.SIGNING_KEY, IntegrationJwksTestSupport.KEY_ID,
                    "not-ams-api", 4102444800L);
            mockMvc.perform(get("/api/integrations/whoami").header("Authorization", "Bearer " + wrongAudience))
                    .andExpect(status().isUnauthorized());
            String expired = IntegrationJwksTestSupport.token(
                    ISSUER, IntegrationJwksTestSupport.SIGNING_KEY, IntegrationJwksTestSupport.KEY_ID,
                    "ams-api", 1L);
            mockMvc.perform(get("/api/integrations/whoami").header("Authorization", "Bearer " + expired))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/integrations/whoami").header("Authorization", "Bearer not-a-jwt"))
                    .andExpect(status().isUnauthorized());
        } finally {
            keys.stop(0);
        }
    }
}
