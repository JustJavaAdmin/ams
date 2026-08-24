package com.justjava.ams.accountant.service;

import com.justjava.ams.accountant.dto.PurchaseInvoiceDTO;
import com.justjava.ams.accountant.dto.PurchaseLineItemDTO;
import com.justjava.ams.accountant.dto.PaymentRequest;
import com.justjava.ams.accountant.dto.AgingReportResponse;
import com.justjava.ams.accountant.dto.AgingReportRowDTO;
import com.justjava.ams.accountant.entity.BankAccount;
import com.justjava.ams.accountant.entity.ChartOfAccounts;
import com.justjava.ams.accountant.entity.GeneralLedger;
import com.justjava.ams.accountant.entity.PurchaseInvoice;
import com.justjava.ams.accountant.entity.PurchaseLineItem;
import com.justjava.ams.accountant.entity.Vendor;
import com.justjava.ams.accountant.repository.BankAccountRepository;
import com.justjava.ams.accountant.repository.ChartOfAccountsRepository;
import com.justjava.ams.accountant.repository.PurchaseInvoiceRepository;
import com.justjava.ams.accountant.repository.PurchaseLineItemRepository;
import com.justjava.ams.accountant.repository.VendorRepository;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.entity.User;
import com.justjava.ams.common.repository.OrganizationRepository;
import com.justjava.ams.common.repository.UserRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PurchaseInvoiceService {

    private final PurchaseInvoiceRepository purchaseInvoiceRepository;
    private final PurchaseLineItemRepository purchaseLineItemRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final VendorRepository vendorRepository;
    private final BankAccountRepository bankAccountRepository;
    private final GeneralLedgerService generalLedgerService;
    private final FiscalPeriodService fiscalPeriodService;
    private final TaxCalculationService taxCalculationService;
    private final BudgetControlService budgetControlService;
    private final PaymentScheduleService paymentScheduleService;
    private final ApprovalWorkflowService approvalWorkflowService;

    public PurchaseInvoiceDTO createPurchaseInvoice(Long organizationId, PurchaseInvoiceDTO dto, Long userId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        if (purchaseInvoiceRepository.findByOrganizationIdAndPurchaseOrderNumber(organizationId, dto.getPurchaseOrderNumber()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Purchase order number already exists for organization");
        }

        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;
        Totals totals = calculateTotals(organizationId, dto.getLineItems(), dto.getTaxJurisdictionId());
        Vendor vendor = findVendor(dto.getVendorId(), organizationId);

        PurchaseInvoice invoice = PurchaseInvoice.builder()
                .organization(organization)
                .vendor(vendor)
                .purchaseOrderNumber(dto.getPurchaseOrderNumber())
                .vendorName(vendor != null ? vendor.getLegalName() : required(dto.getVendorName(), "Vendor name is required"))
                .vendorEmail(vendor != null ? vendor.getEmail() : dto.getVendorEmail())
                .vendorPhone(vendor != null ? vendor.getPhone() : dto.getVendorPhone())
                .vendorAddress(vendor != null ? vendor.getBillingAddress() : dto.getVendorAddress())
                .purchaseDate(dto.getPurchaseDate())
                .dueDate(dto.getDueDate())
                .subtotal(totals.subtotal())
                .taxJurisdiction(totals.taxResult().jurisdiction())
                .taxCode(totals.taxResult().taxCode())
                .taxRate(totals.taxResult().taxRate())
                .taxCalculationType(totals.taxResult().taxCalculationType())
                .taxAmount(totals.taxAmount())
                .totalAmount(totals.totalAmount())
                .amountPaid(BigDecimal.ZERO)
                .status(PurchaseInvoice.PurchaseStatus.DRAFT)
                .notes(dto.getNotes())
                .createdByUser(user)
                .build();

        PurchaseInvoice saved = purchaseInvoiceRepository.save(invoice);
        replaceLineItems(saved, dto.getLineItems());
        return mapToDTO(saved);
    }

    public PurchaseInvoiceDTO getPurchaseInvoice(Long invoiceId) {
        PurchaseInvoice invoice = purchaseInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase invoice not found"));
        return mapToDTO(invoice);
    }

    public List<PurchaseInvoiceDTO> getPurchaseInvoicesByStatus(Long organizationId, String status) {
        return purchaseInvoiceRepository.findByOrganizationIdAndStatus(
                organizationId,
                PurchaseInvoice.PurchaseStatus.valueOf(status))
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public PurchaseInvoiceDTO updatePurchaseInvoiceStatus(Long invoiceId, String status) {
        PurchaseInvoice invoice = purchaseInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase invoice not found"));
        PurchaseInvoice.PurchaseStatus target = parseStatus(status);
        switch (target) {
            case SUBMITTED -> submit(invoice, "system");
            case APPROVED -> approve(invoice, "system");
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported purchase invoice status transition");
        }
        return mapToDTO(purchaseInvoiceRepository.save(invoice));
    }

    // Controller-friendly aliases and helper methods
    public PurchaseInvoiceDTO getPurchaseInvoiceById(Long invoiceId) {
        return getPurchaseInvoice(invoiceId);
    }

    public List<PurchaseInvoiceDTO> getPurchaseInvoicesByOrganization(Long organizationId) {
        return purchaseInvoiceRepository.findByOrganizationId(organizationId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<PurchaseInvoiceDTO> getAgedPayables(Long organizationId) {
        return purchaseInvoiceRepository.findByOrganizationId(organizationId)
                .stream()
                .filter(invoice -> !PurchaseInvoice.PurchaseStatus.PAID.equals(invoice.getStatus()))
                .filter(invoice -> !PurchaseInvoice.PurchaseStatus.CANCELLED.equals(invoice.getStatus()))
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public AgingReportResponse generateAgedPayables(Long organizationId, LocalDate asOfDate) {
        LocalDate reportDate = asOfDate != null ? asOfDate : LocalDate.now();
        List<AgingReportRowDTO> rows = purchaseInvoiceRepository.findByOrganizationId(organizationId)
                .stream()
                .filter(invoice -> !PurchaseInvoice.PurchaseStatus.PAID.equals(invoice.getStatus()))
                .filter(invoice -> !PurchaseInvoice.PurchaseStatus.CANCELLED.equals(invoice.getStatus()))
                .map(invoice -> agingRow(invoice, reportDate))
                .filter(row -> row.getOutstandingAmount().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());
        return agingResponse(organizationId, "AGED_PAYABLES", reportDate, rows);
    }

    public List<PurchaseInvoiceDTO> getPurchaseInvoicesByOrganizationAndStatus(Long organizationId, String status) {
        return getPurchaseInvoicesByStatus(organizationId, status);
    }

    public PurchaseInvoiceDTO updatePurchaseInvoice(Long invoiceId, PurchaseInvoiceDTO dto) {
        PurchaseInvoice invoice = purchaseInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase invoice not found"));
        if (!PurchaseInvoice.PurchaseStatus.DRAFT.equals(invoice.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT purchase invoices can be edited");
        }

        if (dto.getVendorId() != null) {
            Vendor vendor = findVendor(dto.getVendorId(), invoice.getOrganization().getId());
            invoice.setVendor(vendor);
            invoice.setVendorName(vendor.getLegalName());
            invoice.setVendorEmail(vendor.getEmail());
            invoice.setVendorPhone(vendor.getPhone());
            invoice.setVendorAddress(vendor.getBillingAddress());
        }
        if (dto.getPurchaseOrderNumber() != null) invoice.setPurchaseOrderNumber(dto.getPurchaseOrderNumber());
        if (dto.getVendorName() != null) invoice.setVendorName(dto.getVendorName());
        if (dto.getVendorEmail() != null) invoice.setVendorEmail(dto.getVendorEmail());
        if (dto.getVendorPhone() != null) invoice.setVendorPhone(dto.getVendorPhone());
        if (dto.getVendorAddress() != null) invoice.setVendorAddress(dto.getVendorAddress());
        if (dto.getPurchaseDate() != null) invoice.setPurchaseDate(dto.getPurchaseDate());
        if (dto.getDueDate() != null) invoice.setDueDate(dto.getDueDate());
        if (dto.getNotes() != null) invoice.setNotes(dto.getNotes());
        if (dto.getLineItems() != null) {
            Totals totals = calculateTotals(invoice.getOrganization().getId(), dto.getLineItems(), dto.getTaxJurisdictionId());
            invoice.setSubtotal(totals.subtotal());
            invoice.setTaxJurisdiction(totals.taxResult().jurisdiction());
            invoice.setTaxCode(totals.taxResult().taxCode());
            invoice.setTaxRate(totals.taxResult().taxRate());
            invoice.setTaxCalculationType(totals.taxResult().taxCalculationType());
            invoice.setTaxAmount(totals.taxAmount());
            invoice.setTotalAmount(totals.totalAmount());
            replaceLineItems(invoice, dto.getLineItems());
        }

        return mapToDTO(purchaseInvoiceRepository.save(invoice));
    }

    public PurchaseInvoiceDTO submitPurchaseInvoice(Long invoiceId) {
        PurchaseInvoice invoice = purchaseInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase invoice not found"));
        submit(invoice, "system");
        return mapToDTO(purchaseInvoiceRepository.save(invoice));
    }

    public PurchaseInvoiceDTO approvePurchaseInvoice(Long invoiceId, String approvedBy) {
        PurchaseInvoice invoice = purchaseInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase invoice not found"));
        approve(invoice, approvedBy);
        return mapToDTO(purchaseInvoiceRepository.save(invoice));
    }

    public PurchaseInvoiceDTO rejectPurchaseInvoice(Long invoiceId, String rejectionReason) {
        PurchaseInvoice invoice = purchaseInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase invoice not found"));
        if (!PurchaseInvoice.PurchaseStatus.SUBMITTED.equals(invoice.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only SUBMITTED purchase invoices can be rejected");
        }
        if (invoice.getApprovalRequestId() != null) {
            approvalWorkflowService.rejectPending("PurchaseInvoice", invoice.getId(), required(rejectionReason, "Rejection reason is required"));
        }
        invoice.setStatus(PurchaseInvoice.PurchaseStatus.REJECTED);
        return mapToDTO(purchaseInvoiceRepository.save(invoice));
    }

    public PurchaseInvoiceDTO postPurchaseInvoice(Long invoiceId, String postedBy) {
        PurchaseInvoice invoice = purchaseInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase invoice not found"));
        if (!PurchaseInvoice.PurchaseStatus.APPROVED.equals(invoice.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only APPROVED purchase invoices can be posted");
        }
        approvalWorkflowService.requireApproved("PurchaseInvoice", invoiceId);
        fiscalPeriodService.requireOpenPeriod(invoice.getOrganization().getId(), invoice.getPurchaseDate());

        List<PurchaseLineItem> lines = purchaseLineItemRepository.findByPurchaseInvoiceId(invoiceId);
        if (lines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Purchase invoice must have at least one line before posting");
        }
        ChartOfAccounts payable = requirePostingAccount(invoice.getOrganization().getId(), ChartOfAccounts.AccountType.LIABILITY, ChartOfAccounts.AccountSubtype.CURRENT_LIABILITY, "accounts payable");
        List<GeneralLedger> debits = new java.util.ArrayList<>();
        List<BudgetControlService.ExpenseBudgetLine> budgetLines = new java.util.ArrayList<>();
        for (PurchaseLineItem line : lines) {
            ChartOfAccounts lineAccount = line.getChartAccount() != null
                    ? line.getChartAccount()
                    : requirePostingAccount(invoice.getOrganization().getId(), ChartOfAccounts.AccountType.EXPENSE, ChartOfAccounts.AccountSubtype.OPERATING_EXPENSE, "expense");
            debits.add(GeneralLedger.builder()
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
        if (invoice.getTaxAmount() != null && invoice.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
            ChartOfAccounts taxRecoverable = requirePostingAccount(invoice.getOrganization().getId(), ChartOfAccounts.AccountType.ASSET, ChartOfAccounts.AccountSubtype.CURRENT_ASSET, "tax recoverable");
            debits.add(GeneralLedger.builder()
                    .account(taxRecoverable)
                    .amount(invoice.getTaxAmount())
                    .description("Tax on purchase invoice " + invoice.getPurchaseOrderNumber())
                    .build());
        }
        generalLedgerService.postPurchaseInvoice(
                invoice.getId(),
                invoice.getPurchaseOrderNumber(),
                invoice.getPurchaseDate(),
                payable,
                debits,
                "Purchase invoice " + invoice.getPurchaseOrderNumber(),
                postedBy,
                budgetLines);
        invoice.setStatus(PurchaseInvoice.PurchaseStatus.POSTED);
        invoice.setPostedBy(postedBy);
        invoice.setPostedDate(LocalDate.now());
        PurchaseInvoice saved = purchaseInvoiceRepository.save(invoice);
        paymentScheduleService.createDefaultScheduleForInvoice(saved.getId(), postedBy);
        return mapToDTO(saved);
    }

    public PurchaseInvoiceDTO recordPayment(Long invoiceId, PaymentRequest request, String paidBy) {
        PurchaseInvoice invoice = purchaseInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase invoice not found"));
        if (!(PurchaseInvoice.PurchaseStatus.POSTED.equals(invoice.getStatus())
                || PurchaseInvoice.PurchaseStatus.PARTIALLY_PAID.equals(invoice.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only POSTED purchase invoices can be paid");
        }
        BigDecimal amount = positive(request.getAmount(), "Payment amount must be positive");
        BigDecimal paid = invoice.getAmountPaid() != null ? invoice.getAmountPaid() : BigDecimal.ZERO;
        BigDecimal balance = invoice.getTotalAmount().subtract(paid);
        if (amount.compareTo(balance) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment exceeds purchase invoice balance");
        }
        requireDirectPaymentAllowed(invoice.getOrganization().getId(), invoice.getId(), amount);
        LocalDate paymentDate = request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now();
        fiscalPeriodService.requireOpenPeriod(invoice.getOrganization().getId(), paymentDate);
        BankAccount bankAccount = findBankAccount(request.getBankAccountId(), invoice.getOrganization().getId());
        ChartOfAccounts payable = requirePostingAccount(invoice.getOrganization().getId(), ChartOfAccounts.AccountType.LIABILITY, ChartOfAccounts.AccountSubtype.CURRENT_LIABILITY, "accounts payable");
        generalLedgerService.postSupplierPayment(
                invoice.getId(),
                invoice.getPurchaseOrderNumber(),
                paymentDate,
                payable,
                bankAccount.getChartAccount(),
                amount,
                request.getNotes() != null ? request.getNotes() : "Supplier payment " + invoice.getPurchaseOrderNumber(),
                paidBy);
        BigDecimal newPaid = paid.add(amount);
        invoice.setAmountPaid(newPaid);
        invoice.setStatus(newPaid.compareTo(invoice.getTotalAmount()) >= 0
                ? PurchaseInvoice.PurchaseStatus.PAID
                : PurchaseInvoice.PurchaseStatus.PARTIALLY_PAID);
        PurchaseInvoice saved = purchaseInvoiceRepository.save(invoice);
        paymentScheduleService.applyInvoicePayment(saved, amount);
        return mapToDTO(saved);
    }

    private PurchaseInvoiceDTO mapToDTO(PurchaseInvoice invoice) {
        return PurchaseInvoiceDTO.builder()
                .id(invoice.getId())
                .organizationId(invoice.getOrganization().getId())
                .vendorId(invoice.getVendor() != null ? invoice.getVendor().getId() : null)
                .purchaseOrderNumber(invoice.getPurchaseOrderNumber())
                .vendorName(invoice.getVendorName())
                .vendorEmail(invoice.getVendorEmail())
                .vendorPhone(invoice.getVendorPhone())
                .vendorAddress(invoice.getVendorAddress())
                .purchaseDate(invoice.getPurchaseDate())
                .dueDate(invoice.getDueDate())
                .subtotal(invoice.getSubtotal())
                .taxJurisdictionId(invoice.getTaxJurisdiction() != null ? invoice.getTaxJurisdiction().getId() : null)
                .taxCode(invoice.getTaxCode())
                .taxRate(invoice.getTaxRate())
                .taxCalculationType(invoice.getTaxCalculationType())
                .taxAmount(invoice.getTaxAmount())
                .totalAmount(invoice.getTotalAmount())
                .amountPaid(invoice.getAmountPaid())
                .status(invoice.getStatus().toString())
                .approvalRequestId(invoice.getApprovalRequestId())
                .approvalRuleId(invoice.getApprovalRuleId())
                .approvalRuleName(invoice.getApprovalRuleName())
                .requiredApprovals(invoice.getRequiredApprovals())
                .notes(invoice.getNotes())
                .createdByUserId(invoice.getCreatedByUser() != null ? invoice.getCreatedByUser().getId() : null)
                .lineItems(purchaseLineItemRepository.findByPurchaseInvoiceId(invoice.getId()).stream()
                        .map(this::mapLineToDTO)
                        .collect(Collectors.toSet()))
                .createdAt(invoice.getCreatedAt())
                .updatedAt(invoice.getUpdatedAt())
                .build();
    }

    private void submit(PurchaseInvoice invoice, String submittedBy) {
        if (!PurchaseInvoice.PurchaseStatus.DRAFT.equals(invoice.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT purchase invoices can be submitted");
        }
        List<PurchaseLineItem> lines = purchaseLineItemRepository.findByPurchaseInvoiceId(invoice.getId());
        ApprovalDecisionDTO decision = approvalWorkflowService.submitForApproval(ApprovalEvaluationRequest.builder()
                .organizationId(invoice.getOrganization().getId())
                .moduleType(ModuleControl.ModuleType.ACCOUNTS_PAYABLE)
                .transactionType("PURCHASE_INVOICE")
                .entityType("PurchaseInvoice")
                .entityId(invoice.getId())
                .amount(invoice.getTotalAmount())
                .accountTypes(lines.stream()
                        .map(line -> line.getChartAccount().getAccountType())
                        .collect(Collectors.toSet()))
                .submittedBy(submittedBy)
                .build());
        invoice.setStatus(Boolean.TRUE.equals(decision.getApprovalRequired())
                ? PurchaseInvoice.PurchaseStatus.SUBMITTED
                : PurchaseInvoice.PurchaseStatus.APPROVED);
        invoice.setSubmittedBy(submittedBy);
        invoice.setSubmittedDate(LocalDate.now());
        invoice.setApprovalRequestId(decision.getApprovalRequestId());
        invoice.setApprovalRuleId(decision.getApprovalRuleId());
        invoice.setApprovalRuleName(decision.getApprovalRuleName());
        invoice.setRequiredApprovals(decision.getRequiredApprovals());
        if (!Boolean.TRUE.equals(decision.getApprovalRequired())) {
            invoice.setApprovedBy(submittedBy);
            invoice.setApprovedDate(LocalDate.now());
        }
    }

    private void approve(PurchaseInvoice invoice, String approvedBy) {
        if (!PurchaseInvoice.PurchaseStatus.SUBMITTED.equals(invoice.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only SUBMITTED purchase invoices can be approved");
        }
        if (invoice.getApprovalRequestId() != null) {
            approvalWorkflowService.approvePending("PurchaseInvoice", invoice.getId(), "Approved by " + approvedBy);
        }
        invoice.setStatus(PurchaseInvoice.PurchaseStatus.APPROVED);
        invoice.setApprovedBy(approvedBy);
        invoice.setApprovedDate(LocalDate.now());
    }

    private void requireDirectPaymentAllowed(Long organizationId, Long invoiceId, BigDecimal amount) {
        ApprovalDecisionDTO decision = approvalWorkflowService.evaluate(ApprovalEvaluationRequest.builder()
                .organizationId(organizationId)
                .moduleType(ModuleControl.ModuleType.PAYMENTS)
                .transactionType("SUPPLIER_PAYMENT")
                .entityType("PurchaseInvoice")
                .entityId(invoiceId)
                .amount(amount)
                .build());
        if (Boolean.TRUE.equals(decision.getApprovalRequired())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Supplier payment requires approval; use a payment run");
        }
    }

    private void replaceLineItems(PurchaseInvoice invoice, Set<PurchaseLineItemDTO> lineItemDtos) {
        purchaseLineItemRepository.findByPurchaseInvoiceId(invoice.getId()).forEach(purchaseLineItemRepository::delete);
        if (lineItemDtos == null || lineItemDtos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Purchase invoice must have at least one line item");
        }
        for (PurchaseLineItemDTO lineDto : lineItemDtos) {
            BigDecimal quantity = positive(lineDto.getQuantity(), "Line quantity must be positive");
            BigDecimal unitPrice = positive(lineDto.getUnitPrice(), "Line unit price must be positive");
            BigDecimal lineTotal = quantity.multiply(unitPrice);
            purchaseLineItemRepository.save(PurchaseLineItem.builder()
                    .purchaseInvoice(invoice)
                    .chartAccount(resolveLineAccount(lineDto.getChartAccountId(), invoice.getOrganization().getId(), ChartOfAccounts.AccountType.EXPENSE))
                    .description(required(lineDto.getDescription(), "Line description is required"))
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .lineTotal(lineTotal)
                    .notes(lineDto.getNotes())
                    .build());
        }
    }

    private Totals calculateTotals(Long organizationId, Set<PurchaseLineItemDTO> lineItems, Long taxJurisdictionId) {
        if (lineItems == null || lineItems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Purchase invoice must have at least one line item");
        }
        BigDecimal subtotal = lineItems.stream()
                .map(line -> positive(line.getQuantity(), "Line quantity must be positive")
                        .multiply(positive(line.getUnitPrice(), "Line unit price must be positive")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        TaxCalculationService.Result taxResult = taxCalculationService.calculate(organizationId, taxJurisdictionId, subtotal);
        return new Totals(subtotal, taxResult.taxAmount(), taxResult.totalAmount(), taxResult);
    }

    private PurchaseInvoice.PurchaseStatus parseStatus(String status) {
        try {
            return PurchaseInvoice.PurchaseStatus.valueOf(required(status, "Status is required").toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported purchase invoice status");
        }
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

    private Vendor findVendor(Long vendorId, Long organizationId) {
        if (vendorId == null) {
            return null;
        }
        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found"));
        if (!vendor.getOrganization().getId().equals(organizationId) || Boolean.FALSE.equals(vendor.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vendor does not belong to organization or is inactive");
        }
        return vendor;
    }

    private BankAccount findBankAccount(Long bankAccountId, Long organizationId) {
        if (bankAccountId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank account is required");
        }
        BankAccount bankAccount = bankAccountRepository.findById(bankAccountId)
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

    private PurchaseLineItemDTO mapLineToDTO(PurchaseLineItem line) {
        return PurchaseLineItemDTO.builder()
                .id(line.getId())
                .purchaseInvoiceId(line.getPurchaseInvoice().getId())
                .chartAccountId(line.getChartAccount() != null ? line.getChartAccount().getId() : null)
                .accountCode(line.getChartAccount() != null ? line.getChartAccount().getAccountCode() : null)
                .accountName(line.getChartAccount() != null ? line.getChartAccount().getAccountName() : null)
                .description(line.getDescription())
                .quantity(line.getQuantity())
                .unitPrice(line.getUnitPrice())
                .lineTotal(line.getLineTotal())
                .notes(line.getNotes())
                .createdAt(line.getCreatedAt())
                .build();
    }

    private AgingReportRowDTO agingRow(PurchaseInvoice invoice, LocalDate asOfDate) {
        BigDecimal paid = invoice.getAmountPaid() != null ? invoice.getAmountPaid() : BigDecimal.ZERO;
        BigDecimal outstanding = invoice.getTotalAmount().subtract(paid);
        long daysOverdue = Math.max(0, ChronoUnit.DAYS.between(invoice.getDueDate(), asOfDate));
        return AgingReportRowDTO.builder()
                .documentId(invoice.getId())
                .partyName(invoice.getVendorName())
                .documentNumber(invoice.getPurchaseOrderNumber())
                .documentDate(invoice.getPurchaseDate())
                .dueDate(invoice.getDueDate())
                .originalAmount(invoice.getTotalAmount())
                .paidAmount(paid)
                .outstandingAmount(outstanding)
                .daysOverdue(daysOverdue)
                .bucket(bucket(daysOverdue))
                .status(invoice.getStatus().name())
                .build();
    }

    private AgingReportResponse agingResponse(Long organizationId, String reportType, LocalDate asOfDate, List<AgingReportRowDTO> rows) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        List.of("CURRENT", "1-30", "31-60", "61-90", "90+").forEach(bucket -> totals.put(bucket, BigDecimal.ZERO));
        rows.forEach(row -> totals.compute(row.getBucket(), (key, value) -> (value != null ? value : BigDecimal.ZERO).add(row.getOutstandingAmount())));
        BigDecimal grandTotal = rows.stream().map(AgingReportRowDTO::getOutstandingAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return AgingReportResponse.builder()
                .organizationId(organizationId)
                .reportType(reportType)
                .asOfDate(asOfDate)
                .rows(rows)
                .bucketTotals(totals)
                .grandTotal(grandTotal)
                .build();
    }

    private String bucket(long daysOverdue) {
        if (daysOverdue <= 0) return "CURRENT";
        if (daysOverdue <= 30) return "1-30";
        if (daysOverdue <= 60) return "31-60";
        if (daysOverdue <= 90) return "61-90";
        return "90+";
    }

    private record Totals(BigDecimal subtotal, BigDecimal taxAmount, BigDecimal totalAmount, TaxCalculationService.Result taxResult) {}
}

