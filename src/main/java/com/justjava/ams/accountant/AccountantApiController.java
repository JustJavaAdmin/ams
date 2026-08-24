package com.justjava.ams.accountant;

import com.justjava.ams.accountant.dto.GeneralLedgerDTO;
import com.justjava.ams.accountant.dto.ChartOfAccountsDTO;
import com.justjava.ams.accountant.dto.JournalLineCreateRequest;
import com.justjava.ams.accountant.dto.JournalLineDTO;
import com.justjava.ams.accountant.dto.JournalLineUpdateRequest;
import com.justjava.ams.accountant.dto.JournalPostRequest;
import com.justjava.ams.accountant.dto.JournalSubmitRequest;
import com.justjava.ams.accountant.dto.ManualJournalCreateRequest;
import com.justjava.ams.accountant.dto.ManualJournalDTO;
import com.justjava.ams.accountant.dto.CustomerInvoiceDTO;
import com.justjava.ams.accountant.dto.CustomerDTO;
import com.justjava.ams.accountant.dto.BankAccountDTO;
import com.justjava.ams.accountant.dto.BankReconciliationCreateRequest;
import com.justjava.ams.accountant.dto.BankReconciliationDTO;
import com.justjava.ams.accountant.dto.BankStatementLineMatchRequest;
import com.justjava.ams.accountant.dto.DepreciationJournalImportDTO;
import com.justjava.ams.accountant.dto.DepreciationJournalImportRequest;
import com.justjava.ams.accountant.dto.AgingReportResponse;
import com.justjava.ams.accountant.dto.PaymentRequest;
import com.justjava.ams.accountant.dto.PaymentRunCreateRequest;
import com.justjava.ams.accountant.dto.PaymentRunDTO;
import com.justjava.ams.accountant.dto.PaymentScheduleDTO;
import com.justjava.ams.accountant.dto.PaymentScheduleRequest;
import com.justjava.ams.accountant.dto.PurchaseInvoiceDTO;
import com.justjava.ams.accountant.dto.ExpenseDTO;
import com.justjava.ams.accountant.dto.CashRequirementForecastResponse;
import com.justjava.ams.accountant.dto.CollectionCaseActionRequest;
import com.justjava.ams.accountant.dto.CreditHoldRequest;
import com.justjava.ams.accountant.dto.CustomerStatementCreateRequest;
import com.justjava.ams.accountant.dto.CustomerStatementDTO;
import com.justjava.ams.accountant.dto.PromiseToPayDTO;
import com.justjava.ams.accountant.dto.ReceivablesCollectionCaseDTO;
import com.justjava.ams.accountant.dto.SupplierStatementCreateRequest;
import com.justjava.ams.accountant.dto.SupplierStatementDTO;
import com.justjava.ams.accountant.dto.VendorDTO;
import com.justjava.ams.accountant.service.GeneralLedgerService;
import com.justjava.ams.accountant.service.BankAccountService;
import com.justjava.ams.accountant.service.BankReconciliationService;
import com.justjava.ams.accountant.service.ChartOfAccountsService;
import com.justjava.ams.accountant.service.CustomerInvoiceService;
import com.justjava.ams.accountant.service.CustomerService;
import com.justjava.ams.accountant.service.DepreciationJournalImportService;
import com.justjava.ams.accountant.service.ManualJournalService;
import com.justjava.ams.accountant.service.PaymentRunService;
import com.justjava.ams.accountant.service.PaymentScheduleService;
import com.justjava.ams.accountant.service.PurchaseInvoiceService;
import com.justjava.ams.accountant.service.ExpenseService;
import com.justjava.ams.accountant.service.ReceivablesCollectionService;
import com.justjava.ams.accountant.service.SupplierStatementService;
import com.justjava.ams.accountant.service.VendorService;
import com.justjava.ams.financeAdmin.dto.TaxJurisdictionDTO;
import com.justjava.ams.financeAdmin.service.TaxJurisdictionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/accountant")
@RequiredArgsConstructor
public class AccountantApiController {
    private final ManualJournalService manualJournalService;
    private final GeneralLedgerService generalLedgerService;
    private final ChartOfAccountsService chartOfAccountsService;
    private final CustomerInvoiceService customerInvoiceService;
    private final PurchaseInvoiceService purchaseInvoiceService;
    private final CustomerService customerService;
    private final VendorService vendorService;
    private final BankAccountService bankAccountService;
    private final BankReconciliationService bankReconciliationService;
    private final ExpenseService expenseService;
    private final DepreciationJournalImportService depreciationJournalImportService;
    private final TaxJurisdictionService taxJurisdictionService;
    private final PaymentScheduleService paymentScheduleService;
    private final PaymentRunService paymentRunService;
    private final SupplierStatementService supplierStatementService;
    private final ReceivablesCollectionService receivablesCollectionService;

