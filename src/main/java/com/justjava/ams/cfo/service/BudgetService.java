package com.justjava.ams.cfo.service;

import com.justjava.ams.accountant.entity.ChartOfAccounts;
import com.justjava.ams.accountant.repository.ChartOfAccountsRepository;
import com.justjava.ams.cfo.dto.BudgetDTO;
import com.justjava.ams.cfo.dto.BudgetControlDecision;
import com.justjava.ams.cfo.dto.BudgetDashboardResponse;
import com.justjava.ams.cfo.dto.BudgetLineDTO;
import com.justjava.ams.cfo.entity.Budget;
import com.justjava.ams.cfo.entity.BudgetLine;
import com.justjava.ams.cfo.repository.BudgetLineRepository;
import com.justjava.ams.cfo.repository.BudgetRepository;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import com.justjava.ams.financeAdmin.dto.ApprovalDecisionDTO;
import com.justjava.ams.financeAdmin.dto.ApprovalEvaluationRequest;
import com.justjava.ams.financeAdmin.entity.ModuleControl;
import com.justjava.ams.financeAdmin.service.ApprovalWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final BudgetLineRepository budgetLineRepository;
    private final OrganizationRepository organizationRepository;
    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final ApprovalWorkflowService approvalWorkflowService;
    private final BudgetControlService budgetControlService;

    public BudgetDTO createBudget(Long organizationId, BudgetDTO dto) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        validateBudget(dto);
        budgetRepository.findByOrganizationIdAndBudgetCode(organizationId, dto.getBudgetCode())
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Budget code already exists for organization");
                });

        Budget budget = Budget.builder()
                .organization(organization)
                .budgetCode(required(dto.getBudgetCode(), "Budget code is required"))
                .budgetName(required(dto.getBudgetName(), "Budget name is required"))
                .budgetYear(dto.getBudgetYear())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .totalBudget(dto.getTotalBudget())
                .allocatedAmount(BigDecimal.ZERO)
                .spentAmount(BigDecimal.ZERO)
                .departmentName(dto.getDepartmentName())
                .notes(dto.getNotes())
                .build();

        Budget saved = budgetRepository.save(budget);
        if (dto.getLines() != null) {
            for (BudgetLineDTO line : dto.getLines()) {
                createBudgetLine(saved.getId(), line);
            }
        }
        return getBudget(saved.getId());
    }

    public BudgetDTO getBudget(Long budgetId) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));
        return mapToDTO(budget);
    }

    public List<BudgetDTO> getBudgetsByOrganization(Long organizationId) {
        return budgetRepository.findByOrganizationId(organizationId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<BudgetDTO> getBudgetsByYear(Long organizationId, Integer year) {
        return budgetRepository.findByOrganizationIdAndBudgetYear(organizationId, year)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<BudgetDTO> getBudgetsByStatus(Long organizationId, String status) {
        return budgetRepository.findByOrganizationIdAndStatus(organizationId, Budget.BudgetStatus.valueOf(status))
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public BudgetDTO updateBudgetStatus(Long budgetId, String status) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));
        Budget.BudgetStatus parsed = parseStatus(status);
        if (Budget.BudgetStatus.SUBMITTED.equals(parsed)) {
            ApprovalDecisionDTO decision = approvalWorkflowService.submitForApproval(ApprovalEvaluationRequest.builder()
                    .organizationId(budget.getOrganization().getId())
                    .moduleType(ModuleControl.ModuleType.BUDGETING)
                    .transactionType("BUDGET")
                    .entityType("Budget")
                    .entityId(budget.getId())
                    .amount(budget.getTotalBudget())
                    .departmentCode(budget.getDepartmentName())
                    .build());
            budget.setApprovalRequestId(decision.getApprovalRequestId());
            budget.setApprovalRuleId(decision.getApprovalRuleId());
            budget.setApprovalRuleName(decision.getApprovalRuleName());
            budget.setRequiredApprovals(decision.getRequiredApprovals());
            if (!Boolean.TRUE.equals(decision.getApprovalRequired())) {
                budget.setApproved(true);
                parsed = Budget.BudgetStatus.APPROVED;
            }
        }
        budget.setStatus(parsed);
        return mapToDTO(budgetRepository.save(budget));
    }

    public BudgetDTO approveBudget(Long budgetId) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));
        if (!Budget.BudgetStatus.SUBMITTED.equals(budget.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only SUBMITTED budgets can be approved");
        }
        if (budget.getApprovalRequestId() != null) {
            approvalWorkflowService.approvePending("Budget", budget.getId(), "Budget approved");
        }
        budget.setApproved(true);
        budget.setStatus(Budget.BudgetStatus.APPROVED);
        return mapToDTO(budgetRepository.save(budget));
    }

    public BudgetDTO rejectBudget(Long budgetId, String rejectionReason) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));
        if (!Budget.BudgetStatus.SUBMITTED.equals(budget.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only SUBMITTED budgets can be rejected");
        }
        if (budget.getApprovalRequestId() != null) {
            approvalWorkflowService.rejectPending("Budget", budget.getId(), required(rejectionReason, "Rejection reason is required"));
        }
        budget.setApproved(false);
        budget.setStatus(Budget.BudgetStatus.REJECTED);
        return mapToDTO(budgetRepository.save(budget));
    }

    public BudgetDTO activateBudget(Long budgetId) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));
        if (!Boolean.TRUE.equals(budget.getApproved()) && !Budget.BudgetStatus.APPROVED.equals(budget.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only approved budgets can be activated");
        }
        approvalWorkflowService.requireApproved("Budget", budgetId);
        budget.setApproved(true);
        budget.setStatus(Budget.BudgetStatus.ACTIVE);
        return mapToDTO(budgetRepository.save(budget));
    }

    public BudgetLineDTO createBudgetLine(Long budgetId, BudgetLineDTO dto) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));
        ChartOfAccounts account = chartOfAccountsRepository.findById(dto.getChartAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chart account not found"));
        if (!account.getOrganization().getId().equals(budget.getOrganization().getId())
                || !ChartOfAccounts.AccountType.EXPENSE.equals(account.getAccountType())
                || Boolean.FALSE.equals(account.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Budget lines require an active expense account in the budget organization");
        }
        if (dto.getAllocatedAmount() == null || dto.getAllocatedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Allocated amount must be positive");
        }
        BudgetLine line = BudgetLine.builder()
                .budget(budget)
                .chartAccount(account)
                .departmentCode(blankToNull(dto.getDepartmentCode()))
                .projectCode(blankToNull(dto.getProjectCode()))
                .branchCode(blankToNull(dto.getBranchCode()))
                .allocatedAmount(dto.getAllocatedAmount())
                .warningThresholdPercent(dto.getWarningThresholdPercent() != null ? dto.getWarningThresholdPercent() : new BigDecimal("90.00"))
                .hardStopEnabled(dto.getHardStopEnabled() != null ? dto.getHardStopEnabled() : true)
                .active(dto.getActive() != null ? dto.getActive() : true)
                .notes(dto.getNotes())
                .build();
        BudgetLine saved = budgetLineRepository.save(line);
        refreshBudgetRollups(budget);
        return mapLineToDTO(saved);
    }

    public List<BudgetLineDTO> getBudgetLines(Long budgetId) {
        budgetRepository.findById(budgetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));
        return budgetLineRepository.findByBudgetIdAndActiveTrue(budgetId)
                .stream()
                .map(this::mapLineToDTO)
                .collect(Collectors.toList());
    }

    public BudgetDashboardResponse getDashboard(Long organizationId, Integer year) {
        int budgetYear = year != null ? year : LocalDate.now().getYear();
        List<BudgetLineDTO> lines = budgetLineRepository
                .findByBudgetOrganizationIdAndBudgetBudgetYearAndActiveTrue(organizationId, budgetYear)
                .stream()
                .map(this::mapLineToDTO)
                .sorted(Comparator.comparing(BudgetLineDTO::getUtilizationPercent).reversed())
                .collect(Collectors.toList());
        BigDecimal allocated = lines.stream().map(BudgetLineDTO::getAllocatedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal spent = lines.stream().map(BudgetLineDTO::getSpentAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = allocated.subtract(spent);
        List<BudgetControlDecision> alerts = lines.stream()
                .filter(line -> "EXCEEDED".equals(line.getStatus()) || "WARNING".equals(line.getStatus()))
                .map(line -> BudgetControlDecision.builder()
                        .allowed(!"EXCEEDED".equals(line.getStatus()))
                        .severity(line.getStatus())
                        .budgetLineId(line.getId())
                        .allocatedAmount(line.getAllocatedAmount())
                        .spentAmount(line.getSpentAmount())
                        .availableAmount(line.getRemainingAmount())
                        .projectedSpend(line.getSpentAmount())
                        .message(line.getAccountCode() + " is " + line.getUtilizationPercent() + "% utilized")
                        .build())
                .collect(Collectors.toList());
        return BudgetDashboardResponse.builder()
                .organizationId(organizationId)
                .year(budgetYear)
                .totalBudget(allocated)
                .allocatedAmount(allocated)
                .spentAmount(spent)
                .remainingAmount(remaining)
                .utilizationPercent(budgetControlService.percent(spent, allocated))
                .lines(lines)
                .alerts(alerts)
                .build();
    }

    private BudgetDTO mapToDTO(Budget budget) {
        List<BudgetLineDTO> lines = budgetLineRepository.findByBudgetIdAndActiveTrue(budget.getId())
                .stream()
                .map(this::mapLineToDTO)
                .collect(Collectors.toList());
        BigDecimal allocated = lines.stream().map(BudgetLineDTO::getAllocatedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal spent = lines.stream().map(BudgetLineDTO::getSpentAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return BudgetDTO.builder()
                .id(budget.getId())
                .organizationId(budget.getOrganization().getId())
                .budgetCode(budget.getBudgetCode())
                .budgetName(budget.getBudgetName())
                .budgetYear(budget.getBudgetYear())
                .startDate(budget.getStartDate())
                .endDate(budget.getEndDate())
                .totalBudget(budget.getTotalBudget())
                .allocatedAmount(allocated)
                .spentAmount(spent)
                .status(budget.getStatus().toString())
                .approved(budget.getApproved())
                .approvalRequestId(budget.getApprovalRequestId())
                .approvalRuleId(budget.getApprovalRuleId())
                .approvalRuleName(budget.getApprovalRuleName())
                .requiredApprovals(budget.getRequiredApprovals())
                .departmentName(budget.getDepartmentName())
                .notes(budget.getNotes())
                .lines(lines)
                .createdAt(budget.getCreatedAt())
                .updatedAt(budget.getUpdatedAt())
                .build();
    }

    private BudgetLineDTO mapLineToDTO(BudgetLine line) {
        BigDecimal spent = budgetControlService.spent(line.getId());
        BigDecimal allocated = line.getAllocatedAmount() != null ? line.getAllocatedAmount() : BigDecimal.ZERO;
        BigDecimal remaining = allocated.subtract(spent);
        BigDecimal utilization = budgetControlService.percent(spent, allocated);
        String status = spent.compareTo(allocated) > 0
                ? "EXCEEDED"
                : utilization.compareTo(line.getWarningThresholdPercent()) >= 0 ? "WARNING" : "OK";
        return BudgetLineDTO.builder()
                .id(line.getId())
                .budgetId(line.getBudget().getId())
                .chartAccountId(line.getChartAccount().getId())
                .accountCode(line.getChartAccount().getAccountCode())
                .accountName(line.getChartAccount().getAccountName())
                .departmentCode(line.getDepartmentCode())
                .projectCode(line.getProjectCode())
                .branchCode(line.getBranchCode())
                .allocatedAmount(allocated)
                .spentAmount(spent)
                .remainingAmount(remaining)
                .utilizationPercent(utilization)
                .warningThresholdPercent(line.getWarningThresholdPercent())
                .hardStopEnabled(line.getHardStopEnabled())
                .active(line.getActive())
                .status(status)
                .notes(line.getNotes())
                .createdAt(line.getCreatedAt())
                .updatedAt(line.getUpdatedAt())
                .build();
    }

    private void refreshBudgetRollups(Budget budget) {
        List<BudgetLineDTO> lines = budgetLineRepository.findByBudgetIdAndActiveTrue(budget.getId())
                .stream()
                .map(this::mapLineToDTO)
                .collect(Collectors.toList());
        budget.setAllocatedAmount(lines.stream().map(BudgetLineDTO::getAllocatedAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        budget.setSpentAmount(lines.stream().map(BudgetLineDTO::getSpentAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        budgetRepository.save(budget);
    }

    private void validateBudget(BudgetDTO dto) {
        required(dto.getBudgetCode(), "Budget code is required");
        required(dto.getBudgetName(), "Budget name is required");
        if (dto.getBudgetYear() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Budget year is required");
        if (dto.getStartDate() == null || dto.getEndDate() == null || dto.getStartDate().isAfter(dto.getEndDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid budget date range is required");
        }
        if (dto.getTotalBudget() == null || dto.getTotalBudget().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Total budget must be positive");
        }
    }

    private Budget.BudgetStatus parseStatus(String status) {
        try {
            return Budget.BudgetStatus.valueOf(required(status, "Budget status is required").toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported budget status");
        }
    }

    private String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value != null && !value.trim().isEmpty() ? value.trim() : null;
    }
}

