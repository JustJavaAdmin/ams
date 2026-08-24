package com.justjava.ams.accountant.service;

import com.justjava.ams.accountant.dto.*;
import com.justjava.ams.accountant.entity.*;
import com.justjava.ams.accountant.repository.*;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ReceivablesCollectionService {

    private final OrganizationRepository organizationRepository;
    private final CustomerRepository customerRepository;
    private final CustomerInvoiceRepository customerInvoiceRepository;
    private final ReceivablesCollectionCaseRepository collectionCaseRepository;
    private final CollectionActivityRepository activityRepository;
    private final PromiseToPayRepository promiseRepository;
    private final CustomerStatementRepository customerStatementRepository;
    private final CustomerStatementLineRepository customerStatementLineRepository;

    public List<ReceivablesCollectionCaseDTO> generateCases(Long organizationId, LocalDate asOfDate) {
        Organization organization = findOrganization(organizationId);
        LocalDate date = asOfDate != null ? asOfDate : LocalDate.now();
        customerInvoiceRepository.findByOrganizationId(organizationId).stream()
                .filter(invoice -> !CustomerInvoice.InvoiceStatus.PAID.equals(invoice.getStatus()))
                .filter(invoice -> !CustomerInvoice.InvoiceStatus.CANCELLED.equals(invoice.getStatus()))
                .filter(invoice -> outstanding(invoice).compareTo(BigDecimal.ZERO) > 0)
                .filter(invoice -> invoice.getDueDate() != null && invoice.getDueDate().isBefore(date))
                .forEach(invoice -> upsertCase(organization, invoice, date));
        reconcilePromises(organizationId, date);
        return getCases(organizationId);
    }

    @Transactional(readOnly = true)
    public List<ReceivablesCollectionCaseDTO> getCases(Long organizationId) {
        return collectionCaseRepository.findByOrganizationIdOrderByDaysOverdueDescIdDesc(organizationId)
                .stream()
                .map(this::mapCaseToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReceivablesCollectionCaseDTO getCase(Long caseId) {
        return mapCaseToDTO(findCase(caseId));
    }

    public ReceivablesCollectionCaseDTO assignCase(Long caseId, CollectionCaseActionRequest request, String assignedBy) {
        ReceivablesCollectionCase collectionCase = findCase(caseId);
        String collector = required(request.getCollectorUsername(), "Collector username is required");
        collectionCase.setCollectorUsername(collector);
        collectionCase.setAssignedAt(LocalDateTime.now());
        if (ReceivablesCollectionCase.CaseStatus.OPEN.equals(collectionCase.getStatus())) {
            collectionCase.setStatus(ReceivablesCollectionCase.CaseStatus.IN_PROGRESS);
        }
        ReceivablesCollectionCase saved = collectionCaseRepository.save(collectionCase);
        activity(saved, CollectionActivity.ActivityType.ASSIGNMENT, "Collector assigned",
                "Assigned to " + collector, assignedBy, CollectionActivity.ActivityStatus.COMPLETED);
        return mapCaseToDTO(saved);
    }

    public ReceivablesCollectionCaseDTO escalateCase(Long caseId, CollectionCaseActionRequest request, String escalatedBy) {
        ReceivablesCollectionCase collectionCase = findCase(caseId);
        collectionCase.setStatus(ReceivablesCollectionCase.CaseStatus.ESCALATED);
        collectionCase.setEscalatedTo(required(request.getEscalatedTo(), "Escalation target is required"));
        collectionCase.setEscalationReason(request.getReason());
        collectionCase.setEscalatedAt(LocalDateTime.now());
        ReceivablesCollectionCase saved = collectionCaseRepository.save(collectionCase);
        activity(saved, CollectionActivity.ActivityType.ESCALATION, "Case escalated",
                request.getReason(), escalatedBy, CollectionActivity.ActivityStatus.COMPLETED);
        return mapCaseToDTO(saved);
    }

    public ReceivablesCollectionCaseDTO closeCase(Long caseId, CollectionCaseActionRequest request, String closedBy) {
        ReceivablesCollectionCase collectionCase = findCase(caseId);
        collectionCase.setStatus(ReceivablesCollectionCase.CaseStatus.CLOSED);
        collectionCase.setCloseReason(request != null ? request.getReason() : null);
        ReceivablesCollectionCase saved = collectionCaseRepository.save(collectionCase);
        activity(saved, CollectionActivity.ActivityType.CLOSURE, "Case closed",
                saved.getCloseReason(), closedBy, CollectionActivity.ActivityStatus.COMPLETED);
        return mapCaseToDTO(saved);
    }

    public ReceivablesCollectionCaseDTO addActivity(Long caseId, CollectionCaseActionRequest request, String createdBy) {
        ReceivablesCollectionCase collectionCase = findCase(caseId);
        CollectionActivity.ActivityType type = parseActivityType(request.getActivityType());
        activity(collectionCase, type, request.getSubject(), request.getNotes(), createdBy, CollectionActivity.ActivityStatus.COMPLETED);
        return mapCaseToDTO(collectionCase);
    }

    public ReceivablesCollectionCaseDTO createDunning(Long caseId, String createdBy) {
        ReceivablesCollectionCase collectionCase = findCase(caseId);
        int level = dunningLevel(collectionCase.getDaysOverdue());
        String subject = switch (level) {
            case 1 -> "Payment reminder";
            case 2 -> "Overdue payment notice";
            default -> "Final overdue payment notice";
        };
        String notes = "Invoice " + collectionCase.getCustomerInvoice().getInvoiceNumber()
                + " is " + collectionCase.getDaysOverdue() + " days overdue with outstanding balance "
                + collectionCase.getOutstandingAmount() + ".";
        collectionCase.setDunningLevel(level);
        collectionCase.setLastDunningDate(LocalDate.now());
        collectionCase.setNextActionDate(LocalDate.now().plusDays(level == 1 ? 7 : 3));
        if (ReceivablesCollectionCase.CaseStatus.OPEN.equals(collectionCase.getStatus())) {
            collectionCase.setStatus(ReceivablesCollectionCase.CaseStatus.IN_PROGRESS);
        }
        ReceivablesCollectionCase saved = collectionCaseRepository.save(collectionCase);
        activity(saved, CollectionActivity.ActivityType.DUNNING, subject, notes, createdBy, CollectionActivity.ActivityStatus.SENT);
        return mapCaseToDTO(saved);
    }

    public ReceivablesCollectionCaseDTO createPromise(Long caseId, CollectionCaseActionRequest request, String createdBy) {
        ReceivablesCollectionCase collectionCase = findCase(caseId);
        BigDecimal amount = positive(request.getPromisedAmount(), "Promised amount must be positive");
        if (amount.compareTo(collectionCase.getOutstandingAmount()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promised amount exceeds outstanding balance");
        }
        if (request.getPromisedDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promised date is required");
        }
        promiseRepository.save(PromiseToPay.builder()
                .collectionCase(collectionCase)
                .promisedAmount(amount)
                .promisedDate(request.getPromisedDate())
                .notes(request.getNotes())
                .createdBy(defaultUser(createdBy))
                .status(PromiseToPay.PromiseStatus.ACTIVE)
                .build());
        collectionCase.setStatus(ReceivablesCollectionCase.CaseStatus.PROMISED);
        collectionCase.setNextActionDate(request.getPromisedDate());
        ReceivablesCollectionCase saved = collectionCaseRepository.save(collectionCase);
        activity(saved, CollectionActivity.ActivityType.PROMISE, "Promise to pay",
                "Promised " + amount + " by " + request.getPromisedDate(), createdBy, CollectionActivity.ActivityStatus.COMPLETED);
        return mapCaseToDTO(saved);
    }

    public PromiseToPayDTO updatePromiseStatus(Long promiseId, String status) {
        PromiseToPay promise = promiseRepository.findById(promiseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Promise to pay not found"));
        promise.setStatus(parsePromiseStatus(status));
        return mapPromiseToDTO(promiseRepository.save(promise));
    }

    public List<ReceivablesCollectionCaseDTO> reconcilePromises(Long organizationId, LocalDate asOfDate) {
        LocalDate date = asOfDate != null ? asOfDate : LocalDate.now();
        collectionCaseRepository.findByOrganizationIdOrderByDaysOverdueDescIdDesc(organizationId).forEach(collectionCase -> {
            refreshCaseBalance(collectionCase, date);
            List<PromiseToPay> activePromises = promiseRepository.findByCollectionCaseIdAndStatusIn(
                    collectionCase.getId(), List.of(PromiseToPay.PromiseStatus.ACTIVE));
            for (PromiseToPay promise : activePromises) {
                if (outstanding(collectionCase.getCustomerInvoice()).compareTo(BigDecimal.ZERO) <= 0) {
                    promise.setStatus(PromiseToPay.PromiseStatus.KEPT);
                    promiseRepository.save(promise);
                } else if (promise.getPromisedDate().isBefore(date)) {
                    promise.setStatus(PromiseToPay.PromiseStatus.BROKEN);
                    promiseRepository.save(promise);
                    if (ReceivablesCollectionCase.CaseStatus.PROMISED.equals(collectionCase.getStatus())) {
                        collectionCase.setStatus(ReceivablesCollectionCase.CaseStatus.IN_PROGRESS);
                        collectionCaseRepository.save(collectionCase);
                    }
                }
            }
        });
        return getCases(organizationId);
    }

    public void updateAfterPayment(CustomerInvoice invoice, BigDecimal paymentAmount, String receivedBy) {
        collectionCaseRepository.findByCustomerInvoiceId(invoice.getId()).ifPresent(collectionCase -> {
            refreshCaseBalance(collectionCase, LocalDate.now());
            activity(collectionCase, CollectionActivity.ActivityType.PAYMENT, "Customer payment received",
                    "Payment received: " + paymentAmount, receivedBy, CollectionActivity.ActivityStatus.COMPLETED);
            if (outstanding(invoice).compareTo(BigDecimal.ZERO) <= 0) {
                collectionCase.setStatus(ReceivablesCollectionCase.CaseStatus.RESOLVED);
                collectionCase.setCloseReason("Invoice paid in full");
                promiseRepository.findByCollectionCaseIdAndStatusIn(collectionCase.getId(), List.of(PromiseToPay.PromiseStatus.ACTIVE))
                        .forEach(promise -> {
                            promise.setStatus(PromiseToPay.PromiseStatus.KEPT);
                            promiseRepository.save(promise);
                        });
            }
            collectionCaseRepository.save(collectionCase);
        });
    }

    public CustomerDTO placeCreditHold(Long customerId, CreditHoldRequest request, String placedBy) {
        Customer customer = findCustomer(customerId);
        customer.setCreditHold(true);
        customer.setCreditHoldReason(request != null ? request.getReason() : null);
        customer.setCreditHoldPlacedBy(defaultUser(placedBy));
        customer.setCreditHoldPlacedAt(LocalDateTime.now());
        customer.setCreditHoldReleasedBy(null);
        customer.setCreditHoldReleasedAt(null);
        return mapCustomerToDTO(customerRepository.save(customer));
    }

    public CustomerDTO releaseCreditHold(Long customerId, CreditHoldRequest request, String releasedBy) {
        Customer customer = findCustomer(customerId);
        customer.setCreditHold(false);
        customer.setCreditHoldReason(request != null ? request.getReason() : null);
        customer.setCreditHoldReleasedBy(defaultUser(releasedBy));
        customer.setCreditHoldReleasedAt(LocalDateTime.now());
        return mapCustomerToDTO(customerRepository.save(customer));
    }

    public CustomerStatementDTO createCustomerStatement(Long organizationId, CustomerStatementCreateRequest request) {
        Organization organization = findOrganization(organizationId);
        Customer customer = findCustomer(request.getCustomerId());
        if (!customer.getOrganization().getId().equals(organizationId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer does not belong to organization");
        }
        LocalDate start = requiredDate(request.getStartDate(), "Start date is required");
        LocalDate end = requiredDate(request.getEndDate(), "End date is required");
        if (start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date must not be after end date");
        }
        LocalDate statementDate = request.getStatementDate() != null ? request.getStatementDate() : LocalDate.now();
        List<CustomerInvoice> invoices = customerInvoiceRepository.findByOrganizationId(organizationId).stream()
                .filter(invoice -> invoice.getCustomer() != null && invoice.getCustomer().getId().equals(customer.getId()))
                .filter(invoice -> !CustomerInvoice.InvoiceStatus.CANCELLED.equals(invoice.getStatus()))
                .sorted(Comparator.comparing(CustomerInvoice::getInvoiceDate).thenComparing(CustomerInvoice::getId))
                .toList();
        BigDecimal opening = invoices.stream()
                .filter(invoice -> invoice.getInvoiceDate().isBefore(start))
                .map(this::outstanding)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CustomerInvoice> periodInvoices = invoices.stream()
                .filter(invoice -> !invoice.getInvoiceDate().isBefore(start) && !invoice.getInvoiceDate().isAfter(end))
                .toList();
        BigDecimal totalInvoiced = periodInvoices.stream().map(CustomerInvoice::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = periodInvoices.stream()
                .map(invoice -> invoice.getAmountPaid() != null ? invoice.getAmountPaid() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        CustomerStatement statement = CustomerStatement.builder()
                .organization(organization)
                .customer(customer)
                .statementDate(statementDate)
                .startDate(start)
                .endDate(end)
                .openingBalance(opening)
                .totalInvoiced(totalInvoiced)
                .totalPaid(totalPaid)
                .closingBalance(opening.add(totalInvoiced).subtract(totalPaid))
                .status(CustomerStatement.StatementStatus.GENERATED)
                .build();
        CustomerStatement saved = customerStatementRepository.save(statement);
        for (CustomerInvoice invoice : periodInvoices) {
            customerStatementLineRepository.save(CustomerStatementLine.builder()
                    .customerStatement(saved)
                    .customerInvoice(invoice)
                    .transactionDate(invoice.getInvoiceDate())
                    .referenceNumber(invoice.getInvoiceNumber())
                    .lineType(CustomerStatementLine.LineType.INVOICE)
                    .description("Invoice " + invoice.getInvoiceNumber())
                    .debitAmount(invoice.getTotalAmount())
                    .creditAmount(BigDecimal.ZERO)
                    .build());
            BigDecimal paid = invoice.getAmountPaid() != null ? invoice.getAmountPaid() : BigDecimal.ZERO;
            if (paid.compareTo(BigDecimal.ZERO) > 0) {
                customerStatementLineRepository.save(CustomerStatementLine.builder()
                        .customerStatement(saved)
                        .customerInvoice(invoice)
                        .transactionDate(invoice.getPostedDate() != null ? invoice.getPostedDate() : invoice.getInvoiceDate())
                        .referenceNumber("PAY-" + invoice.getInvoiceNumber())
                        .lineType(CustomerStatementLine.LineType.PAYMENT)
                        .description("Payment applied to " + invoice.getInvoiceNumber())
                        .debitAmount(BigDecimal.ZERO)
                        .creditAmount(paid)
                        .build());
            }
        }
        return getCustomerStatement(saved.getId());
    }

    @Transactional(readOnly = true)
    public List<CustomerStatementDTO> getCustomerStatements(Long organizationId) {
        return customerStatementRepository.findByOrganizationIdOrderByStatementDateDescIdDesc(organizationId)
                .stream()
                .map(this::mapStatementToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CustomerStatementDTO getCustomerStatement(Long statementId) {
        return mapStatementToDTO(findStatement(statementId));
    }

    public CustomerStatementDTO sendCustomerStatement(Long statementId) {
        CustomerStatement statement = findStatement(statementId);
        statement.setStatus(CustomerStatement.StatementStatus.SENT);
        return mapStatementToDTO(customerStatementRepository.save(statement));
    }

    private ReceivablesCollectionCase upsertCase(Organization organization, CustomerInvoice invoice, LocalDate asOfDate) {
        ReceivablesCollectionCase collectionCase = collectionCaseRepository.findByCustomerInvoiceId(invoice.getId())
                .orElseGet(() -> ReceivablesCollectionCase.builder()
                        .organization(organization)
                        .customer(invoice.getCustomer())
                        .customerInvoice(invoice)
                        .status(ReceivablesCollectionCase.CaseStatus.OPEN)
                        .dunningLevel(0)
                        .build());
        return refreshCaseBalance(collectionCase, asOfDate);
    }

    private ReceivablesCollectionCase refreshCaseBalance(ReceivablesCollectionCase collectionCase, LocalDate asOfDate) {
        CustomerInvoice invoice = collectionCase.getCustomerInvoice();
        BigDecimal balance = outstanding(invoice);
        collectionCase.setOutstandingAmount(balance);
        collectionCase.setDueDate(invoice.getDueDate());
        collectionCase.setDaysOverdue(Math.max(0, ChronoUnit.DAYS.between(invoice.getDueDate(), asOfDate)));
        if (balance.compareTo(BigDecimal.ZERO) <= 0
                && !ReceivablesCollectionCase.CaseStatus.CLOSED.equals(collectionCase.getStatus())) {
            collectionCase.setStatus(ReceivablesCollectionCase.CaseStatus.RESOLVED);
            collectionCase.setCloseReason("Invoice paid in full");
        }
        return collectionCaseRepository.save(collectionCase);
    }

    private void activity(ReceivablesCollectionCase collectionCase,
                          CollectionActivity.ActivityType type,
                          String subject,
                          String notes,
                          String createdBy,
                          CollectionActivity.ActivityStatus status) {
        activityRepository.save(CollectionActivity.builder()
                .collectionCase(collectionCase)
                .activityType(type)
                .subject(subject)
                .notes(notes)
                .createdBy(defaultUser(createdBy))
                .sentAt(CollectionActivity.ActivityStatus.SENT.equals(status) ? LocalDateTime.now() : null)
                .status(status)
                .build());
    }

    private ReceivablesCollectionCaseDTO mapCaseToDTO(ReceivablesCollectionCase collectionCase) {
        CustomerInvoice invoice = collectionCase.getCustomerInvoice();
        return ReceivablesCollectionCaseDTO.builder()
                .id(collectionCase.getId())
                .organizationId(collectionCase.getOrganization().getId())
                .customerId(collectionCase.getCustomer() != null ? collectionCase.getCustomer().getId() : null)
                .customerName(collectionCase.getCustomer() != null ? collectionCase.getCustomer().getLegalName() : invoice.getCustomerName())
                .customerInvoiceId(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .invoiceDate(invoice.getInvoiceDate())
                .dueDate(collectionCase.getDueDate())
                .invoiceTotal(invoice.getTotalAmount())
                .amountPaid(invoice.getAmountPaid())
                .outstandingAmount(collectionCase.getOutstandingAmount())
                .daysOverdue(collectionCase.getDaysOverdue())
                .status(collectionCase.getStatus().name())
                .collectorUsername(collectionCase.getCollectorUsername())
                .assignedAt(collectionCase.getAssignedAt())
                .escalatedTo(collectionCase.getEscalatedTo())
                .escalatedAt(collectionCase.getEscalatedAt())
                .escalationReason(collectionCase.getEscalationReason())
                .dunningLevel(collectionCase.getDunningLevel())
                .lastDunningDate(collectionCase.getLastDunningDate())
                .nextActionDate(collectionCase.getNextActionDate())
                .closeReason(collectionCase.getCloseReason())
                .activities(activityRepository.findByCollectionCaseIdOrderByCreatedAtDescIdDesc(collectionCase.getId())
                        .stream().map(this::mapActivityToDTO).collect(Collectors.toList()))
                .promises(promiseRepository.findByCollectionCaseIdOrderByPromisedDateDescIdDesc(collectionCase.getId())
                        .stream().map(this::mapPromiseToDTO).collect(Collectors.toList()))
                .createdAt(collectionCase.getCreatedAt())
                .updatedAt(collectionCase.getUpdatedAt())
                .build();
    }

    private CollectionActivityDTO mapActivityToDTO(CollectionActivity activity) {
        return CollectionActivityDTO.builder()
                .id(activity.getId())
                .collectionCaseId(activity.getCollectionCase().getId())
                .activityType(activity.getActivityType().name())
                .subject(activity.getSubject())
                .notes(activity.getNotes())
                .createdBy(activity.getCreatedBy())
                .sentAt(activity.getSentAt())
                .status(activity.getStatus().name())
                .createdAt(activity.getCreatedAt())
                .build();
    }

    private PromiseToPayDTO mapPromiseToDTO(PromiseToPay promise) {
        return PromiseToPayDTO.builder()
                .id(promise.getId())
                .collectionCaseId(promise.getCollectionCase().getId())
                .promisedAmount(promise.getPromisedAmount())
                .promisedDate(promise.getPromisedDate())
                .notes(promise.getNotes())
                .createdBy(promise.getCreatedBy())
                .status(promise.getStatus().name())
                .createdAt(promise.getCreatedAt())
                .updatedAt(promise.getUpdatedAt())
                .build();
    }

    private CustomerStatementDTO mapStatementToDTO(CustomerStatement statement) {
        return CustomerStatementDTO.builder()
                .id(statement.getId())
                .organizationId(statement.getOrganization().getId())
                .customerId(statement.getCustomer().getId())
                .customerName(statement.getCustomer().getLegalName())
                .statementDate(statement.getStatementDate())
                .startDate(statement.getStartDate())
                .endDate(statement.getEndDate())
                .openingBalance(statement.getOpeningBalance())
                .closingBalance(statement.getClosingBalance())
                .totalInvoiced(statement.getTotalInvoiced())
                .totalPaid(statement.getTotalPaid())
                .status(statement.getStatus().name())
                .lines(customerStatementLineRepository.findByCustomerStatementIdOrderByTransactionDateAscIdAsc(statement.getId())
                        .stream().map(this::mapStatementLineToDTO).collect(Collectors.toList()))
                .createdAt(statement.getCreatedAt())
                .updatedAt(statement.getUpdatedAt())
                .build();
    }

    private CustomerStatementLineDTO mapStatementLineToDTO(CustomerStatementLine line) {
        return CustomerStatementLineDTO.builder()
                .id(line.getId())
                .customerStatementId(line.getCustomerStatement().getId())
                .customerInvoiceId(line.getCustomerInvoice() != null ? line.getCustomerInvoice().getId() : null)
                .transactionDate(line.getTransactionDate())
                .referenceNumber(line.getReferenceNumber())
                .lineType(line.getLineType().name())
                .description(line.getDescription())
                .debitAmount(line.getDebitAmount())
                .creditAmount(line.getCreditAmount())
                .createdAt(line.getCreatedAt())
                .build();
    }

    private CustomerDTO mapCustomerToDTO(Customer customer) {
        return CustomerDTO.builder()
                .id(customer.getId())
                .organizationId(customer.getOrganization().getId())
                .customerCode(customer.getCustomerCode())
                .legalName(customer.getLegalName())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .billingAddress(customer.getBillingAddress())
                .taxId(customer.getTaxId())
                .paymentTerms(customer.getPaymentTerms())
                .creditLimit(customer.getCreditLimit())
                .creditHold(customer.getCreditHold())
                .creditHoldReason(customer.getCreditHoldReason())
                .active(customer.getActive())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();
    }

    private Organization findOrganization(Long organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
    }

    private Customer findCustomer(Long customerId) {
        if (customerId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer is required");
        }
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
    }

    private ReceivablesCollectionCase findCase(Long caseId) {
        return collectionCaseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection case not found"));
    }

    private CustomerStatement findStatement(Long statementId) {
        return customerStatementRepository.findById(statementId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer statement not found"));
    }

    private BigDecimal outstanding(CustomerInvoice invoice) {
        BigDecimal total = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal paid = invoice.getAmountPaid() != null ? invoice.getAmountPaid() : BigDecimal.ZERO;
        return total.subtract(paid);
    }

    private int dunningLevel(Long daysOverdue) {
        long days = daysOverdue != null ? daysOverdue : 0;
        if (days <= 30) return 1;
        if (days <= 60) return 2;
        return 3;
    }

    private CollectionActivity.ActivityType parseActivityType(String activityType) {
        if (activityType == null || activityType.isBlank()) {
            return CollectionActivity.ActivityType.NOTE;
        }
        try {
            return CollectionActivity.ActivityType.valueOf(activityType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported collection activity type");
        }
    }

    private PromiseToPay.PromiseStatus parsePromiseStatus(String status) {
        try {
            return PromiseToPay.PromiseStatus.valueOf(required(status, "Promise status is required").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported promise status");
        }
    }

    private LocalDate requiredDate(LocalDate value, String message) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value;
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

    private String defaultUser(String user) {
        return user != null && !user.isBlank() ? user : "system";
    }
}
