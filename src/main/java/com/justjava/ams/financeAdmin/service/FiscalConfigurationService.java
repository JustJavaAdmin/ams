package com.justjava.ams.financeAdmin.service;

import com.justjava.ams.financeAdmin.dto.FiscalConfigurationDTO;
import com.justjava.ams.financeAdmin.entity.FiscalConfiguration;
import com.justjava.ams.financeAdmin.repository.FiscalConfigurationRepository;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class FiscalConfigurationService {

    private final FiscalConfigurationRepository fiscalConfigurationRepository;
    private final OrganizationRepository organizationRepository;

    public FiscalConfigurationDTO createFiscalConfiguration(Long organizationId, FiscalConfigurationDTO dto) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Organization not found"));

        fiscalConfigurationRepository.findByOrganizationId(organizationId)
                .ifPresent(existing -> {
                    throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Fiscal configuration already exists for organization");
                });

        FiscalConfiguration config = FiscalConfiguration.builder()
                .organization(organization)
                .fiscalYearStartMonth(dto.getFiscalYearStartMonth())
                .fiscalYearEndMonth(dto.getFiscalYearEndMonth())
                .numberOfQuarters(dto.getNumberOfQuarters() != null ? dto.getNumberOfQuarters() : 4)
                .accountingMethod(FiscalConfiguration.AccountingMethod.valueOf(dto.getAccountingMethod()))
                .multiCurrencyEnabled(dto.getMultiCurrencyEnabled() != null ? dto.getMultiCurrencyEnabled() : false)
                .baseCurrency(dto.getBaseCurrency() != null ? dto.getBaseCurrency() : "NGN")
                .allowNegativeInventory(dto.getAllowNegativeInventory() != null ? dto.getAllowNegativeInventory() : false)
                .requireApprovalForTransactions(dto.getRequireApprovalForTransactions() != null ? dto.getRequireApprovalForTransactions() : true)
                .approvalHierarchyLevels(dto.getApprovalHierarchyLevels() != null ? dto.getApprovalHierarchyLevels() : 2)
                .notes(dto.getNotes())
                .build();

        return mapToDTO(fiscalConfigurationRepository.save(config));
    }

    public FiscalConfigurationDTO getFiscalConfiguration(Long configId) {
        FiscalConfiguration config = fiscalConfigurationRepository.findById(configId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Fiscal configuration not found"));
        return mapToDTO(config);
    }

    public FiscalConfigurationDTO getByOrganization(Long organizationId) {
        FiscalConfiguration config = fiscalConfigurationRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Fiscal configuration not found for organization"));
        return mapToDTO(config);
    }

    public FiscalConfigurationDTO updateFiscalConfiguration(Long configId, FiscalConfigurationDTO dto) {
        FiscalConfiguration config = fiscalConfigurationRepository.findById(configId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Fiscal configuration not found"));

        if (dto.getFiscalYearStartMonth() != null) config.setFiscalYearStartMonth(dto.getFiscalYearStartMonth());
        if (dto.getFiscalYearEndMonth() != null) config.setFiscalYearEndMonth(dto.getFiscalYearEndMonth());
        if (dto.getNumberOfQuarters() != null) config.setNumberOfQuarters(dto.getNumberOfQuarters());
        if (dto.getAccountingMethod() != null) config.setAccountingMethod(FiscalConfiguration.AccountingMethod.valueOf(dto.getAccountingMethod()));
        if (dto.getMultiCurrencyEnabled() != null) config.setMultiCurrencyEnabled(dto.getMultiCurrencyEnabled());
        if (dto.getBaseCurrency() != null) config.setBaseCurrency(dto.getBaseCurrency());
        if (dto.getAllowNegativeInventory() != null) config.setAllowNegativeInventory(dto.getAllowNegativeInventory());
        if (dto.getRequireApprovalForTransactions() != null) config.setRequireApprovalForTransactions(dto.getRequireApprovalForTransactions());
        if (dto.getApprovalHierarchyLevels() != null) config.setApprovalHierarchyLevels(dto.getApprovalHierarchyLevels());

        return mapToDTO(fiscalConfigurationRepository.save(config));
    }

    private FiscalConfigurationDTO mapToDTO(FiscalConfiguration config) {
        return FiscalConfigurationDTO.builder()
                .id(config.getId())
                .organizationId(config.getOrganization().getId())
                .fiscalYearStartMonth(config.getFiscalYearStartMonth())
                .fiscalYearEndMonth(config.getFiscalYearEndMonth())
                .numberOfQuarters(config.getNumberOfQuarters())
                .accountingMethod(config.getAccountingMethod().toString())
                .multiCurrencyEnabled(config.getMultiCurrencyEnabled())
                .baseCurrency(config.getBaseCurrency())
                .allowNegativeInventory(config.getAllowNegativeInventory())
                .requireApprovalForTransactions(config.getRequireApprovalForTransactions())
                .approvalHierarchyLevels(config.getApprovalHierarchyLevels())
                .notes(config.getNotes())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
