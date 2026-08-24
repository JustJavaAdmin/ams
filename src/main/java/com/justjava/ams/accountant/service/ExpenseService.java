package com.justjava.ams.accountant.service;

import com.justjava.ams.accountant.dto.ExpenseDTO;
import com.justjava.ams.accountant.dto.ExpenseLineItemDTO;
import com.justjava.ams.accountant.entity.ChartOfAccounts;
import com.justjava.ams.accountant.entity.Expense;
import com.justjava.ams.accountant.entity.ExpenseLineItem;
import com.justjava.ams.accountant.repository.ExpenseLineItemRepository;
import com.justjava.ams.accountant.repository.ExpenseRepository;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.entity.User;
import com.justjava.ams.common.repository.OrganizationRepository;
import com.justjava.ams.common.repository.UserRepository;
import com.justjava.ams.accountant.repository.ChartOfAccountsRepository;
import com.justjava.ams.accountant.service.GeneralLedgerService;
import com.justjava.ams.accountant.service.FiscalPeriodService;
import com.justjava.ams.accountant.dto.PaymentRequest;
import com.justjava.ams.accountant.entity.BankAccount;
import com.justjava.ams.accountant.repository.BankAccountRepository;
import com.justjava.ams.cfo.service.BudgetControlService;
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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseLineItemRepository expenseLineItemRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final BankAccountRepository bankAccountRepository;
    private final GeneralLedgerService generalLedgerService;
    private final FiscalPeriodService fiscalPeriodService;
    private final TaxCalculationService taxCalculationService;
    private final BudgetControlService budgetControlService;
    private final ApprovalWorkflowService approvalWorkflowService;

    public ExpenseDTO createExpense(Long organizationId, ExpenseDTO dto, Long userId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        if (expenseRepository.findByOrganizationIdAndExpenseNumber(organizationId, dto.getExpenseNumber()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Expense number already exists for organization");
        }

        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;
        Totals totals = calculateTotals(organizationId, dto.getLineItems(), dto.getTaxJurisdictionId());

        Expense expense = Expense.builder()
                .organization(organization)
                .expenseNumber(dto.getExpenseNumber())
                .payeeName(required(dto.getPayeeName(), "Payee name is required"))
                .payeeEmail(dto.getPayeeEmail())
                .payeePhone(dto.getPayeePhone())
                .payeeAddress(dto.getPayeeAddress())
                .expenseDate(dto.getExpenseDate())
                .dueDate(dto.getDueDate())
                .subtotal(totals.subtotal())
                .taxJurisdiction(totals.taxResult().jurisdiction())
                .taxCode(totals.taxResult().taxCode())
                .taxRate(totals.taxResult().taxRate())
                .taxCalculationType(totals.taxResult().taxCalculationType())
                .taxAmount(totals.taxAmount())
                .totalAmount(totals.totalAmount())
                .amountPaid(BigDecimal.ZERO)
                .status(Expense.ExpenseStatus.DRAFT)
                .notes(dto.getNotes())
                .createdByUser(user)
                .build();

        Expense saved = expenseRepository.save(expense);
        replaceLineItems(saved, dto.getLineItems());
        return mapToDTO(saved);
    }

    public ExpenseDTO getExpense(Long expenseId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
        return mapToDTO(expense);
    }

    public List<ExpenseDTO> getExpensesByOrganization(Long organizationId) {
        return expenseRepository.findByOrganizationId(organizationId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public ExpenseDTO updateExpense(Long expenseId, ExpenseDTO dto) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
        if (!Expense.ExpenseStatus.DRAFT.equals(expense.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT expenses can be edited");
        }

        if (dto.getPayeeName() != null) expense.setPayeeName(dto.getPayeeName());
        if (dto.getPayeeEmail() != null) expense.setPayeeEmail(dto.getPayeeEmail());
        if (dto.getPayeePhone() != null) expense.setPayeePhone(dto.getPayeePhone());
        if (dto.getPayeeAddress() != null) expense.setPayeeAddress(dto.getPayeeAddress());
        if (dto.getExpenseDate() != null) expense.setExpenseDate(dto.getExpenseDate());
        if (dto.getDueDate() != null) expense.setDueDate(dto.getDueDate());
        if (dto.getNotes() != null) expense.setNotes(dto.getNotes());
        if (dto.getLineItems() != null) {
            Totals totals = calculateTotals(expense.getOrganization().getId(), dto.getLineItems(), dto.getTaxJurisdictionId());
            expense.setSubtotal(totals.subtotal());
            expense.setTaxJurisdiction(totals.taxResult().jurisdiction());
            expense.setTaxCode(totals.taxResult().taxCode());
            expense.setTaxRate(totals.taxResult().taxRate());
            expense.setTaxCalculationType(totals.taxResult().taxCalculationType());
            expense.setTaxAmount(totals.taxAmount());
            expense.setTotalAmount(totals.totalAmount());
            replaceLineItems(expense, dto.getLineItems());
        }

        return mapToDTO(expenseRepository.save(expense));
    }

    public ExpenseDTO submitExpense(Long expenseId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
        submit(expense, "system");
        return mapToDTO(expenseRepository.save(expense));
    }

    public ExpenseDTO approveExpense(Long expenseId, String approvedBy) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
        approve(expense, approvedBy);
        return mapToDTO(expenseRepository.save(expense));
    }

    public ExpenseDTO rejectExpense(Long expenseId, String rejectionReason) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
        if (!Expense.ExpenseStatus.SUBMITTED.equals(expense.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only SUBMITTED expenses can be rejected");
        }
        if (expense.getApprovalRequestId() != null) {
            approvalWorkflowService.rejectPending("Expense", expense.getId(), required(rejectionReason, "Rejection reason is required"));
        }
        expense.setStatus(Expense.ExpenseStatus.REJECTED);
        return mapToDTO(expenseRepository.save(expense));
    }

    public ExpenseDTO postExpense(Long expenseId, String postedBy) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
        if (!Expense.ExpenseStatus.APPROVED.equals(expense.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only APPROVED expenses can be posted");
        }
        approvalWorkflowService.requireApproved("Expense", expenseId);
        fiscalPeriodService.requireOpenPeriod(expense.getOrganization().getId(), expense.getExpenseDate());

        List<ExpenseLineItem> lines = expenseLineItemRepository.findByExpenseId(expenseId);
        if (lines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expense must have at least one line before posting");
        }
        ChartOfAccounts payable = requirePostingAccount(expense.getOrganization().getId(), ChartOfAccounts.AccountType.LIABILITY, ChartOfAccounts.AccountSubtype.CURRENT_LIABILITY, "accounts payable");
        List<com.justjava.ams.accountant.entity.GeneralLedger> debits = new java.util.ArrayList<>();
        List<BudgetControlService.ExpenseBudgetLine> budgetLines = new java.util.ArrayList<>();
        for (ExpenseLineItem line : lines) {
            ChartOfAccounts lineAccount = line.getChartAccount() != null
                    ? line.getChartAccount()
                    : requirePostingAccount(expense.getOrganization().getId(), ChartOfAccounts.AccountType.EXPENSE, ChartOfAccounts.AccountSubtype.OPERATING_EXPENSE, "expense");
            debits.add(com.justjava.ams.accountant.entity.GeneralLedger.builder()
                    .account(lineAccount)
                    .amount(line.getLineTotal())
                    .description(line.getDescription())
                    .notes(line.getNotes())
                    .build());
            budgetLines.add(new BudgetControlService.ExpenseBudgetLine(
                    line.getId(),
                    lineAccount,
                    line.getLineTotal(),
                    line.getDescription()));
        }
        if (expense.getTaxAmount() != null && expense.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
            ChartOfAccounts taxRecoverable = requirePostingAccount(expense.getOrganization().getId(), ChartOfAccounts.AccountType.ASSET, ChartOfAccounts.AccountSubtype.CURRENT_ASSET, "tax recoverable");
            debits.add(com.justjava.ams.accountant.entity.GeneralLedger.builder()
                    .account(taxRecoverable)
                    .amount(expense.getTaxAmount())
                    .description("Tax on expense " + expense.getExpenseNumber())
                    .build());
        }

        generalLedgerService.postExpense(
                expense.getId(),
                expense.getExpenseNumber(),
                expense.getExpenseDate(),
                payable,
                debits,
                "Expense " + expense.getExpenseNumber(),
                postedBy,
                budgetLines);
        expense.setStatus(Expense.ExpenseStatus.POSTED);
        expense.setPostedBy(postedBy);
        expense.setPostedDate(LocalDate.now());
        return mapToDTO(expenseRepository.save(expense));
    }

    public ExpenseDTO recordPayment(Long expenseId, com.justjava.ams.accountant.dto.PaymentRequest request, String paidBy) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
        if (!(Expense.ExpenseStatus.POSTED.equals(expense.getStatus())
                || Expense.ExpenseStatus.PARTIALLY_PAID.equals(expense.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only POSTED expenses can be paid");
        }
        BigDecimal amount = positive(request.getAmount(), "Payment amount must be positive");
        BigDecimal paid = expense.getAmountPaid() != null ? expense.getAmountPaid() : BigDecimal.ZERO;
        BigDecimal balance = expense.getTotalAmount().subtract(paid);
        if (amount.compareTo(balance) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment exceeds expense balance");
        }
        requireDirectPaymentAllowed(expense.getOrganization().getId(), expense.getId(), amount);
        LocalDate paymentDate = request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now();
        fiscalPeriodService.requireOpenPeriod(expense.getOrganization().getId(), paymentDate);
        com.justjava.ams.accountant.entity.BankAccount bankAccount = findBankAccount(request.getBankAccountId(), expense.getOrganization().getId());
        ChartOfAccounts payable = requirePostingAccount(expense.getOrganization().getId(), ChartOfAccounts.AccountType.LIABILITY, ChartOfAccounts.AccountSubtype.CURRENT_LIABILITY, "accounts payable");
        generalLedgerService.postExpensePayment(
                expense.getId(),
                expense.getExpenseNumber(),
                paymentDate,
                payable,
                bankAccount.getChartAccount(),
                amount,
                request.getNotes() != null ? request.getNotes() : "Expense payment " + expense.getExpenseNumber(),
                paidBy);
        BigDecimal newPaid = paid.add(amount);
        expense.setAmountPaid(newPaid);
        expense.setStatus(newPaid.compareTo(expense.getTotalAmount()) >= 0
                ? Expense.ExpenseStatus.PAID
                : Expense.ExpenseStatus.PARTIALLY_PAID);
        return mapToDTO(expenseRepository.save(expense));
    }

    private ExpenseDTO mapToDTO(Expense expense) {
        return ExpenseDTO.builder()
                .id(expense.getId())
                .organizationId(expense.getOrganization().getId())
                .expenseNumber(expense.getExpenseNumber())
                .payeeName(expense.getPayeeName())
                .payeeEmail(expense.getPayeeEmail())
                .payeePhone(expense.getPayeePhone())
                .payeeAddress(expense.getPayeeAddress())
                .expenseDate(expense.getExpenseDate())
                .dueDate(expense.getDueDate())
                .subtotal(expense.getSubtotal())
                .taxJurisdictionId(expense.getTaxJurisdiction() != null ? expense.getTaxJurisdiction().getId() : null)
                .taxCode(expense.getTaxCode())
                .taxRate(expense.getTaxRate())
                .taxCalculationType(expense.getTaxCalculationType())
                .taxAmount(expense.getTaxAmount())
                .totalAmount(expense.getTotalAmount())
                .amountPaid(expense.getAmountPaid())
                .status(expense.getStatus().toString())
                .approvalRequestId(expense.getApprovalRequestId())
                .approvalRuleId(expense.getApprovalRuleId())
                .approvalRuleName(expense.getApprovalRuleName())
                .requiredApprovals(expense.getRequiredApprovals())
                .notes(expense.getNotes())
                .createdByUserId(expense.getCreatedByUser() != null ? expense.getCreatedByUser().getId() : null)
                .lineItems(expenseLineItemRepository.findByExpenseId(expense.getId()).stream()
                        .map(this::mapLineToDTO)
                        .collect(Collectors.toSet()))
                .createdAt(expense.getCreatedAt())
                .updatedAt(expense.getUpdatedAt())
                .build();
    }

    private void submit(Expense expense, String submittedBy) {
        if (!Expense.ExpenseStatus.DRAFT.equals(expense.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT expenses can be submitted");
        }
        List<ExpenseLineItem> lines = expenseLineItemRepository.findByExpenseId(expense.getId());
        ApprovalDecisionDTO decision = approvalWorkflowService.submitForApproval(ApprovalEvaluationRequest.builder()
                .organizationId(expense.getOrganization().getId())
                .moduleType(ModuleControl.ModuleType.ACCOUNTS_PAYABLE)
                .transactionType("EXPENSE")
                .entityType("Expense")
                .entityId(expense.getId())
                .amount(expense.getTotalAmount())
                .accountTypes(lines.stream()
                        .map(line -> line.getChartAccount().getAccountType())
                        .collect(Collectors.toSet()))
                .submittedBy(submittedBy)
                .build());
        expense.setStatus(Boolean.TRUE.equals(decision.getApprovalRequired())
                ? Expense.ExpenseStatus.SUBMITTED
                : Expense.ExpenseStatus.APPROVED);
        expense.setSubmittedBy(submittedBy);
        expense.setSubmittedDate(LocalDate.now());
        expense.setApprovalRequestId(decision.getApprovalRequestId());
        expense.setApprovalRuleId(decision.getApprovalRuleId());
        expense.setApprovalRuleName(decision.getApprovalRuleName());
        expense.setRequiredApprovals(decision.getRequiredApprovals());
        if (!Boolean.TRUE.equals(decision.getApprovalRequired())) {
            expense.setApprovedBy(submittedBy);
            expense.setApprovedDate(LocalDate.now());
        }
    }

    private void approve(Expense expense, String approvedBy) {
        if (!Expense.ExpenseStatus.SUBMITTED.equals(expense.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only SUBMITTED expenses can be approved");
        }
        if (expense.getApprovalRequestId() != null) {
            approvalWorkflowService.approvePending("Expense", expense.getId(), "Approved by " + approvedBy);
        }
        expense.setStatus(Expense.ExpenseStatus.APPROVED);
        expense.setApprovedBy(approvedBy);
        expense.setApprovedDate(LocalDate.now());
    }

    private void requireDirectPaymentAllowed(Long organizationId, Long expenseId, BigDecimal amount) {
        ApprovalDecisionDTO decision = approvalWorkflowService.evaluate(ApprovalEvaluationRequest.builder()
                .organizationId(organizationId)
                .moduleType(ModuleControl.ModuleType.PAYMENTS)
                .transactionType("EXPENSE_PAYMENT")
                .entityType("Expense")
                .entityId(expenseId)
                .amount(amount)
                .build());
        if (Boolean.TRUE.equals(decision.getApprovalRequired())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Expense payment requires approval; use a payment run");
        }
    }

    private void replaceLineItems(Expense expense, Set<ExpenseLineItemDTO> lineItemDtos) {
        expenseLineItemRepository.findByExpenseId(expense.getId()).forEach(expenseLineItemRepository::delete);
        if (lineItemDtos == null || lineItemDtos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expense must have at least one line item");
        }
        for (ExpenseLineItemDTO lineDto : lineItemDtos) {
            BigDecimal quantity = positive(lineDto.getQuantity(), "Line quantity must be positive");
            BigDecimal unitPrice = positive(lineDto.getUnitPrice(), "Line unit price must be positive");
            BigDecimal lineTotal = quantity.multiply(unitPrice);
            expenseLineItemRepository.save(ExpenseLineItem.builder()
                    .expense(expense)
                    .chartAccount(resolveLineAccount(lineDto.getChartAccountId(), expense.getOrganization().getId(), ChartOfAccounts.AccountType.EXPENSE))
                    .description(required(lineDto.getDescription(), "Line description is required"))
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .lineTotal(lineTotal)
                    .notes(lineDto.getNotes())
                    .build());
        }
    }

    private Totals calculateTotals(Long organizationId, Set<ExpenseLineItemDTO> lineItems, Long taxJurisdictionId) {
        if (lineItems == null || lineItems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expense must have at least one line item");
        }
        BigDecimal subtotal = lineItems.stream()
                .map(line -> positive(line.getQuantity(), "Line quantity must be positive")
                        .multiply(positive(line.getUnitPrice(), "Line unit price must be positive")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        TaxCalculationService.Result taxResult = taxCalculationService.calculate(organizationId, taxJurisdictionId, subtotal);
        return new Totals(subtotal, taxResult.taxAmount(), taxResult.totalAmount(), taxResult);
    }

    private ChartOfAccounts requirePostingAccount(Long organizationId, ChartOfAccounts.AccountType type, ChartOfAccounts.AccountSubtype subtype, String purpose) {
        return chartOfAccountsRepository.findByOrganizationIdAndAccountType(organizationId, type).stream()
                .filter(account -> Boolean.TRUE.equals(account.getActive()))
                .filter(account -> subtype.equals(account.getAccountSubtype()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "No active " + purpose + " account configured"));
    }

    private ChartOfAccounts resolveLineAccount(Long accountId, Long organizationId, ChartOfAccounts.AccountType expectedType) {
        if (accountId == null) {
            return null;
        }
        ChartOfAccounts account = chartOfAccountsRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Line account not found"));
        if (!account.getOrganization().getId().equals(organizationId)
                || !expectedType.equals(account.getAccountType())
                || Boolean.FALSE.equals(account.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Line account is not a valid active " + expectedType + " account");
        }
        return account;
    }

    private com.justjava.ams.accountant.entity.BankAccount findBankAccount(Long bankAccountId, Long organizationId) {
        if (bankAccountId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank account is required");
        }
        com.justjava.ams.accountant.entity.BankAccount bankAccount = bankAccountRepository.findById(bankAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bank account not found"));
        if (!bankAccount.getOrganization().getId().equals(organizationId) || Boolean.FALSE.equals(bankAccount.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank account does not belong to organization or is inactive");
        }
        return bankAccount;
    }

    private BigDecimal positive(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value;
    }

    private String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private ExpenseLineItemDTO mapLineToDTO(ExpenseLineItem line) {
        return ExpenseLineItemDTO.builder()
                .id(line.getId())
                .expenseId(line.getExpense().getId())
                .chartAccountId(line.getChartAccount() != null ? line.getChartAccount().getId() : null)
                .accountCode(line.getChartAccount() != null ? line.getChartAccount().getAccountCode() : null)
                .accountName(line.getChartAccount() != null ? line.getChartAccount().getAccountName() : null)
                .description(line.getDescription())
                .quantity(line.getQuantity())
                .unitPrice(line.getUnitPrice())
                .lineTotal(line.getLineTotal())
                .notes(line.getNotes())
                .build();
    }

    private record Totals(java.math.BigDecimal subtotal, java.math.BigDecimal taxAmount, java.math.BigDecimal totalAmount, TaxCalculationService.Result taxResult) {}
}
