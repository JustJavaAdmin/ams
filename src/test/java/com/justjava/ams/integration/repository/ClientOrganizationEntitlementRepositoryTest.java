package com.justjava.ams.integration.repository;

import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.integration.entity.ClientOrganizationEntitlement;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * STAGE 1 tests for the real entitlement query and the table constraint (D2).
 * Uses the local PostgreSQL configured in application.yml. Every test runs in a
 * transaction that is rolled back, so no test rows stay in the database.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ClientOrganizationEntitlementRepositoryTest {

    private static final String CLIENT = "jjeip-ams";

    @Autowired
    private ClientOrganizationEntitlementRepository repository;

    @Autowired
    private EntityManager em;

    private Organization newOrganization(boolean active) {
        Organization org = new Organization();
        org.setName("test-org-" + UUID.randomUUID());
        org.setRegistrationNumber("REG-" + UUID.randomUUID());
        org.setActive(active);
        em.persist(org);
        em.flush();
        return org;
    }

    private ClientOrganizationEntitlement newMapping(String clientId, Organization org, boolean active) {
        return ClientOrganizationEntitlement.builder()
                .clientId(clientId)
                .organization(org)
                .active(active)
                .createdBy("stage1-test")
                .build();
    }

    private boolean allowed(String clientId, Long organizationId) {
        em.flush();
        em.clear();
        return repository.existsByClientIdAndOrganization_IdAndActiveTrueAndOrganization_ActiveTrue(
                clientId, organizationId);
    }

    @Test
    void activeMappingToActiveOrganisation_isAllowed() {
        Organization org = newOrganization(true);
        repository.save(newMapping(CLIENT, org, true));

        assertTrue(allowed(CLIENT, org.getId()));
    }

    @Test
    void mappedOrganisationIsAllowedButAnotherOrganisationIsNot() {
        Organization a = newOrganization(true);
        Organization b = newOrganization(true);
        repository.save(newMapping(CLIENT, a, true));

        assertTrue(allowed(CLIENT, a.getId()));
        assertFalse(allowed(CLIENT, b.getId()));
    }

    @Test
    void differentClient_isNotAllowed() {
        Organization org = newOrganization(true);
        repository.save(newMapping(CLIENT, org, true));

        assertFalse(allowed("some-other-client", org.getId()));
    }

    @Test
    void deactivatedMapping_isNotAllowed() {
        Organization org = newOrganization(true);
        repository.save(newMapping(CLIENT, org, false));

        assertFalse(allowed(CLIENT, org.getId()));
    }

    @Test
    void inactiveOrganisation_isNotAllowed() {
        Organization org = newOrganization(false);
        repository.save(newMapping(CLIENT, org, true));

        assertFalse(allowed(CLIENT, org.getId()));
    }

    @Test
    void nonExistentOrganisation_isNotAllowed() {
        assertFalse(allowed(CLIENT, Long.MAX_VALUE));
    }

    @Test
    void duplicateMappingForSameClientAndOrganisation_isRejectedByTheDatabase() {
        Organization org = newOrganization(true);
        repository.saveAndFlush(newMapping(CLIENT, org, true));

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(newMapping(CLIENT, org, true)));
    }
}