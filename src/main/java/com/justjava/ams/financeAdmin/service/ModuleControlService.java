package com.justjava.ams.financeAdmin.service;
import com.justjava.ams.financeAdmin.dto.ModuleControlDefaultsResponse;
import com.justjava.ams.financeAdmin.dto.ModuleControlDTO;
import com.justjava.ams.financeAdmin.dto.ModuleControlUpdateRequest;
import com.justjava.ams.financeAdmin.entity.ModuleControl;
import com.justjava.ams.financeAdmin.repository.ModuleControlRepository;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import com.justjava.ams.accountant.repository.GeneralLedgerRepository;
import com.justjava.ams.auditor.service.AuditLogService;
import com.justjava.ams.auditor.service.SecurityEventService;
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
public class ModuleControlService {
    private final ModuleControlRepository moduleControlRepository;
    private final OrganizationRepository organizationRepository;
    private final AuditLogService auditLogService;
    private final SecurityEventService securityEventService;
    private final GeneralLedgerRepository generalLedgerRepository;

    private static final List<ModuleControl.ModuleType> DEFAULT_ENABLED_MODULES = List.of(
            ModuleControl.ModuleType.GENERAL_LEDGER,
            ModuleControl.ModuleType.REPORTING,
            ModuleControl.ModuleType.AUDIT,
            ModuleControl.ModuleType.APPROVALS
    );

    private static final List<ModuleControl.ModuleType> DEFAULT_DISABLED_MODULES = List.of(
            ModuleControl.ModuleType.BANKING,
            ModuleControl.ModuleType.FIXED_ASSETS,
            ModuleControl.ModuleType.BUDGETING,
            ModuleControl.ModuleType.PAYROLL,
            ModuleControl.ModuleType.ACCOUNTS_PAYABLE,
            ModuleControl.ModuleType.ACCOUNTS_RECEIVABLE
    );