    @GetMapping("/manual-journals/org/{organizationId}")
    public List<ManualJournalDTO> getManualJournals(@PathVariable Long organizationId) {
        return manualJournalService.getJournalsByOrganization(organizationId);
    }

    @PostMapping("/manual-journals/org/{organizationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ManualJournalDTO createManualJournal(
            @PathVariable Long organizationId,
            @Valid @RequestBody ManualJournalCreateRequest request,
            Principal principal) {
        return manualJournalService.createManualJournal(organizationId, toDTO(request), getUserName(principal));
    }

    @GetMapping("/manual-journals/{journalId}")
    public ManualJournalDTO getManualJournal(@PathVariable Long journalId) {
        return manualJournalService.getJournal(journalId);
    }

    @PostMapping("/manual-journals/{journalId}/lines")
    @ResponseStatus(HttpStatus.CREATED)
    public JournalLineDTO addJournalLine(
            @PathVariable Long journalId,
            @Valid @RequestBody JournalLineCreateRequest request) {
        return manualJournalService.addJournalLine(journalId, toDTO(request));
    }

    @PutMapping("/manual-journals/lines/{lineId}")
    public JournalLineDTO updateJournalLine(
            @PathVariable Long lineId,
            @Valid @RequestBody JournalLineUpdateRequest request) {
        return manualJournalService.updateJournalLine(lineId, toDTO(request));
    }

    @DeleteMapping("/manual-journals/lines/{lineId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteJournalLine(@PathVariable Long lineId) {
        manualJournalService.deleteJournalLine(lineId);
    }

    @PatchMapping("/manual-journals/{journalId}/submit")
    public ManualJournalDTO submitManualJournal(
            @PathVariable Long journalId,
            @Valid @RequestBody(required = false) JournalSubmitRequest request,
            Principal principal) {
        JournalSubmitRequest submitRequest = request != null ? request : new JournalSubmitRequest();
        if (submitRequest.getSubmittedBy() == null || submitRequest.getSubmittedBy().trim().isEmpty()) {
            submitRequest.setSubmittedBy(getUserName(principal));
        }
        return manualJournalService.submitJournal(journalId, submitRequest);
    }

    @PatchMapping("/manual-journals/{journalId}/post")
    public ManualJournalDTO postManualJournal(
            @PathVariable Long journalId,
            @Valid @RequestBody(required = false) JournalPostRequest request,
            Principal principal) {
        String postedBy = principal != null ? principal.getName() : null;
        if ((postedBy == null || postedBy.trim().isEmpty())
                && request != null
                && request.getPostedBy() != null
                && !request.getPostedBy().trim().isEmpty()) {
            postedBy = request.getPostedBy();
        }
        return manualJournalService.postJournal(journalId, postedBy);
    }

    @GetMapping("/general-ledger/journal/{journalId}")
    public List<GeneralLedgerDTO> getGeneralLedgerEntriesForJournal(@PathVariable Long journalId) {
        return generalLedgerService.getEntriesByJournalId(journalId);
    }

    @GetMapping("/chart-of-accounts/org/{organizationId}")
    public List<ChartOfAccountsDTO> getChartOfAccounts(@PathVariable Long organizationId) {
        return chartOfAccountsService.getAccountsByOrganization(organizationId);
    }

    @GetMapping("/tax-jurisdictions/org/{organizationId}")
    public List<TaxJurisdictionDTO> getTaxJurisdictions(@PathVariable Long organizationId) {
        return taxJurisdictionService.getJurisdictionsByOrganization(organizationId);
    }

    @GetMapping("/bank-accounts/org/{organizationId}")
    public List<BankAccountDTO> getBankAccounts(@PathVariable Long organizationId) {
        return bankAccountService.getBankAccountsByOrganization(organizationId);
    }

