package com.justjava.ams.financeAdmin.service;

import com.justjava.ams.accountant.entity.ChartOfAccounts;
import com.justjava.ams.accountant.entity.JournalLine;
import com.justjava.ams.accountant.entity.ManualJournal;
import com.justjava.ams.auditor.service.AuditLogService;
import com.justjava.ams.auditor.service.SecurityEventService;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import com.justjava.ams.financeAdmin.dto.ApprovalDecisionDTO;
import com.justjava.ams.financeAdmin.dto.ApprovalEvaluationRequest;
import com.justjava.ams.financeAdmin.dto.ApprovalRuleDTO;
import com.justjava.ams.financeAdmin.entity.ApprovalRule;
import com.justjava.ams.financeAdmin.entity.ModuleControl;
import com.justjava.ams.financeAdmin.repository.ApprovalRuleRepository;
import com.justjava.ams.financeAdmin.repository.ModuleControlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
public class ApprovalRuleService {

    private static final String MANUAL_JOURNAL = "MANUAL_JOURNAL";

    private final ApprovalRuleRepository approvalRuleRepository;
    private final OrganizationRepository organizationRepository;
    private final AuditLogService auditLogService;
    private final SecurityEventService securityEventService;
    private final ModuleControlRepository moduleControlRepository;

    public ApprovalRuleDTO createRule(Long organizationId, ApprovalRuleDTO dto, String changedBy) {
        Organization organization = findOrganization(organizationId);
        String ruleName = required(dto.getRuleName(), "Rule name is required");
        approvalRuleRepository.findByOrganizationIdAndRuleNameIgnoreCase(organizationId, ruleName)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Approval rule name already exists for this organization");
                });

        ApprovalRule rule = ApprovalRule.builder()
                .organization(organization)
                .ruleName(ruleName)
                .moduleType(parseModuleType(dto.getModuleType()))
                .transactionType(normalizeTransactionType(dto.getTransactionType()))
                .minAmount(nonNegative(dto.getMinAmount(), "Minimum amount cannot be negative"))
                .maxAmount(nonNegativeOrNull(dto.getMaxAmount(), "Maximum amount cannot be negative"))
                .accountType(parseAccountType(dto.getAccountType()))
                .branchId(dto.getBranchId())
                .departmentCode(trimToNull(dto.getDepartmentCode()))
                .requiredApprovals(requiredApprovals(dto.getRequiredApprovals()))
                .approverRole(trimToNull(dto.getApproverRole()))
                .priority(dto.getPriority() != null ? dto.getPriority() : 100)
                .active(dto.getActive() != null ? dto.getActive() : true)
                .notes(dto.getNotes())
                .build();
        validateAmountRange(rule.getMinAmount(), rule.getMaxAmount());

        ApprovalRule saved = approvalRuleRepository.save(rule);
        logChange(organizationId, saved.getId(), "CREATE", null, saved.getRuleName(), "Approval rule created by " + user(changedBy));
        return mapToDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<ApprovalRuleDTO> getRulesByOrganization(Long organizationId) {
        findOrganization(organizationId);
        return approvalRuleRepository.findByOrganizationIdOrderByPriorityAscIdAsc(organizationId)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public ApprovalRuleDTO getRule(Long ruleId) {
        return mapToDTO(findRule(ruleId));
    }

    public ApprovalRuleDTO updateRule(Long ruleId, ApprovalRuleDTO dto, String changedBy) {
        ApprovalRule rule = findRule(ruleId);
        String oldValue = rule.getRuleName() + ":" + rule.getActive();

        if (dto.getRuleName() != null) {
            String newName = required(dto.getRuleName(), "Rule name is required");
            approvalRuleRepository.findByOrganizationIdAndRuleNameIgnoreCase(rule.getOrganization().getId(), newName)
                    .filter(existing -> !existing.getId().equals(ruleId))
                    .ifPresent(existing -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Approval rule name already exists for this organization");
                    });
            rule.setRuleName(newName);
        }
        if (dto.getModuleType() != null) rule.setModuleType(parseModuleType(dto.getModuleType()));
        if (dto.getTransactionType() != null) rule.setTransactionType(normalizeTransactionType(dto.getTransactionType()));
        if (dto.getMinAmount() != null) rule.setMinAmount(nonNegative(dto.getMinAmount(), "Minimum amount cannot be negative"));
        if (dto.getMaxAmount() != null) rule.setMaxAmount(nonNegative(dto.getMaxAmount(), "Maximum amount cannot be negative"));
        if (dto.getAccountType() != null) rule.setAccountType(parseAccountType(dto.getAccountType()));
        if (dto.getBranchId() != null) rule.setBranchId(dto.getBranchId());
        if (dto.getDepartmentCode() != null) rule.setDepartmentCode(trimToNull(dto.getDepartmentCode()));
        if (dto.getRequiredApprovals() != null) rule.setRequiredApprovals(requiredApprovals(dto.getRequiredApprovals()));
        if (dto.getApproverRole() != null) rule.setApproverRole(trimToNull(dto.getApproverRole()));
        if (dto.getPriority() != null) rule.setPriority(dto.getPriority());
        if (dto.getActive() != null) rule.setActive(dto.getActive());
        if (dto.getNotes() != null) rule.setNotes(dto.getNotes());
        validateAmountRange(rule.getMinAmount(), rule.getMaxAmount());

        ApprovalRule saved = approvalRuleRepository.save(rule);
        logChange(saved.getOrganization().getId(), saved.getId(), "UPDATE", oldValue,
                saved.getRuleName() + ":" + saved.getActive(), "Approval rule updated by " + user(changedBy));
        return mapToDTO(saved);
    }

    public void deactivateRule(Long ruleId, String changedBy) {
        ApprovalRule rule = findRule(ruleId);
        rule.setActive(false);
        ApprovalRule saved = approvalRuleRepository.save(rule);
        logChange(saved.getOrganization().getId(), saved.getId(), "DELETE", "active=true", "active=false",
                "Approval rule deactivated by " + user(changedBy));
    }

    @Transactional(readOnly = true)
    public ApprovalDecisionDTO evaluateManualJournal(ManualJournal journal, List<JournalLine> lines) {
        BigDecimal totalDebits = lines.stream()
                .map(line -> line.getDebitAmount() != null ? line.getDebitAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return evaluate(ApprovalEvaluationRequest.builder()
                .organizationId(journal.getOrganization().getId())
                .moduleType(ModuleControl.ModuleType.GENERAL_LEDGER)
                .transactionType(MANUAL_JOURNAL)
                .entityType("ManualJournal")
                .entityId(journal.getId())
                .amount(totalDebits)
                .branchId(journal.getBranch() != null ? journal.getBranch().getId() : null)
                .departmentCode(firstDepartment(lines))
                .accountTypes(lines.stream()
                        .map(line -> line.getChartOfAccounts().getAccountType())
                        .collect(java.util.stream.Collectors.toSet()))
                .build());
    }

    @Transactional(readOnly = true)
    public ApprovalDecisionDTO evaluate(ApprovalEvaluationRequest request) {
        Long organizationId = request.getOrganizationId();
        ModuleControl.ModuleType moduleType = request.getModuleType();
        String transactionType = normalizeTransactionType(request.getTransactionType());
        BigDecimal amount = request.getAmount() != null ? request.getAmount() : BigDecimal.ZERO;

        ApprovalRule matched = approvalRuleRepository
                .findByOrganizationIdAndModuleTypeAndActiveTrueOrderByPriorityAscIdAsc(
                        organizationId,
                        moduleType)
                .stream()
                .filter(rule -> matchesTransactionType(rule, transactionType))
                .filter(rule -> matchesAmount(rule, amount))
                .filter(rule -> matchesBranch(rule, request.getBranchId()))
                .filter(rule -> matchesDepartment(rule, request.getDepartmentCode()))
                .filter(rule -> matchesAccountType(rule, request.getAccountTypes()))
                .findFirst()
                .orElse(null);

        if (matched == null) {
            boolean requiredByModule = moduleControlRepository.findByOrganizationIdAndModuleType(organizationId, moduleType)
                    .map(ModuleControl::getRequiresApproval)
                    .orElse(true);
            return ApprovalDecisionDTO.builder()
                    .approvalRequired(requiredByModule)
                    .requiredApprovals(1)
                    .evaluatedAmount(amount)
                    .reason(requiredByModule
                            ? "Default " + transactionType + " approval"
                            : "No matching approval rule and module approval is not required")
                    .build();
        }

        return ApprovalDecisionDTO.builder()
                .approvalRequired(true)
                .approvalRuleId(matched.getId())
                .approvalRuleName(matched.getRuleName())
                .requiredApprovals(matched.getRequiredApprovals())
                .approverRole(matched.getApproverRole())
                .evaluatedAmount(amount)
                .reason("Matched approval rule: " + matched.getRuleName())
                .build();
    }

    private boolean matchesTransactionType(ApprovalRule rule, String transactionType) {
        String value = normalizeTransactionType(rule.getTransactionType());
        return "ALL".equals(value) || transactionType.equals(value);
    }

    private boolean matchesAmount(ApprovalRule rule, BigDecimal amount) {
        BigDecimal value = amount != null ? amount : BigDecimal.ZERO;
        BigDecimal min = rule.getMinAmount() != null ? rule.getMinAmount() : BigDecimal.ZERO;
        if (value.compareTo(min) < 0) {
            return false;
        }
        return rule.getMaxAmount() == null || value.compareTo(rule.getMaxAmount()) <= 0;
    }

    private boolean matchesBranch(ApprovalRule rule, Long branchId) {
        return rule.getBranchId() == null
                || rule.getBranchId().equals(branchId);
    }

    private boolean matchesDepartment(ApprovalRule rule, String requestDepartmentCode) {
        String ruleDepartment = trimToNull(rule.getDepartmentCode());
        String requestDepartment = trimToNull(requestDepartmentCode);
        return ruleDepartment == null || ruleDepartment.equalsIgnoreCase(requestDepartment);
    }

    private boolean matchesAccountType(ApprovalRule rule, java.util.Set<ChartOfAccounts.AccountType> accountTypes) {
        return rule.getAccountType() == null || (accountTypes != null && accountTypes.contains(rule.getAccountType()));
    }

    private String firstDepartment(List<JournalLine> lines) {
        return lines.stream()
                .map(JournalLine::getDepartmentCode)
                .map(this::trimToNull)
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }

    private ApprovalRule findRule(Long ruleId) {
        return approvalRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval rule not found"));
    }

    private Organization findOrganization(Long organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
    }

    private ModuleControl.ModuleType parseModuleType(String moduleType) {
        try {
            return ModuleControl.ModuleType.valueOf(required(moduleType, "Module type is required").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid module type");
        }
    }

    private ChartOfAccounts.AccountType parseAccountType(String accountType) {
        String normalized = trimToNull(accountType);
        if (normalized == null) {
            return null;
        }
        try {
            return ChartOfAccounts.AccountType.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid account type");
        }
    }

    private String normalizeTransactionType(String transactionType) {
        String normalized = trimToNull(transactionType);
        return normalized != null ? normalized.toUpperCase(Locale.ROOT) : "ALL";
    }

    private BigDecimal nonNegative(BigDecimal amount, String message) {
        BigDecimal value = amount != null ? amount : BigDecimal.ZERO;
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value;
    }

    private BigDecimal nonNegativeOrNull(BigDecimal amount, String message) {
        if (amount == null) {
            return null;
        }
        return nonNegative(amount, message);
    }

    private Integer requiredApprovals(Integer value) {
        if (value == null || value < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Required approvals must be at least 1");
        }
        return value;
    }

    private void validateAmountRange(BigDecimal minAmount, BigDecimal maxAmount) {
        if (maxAmount != null && maxAmount.compareTo(minAmount != null ? minAmount : BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum amount cannot be lower than minimum amount");
        }
    }

    private String required(String value, String message) {
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

    private String user(String changedBy) {
        String normalized = trimToNull(changedBy);
        return normalized != null ? normalized : "system";
    }

    private void logChange(Long organizationId, Long ruleId, String action, String oldValue, String newValue, String description) {
        try {
            auditLogService.log(organizationId, "ApprovalRule", ruleId, action, oldValue, newValue, description);
        } catch (Exception ex) {
        }
        try {
            securityEventService.logEvent(organizationId, "CONFIGURATION_CHANGE", "MEDIUM",
                    "Approval Rule " + action, description, null, null);
        } catch (Exception ex) {
        }
    }

    private ApprovalRuleDTO mapToDTO(ApprovalRule rule) {
        return ApprovalRuleDTO.builder()
                .id(rule.getId())
                .organizationId(rule.getOrganization().getId())
                .ruleName(rule.getRuleName())
                .moduleType(rule.getModuleType().name())
                .transactionType(rule.getTransactionType())
                .minAmount(rule.getMinAmount())
                .maxAmount(rule.getMaxAmount())
                .accountType(rule.getAccountType() != null ? rule.getAccountType().name() : null)
                .branchId(rule.getBranchId())
                .departmentCode(rule.getDepartmentCode())
                .requiredApprovals(rule.getRequiredApprovals())
                .approverRole(rule.getApproverRole())
                .priority(rule.getPriority())
                .active(rule.getActive())
                .notes(rule.getNotes())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}