    public ModuleControlDTO createModuleControl(Long organizationId, ModuleControlDTO dto) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        ModuleControl.ModuleType moduleType = parseModuleType(dto.getModuleType());
        if (moduleControlRepository.existsByOrganizationIdAndModuleType(organizationId, moduleType)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Module control already exists");
        }
        ModuleControl module = ModuleControl.builder()
                .organization(organization)
                .moduleType(moduleType)
                .enabled(dto.getEnabled() != null ? dto.getEnabled() : true)
                .allowUserConfiguration(dto.getAllowUserConfiguration() != null ? dto.getAllowUserConfiguration() : false)
                .requiresApproval(dto.getRequiresApproval() != null ? dto.getRequiresApproval() : false)
                .enableAuditTrail(dto.getEnableAuditTrail() != null ? dto.getEnableAuditTrail() : true)
                .enableNotifications(dto.getEnableNotifications() != null ? dto.getEnableNotifications() : true)
                .maxTransactionAmountLimit(dto.getMaxTransactionAmountLimit() != null ? dto.getMaxTransactionAmountLimit() : Integer.MAX_VALUE)
                .configurationJson(dto.getConfigurationJson())
                .notes(dto.getNotes())
                .build();
        ModuleControl saved = moduleControlRepository.save(module);
        logAudit(organizationId, saved.getId(), "CREATE", null, saved.getModuleType().name(),
                "Module control created for " + saved.getModuleType());
        return mapToDTO(saved);
    }
    public ModuleControlDTO getModuleControl(Long moduleId) {
        ModuleControl module = moduleControlRepository.findById(moduleId)
                .orElseThrow(() -> new RuntimeException("Module control not found"));
        return mapToDTO(module);
    }
    public ModuleControlDTO getByOrganizationAndType(Long organizationId, String moduleType) {
        ModuleControl module = moduleControlRepository.findByOrganizationIdAndModuleType(
                organizationId,
                ModuleControl.ModuleType.valueOf(moduleType))
                .orElseThrow(() -> new RuntimeException("Module control not found"));
        return mapToDTO(module);
    }
    public List<ModuleControlDTO> getModulesByOrganization(Long organizationId) {
        return moduleControlRepository.findByOrganizationId(organizationId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }
    public List<ModuleControlDTO> getEnabledModulesByOrganization(Long organizationId) {
        return moduleControlRepository.findByOrganizationIdAndEnabledTrue(organizationId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }
    public ModuleControlDTO updateModuleControl(Long moduleId, ModuleControlDTO dto) {
        return updateModuleControl(moduleId, dto, "system");
    }

    public ModuleControlDTO updateModuleControl(Long moduleId, ModuleControlDTO dto, String changedBy) {
        ModuleControl module = moduleControlRepository.findById(moduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Module control not found"));
        Boolean oldEnabled = module.getEnabled();
        if (dto.getEnabled() != null) {
            validateToggle(module, dto.getEnabled());
            module.setEnabled(dto.getEnabled());
        }
        if (dto.getAllowUserConfiguration() != null) module.setAllowUserConfiguration(dto.getAllowUserConfiguration());
        if (dto.getRequiresApproval() != null) module.setRequiresApproval(dto.getRequiresApproval());
        if (dto.getEnableAuditTrail() != null) module.setEnableAuditTrail(dto.getEnableAuditTrail());
        if (dto.getEnableNotifications() != null) module.setEnableNotifications(dto.getEnableNotifications());
        if (dto.getMaxTransactionAmountLimit() != null) module.setMaxTransactionAmountLimit(dto.getMaxTransactionAmountLimit());
        if (dto.getConfigurationJson() != null) module.setConfigurationJson(dto.getConfigurationJson());
        if (dto.getNotes() != null) module.setNotes(dto.getNotes());
        ModuleControl saved = moduleControlRepository.save(module);
        logAudit(saved.getOrganization().getId(), saved.getId(), "UPDATE",
                "enabled=" + oldEnabled, "enabled=" + saved.getEnabled(),
                "Module control updated for " + saved.getModuleType() + " by " + defaultUser(changedBy));
        logConfigurationChange(saved, "Module Control Updated",
                "Module " + saved.getModuleType() + " updated by " + defaultUser(changedBy));
        return mapToDTO(saved);
    }

    public ModuleControlDefaultsResponse initializeDefaultModules(Long organizationId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        int existingCount = moduleControlRepository.findByOrganizationId(organizationId).size();
        int createdCount = 0;
        for (ModuleControl.ModuleType moduleType : DEFAULT_ENABLED_MODULES) {
            createdCount += createDefaultIfMissing(organization, moduleType, true);
        }
        for (ModuleControl.ModuleType moduleType : DEFAULT_DISABLED_MODULES) {
            createdCount += createDefaultIfMissing(organization, moduleType, false);
        }

        List<ModuleControlDTO> modules = getModulesByOrganization(organizationId);
        if (createdCount > 0) {
            securityEventService.logEvent(organizationId, "CONFIGURATION_CHANGE", "MEDIUM",
                    "Default Module Controls Initialized",
                    "Initialized " + createdCount + " default module controls",
                    null, null);
        }
        return ModuleControlDefaultsResponse.builder()
                .organizationId(organizationId)
                .createdCount(createdCount)
                .existingCount(existingCount)
                .modules(modules)
                .build();
    }

    public ModuleControlDTO toggleModule(Long moduleId, Boolean enabled, String reason, String changedBy) {
        if (enabled == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enabled is required");
        }
        ModuleControl module = moduleControlRepository.findById(moduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Module control not found"));
        validateToggle(module, enabled);

        Boolean oldEnabled = module.getEnabled();
        module.setEnabled(enabled);
        module.setNotes(reason);
        ModuleControl saved = moduleControlRepository.save(module);
        String user = defaultUser(changedBy);
        logAudit(saved.getOrganization().getId(), saved.getId(), "UPDATE",
                "enabled=" + oldEnabled, "enabled=" + enabled,
                "Module " + saved.getModuleType() + " toggled by " + user);
        logConfigurationChange(saved, "Module Control Changed",
                "Module " + saved.getModuleType() + " changed from " + oldEnabled + " to " + enabled + " by " + user);
        return mapToDTO(saved);
    }

    public ModuleControlDTO upsertModuleControl(Long organizationId, String moduleType, ModuleControlUpdateRequest request, String changedBy) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        ModuleControl.ModuleType parsedModuleType = parseModuleType(moduleType);
        ModuleControl module = moduleControlRepository.findByOrganizationIdAndModuleType(organizationId, parsedModuleType)
                .orElseGet(() -> ModuleControl.builder()
                        .organization(organization)
                        .moduleType(parsedModuleType)
                        .enabled(true)
                        .allowUserConfiguration(false)
                        .requiresApproval(false)
                        .enableAuditTrail(true)
                        .enableNotifications(true)
                        .maxTransactionAmountLimit(Integer.MAX_VALUE)
                        .build());

        ModuleControlDTO dto = ModuleControlDTO.builder()
                .enabled(request.getEnabled())
                .allowUserConfiguration(request.getAllowUserConfiguration())
                .requiresApproval(request.getRequiresApproval())
                .enableAuditTrail(request.getEnableAuditTrail())
                .enableNotifications(request.getEnableNotifications())
                .maxTransactionAmountLimit(request.getMaxTransactionAmountLimit())
                .configurationJson(request.getConfigurationJson())
                .notes(request.getNotes())
                .build();

        if (module.getId() == null) {
            if (dto.getEnabled() != null) module.setEnabled(dto.getEnabled());
            if (dto.getAllowUserConfiguration() != null) module.setAllowUserConfiguration(dto.getAllowUserConfiguration());
            if (dto.getRequiresApproval() != null) module.setRequiresApproval(dto.getRequiresApproval());
            if (dto.getEnableAuditTrail() != null) module.setEnableAuditTrail(dto.getEnableAuditTrail());
            if (dto.getEnableNotifications() != null) module.setEnableNotifications(dto.getEnableNotifications());
            if (dto.getMaxTransactionAmountLimit() != null) module.setMaxTransactionAmountLimit(dto.getMaxTransactionAmountLimit());
            module.setConfigurationJson(dto.getConfigurationJson());
            module.setNotes(dto.getNotes());
            ModuleControl saved = moduleControlRepository.save(module);
            logAudit(organizationId, saved.getId(), "CREATE", null, saved.getModuleType().name(),
                    "Module control upsert created by " + defaultUser(changedBy));
            logConfigurationChange(saved, "Module Control Created",
                    "Module " + saved.getModuleType() + " created by " + defaultUser(changedBy));
            return mapToDTO(saved);
        }

        return updateModuleControl(module.getId(), dto, changedBy);
    }

    /**
     * Check if a module is enabled for an organization.
     */
    public boolean isModuleEnabled(Long organizationId, ModuleControl.ModuleType moduleType) {
        organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        return moduleControlRepository.findByOrganizationIdAndModuleType(organizationId, moduleType)
                .map(ModuleControl::getEnabled)
                .orElse(false);
    }

    /**
     * Require that a module is enabled for an organization.
     * Throws an exception if the module is not found or not enabled.
     */
    public void requireModuleEnabled(Long organizationId, ModuleControl.ModuleType moduleType) {
        organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        ModuleControl module = moduleControlRepository.findByOrganizationIdAndModuleType(organizationId, moduleType)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Module control not found for type: " + moduleType));

        if (!Boolean.TRUE.equals(module.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Module " + moduleType + " is not enabled");
        }
    }

    public void requireTransactionWithinLimit(Long organizationId, ModuleControl.ModuleType moduleType, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        moduleControlRepository.findByOrganizationIdAndModuleType(organizationId, moduleType)
                .map(ModuleControl::getMaxTransactionAmountLimit)
                .filter(limit -> limit != null && limit < Integer.MAX_VALUE)
                .ifPresent(limit -> {
                    if (amount.compareTo(BigDecimal.valueOf(limit.longValue())) > 0) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Transaction amount exceeds " + moduleType + " limit of " + limit);
                    }
                });
    }

    private int createDefaultIfMissing(Organization organization, ModuleControl.ModuleType moduleType, boolean enabled) {
        if (moduleControlRepository.existsByOrganizationIdAndModuleType(organization.getId(), moduleType)) {
            return 0;
        }
        ModuleControl module = ModuleControl.builder()
                .organization(organization)
                .moduleType(moduleType)
                .enabled(enabled)
                .allowUserConfiguration(false)
                .requiresApproval(false)
                .enableAuditTrail(true)
                .enableNotifications(true)
                .maxTransactionAmountLimit(Integer.MAX_VALUE)
                .notes("default")
                .build();
        ModuleControl saved = moduleControlRepository.save(module);
        logAudit(organization.getId(), saved.getId(), "CREATE", null, saved.getModuleType().name(),
                "Default module control created for " + saved.getModuleType());
        return 1;
    }

    private void validateToggle(ModuleControl module, Boolean enabled) {
        if (Boolean.FALSE.equals(enabled)
                && ModuleControl.ModuleType.GENERAL_LEDGER.equals(module.getModuleType())
                && generalLedgerRepository.existsPostedByOrganization(module.getOrganization().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "GENERAL_LEDGER cannot be disabled while posted GL entries exist");
        }
    }

    private ModuleControl.ModuleType parseModuleType(String moduleType) {
        if (moduleType == null || moduleType.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Module type is required");
        }
        try {
            return ModuleControl.ModuleType.valueOf(moduleType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid module type");
        }
    }

    private void logAudit(Long organizationId, Long entityId, String action, String oldValue, String newValue, String description) {
        try {
            auditLogService.log(organizationId, "ModuleControl", entityId, action, oldValue, newValue, description);
        } catch (Exception ex) {
        }
    }

    private void logConfigurationChange(ModuleControl module, String title, String description) {
        try {
            securityEventService.logEvent(module.getOrganization().getId(), "CONFIGURATION_CHANGE", "MEDIUM",
                    title, description, null, null);
        } catch (Exception ex) {
        }
    }

    private String defaultUser(String changedBy) {
        return changedBy != null && !changedBy.trim().isEmpty() ? changedBy : "system";
    }

    private ModuleControlDTO mapToDTO(ModuleControl module) {
        return ModuleControlDTO.builder()
                .id(module.getId())
                .organizationId(module.getOrganization().getId())
                .moduleType(module.getModuleType().toString())
                .enabled(module.getEnabled())
                .allowUserConfiguration(module.getAllowUserConfiguration())
                .requiresApproval(module.getRequiresApproval())
                .enableAuditTrail(module.getEnableAuditTrail())
                .enableNotifications(module.getEnableNotifications())
                .maxTransactionAmountLimit(module.getMaxTransactionAmountLimit())
                .configurationJson(module.getConfigurationJson())
                .notes(module.getNotes())
                .createdAt(module.getCreatedAt())
                .updatedAt(module.getUpdatedAt())
                .build();
    }
}