    @PostMapping("/bank-accounts/org/{organizationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public BankAccountDTO createBankAccount(
            @PathVariable Long organizationId,
            @Valid @RequestBody BankAccountDTO request) {
        return bankAccountService.createBankAccount(organizationId, request);
    }

    @GetMapping("/bank-reconciliations/org/{organizationId}/bank-account/{bankAccountId}")
    public List<BankReconciliationDTO> getBankReconciliations(
            @PathVariable Long organizationId,
            @PathVariable Long bankAccountId) {
        return bankReconciliationService.getReconciliations(organizationId, bankAccountId);
    }

    @PostMapping("/bank-reconciliations/org/{organizationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public BankReconciliationDTO createBankReconciliation(
            @PathVariable Long organizationId,
            @Valid @RequestBody BankReconciliationCreateRequest request,
            Principal principal) {
        return bankReconciliationService.createReconciliation(organizationId, request, getUserName(principal));
    }

    @PostMapping(value = "/bank-reconciliations/org/{organizationId}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BankReconciliationDTO importBankReconciliation(
            @PathVariable Long organizationId,
            @RequestParam Long bankAccountId,
            @RequestParam LocalDate statementDate,
            @RequestParam BigDecimal openingBalance,
            @RequestParam BigDecimal closingBalance,
            @RequestParam("file") MultipartFile file,
            Principal principal) {
        return bankReconciliationService.importReconciliation(
                organizationId,
                bankAccountId,
                statementDate,
                openingBalance,
                closingBalance,
                file,
                getUserName(principal));
    }

    @GetMapping("/bank-reconciliations/{reconciliationId}")
    public BankReconciliationDTO getBankReconciliation(@PathVariable Long reconciliationId) {
        return bankReconciliationService.getReconciliation(reconciliationId);
    }

    @PatchMapping("/bank-reconciliations/{reconciliationId}/auto-match")
    public BankReconciliationDTO autoMatchBankReconciliation(
            @PathVariable Long reconciliationId,
            Principal principal) {
        return bankReconciliationService.autoMatch(reconciliationId, getUserName(principal));
    }

    @PatchMapping("/bank-reconciliations/{reconciliationId}/lines/{statementLineId}/match")
    public BankReconciliationDTO matchBankStatementLine(
            @PathVariable Long reconciliationId,
            @PathVariable Long statementLineId,
            @Valid @RequestBody BankStatementLineMatchRequest request,
            Principal principal) {
        return bankReconciliationService.manuallyMatchLine(
                reconciliationId,
                statementLineId,
                request.getGeneralLedgerId(),
                getUserName(principal));
    }

    @GetMapping("/bank-reconciliations/{reconciliationId}/lines/{statementLineId}/candidates")
    public List<GeneralLedgerDTO> getBankStatementLineCandidates(
            @PathVariable Long reconciliationId,
            @PathVariable Long statementLineId) {
        return bankReconciliationService.getMatchCandidates(reconciliationId, statementLineId);
    }

    @PatchMapping("/bank-reconciliations/{reconciliationId}/lines/{statementLineId}/unmatch")
    public BankReconciliationDTO unmatchBankStatementLine(
            @PathVariable Long reconciliationId,
            @PathVariable Long statementLineId,
            Principal principal) {
        return bankReconciliationService.unmatchLine(reconciliationId, statementLineId, getUserName(principal));
    }

    @PatchMapping("/bank-reconciliations/{reconciliationId}/complete")
    public BankReconciliationDTO completeBankReconciliation(
            @PathVariable Long reconciliationId,
            Principal principal) {
        return bankReconciliationService.completeReconciliation(reconciliationId, getUserName(principal));
    }

    @GetMapping("/depreciation-journals/org/{organizationId}/imports")
    public List<DepreciationJournalImportDTO> getDepreciationJournalImports(@PathVariable Long organizationId) {
        return depreciationJournalImportService.getImports(organizationId);
    }

    @PostMapping("/depreciation-journals/org/{organizationId}/import")
    @ResponseStatus(HttpStatus.CREATED)
    public DepreciationJournalImportDTO importDepreciationJournal(
            @PathVariable Long organizationId,
            @Valid @RequestBody DepreciationJournalImportRequest request,
            Principal principal) {
        return depreciationJournalImportService.importFromRequest(organizationId, request, getUserName(principal));
    }

