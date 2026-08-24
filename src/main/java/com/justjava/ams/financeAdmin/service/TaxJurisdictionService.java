package com.justjava.ams.financeAdmin.service;

import com.justjava.ams.financeAdmin.dto.TaxJurisdictionDTO;
import com.justjava.ams.financeAdmin.entity.TaxJurisdiction;
import com.justjava.ams.financeAdmin.repository.TaxJurisdictionRepository;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class TaxJurisdictionService {

    private final TaxJurisdictionRepository taxJurisdictionRepository;
    private final OrganizationRepository organizationRepository;

    public TaxJurisdictionDTO createTaxJurisdiction(Long organizationId, TaxJurisdictionDTO dto) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        validate(dto);
        taxJurisdictionRepository.findByOrganizationIdAndJurisdictionCode(organizationId, required(dto.getJurisdictionCode(), "Jurisdiction code is required"))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Tax jurisdiction code already exists for organization");
                });

        TaxJurisdiction jurisdiction = TaxJurisdiction.builder()
                .organization(organization)
                .jurisdictionName(required(dto.getJurisdictionName(), "Jurisdiction name is required"))
                .jurisdictionCode(required(dto.getJurisdictionCode(), "Jurisdiction code is required"))
                .country(dto.getCountry())
                .state(dto.getState())
                .municipality(dto.getMunicipality())
                .taxRate(dto.getTaxRate())
                .taxType(parseTaxType(dto.getTaxType()))
                .calculationType(parseCalculationType(dto.getCalculationType()))
                .description(dto.getDescription())
                .active(true)
                .build();

        return mapToDTO(taxJurisdictionRepository.save(jurisdiction));
    }

    public TaxJurisdictionDTO getTaxJurisdiction(Long jurisdictionId) {
        TaxJurisdiction jurisdiction = taxJurisdictionRepository.findById(jurisdictionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tax jurisdiction not found"));
        return mapToDTO(jurisdiction);
    }

    public List<TaxJurisdictionDTO> getJurisdictionsByOrganization(Long organizationId) {
        return taxJurisdictionRepository.findByOrganizationIdAndActiveTrue(organizationId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<TaxJurisdictionDTO> getJurisdictionsByType(Long organizationId, String taxType) {
        return taxJurisdictionRepository.findByOrganizationIdAndTaxType(organizationId, parseTaxType(taxType))
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public TaxJurisdictionDTO updateTaxJurisdiction(Long jurisdictionId, TaxJurisdictionDTO dto) {
        TaxJurisdiction jurisdiction = taxJurisdictionRepository.findById(jurisdictionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tax jurisdiction not found"));

        if (dto.getJurisdictionName() != null) jurisdiction.setJurisdictionName(required(dto.getJurisdictionName(), "Jurisdiction name is required"));
        if (dto.getTaxRate() != null) {
            if (dto.getTaxRate().compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tax rate cannot be negative");
            }
            jurisdiction.setTaxRate(dto.getTaxRate());
        }
        if (dto.getActive() != null) jurisdiction.setActive(dto.getActive());
        if (dto.getDescription() != null) jurisdiction.setDescription(dto.getDescription());
        if (dto.getCalculationType() != null) jurisdiction.setCalculationType(parseCalculationType(dto.getCalculationType()));
        if (dto.getTaxType() != null) jurisdiction.setTaxType(parseTaxType(dto.getTaxType()));

        return mapToDTO(taxJurisdictionRepository.save(jurisdiction));
    }

    public void deleteTaxJurisdiction(Long jurisdictionId) {
        TaxJurisdiction jurisdiction = taxJurisdictionRepository.findById(jurisdictionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tax jurisdiction not found"));
        jurisdiction.setActive(false);
        taxJurisdictionRepository.save(jurisdiction);
    }

    private void validate(TaxJurisdictionDTO dto) {
        required(dto.getJurisdictionName(), "Jurisdiction name is required");
        required(dto.getJurisdictionCode(), "Jurisdiction code is required");
        if (dto.getTaxRate() == null || dto.getTaxRate().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tax rate cannot be negative");
        }
        parseTaxType(dto.getTaxType());
        parseCalculationType(dto.getCalculationType());
    }

    private TaxJurisdiction.TaxType parseTaxType(String value) {
        try {
            return TaxJurisdiction.TaxType.valueOf(required(value, "Tax type is required").toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported tax type");
        }
    }

    private TaxJurisdiction.TaxCalculationType parseCalculationType(String value) {
        try {
            return TaxJurisdiction.TaxCalculationType.valueOf(required(value, "Calculation type is required").toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported tax calculation type");
        }
    }

    private String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private TaxJurisdictionDTO mapToDTO(TaxJurisdiction jurisdiction) {
        return TaxJurisdictionDTO.builder()
                .id(jurisdiction.getId())
                .organizationId(jurisdiction.getOrganization().getId())
                .jurisdictionName(jurisdiction.getJurisdictionName())
                .jurisdictionCode(jurisdiction.getJurisdictionCode())
                .country(jurisdiction.getCountry())
                .state(jurisdiction.getState())
                .municipality(jurisdiction.getMunicipality())
                .taxRate(jurisdiction.getTaxRate())
                .taxType(jurisdiction.getTaxType().toString())
                .calculationType(jurisdiction.getCalculationType().toString())
                .description(jurisdiction.getDescription())
                .active(jurisdiction.getActive())
                .createdAt(jurisdiction.getCreatedAt())
                .updatedAt(jurisdiction.getUpdatedAt())
                .build();
    }
}
