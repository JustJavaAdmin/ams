package com.justjava.ams.accountant.service;

import com.justjava.ams.accountant.dto.CustomerInvoiceDTO;
import com.justjava.ams.accountant.dto.InvoiceLineItemDTO;
import com.justjava.ams.accountant.dto.PaymentRequest;
import com.justjava.ams.accountant.dto.AgingReportResponse;
import com.justjava.ams.accountant.dto.AgingReportRowDTO;
import com.justjava.ams.accountant.entity.BankAccount;
import com.justjava.ams.accountant.entity.ChartOfAccounts;
import com.justjava.ams.accountant.entity.Customer;
import com.justjava.ams.accountant.entity.CustomerInvoice;
import com.justjava.ams.accountant.entity.GeneralLedger;
import com.justjava.ams.accountant.entity.InvoiceLineItem;
import com.justjava.ams.accountant.repository.BankAccountRepository;
import com.justjava.ams.accountant.repository.ChartOfAccountsRepository;
import com.justjava.ams.accountant.repository.CustomerRepository;
import com.justjava.ams.accountant.repository.CustomerInvoiceRepository;
import com.justjava.ams.accountant.repository.InvoiceLineItemRepository;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.entity.User;
import com.justjava.ams.common.repository.OrganizationRepository;
import com.justjava.ams.common.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
public class CustomerInvoiceService {

    private final CustomerInvoiceRepository customerInvoiceRepository;
    private final InvoiceLineItemRepository invoiceLineItemRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final CustomerRepository customerRepository;
    private final BankAccountRepository bankAccountRepository;
    private final GeneralLedgerService generalLedgerService;
    private final FiscalPeriodService fiscalPeriodService;
    private final TaxCalculationService taxCalculationService;
    private final ReceivablesCollectionService receivablesCollectionService;

