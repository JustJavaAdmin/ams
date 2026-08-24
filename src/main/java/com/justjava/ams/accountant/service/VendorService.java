package com.justjava.ams.accountant.service;

import com.justjava.ams.accountant.dto.VendorDTO;
import com.justjava.ams.accountant.entity.Vendor;
import com.justjava.ams.accountant.repository.VendorRepository;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class VendorService {
    private final VendorRepository vendorRepository;
    private final OrganizationRepository organizationRepository;

    public VendorDTO createVendor(Long organizationId, VendorDTO dto) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        String code = required(dto.getVendorCode(), "Vendor code is required");
        if (vendorRepository.existsByOrganizationIdAndVendorCodeIgnoreCase(organizationId, code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vendor code already exists");
        }
        Vendor vendor = Vendor.builder()
                .organization(organization)
                .vendorCode(code)
                .legalName(required(dto.getLegalName(), "Vendor legal name is required"))
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .billingAddress(dto.getBillingAddress())
                .taxId(dto.getTaxId())
                .paymentTerms(dto.getPaymentTerms())
                .bankDetails(dto.getBankDetails())
                .active(dto.getActive() != null ? dto.getActive() : true)
                .build();
        return mapToDTO(vendorRepository.save(vendor));
    }

    @Transactional(readOnly = true)
    public List<VendorDTO> getActiveVendors(Long organizationId) {
        return vendorRepository.findByOrganizationIdAndActiveTrueOrderByLegalNameAsc(organizationId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    private VendorDTO mapToDTO(Vendor vendor) {
        return VendorDTO.builder()
                .id(vendor.getId())
                .organizationId(vendor.getOrganization().getId())
                .vendorCode(vendor.getVendorCode())
                .legalName(vendor.getLegalName())
                .email(vendor.getEmail())
                .phone(vendor.getPhone())
                .billingAddress(vendor.getBillingAddress())
                .taxId(vendor.getTaxId())
                .paymentTerms(vendor.getPaymentTerms())
                .bankDetails(vendor.getBankDetails())
                .active(vendor.getActive())
                .createdAt(vendor.getCreatedAt())
                .updatedAt(vendor.getUpdatedAt())
                .build();
    }

    private String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }
}
