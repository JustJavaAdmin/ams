package com.justjava.ams.common.service;

import com.justjava.ams.auditor.dto.AuditLogDTO;
import com.justjava.ams.auditor.service.AuditLogService;
import com.justjava.ams.common.dto.OrganizationCreateRequest;
import com.justjava.ams.common.dto.OrganizationDTO;
import com.justjava.ams.common.dto.OrganizationUpdateRequest;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import com.justjava.ams.integration.service.PayrollEntitlementBootstrapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class OrganizationService {
    private static final String ENTITY_TYPE = "Organization";

    private final OrganizationRepository organizationRepository;
    private final AuditLogService auditLogService;
    private final PayrollEntitlementBootstrapService payrollEntitlementBootstrapService;

    public OrganizationDTO createOrganization(OrganizationCreateRequest request) {
        String name = normalizeRequired(request.getName(), "Organization name is required");
        String registrationNumber = normalizeRequired(request.getRegistrationNumber(), "Registration number is required");

        if (organizationRepository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Organization name already exists");
        }

        if (organizationRepository.existsByRegistrationNumberIgnoreCase(registrationNumber)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Registration number already exists");
        }

        Organization organization = Organization.builder()
                .name(name)
                .description(trimToNull(request.getDescription()))
                .registrationNumber(registrationNumber)
                .taxId(trimToNull(request.getTaxId()))
                .address(trimToNull(request.getAddress()))
                .city(trimToNull(request.getCity()))
                .state(trimToNull(request.getState()))
                .country(trimToNull(request.getCountry()))
                .postalCode(trimToNull(request.getPostalCode()))
                .phone(trimToNull(request.getPhone()))
                .email(trimToNull(request.getEmail()))
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        Organization saved = organizationRepository.save(organization);
        payrollEntitlementBootstrapService.ensureEntitlementForSingleOrganization();
        OrganizationDTO dto = mapToDTO(saved);
        log(saved.getId(), saved.getId(), "CREATE", null, dto.toString(), "Created organization " + saved.getName());
        return dto;
    }

    @Transactional(readOnly = true)
    public List<OrganizationDTO> getAllOrganizations() {
        return organizationRepository.findAll()
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrganizationDTO getOrganization(Long id) {
        return mapToDTO(findOrganization(id));
    }

    public OrganizationDTO updateOrganization(Long id, OrganizationUpdateRequest request) {
        Organization organization = findOrganization(id);
        OrganizationDTO oldValue = mapToDTO(organization);

        String name = normalizeRequired(request.getName(), "Organization name is required");
        String registrationNumber = normalizeRequired(request.getRegistrationNumber(), "Registration number is required");

        organizationRepository.findByNameIgnoreCase(name)
                .filter(existing -> !Objects.equals(existing.getId(), id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Organization name already exists");
                });

        organizationRepository.findByRegistrationNumberIgnoreCase(registrationNumber)
                .filter(existing -> !Objects.equals(existing.getId(), id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Registration number already exists");
                });

        organization.setName(name);
        organization.setDescription(trimToNull(request.getDescription()));
        organization.setRegistrationNumber(registrationNumber);
        organization.setTaxId(trimToNull(request.getTaxId()));
        organization.setAddress(trimToNull(request.getAddress()));
        organization.setCity(trimToNull(request.getCity()));
        organization.setState(trimToNull(request.getState()));
        organization.setCountry(trimToNull(request.getCountry()));
        organization.setPostalCode(trimToNull(request.getPostalCode()));
        organization.setPhone(trimToNull(request.getPhone()));
        organization.setEmail(trimToNull(request.getEmail()));
        organization.setActive(request.getActive() != null ? request.getActive() : organization.getActive());

        Organization saved = organizationRepository.save(organization);
        OrganizationDTO dto = mapToDTO(saved);
        log(saved.getId(), saved.getId(), "UPDATE", oldValue.toString(), dto.toString(), "Updated organization " + saved.getName());
        return dto;
    }

    private Organization findOrganization(Long id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
    }

    private OrganizationDTO mapToDTO(Organization organization) {
        return OrganizationDTO.builder()
                .id(organization.getId())
                .name(organization.getName())
                .description(organization.getDescription())
                .registrationNumber(organization.getRegistrationNumber())
                .taxId(organization.getTaxId())
                .address(organization.getAddress())
                .city(organization.getCity())
                .state(organization.getState())
                .country(organization.getCountry())
                .postalCode(organization.getPostalCode())
                .phone(organization.getPhone())
                .email(organization.getEmail())
                .active(organization.getActive())
                .createdAt(organization.getCreatedAt())
                .updatedAt(organization.getUpdatedAt())
                .build();
    }

    private void log(Long organizationId, Long entityId, String action, String oldValue, String newValue, String description) {
        AuditLogDTO auditLog = AuditLogDTO.builder()
                .entityType(ENTITY_TYPE)
                .entityId(entityId)
                .action(action)
                .oldValue(oldValue)
                .newValue(newValue)
                .description(description)
                .build();

        auditLogService.createAuditLog(organizationId, auditLog);
    }

    private String normalizeRequired(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