    @PostMapping(value = "/depreciation-journals/org/{organizationId}/import-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DepreciationJournalImportDTO importDepreciationJournalFile(
            @PathVariable Long organizationId,
            @RequestParam String externalSystem,
            @RequestParam String externalBatchId,
            @RequestParam LocalDate journalDate,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String branchCode,
            @RequestParam(required = false) Boolean autoSubmit,
            @RequestParam("file") MultipartFile file,
            Principal principal) {
        return depreciationJournalImportService.importFromFile(
                organizationId,
                externalSystem,
                externalBatchId,
                journalDate,
                description,
                branchCode,
                autoSubmit,
                file,
                getUserName(principal));
    }

    @GetMapping("/customer-invoices/org/{organizationId}")
    public List<CustomerInvoiceDTO> getCustomerInvoices(@PathVariable Long organizationId) {
        return customerInvoiceService.getCustomerInvoicesByOrganization(organizationId);
    }

    @GetMapping("/reports/aged-receivables/org/{organizationId}")
    public AgingReportResponse getAgedReceivables(@PathVariable Long organizationId) {
        return customerInvoiceService.generateAgedReceivables(organizationId, LocalDate.now());
    }

    @GetMapping("/receivables/collections/org/{organizationId}")
    public List<ReceivablesCollectionCaseDTO> getCollectionCases(@PathVariable Long organizationId) {
        return receivablesCollectionService.getCases(organizationId);
    }

    @PostMapping("/receivables/collections/org/{organizationId}/generate")
    public List<ReceivablesCollectionCaseDTO> generateCollectionCases(
            @PathVariable Long organizationId,
            @RequestParam(required = false) LocalDate asOfDate) {
        return receivablesCollectionService.generateCases(organizationId, asOfDate);
    }

    @GetMapping("/receivables/collections/{caseId}")
    public ReceivablesCollectionCaseDTO getCollectionCase(@PathVariable Long caseId) {
        return receivablesCollectionService.getCase(caseId);
    }

    @PatchMapping("/receivables/collections/{caseId}/assign")
    public ReceivablesCollectionCaseDTO assignCollectionCase(
            @PathVariable Long caseId,
            @RequestBody CollectionCaseActionRequest request,
            Principal principal) {
        return receivablesCollectionService.assignCase(caseId, request, getUserName(principal));
    }

    @PatchMapping("/receivables/collections/{caseId}/escalate")
    public ReceivablesCollectionCaseDTO escalateCollectionCase(
            @PathVariable Long caseId,
            @RequestBody CollectionCaseActionRequest request,
            Principal principal) {
        return receivablesCollectionService.escalateCase(caseId, request, getUserName(principal));
    }

    @PatchMapping("/receivables/collections/{caseId}/close")
    public ReceivablesCollectionCaseDTO closeCollectionCase(
            @PathVariable Long caseId,
            @RequestBody(required = false) CollectionCaseActionRequest request,
            Principal principal) {
        return receivablesCollectionService.closeCase(caseId, request != null ? request : new CollectionCaseActionRequest(), getUserName(principal));
    }

    @PostMapping("/receivables/collections/{caseId}/activities")
    public ReceivablesCollectionCaseDTO addCollectionActivity(
            @PathVariable Long caseId,
            @RequestBody CollectionCaseActionRequest request,
            Principal principal) {
        return receivablesCollectionService.addActivity(caseId, request, getUserName(principal));
    }

    @PostMapping("/receivables/collections/{caseId}/dunning")
    public ReceivablesCollectionCaseDTO createDunningNotice(@PathVariable Long caseId, Principal principal) {
        return receivablesCollectionService.createDunning(caseId, getUserName(principal));
    }

    @PostMapping("/receivables/collections/{caseId}/promises")
    public ReceivablesCollectionCaseDTO createPromiseToPay(
            @PathVariable Long caseId,
            @RequestBody CollectionCaseActionRequest request,
            Principal principal) {
        return receivablesCollectionService.createPromise(caseId, request, getUserName(principal));
    }

    @PatchMapping("/receivables/promises/{promiseId}/status")
    public PromiseToPayDTO updatePromiseStatus(
            @PathVariable Long promiseId,
            @RequestParam String status) {
        return receivablesCollectionService.updatePromiseStatus(promiseId, status);
    }

