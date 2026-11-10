package com.justjava.ams.integration.repository;


import com.justjava.ams.integration.entity.ClientOrganizationEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientOrganizationEntitlementRepository
        extends JpaRepository<ClientOrganizationEntitlement, Long> {

    Optional<ClientOrganizationEntitlement> findByClientIdAndOrganization_Id(
            String clientId, Long organizationId);

    /**
     * True only when an ACTIVE mapping exists for an ACTIVE organisation.
     * A missing organisation, a missing mapping, an inactive mapping and an inactive
     * organisation all give the same answer (false), so callers cannot tell them apart.
     */
    boolean existsByClientIdAndOrganization_IdAndActiveTrueAndOrganization_ActiveTrue(
            String clientId, Long organizationId);
}
