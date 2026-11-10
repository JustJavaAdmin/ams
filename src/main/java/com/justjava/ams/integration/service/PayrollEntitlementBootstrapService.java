package com.justjava.ams.integration.service;

import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import com.justjava.ams.integration.entity.ClientOrganizationEntitlement;
import com.justjava.ams.integration.repository.ClientOrganizationEntitlementRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class PayrollEntitlementBootstrapService implements ApplicationRunner {
    private static final String CREATED_BY = "ams-bootstrap";
    private static final String NOTE = "Default payroll integration entitlement";

    private final OrganizationRepository organizationRepository;
    private final ClientOrganizationEntitlementRepository entitlementRepository;
    private final boolean enabled;
    private final String clientId;

    public PayrollEntitlementBootstrapService(
            OrganizationRepository organizationRepository,
            ClientOrganizationEntitlementRepository entitlementRepository,
            @Value("${ams.bootstrap.payroll-entitlement.enabled:true}") boolean enabled,
            @Value("${ams.bootstrap.payroll-entitlement.client-id:jjeip-ams}") String clientId) {
        this.organizationRepository = organizationRepository;
        this.entitlementRepository = entitlementRepository;
        this.enabled = enabled;
        this.clientId = clientId;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureEntitlementForSingleOrganization();
    }

    @Transactional
    public void ensureEntitlementForSingleOrganization() {
        if (!enabled) {
            return;
        }

        List<Organization> organizations = organizationRepository.findAll();
        if (organizations.isEmpty()) {
            return;
        }
        if (organizations.size() != 1) {
            log.warn("Payroll entitlement for clientId={} was not bootstrapped: {} organisations exist; "
                    + "set the entitlement manually", clientId, organizations.size());
            return;
        }

        Organization organization = organizations.get(0);
        Optional<ClientOrganizationEntitlement> existing =
                entitlementRepository.findByClientIdAndOrganization_Id(clientId, organization.getId());
        if (existing.isPresent()) {
            if (!Boolean.TRUE.equals(existing.get().getActive())) {
                log.warn("Payroll entitlement for clientId={} organizationId={} is inactive; "
                        + "it was left unchanged", clientId, organization.getId());
            }
            return;
        }

        entitlementRepository.save(ClientOrganizationEntitlement.builder()
                .clientId(clientId)
                .organization(organization)
                .active(true)
                .createdBy(CREATED_BY)
                .note(NOTE)
                .build());
        log.info("Created payroll entitlement for clientId={} organizationId={}",
                clientId, organization.getId());
    }
}