    @PostMapping("/receivables/promises/reconcile/org/{organizationId}")
    public List<ReceivablesCollectionCaseDTO> reconcilePromises(
            @PathVariable Long organizationId,
            @RequestParam(required = false) LocalDate asOfDate) {
        return receivablesCollectionService.reconcilePromises(organizationId, asOfDate);
    }

    @PatchMapping("/customers/{customerId}/credit-hold")
    public CustomerDTO placeCustomerCreditHold(
            @PathVariable Long customerId,
            @RequestBody(required = false) CreditHoldRequest request,
            Principal principal) {
        return receivablesCollectionService.placeCreditHold(customerId, request, getUserName(principal));
    }

    @PatchMapping("/customers/{customerId}/credit-hold/release")
    public CustomerDTO releaseCustomerCreditHold(
            @PathVariable Long customerId,
            @RequestBody(required = false) CreditHoldRequest request,
            Principal principal) {
        return receivablesCollectionService.releaseCreditHold(customerId, request, getUserName(principal));
    }

    @PostMapping("/receivables/customer-statements/org/{organizationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerStatementDTO createCustomerStatement(
            @PathVariable Long organizationId,
            @RequestBody CustomerStatementCreateRequest request) {
        return receivablesCollectionService.createCustomerStatement(organizationId, request);
    }

    @GetMapping("/receivables/customer-statements/org/{organizationId}")
    public List<CustomerStatementDTO> getCustomerStatements(@PathVariable Long organizationId) {
        return receivablesCollectionService.getCustomerStatements(organizationId);
    }

    @GetMapping("/receivables/customer-statements/{statementId}")
    public CustomerStatementDTO getCustomerStatement(@PathVariable Long statementId) {
        return receivablesCollectionService.getCustomerStatement(statementId);
    }

    @PatchMapping("/receivables/customer-statements/{statementId}/send")
    public CustomerStatementDTO sendCustomerStatement(@PathVariable Long statementId) {
        return receivablesCollectionService.sendCustomerStatement(statementId);
    }

    @GetMapping("/customers/org/{organizationId}")
    public List<CustomerDTO> getCustomers(@PathVariable Long organizationId) {
        return customerService.getActiveCustomers(organizationId);
    }

    @PostMapping("/customers/org/{organizationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerDTO createCustomer(
            @PathVariable Long organizationId,
            @Valid @RequestBody CustomerDTO request) {
        return customerService.createCustomer(organizationId, request);
    }

    @PostMapping("/customer-invoices/org/{organizationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerInvoiceDTO createCustomerInvoice(
            @PathVariable Long organizationId,
            @Valid @RequestBody CustomerInvoiceDTO request) {
        return customerInvoiceService.createInvoice(organizationId, request, null);
    }

    @GetMapping("/customer-invoices/{invoiceId}")
    public CustomerInvoiceDTO getCustomerInvoice(@PathVariable Long invoiceId) {
        return customerInvoiceService.getInvoice(invoiceId);
    }

    @PutMapping("/customer-invoices/{invoiceId}")
    public CustomerInvoiceDTO updateCustomerInvoice(
            @PathVariable Long invoiceId,
            @Valid @RequestBody CustomerInvoiceDTO request) {
        return customerInvoiceService.updateCustomerInvoice(invoiceId, request);
    }

    @PatchMapping("/customer-invoices/{invoiceId}/send")
    public CustomerInvoiceDTO sendCustomerInvoice(@PathVariable Long invoiceId) {
        return customerInvoiceService.generateInvoice(invoiceId);
    }

    @PatchMapping("/customer-invoices/{invoiceId}/post")
    public CustomerInvoiceDTO postCustomerInvoice(@PathVariable Long invoiceId, Principal principal) {
        return customerInvoiceService.postInvoice(invoiceId, getUserName(principal));
    }

    @PatchMapping("/customer-invoices/{invoiceId}/payments")
    public CustomerInvoiceDTO recordCustomerPayment(
            @PathVariable Long invoiceId,
            @Valid @RequestBody PaymentRequest request,
            Principal principal) {
        return customerInvoiceService.recordPayment(invoiceId, request, getUserName(principal));
    }

    @GetMapping("/purchase-invoices/org/{organizationId}")
    public List<PurchaseInvoiceDTO> getPurchaseInvoices(@PathVariable Long organizationId) {
        return purchaseInvoiceService.getPurchaseInvoicesByOrganization(organizationId);
    }

