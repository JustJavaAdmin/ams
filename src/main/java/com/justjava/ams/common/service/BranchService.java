package com.justjava.ams.common.service;

import com.justjava.ams.auditor.dto.AuditLogDTO;
import com.justjava.ams.auditor.service.AuditLogService;
import com.justjava.ams.common.dto.BranchCreateRequest;
import com.justjava.ams.common.dto.BranchDTO;
import com.justjava.ams.common.dto.BranchUpdateRequest;
import com.justjava.ams.common.entity.Branch;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.BranchRepository;
import com.justjava.ams.common.repository.OrganizationRepository;
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
public class BranchService {
    private static final String ENTITY_TYPE = "Branch";

    private final BranchRepository branchRepository;
    private final OrganizationRepository organizationRepository;
    private final AuditLogService auditLogService;

    public BranchDTO createBranch(BranchCreateRequest request) {
        Organization organization = findOrganization(request.getOrganizationId());
        String name = normalizeRequired(request.getName(), "Branch name is required");
        String code = normalizeRequired(request.getCode(), "Branch code is required");

        if (branchRepository.existsByCodeIgnoreCase(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Branch code already exists");
        }

        Branch branch = Branch.builder()
                .organization(organization)
                .name(name)
                .code(code)
                .address(trimToNull(request.getAddress()))
                .city(trimToNull(request.getCity()))
                .state(trimToNull(request.getState()))
                .country(trimToNull(request.getCountry()))
                .postalCode(trimToNull(request.getPostalCode()))
                .phone(trimToNull(request.getPhone()))
                .email(trimToNull(request.getEmail()))
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        Branch saved = branchRepository.save(branch);
        BranchDTO dto = mapToDTO(saved);
        log(organization.getId(), saved.getId(), "CREATE", null, dto.toString(), "Created branch " + saved.getCode());
        return dto;
    }

    @Transactional(readOnly = true)
    public List<BranchDTO> getBranchesByOrganization(Long organizationId) {
        findOrganization(organizationId);

        return branchRepository.findByOrganizationId(organizationId)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public BranchDTO getBranch(Long id) {
        return mapToDTO(findBranch(id));
    }

    public BranchDTO updateBranch(Long id, BranchUpdateRequest request) {
        Branch branch = findBranch(id);
        BranchDTO oldValue = mapToDTO(branch);

        String name = normalizeRequired(request.getName(), "Branch name is required");
        String code = normalizeRequired(request.getCode(), "Branch code is required");

        branchRepository.findByCodeIgnoreCase(code)
                .filter(existing -> !Objects.equals(existing.getId(), id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Branch code already exists");
                });

        branch.setName(name);
        branch.setCode(code);
        branch.setAddress(trimToNull(request.getAddress()));
        branch.setCity(trimToNull(request.getCity()));
        branch.setState(trimToNull(request.getState()));
        branch.setCountry(trimToNull(request.getCountry()));
        branch.setPostalCode(trimToNull(request.getPostalCode()));
        branch.setPhone(trimToNull(request.getPhone()));
        branch.setEmail(trimToNull(request.getEmail()));
        branch.setActive(request.getActive() != null ? request.getActive() : branch.getActive());

        Branch saved = branchRepository.save(branch);
        BranchDTO dto = mapToDTO(saved);
        log(saved.getOrganization().getId(), saved.getId(), "UPDATE", oldValue.toString(), dto.toString(), "Updated branch " + saved.getCode());
        return dto;
    }

    private Organization findOrganization(Long id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
    }

    private Branch findBranch(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found"));
    }

    private BranchDTO mapToDTO(Branch branch) {
        return BranchDTO.builder()
                .id(branch.getId())
                .organizationId(branch.getOrganization().getId())
                .name(branch.getName())
                .code(branch.getCode())
                .address(branch.getAddress())
                .city(branch.getCity())
                .state(branch.getState())
                .country(branch.getCountry())
                .postalCode(branch.getPostalCode())
                .phone(branch.getPhone())
                .email(branch.getEmail())
                .active(branch.getActive())
                .createdAt(branch.getCreatedAt())
                .updatedAt(branch.getUpdatedAt())
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
