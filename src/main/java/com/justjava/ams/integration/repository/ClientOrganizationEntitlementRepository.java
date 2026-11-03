package com.justjava.ams.integration.repository;


import com.justjava.ams.integration.entity.ClientOrganizationEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientOrganizationEntitlementRepository
        extends JpaRepository<ClientOrganizationEntitlement, Long> {

    /**
     * True only when an ACTIVE mapping exists for an ACTIVE organisation.
     * A missing organisation, a missing mapping, an inactive mapping and an inactive
     * organisation all give the same answer (false), so callers cannot tell them apart.
     */
    boolean existsByClientIdAndOrganization_IdAndActiveTrueAndOrganization_ActiveTrue(
            String clientId, Long organizationId);
}