    @GetMapping("/reports/aged-payables/org/{organizationId}")
    public AgingReportResponse getAgedPayables(@PathVariable Long organizationId) {
        return purchaseInvoiceService.generateAgedPayables(organizationId, LocalDate.now());
    }

    @GetMapping("/vendors/org/{organizationId}")
    public List<VendorDTO> getVendors(@PathVariable Long organizationId) {
        return vendorService.getActiveVendors(organizationId);
    }

    @PostMapping("/vendors/org/{organizationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public VendorDTO createVendor(
            @PathVariable Long organizationId,
            @Valid @RequestBody VendorDTO request) {
        return vendorService.createVendor(organizationId, request);
    }

    @PostMapping("/purchase-invoices/org/{organizationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseInvoiceDTO createPurchaseInvoice(
            @PathVariable Long organizationId,
            @Valid @RequestBody PurchaseInvoiceDTO request) {
        return purchaseInvoiceService.createPurchaseInvoice(organizationId, request, null);
    }

    @GetMapping("/purchase-invoices/{invoiceId}")
    public PurchaseInvoiceDTO getPurchaseInvoice(@PathVariable Long invoiceId) {
        return purchaseInvoiceService.getPurchaseInvoice(invoiceId);
    }

    @PutMapping("/purchase-invoices/{invoiceId}")
    public PurchaseInvoiceDTO updatePurchaseInvoice(
            @PathVariable Long invoiceId,
            @Valid @RequestBody PurchaseInvoiceDTO request) {
        return purchaseInvoiceService.updatePurchaseInvoice(invoiceId, request);
    }

    @PatchMapping("/purchase-invoices/{invoiceId}/submit")
    public PurchaseInvoiceDTO submitPurchaseInvoice(@PathVariable Long invoiceId) {
        return purchaseInvoiceService.submitPurchaseInvoice(invoiceId);
    }

    @PatchMapping("/purchase-invoices/{invoiceId}/approve")
    public PurchaseInvoiceDTO approvePurchaseInvoice(@PathVariable Long invoiceId, Principal principal) {
        return purchaseInvoiceService.approvePurchaseInvoice(invoiceId, getUserName(principal));
    }

    @PatchMapping("/purchase-invoices/{invoiceId}/reject")
    public PurchaseInvoiceDTO rejectPurchaseInvoice(
            @PathVariable Long invoiceId,
            @RequestBody(required = false) PaymentScheduleRequest request) {
        return purchaseInvoiceService.rejectPurchaseInvoice(invoiceId, request != null ? request.getReason() : null);
    }

    @PatchMapping("/purchase-invoices/{invoiceId}/post")
    public PurchaseInvoiceDTO postPurchaseInvoice(@PathVariable Long invoiceId, Principal principal) {
        return purchaseInvoiceService.postPurchaseInvoice(invoiceId, getUserName(principal));
    }

    @PatchMapping("/purchase-invoices/{invoiceId}/payments")
    public PurchaseInvoiceDTO recordSupplierPayment(
            @PathVariable Long invoiceId,
            @Valid @RequestBody PaymentRequest request,
            Principal principal) {
        return purchaseInvoiceService.recordPayment(invoiceId, request, getUserName(principal));
    }

    @GetMapping("/payables/schedules/org/{organizationId}")
    public List<PaymentScheduleDTO> getPaymentSchedules(
            @PathVariable Long organizationId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String status) {
        return paymentScheduleService.getSchedules(organizationId, from, to, status);
    }

    @GetMapping("/payables/due/org/{organizationId}")
    public List<PaymentScheduleDTO> getDuePaymentSchedules(
            @PathVariable Long organizationId,
            @RequestParam(required = false) LocalDate asOfDate) {
        return paymentScheduleService.getDueSchedules(organizationId, asOfDate);
    }

    @PatchMapping("/payables/schedules/{scheduleId}/approve")
    public PaymentScheduleDTO approvePaymentSchedule(@PathVariable Long scheduleId, Principal principal) {
        return paymentScheduleService.approveSchedule(scheduleId, getUserName(principal));
    }

    @PatchMapping("/payables/schedules/{scheduleId}/submit")
    public PaymentScheduleDTO submitPaymentSchedule(@PathVariable Long scheduleId) {
        return paymentScheduleService.submitSchedule(scheduleId);
    }

