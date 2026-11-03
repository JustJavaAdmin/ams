package com.justjava.ams.integration.service;

import com.justjava.ams.integration.repository.ClientOrganizationEntitlementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * STAGE 1 tests for the decision logic of ClientEntitlementService.
 * The repository answer is faked; the real database query is checked separately.
 */
class ClientEntitlementServiceTest {

    private final ClientOrganizationEntitlementRepository repository =
            mock(ClientOrganizationEntitlementRepository.class);
    private final ClientEntitlementService service = new ClientEntitlementService(repository);

    private Jwt tokenFor(String azp) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token").header("alg", "none").claim("sub", "service-account");
        if (azp != null) {
            builder.claim("azp", azp);
        }
        return builder.build();
    }

    private ResponseStatusException assertForbidden(Jwt jwt, Long organizationId) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.assertEntitled(jwt, organizationId));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        return ex;
    }

    @Test
    void activeMapping_isAllowed() {
        when(repository.existsByClientIdAndOrganization_IdAndActiveTrueAndOrganization_ActiveTrue("jjeip-ams", 1L))
                .thenReturn(true);

        assertDoesNotThrow(() -> service.assertEntitled(tokenFor("jjeip-ams"), 1L));
    }

    @Test
    void noActiveMapping_isForbidden() {
        when(repository.existsByClientIdAndOrganization_IdAndActiveTrueAndOrganization_ActiveTrue("jjeip-ams", 2L))
                .thenReturn(false);

        assertForbidden(tokenFor("jjeip-ams"), 2L);
    }

    @Test
    void mappingForOtherOrganisationOnly_isForbiddenForTheNotMappedOne() {
        when(repository.existsByClientIdAndOrganization_IdAndActiveTrueAndOrganization_ActiveTrue("jjeip-ams", 1L))
                .thenReturn(true);
        when(repository.existsByClientIdAndOrganization_IdAndActiveTrueAndOrganization_ActiveTrue("jjeip-ams", 2L))
                .thenReturn(false);

        assertDoesNotThrow(() -> service.assertEntitled(tokenFor("jjeip-ams"), 1L));
        assertForbidden(tokenFor("jjeip-ams"), 2L);
    }

    @Test
    void tokenWithoutClientId_isForbidden() {
        assertForbidden(tokenFor(null), 1L);
    }

    @Test
    void blankClientId_isForbidden() {
        assertForbidden(tokenFor("  "), 1L);
    }

    @Test
    void nullOrganisationId_isForbidden() {
        assertForbidden(tokenFor("jjeip-ams"), null);
    }

    @Test
    void nullCaller_isForbidden() {
        assertForbidden(null, 1L);
    }

    @Test
    void allRefusalReasonsGiveTheSameMessage() {
        String noClaim = assertForbidden(tokenFor(null), 1L).getReason();
        String noMapping = assertForbidden(tokenFor("jjeip-ams"), 999L).getReason();

        assertEquals(noClaim, noMapping);
    }
}