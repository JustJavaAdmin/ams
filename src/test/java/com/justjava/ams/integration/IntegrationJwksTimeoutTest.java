package com.justjava.ams.integration;

import com.justjava.ams.core.config.AuthenticationManager;
import com.justjava.ams.core.config.IntegrationSecurityConfig;
import com.justjava.ams.integration.controller.IntegrationWhoAmIController;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IntegrationWhoAmIController.class)
@Import(IntegrationSecurityConfig.class)
class IntegrationJwksTimeoutTest {
    private static final int METADATA_PORT = IntegrationJwksTestSupport.freePort();
    private static final int JWKS_PORT = IntegrationJwksTestSupport.freePort();
    private static final String ISSUER = "http://127.0.0.1:" + METADATA_PORT + "/realms/ams";
    private static final HttpServer METADATA =
            IntegrationJwksTestSupport.metadataServer(METADATA_PORT, ISSUER, JWKS_PORT);
    private static final HttpServer HANGING_JWKS =
            IntegrationJwksTestSupport.jwksServer(JWKS_PORT, 2000);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @DynamicPropertySource
    static void integrationSecurityProperties(DynamicPropertyRegistry registry) {
        registry.add("integration.security.issuer-uri", () -> ISSUER);
        registry.add("integration.security.audience", () -> "ams-api");
        registry.add("integration.security.connect-timeout", () -> "1000");
        registry.add("integration.security.read-timeout", () -> "250");
    }

    @AfterAll
    static void stopServers() {
        HANGING_JWKS.stop(0);
        METADATA.stop(0);
    }

    @Test
    void jwksReadTimeoutReturns503AndNeverAuthenticates() throws Exception {
        String token = IntegrationJwksTestSupport.token(
                ISSUER, IntegrationJwksTestSupport.SIGNING_KEY, IntegrationJwksTestSupport.KEY_ID);
        mockMvc.perform(get("/api/integrations/whoami").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.code").value("IDENTITY_PROVIDER_UNAVAILABLE"));
    }
}