    @PatchMapping("/payables/schedules/{scheduleId}/reject")
    public PaymentScheduleDTO rejectPaymentSchedule(
            @PathVariable Long scheduleId,
            @RequestBody(required = false) PaymentScheduleRequest request) {
        return paymentScheduleService.rejectSchedule(scheduleId, request != null ? request.getReason() : null);
    }

    @PatchMapping("/payables/schedules/{scheduleId}/hold")
    public PaymentScheduleDTO holdPaymentSchedule(
            @PathVariable Long scheduleId,
            @RequestBody(required = false) PaymentScheduleRequest request) {
        return paymentScheduleService.holdSchedule(scheduleId, request);
    }

    @PatchMapping("/payables/schedules/{scheduleId}/reschedule")
    public PaymentScheduleDTO reschedulePaymentSchedule(
            @PathVariable Long scheduleId,
            @Valid @RequestBody PaymentScheduleRequest request) {
        return paymentScheduleService.reschedule(scheduleId, request);
    }

    @PatchMapping("/payables/schedules/{scheduleId}/cancel")
    public PaymentScheduleDTO cancelPaymentSchedule(
            @PathVariable Long scheduleId,
            @RequestBody(required = false) PaymentScheduleRequest request) {
        return paymentScheduleService.cancelSchedule(scheduleId, request != null ? request.getReason() : null);
    }

    @PostMapping("/payables/payment-runs/org/{organizationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentRunDTO createPaymentRun(
            @PathVariable Long organizationId,
            @Valid @RequestBody PaymentRunCreateRequest request,
            Principal principal) {
        return paymentRunService.createRun(organizationId, request, getUserName(principal));
    }

    @GetMapping("/payables/payment-runs/org/{organizationId}")
    public List<PaymentRunDTO> getPaymentRuns(@PathVariable Long organizationId) {
        return paymentRunService.getRuns(organizationId);
    }

    @GetMapping("/payables/payment-runs/{runId}")
    public PaymentRunDTO getPaymentRun(@PathVariable Long runId) {
        return paymentRunService.getRun(runId);
    }

    @PatchMapping("/payables/payment-runs/{runId}/submit")
    public PaymentRunDTO submitPaymentRun(@PathVariable Long runId) {
        return paymentRunService.submitRun(runId);
    }

    @PatchMapping("/payables/payment-runs/{runId}/approve")
    public PaymentRunDTO approvePaymentRun(@PathVariable Long runId, Principal principal) {
        return paymentRunService.approveRun(runId, getUserName(principal));
    }

    @PatchMapping("/payables/payment-runs/{runId}/reject")
    public PaymentRunDTO rejectPaymentRun(
            @PathVariable Long runId,
            @RequestBody(required = false) PaymentScheduleRequest request) {
        return paymentRunService.rejectRun(runId, request != null ? request.getReason() : null);
    }

    @PatchMapping("/payables/payment-runs/{runId}/execute")
    public PaymentRunDTO executePaymentRun(@PathVariable Long runId, Principal principal) {
        return paymentRunService.executeRun(runId, getUserName(principal));
    }

    @PatchMapping("/payables/payment-runs/{runId}/cancel")
    public PaymentRunDTO cancelPaymentRun(@PathVariable Long runId) {
        return paymentRunService.cancelRun(runId);
    }

    @GetMapping("/payables/forecast/org/{organizationId}")
    public CashRequirementForecastResponse getPayablesForecast(
            @PathVariable Long organizationId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String bucket) {
        return paymentScheduleService.forecast(organizationId, from, to, bucket);
    }

    @PostMapping("/payables/supplier-statements/org/{organizationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierStatementDTO createSupplierStatement(
            @PathVariable Long organizationId,
            @Valid @RequestBody SupplierStatementCreateRequest request) {
        return supplierStatementService.createStatement(organizationId, request);
    }

    @GetMapping("/payables/supplier-statements/org/{organizationId}")
    public List<SupplierStatementDTO> getSupplierStatements(@PathVariable Long organizationId) {
        return supplierStatementService.getStatements(organizationId);
    }

    @GetMapping("/payables/supplier-statements/{statementId}")
    public SupplierStatementDTO getSupplierStatement(@PathVariable Long statementId) {
        return supplierStatementService.getStatement(statementId);
    }

