package com.justjava.ams.integration.service;

import com.justjava.ams.integration.repository.ClientOrganizationEntitlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * STAGE 1 (D2): answers "may this service client act on this organisation?".
 * A valid token alone is never enough. Every integration endpoint that names an
 * organisation must call assertEntitled(...) first.
 */
@Service
@RequiredArgsConstructor
public class ClientEntitlementService {

    private final ClientOrganizationEntitlementRepository repository;

    /**
     * Returns normally only if the caller's client has an ACTIVE mapping to an ACTIVE organisation.
     * Otherwise throws 403 with the same message for every reason (no mapping, inactive mapping,
     * inactive organisation, organisation does not exist), so nothing is revealed about which it was.
     */
    @Transactional(readOnly = true)
    public void assertEntitled(Jwt caller, Long organizationId) {
        // The client is identified by the token's "azp" claim, never by anything in the request.
        String clientId = (caller == null) ? null : caller.getClaimAsString("azp");

        boolean allowed = clientId != null
                && !clientId.isBlank()
                && organizationId != null
                && repository.existsByClientIdAndOrganization_IdAndActiveTrueAndOrganization_ActiveTrue(
                clientId, organizationId);

        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access to the requested organization is not permitted");
        }
    }
}