    public CustomerInvoiceDTO createInvoice(Long organizationId, CustomerInvoiceDTO dto, Long userId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        if (customerInvoiceRepository.findByOrganizationIdAndInvoiceNumber(organizationId, dto.getInvoiceNumber()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invoice number already exists for organization");
        }

        User user = userId != null ? userRepository.findById(userId).orElse(null) : null;
        Totals totals = calculateTotals(organizationId, dto.getLineItems(), dto.getTaxJurisdictionId());
        Customer customer = findCustomer(dto.getCustomerId(), organizationId);

        CustomerInvoice invoice = CustomerInvoice.builder()
                .organization(organization)
                .customer(customer)
                .invoiceNumber(dto.getInvoiceNumber())
                .customerName(customer != null ? customer.getLegalName() : required(dto.getCustomerName(), "Customer name is required"))
                .customerEmail(customer != null ? customer.getEmail() : dto.getCustomerEmail())
                .customerPhone(customer != null ? customer.getPhone() : dto.getCustomerPhone())
                .customerAddress(customer != null ? customer.getBillingAddress() : dto.getCustomerAddress())
                .invoiceDate(dto.getInvoiceDate())
                .dueDate(dto.getDueDate())
                .subtotal(totals.subtotal())
                .taxJurisdiction(totals.taxResult().jurisdiction())
                .taxCode(totals.taxResult().taxCode())
                .taxRate(totals.taxResult().taxRate())
                .taxCalculationType(totals.taxResult().taxCalculationType())
                .taxAmount(totals.taxAmount())
                .totalAmount(totals.totalAmount())
                .amountPaid(BigDecimal.ZERO)
                .status(CustomerInvoice.InvoiceStatus.DRAFT)
                .notes(dto.getNotes())
                .createdByUser(user)
                .build();

        CustomerInvoice saved = customerInvoiceRepository.save(invoice);
        replaceLineItems(saved, dto.getLineItems());
        return mapToDTO(saved);
    }

    public CustomerInvoiceDTO getInvoice(Long invoiceId) {
        CustomerInvoice invoice = customerInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        return mapToDTO(invoice);
    }

    public List<CustomerInvoiceDTO> getInvoicesByStatus(Long organizationId, String status) {
        return customerInvoiceRepository.findByOrganizationIdAndStatus(
                organizationId,
                CustomerInvoice.InvoiceStatus.valueOf(status))
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public CustomerInvoiceDTO updateInvoiceStatus(Long invoiceId, String status) {
        CustomerInvoice invoice = customerInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        CustomerInvoice.InvoiceStatus target = parseStatus(status);
        if (target == CustomerInvoice.InvoiceStatus.APPROVED || target == CustomerInvoice.InvoiceStatus.SENT) {
            requireStatus(invoice, CustomerInvoice.InvoiceStatus.DRAFT);
            invoice.setStatus(target);
            return mapToDTO(customerInvoiceRepository.save(invoice));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported invoice status transition");
    }

    public void deleteInvoice(Long invoiceId) {
        CustomerInvoice invoice = customerInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        invoice.setStatus(CustomerInvoice.InvoiceStatus.CANCELLED);
        customerInvoiceRepository.save(invoice);
    }

    private CustomerInvoiceDTO mapToDTO(CustomerInvoice invoice) {
        return CustomerInvoiceDTO.builder()
                .id(invoice.getId())
                .organizationId(invoice.getOrganization().getId())
                .customerId(invoice.getCustomer() != null ? invoice.getCustomer().getId() : null)
                .invoiceNumber(invoice.getInvoiceNumber())
                .customerName(invoice.getCustomerName())
                .customerEmail(invoice.getCustomerEmail())
                .customerPhone(invoice.getCustomerPhone())
                .customerAddress(invoice.getCustomerAddress())
                .invoiceDate(invoice.getInvoiceDate())
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
                .notes(invoice.getNotes())
                .createdByUserId(invoice.getCreatedByUser() != null ? invoice.getCreatedByUser().getId() : null)
                .lineItems(invoiceLineItemRepository.findByInvoiceId(invoice.getId()).stream()
                        .map(this::mapLineToDTO)
                        .collect(Collectors.toSet()))
                .createdAt(invoice.getCreatedAt())
                .updatedAt(invoice.getUpdatedAt())
                .build();
    }

    // Controller-friendly aliases and helpers
    public CustomerInvoiceDTO createCustomerInvoice(Long organizationId, CustomerInvoiceDTO dto) {
        Long userId = resolveCurrentUserId();
        return createInvoice(organizationId, dto, userId);
    }

    public CustomerInvoiceDTO getCustomerInvoiceById(Long invoiceId) {
        return getInvoice(invoiceId);
    }

    public List<CustomerInvoiceDTO> getCustomerInvoicesByOrganization(Long organizationId) {
        return customerInvoiceRepository.findByOrganizationId(organizationId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<CustomerInvoiceDTO> getInvoicesByOrganization(Long organizationId) {
        return getCustomerInvoicesByOrganization(organizationId);
    }

    public List<CustomerInvoiceDTO> getAgedReceivables(Long organizationId) {
        return customerInvoiceRepository.findByOrganizationId(organizationId)
                .stream()
                .filter(invoice -> !CustomerInvoice.InvoiceStatus.PAID.equals(invoice.getStatus()))
                .filter(invoice -> !CustomerInvoice.InvoiceStatus.CANCELLED.equals(invoice.getStatus()))
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public AgingReportResponse generateAgedReceivables(Long organizationId, LocalDate asOfDate) {
        LocalDate reportDate = asOfDate != null ? asOfDate : LocalDate.now();
        List<AgingReportRowDTO> rows = customerInvoiceRepository.findByOrganizationId(organizationId)
                .stream()
                .filter(invoice -> !CustomerInvoice.InvoiceStatus.PAID.equals(invoice.getStatus()))
                .filter(invoice -> !CustomerInvoice.InvoiceStatus.CANCELLED.equals(invoice.getStatus()))
                .map(invoice -> agingRow(invoice, reportDate))
                .filter(row -> row.getOutstandingAmount().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());
        return agingResponse(organizationId, "AGED_RECEIVABLES", reportDate, rows);
    }

    public List<CustomerInvoiceDTO> getCustomerInvoicesByOrganizationAndStatus(Long organizationId, String status) {
        return getInvoicesByStatus(organizationId, status);
    }

    public List<CustomerInvoiceDTO> getInvoicesByOrganizationAndStatus(Long organizationId, String status) {
        return getCustomerInvoicesByOrganizationAndStatus(organizationId, status);
    }

    public CustomerInvoiceDTO updateCustomerInvoice(Long invoiceId, CustomerInvoiceDTO dto) {
        CustomerInvoice invoice = customerInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        if (!CustomerInvoice.InvoiceStatus.DRAFT.equals(invoice.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT invoices can be edited");
        }

        if (dto.getCustomerId() != null) {
            Customer customer = findCustomer(dto.getCustomerId(), invoice.getOrganization().getId());
            invoice.setCustomer(customer);
            invoice.setCustomerName(customer.getLegalName());
            invoice.setCustomerEmail(customer.getEmail());
            invoice.setCustomerPhone(customer.getPhone());
            invoice.setCustomerAddress(customer.getBillingAddress());
        }
        if (dto.getInvoiceNumber() != null) invoice.setInvoiceNumber(dto.getInvoiceNumber());
        if (dto.getCustomerName() != null) invoice.setCustomerName(dto.getCustomerName());
        if (dto.getCustomerEmail() != null) invoice.setCustomerEmail(dto.getCustomerEmail());
        if (dto.getCustomerPhone() != null) invoice.setCustomerPhone(dto.getCustomerPhone());
        if (dto.getCustomerAddress() != null) invoice.setCustomerAddress(dto.getCustomerAddress());
        if (dto.getInvoiceDate() != null) invoice.setInvoiceDate(dto.getInvoiceDate());
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

        return mapToDTO(customerInvoiceRepository.save(invoice));
    }

    public CustomerInvoiceDTO updateInvoice(Long invoiceId, CustomerInvoiceDTO dto) {
        return updateCustomerInvoice(invoiceId, dto);
    }

    public CustomerInvoiceDTO generateInvoice(Long invoiceId) {
        CustomerInvoice invoice = customerInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        requireStatus(invoice, CustomerInvoice.InvoiceStatus.DRAFT);
        requireCustomerNotOnCreditHold(invoice);
        invoice.setStatus(CustomerInvoice.InvoiceStatus.SENT);
        return mapToDTO(customerInvoiceRepository.save(invoice));
    }

    public CustomerInvoiceDTO postInvoice(Long invoiceId) {
        return postInvoice(invoiceId, currentUserName());
    }

    public CustomerInvoiceDTO postInvoice(Long invoiceId, String postedBy) {
        CustomerInvoice invoice = customerInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        if (!(CustomerInvoice.InvoiceStatus.APPROVED.equals(invoice.getStatus())
                || CustomerInvoice.InvoiceStatus.SENT.equals(invoice.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only APPROVED or SENT invoices can be posted");
        }
        requireCustomerNotOnCreditHold(invoice);
        fiscalPeriodService.requireOpenPeriod(invoice.getOrganization().getId(), invoice.getInvoiceDate());

        List<InvoiceLineItem> lines = invoiceLineItemRepository.findByInvoiceId(invoiceId);
        if (lines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice must have at least one line before posting");
        }
        ChartOfAccounts receivable = requirePostingAccount(invoice.getOrganization().getId(), ChartOfAccounts.AccountType.ASSET, ChartOfAccounts.AccountSubtype.CURRENT_ASSET, "receivable");
        List<GeneralLedger> credits = new java.util.ArrayList<>();
        for (InvoiceLineItem line : lines) {
            credits.add(GeneralLedger.builder()
                    .account(line.getChartAccount() != null
                            ? line.getChartAccount()
                            : requirePostingAccount(invoice.getOrganization().getId(), ChartOfAccounts.AccountType.REVENUE, ChartOfAccounts.AccountSubtype.REVENUE, "revenue"))
                    .amount(line.getLineTotal())
                    .description(line.getDescription())
                    .notes(line.getNotes())
                    .build());
        }
        if (invoice.getTaxAmount() != null && invoice.getTaxAmount().compareTo(BigDecimal.ZERO) > 0) {
            ChartOfAccounts taxPayable = requirePostingAccount(invoice.getOrganization().getId(), ChartOfAccounts.AccountType.LIABILITY, ChartOfAccounts.AccountSubtype.CURRENT_LIABILITY, "tax payable");
            credits.add(GeneralLedger.builder()
                    .account(taxPayable)
                    .amount(invoice.getTaxAmount())
                    .description("Tax on invoice " + invoice.getInvoiceNumber())
                    .build());
        }
        generalLedgerService.postCustomerInvoice(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getInvoiceDate(),
                receivable,
                credits,
                "Customer invoice " + invoice.getInvoiceNumber(),
                postedBy);
        invoice.setStatus(CustomerInvoice.InvoiceStatus.POSTED);
        invoice.setPostedBy(postedBy);
        invoice.setPostedDate(LocalDate.now());
        return mapToDTO(customerInvoiceRepository.save(invoice));
    }

    public CustomerInvoiceDTO recordPayment(Long invoiceId, PaymentRequest request, String receivedBy) {
        CustomerInvoice invoice = customerInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        if (!(CustomerInvoice.InvoiceStatus.POSTED.equals(invoice.getStatus())
                || CustomerInvoice.InvoiceStatus.PARTIALLY_PAID.equals(invoice.getStatus()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only POSTED invoices can receive payments");
        }
        BigDecimal amount = positive(request.getAmount(), "Payment amount must be positive");
        BigDecimal paid = invoice.getAmountPaid() != null ? invoice.getAmountPaid() : BigDecimal.ZERO;
        BigDecimal balance = invoice.getTotalAmount().subtract(paid);
        if (amount.compareTo(balance) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment exceeds invoice balance");
        }
        LocalDate paymentDate = request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now();
        fiscalPeriodService.requireOpenPeriod(invoice.getOrganization().getId(), paymentDate);
        BankAccount bankAccount = findBankAccount(request.getBankAccountId(), invoice.getOrganization().getId());
        ChartOfAccounts receivable = requirePostingAccount(invoice.getOrganization().getId(), ChartOfAccounts.AccountType.ASSET, ChartOfAccounts.AccountSubtype.CURRENT_ASSET, "receivable");
        generalLedgerService.postCustomerPayment(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                paymentDate,
                bankAccount.getChartAccount(),
                receivable,
                amount,
                request.getNotes() != null ? request.getNotes() : "Customer payment " + invoice.getInvoiceNumber(),
                receivedBy);
        BigDecimal newPaid = paid.add(amount);
        invoice.setAmountPaid(newPaid);
        invoice.setStatus(newPaid.compareTo(invoice.getTotalAmount()) >= 0
                ? CustomerInvoice.InvoiceStatus.PAID
                : CustomerInvoice.InvoiceStatus.PARTIALLY_PAID);
        CustomerInvoice saved = customerInvoiceRepository.save(invoice);
        receivablesCollectionService.updateAfterPayment(saved, amount, receivedBy);
        return mapToDTO(saved);
    }

    private Long resolveCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                return userRepository.findByUsername(auth.getName())
                        .map(u -> u.getId())
                        .orElse(null);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void replaceLineItems(CustomerInvoice invoice, Set<InvoiceLineItemDTO> lineItemDtos) {
        invoiceLineItemRepository.findByInvoiceId(invoice.getId()).forEach(invoiceLineItemRepository::delete);
        if (lineItemDtos == null || lineItemDtos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice must have at least one line item");
        }
        for (InvoiceLineItemDTO lineDto : lineItemDtos) {
            BigDecimal quantity = positive(lineDto.getQuantity(), "Line quantity must be positive");
            BigDecimal unitPrice = positive(lineDto.getUnitPrice(), "Line unit price must be positive");
            BigDecimal lineTotal = quantity.multiply(unitPrice);
            invoiceLineItemRepository.save(InvoiceLineItem.builder()
                    .invoice(invoice)
                    .chartAccount(resolveLineAccount(lineDto.getChartAccountId(), invoice.getOrganization().getId(), ChartOfAccounts.AccountType.REVENUE))
                    .description(required(lineDto.getDescription(), "Line description is required"))
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .lineTotal(lineTotal)
                    .notes(lineDto.getNotes())
                    .build());
        }
    }

    private Totals calculateTotals(Long organizationId, Set<InvoiceLineItemDTO> lineItems, Long taxJurisdictionId) {
        if (lineItems == null || lineItems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice must have at least one line item");
        }
        BigDecimal subtotal = lineItems.stream()
                .map(line -> positive(line.getQuantity(), "Line quantity must be positive")
                        .multiply(positive(line.getUnitPrice(), "Line unit price must be positive")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        TaxCalculationService.Result taxResult = taxCalculationService.calculate(organizationId, taxJurisdictionId, subtotal);
        return new Totals(subtotal, taxResult.taxAmount(), taxResult.totalAmount(), taxResult);
    }

    private CustomerInvoice.InvoiceStatus parseStatus(String status) {
        try {
            return CustomerInvoice.InvoiceStatus.valueOf(required(status, "Status is required").toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported invoice status");
        }
    }

    private void requireStatus(CustomerInvoice invoice, CustomerInvoice.InvoiceStatus requiredStatus) {
        if (!requiredStatus.equals(invoice.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invoice must be " + requiredStatus);
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

    private Customer findCustomer(Long customerId, Long organizationId) {
        if (customerId == null) {
            return null;
        }
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        if (!customer.getOrganization().getId().equals(organizationId) || Boolean.FALSE.equals(customer.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer does not belong to organization or is inactive");
        }
        return customer;
    }

    private void requireCustomerNotOnCreditHold(CustomerInvoice invoice) {
        Customer customer = invoice.getCustomer();
        if (customer != null && Boolean.TRUE.equals(customer.getCreditHold())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Customer is on credit hold" + (customer.getCreditHoldReason() != null ? ": " + customer.getCreditHoldReason() : ""));
        }
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

    private String currentUserName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    private InvoiceLineItemDTO mapLineToDTO(InvoiceLineItem line) {
        return InvoiceLineItemDTO.builder()
                .id(line.getId())
                .invoiceId(line.getInvoice().getId())
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

    private AgingReportRowDTO agingRow(CustomerInvoice invoice, LocalDate asOfDate) {
        BigDecimal paid = invoice.getAmountPaid() != null ? invoice.getAmountPaid() : BigDecimal.ZERO;
        BigDecimal outstanding = invoice.getTotalAmount().subtract(paid);
        long daysOverdue = Math.max(0, ChronoUnit.DAYS.between(invoice.getDueDate(), asOfDate));
        return AgingReportRowDTO.builder()
                .documentId(invoice.getId())
                .partyName(invoice.getCustomerName())
                .documentNumber(invoice.getInvoiceNumber())
                .documentDate(invoice.getInvoiceDate())
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