    @PatchMapping("/payables/supplier-statements/{statementId}/auto-match")
    public SupplierStatementDTO autoMatchSupplierStatement(@PathVariable Long statementId) {
        return supplierStatementService.autoMatch(statementId);
    }

    @PatchMapping("/payables/supplier-statement-lines/{lineId}/dispute")
    public SupplierStatementDTO disputeSupplierStatementLine(
            @PathVariable Long lineId,
            @RequestBody(required = false) PaymentScheduleRequest request) {
        return supplierStatementService.markLineDisputed(lineId, request != null ? request.getReason() : null);
    }

    @PatchMapping("/payables/supplier-statements/{statementId}/complete")
    public SupplierStatementDTO completeSupplierStatement(@PathVariable Long statementId) {
        return supplierStatementService.completeStatement(statementId);
    }

    // Expense endpoints
    @GetMapping("/expenses/org/{organizationId}")
    public List<ExpenseDTO> getExpenses(@PathVariable Long organizationId) {
        return expenseService.getExpensesByOrganization(organizationId);
    }

    @PostMapping("/expenses/org/{organizationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseDTO createExpense(
            @PathVariable Long organizationId,
            @Valid @RequestBody ExpenseDTO request) {
        return expenseService.createExpense(organizationId, request, null);
    }

    @GetMapping("/expenses/{expenseId}")
    public ExpenseDTO getExpense(@PathVariable Long expenseId) {
        return expenseService.getExpense(expenseId);
    }

    @PutMapping("/expenses/{expenseId}")
    public ExpenseDTO updateExpense(
            @PathVariable Long expenseId,
            @Valid @RequestBody ExpenseDTO request) {
        return expenseService.updateExpense(expenseId, request);
    }

    @PatchMapping("/expenses/{expenseId}/submit")
    public ExpenseDTO submitExpense(@PathVariable Long expenseId) {
        return expenseService.submitExpense(expenseId);
    }

    @PatchMapping("/expenses/{expenseId}/approve")
    public ExpenseDTO approveExpense(@PathVariable Long expenseId, Principal principal) {
        return expenseService.approveExpense(expenseId, getUserName(principal));
    }

    @PatchMapping("/expenses/{expenseId}/reject")
    public ExpenseDTO rejectExpense(
            @PathVariable Long expenseId,
            @RequestBody(required = false) PaymentScheduleRequest request) {
        return expenseService.rejectExpense(expenseId, request != null ? request.getReason() : null);
    }

    @PatchMapping("/expenses/{expenseId}/post")
    public ExpenseDTO postExpense(@PathVariable Long expenseId, Principal principal) {
        return expenseService.postExpense(expenseId, getUserName(principal));
    }

    @PatchMapping("/expenses/{expenseId}/payments")
    public ExpenseDTO recordExpensePayment(
            @PathVariable Long expenseId,
            @Valid @RequestBody PaymentRequest request,
            Principal principal) {
        return expenseService.recordPayment(expenseId, request, getUserName(principal));
    }

    private ManualJournalDTO toDTO(ManualJournalCreateRequest request) {
        return ManualJournalDTO.builder()
                .branchId(request.getBranchId())
                .description(request.getDescription())
                .journalDate(request.getJournalDate())
                .build();
    }

    private JournalLineDTO toDTO(JournalLineCreateRequest request) {
        return JournalLineDTO.builder()
                .chartOfAccountId(request.getChartOfAccountId())
                .debitAmount(request.getDebitAmount())
                .creditAmount(request.getCreditAmount())
                .departmentCode(request.getDepartmentCode())
                .projectCode(request.getProjectCode())
                .branchCode(request.getBranchCode())
                .narration(request.getNarration())
                .lineSequence(request.getLineSequence())
                .build();
    }

    private JournalLineDTO toDTO(JournalLineUpdateRequest request) {
        return JournalLineDTO.builder()
                .chartOfAccountId(request.getChartOfAccountId())
                .debitAmount(request.getDebitAmount())
                .creditAmount(request.getCreditAmount())
                .departmentCode(request.getDepartmentCode())
                .projectCode(request.getProjectCode())
                .branchCode(request.getBranchCode())
                .narration(request.getNarration())
                .lineSequence(request.getLineSequence())
                .build();
    }

    private String getUserName(Principal principal) {
        return principal != null ? principal.getName() : "system";
    }
}
