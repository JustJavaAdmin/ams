package com.justjava.ams.integration.service;

import com.justjava.ams.auditor.service.AuditLogService;
import com.justjava.ams.common.dto.OrganizationCreateRequest;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import com.justjava.ams.common.service.OrganizationService;
import com.justjava.ams.integration.entity.ClientOrganizationEntitlement;
import com.justjava.ams.integration.repository.ClientOrganizationEntitlementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayrollEntitlementBootstrapServiceTest {
    private static final String CLIENT_ID = "jjeip-ams";

    @Test
    void emptyDatabaseStartupCreatesNoOrganizationOrEntitlement() {
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        ClientOrganizationEntitlementRepository entitlements =
                mock(ClientOrganizationEntitlementRepository.class);
        when(organizations.findAll()).thenReturn(List.of());

        service(organizations, entitlements).run(new DefaultApplicationArguments());

        verify(organizations, never()).save(any());
        verify(entitlements, never()).save(any());
    }

    @Test
    void organizationCreatedThroughExistingPathGetsActiveEntitlement() {
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        ClientOrganizationEntitlementRepository entitlements =
                mock(ClientOrganizationEntitlementRepository.class);
        Organization organization = organization(12L, "Development");
        when(organizations.existsByNameIgnoreCase("Development")).thenReturn(false);
        when(organizations.existsByRegistrationNumberIgnoreCase("DEV-12")).thenReturn(false);
        when(organizations.save(any(Organization.class))).thenReturn(organization);
        when(organizations.findAll()).thenReturn(List.of(organization));
        when(entitlements.findByClientIdAndOrganization_Id(CLIENT_ID, 12L)).thenReturn(Optional.empty());

        PayrollEntitlementBootstrapService bootstrap = service(organizations, entitlements);
        OrganizationService organizationService = new OrganizationService(
                organizations, mock(AuditLogService.class), bootstrap);
        OrganizationCreateRequest request = new OrganizationCreateRequest();
        request.setName("Development");
        request.setRegistrationNumber("DEV-12");

        organizationService.createOrganization(request);

        var saved = forClass(ClientOrganizationEntitlement.class);
        verify(entitlements).save(saved.capture());
        assertThat(saved.getValue().getClientId()).isEqualTo(CLIENT_ID);
        assertThat(saved.getValue().getOrganization().getId()).isEqualTo(12L);
        assertThat(saved.getValue().getActive()).isTrue();
    }

    @Test
    void secondStartupDoesNotCreateAnotherEntitlement() {
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        ClientOrganizationEntitlementRepository entitlements =
                mock(ClientOrganizationEntitlementRepository.class);
        Organization organization = organization(12L, "Development");
        ClientOrganizationEntitlement existing = entitlement(organization, true);
        AtomicReference<Optional<ClientOrganizationEntitlement>> current =
                new AtomicReference<>(Optional.empty());
        when(organizations.findAll()).thenReturn(List.of(organization));
        when(entitlements.findByClientIdAndOrganization_Id(CLIENT_ID, 12L))
                .thenAnswer(invocation -> current.get());
        when(entitlements.save(any(ClientOrganizationEntitlement.class))).thenAnswer(invocation -> {
            ClientOrganizationEntitlement created = invocation.getArgument(0);
            current.set(Optional.of(existing));
            return created;
        });

        PayrollEntitlementBootstrapService bootstrap = service(organizations, entitlements);
        bootstrap.run(new DefaultApplicationArguments());
        bootstrap.run(new DefaultApplicationArguments());

        verify(entitlements).save(any(ClientOrganizationEntitlement.class));
    }

    @Test
    void multipleOrganizationsReceiveNoBootstrapEntitlement() {
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        ClientOrganizationEntitlementRepository entitlements =
                mock(ClientOrganizationEntitlementRepository.class);
        when(organizations.findAll()).thenReturn(List.of(
                organization(12L, "Development A"), organization(13L, "Development B")));

        service(organizations, entitlements).run(new DefaultApplicationArguments());

        verify(entitlements, never()).save(any());
        verify(entitlements, never()).findByClientIdAndOrganization_Id(any(), any());
    }

    @Test
    void inactiveEntitlementRemainsInactiveAndIsNotSaved() {
        OrganizationRepository organizations = mock(OrganizationRepository.class);
        ClientOrganizationEntitlementRepository entitlements =
                mock(ClientOrganizationEntitlementRepository.class);
        Organization organization = organization(12L, "Development");
        ClientOrganizationEntitlement existing = entitlement(organization, false);
        when(organizations.findAll()).thenReturn(List.of(organization));
        when(entitlements.findByClientIdAndOrganization_Id(CLIENT_ID, 12L))
                .thenReturn(Optional.of(existing));

        service(organizations, entitlements).run(new DefaultApplicationArguments());

        assertThat(existing.getActive()).isFalse();
        verify(entitlements, never()).save(any());
    }

    private PayrollEntitlementBootstrapService service(
            OrganizationRepository organizations,
            ClientOrganizationEntitlementRepository entitlements) {
        return new PayrollEntitlementBootstrapService(organizations, entitlements, true, CLIENT_ID);
    }

    private Organization organization(Long id, String name) {
        return Organization.builder().id(id).name(name).active(true).build();
    }

    private ClientOrganizationEntitlement entitlement(Organization organization, boolean active) {
        return ClientOrganizationEntitlement.builder()
                .clientId(CLIENT_ID)
                .organization(organization)
                .active(active)
                .createdBy("existing")
                .build();
    }
}
