package com.justjava.ams.integration.service;

import com.justjava.ams.integration.repository.ClientOrganizationEntitlementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * STAGE 1 closing item: the entitlement check must fail closed.
 * Whatever goes wrong, assertEntitled(...) must never return normally (which would mean "allowed").
 */
class ClientEntitlementServiceFailClosedTest {

    private final ClientOrganizationEntitlementRepository repository =
            mock(ClientOrganizationEntitlementRepository.class);
    private final ClientEntitlementService service = new ClientEntitlementService(repository);

    private Jwt jwtWithAzp(String azp) {
        return Jwt.withTokenValue("test-token").header("alg", "none").claim("azp", azp).build();
    }

    private Jwt jwtWithoutAzp() {
        return Jwt.withTokenValue("test-token").header("alg", "none").claim("sub", "someone").build();
    }

    @Test
    void unexpectedRepositoryFailure_neverCountsAsAccess() {
        when(repository.existsByClientIdAndOrganization_IdAndActiveTrueAndOrganization_ActiveTrue(
                anyString(), anyLong())).thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class,
                () -> service.assertEntitled(jwtWithAzp("jjeip-ams"), 15L));
    }

    @Test
    void missingClientClaim_isForbidden() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.assertEntitled(jwtWithoutAzp(), 15L));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void nullCaller_isForbidden() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.assertEntitled(null, 15L));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void nullOrganization_isForbidden() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.assertEntitled(jwtWithAzp("jjeip-ams"), null));
        assertEquals(403, ex.getStatusCode().value());
    }
}