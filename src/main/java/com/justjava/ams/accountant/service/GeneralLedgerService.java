package com.justjava.ams.accountant.service;

import com.justjava.ams.accountant.dto.GeneralLedgerDTO;
import com.justjava.ams.accountant.entity.ChartOfAccounts;
import com.justjava.ams.accountant.entity.GeneralLedger;
import com.justjava.ams.accountant.entity.JournalLine;
import com.justjava.ams.accountant.entity.ManualJournal;
import com.justjava.ams.accountant.repository.ChartOfAccountsRepository;
import com.justjava.ams.accountant.repository.GeneralLedgerRepository;
import com.justjava.ams.accountant.repository.JournalLineRepository;
import com.justjava.ams.accountant.repository.ManualJournalRepository;
import com.justjava.ams.auditor.dto.AuditLogDTO;
import com.justjava.ams.auditor.service.AuditLogService;
import com.justjava.ams.common.entity.User;
import com.justjava.ams.common.repository.UserRepository;
import com.justjava.ams.cfo.service.BudgetControlService;
import com.justjava.ams.financeAdmin.entity.ModuleControl;
import com.justjava.ams.financeAdmin.service.ModuleControlService;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class GeneralLedgerService {

    private final GeneralLedgerRepository generalLedgerRepository;
    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final UserRepository userRepository;
    private final ManualJournalRepository manualJournalRepository;
    private final JournalLineRepository journalLineRepository;
    private final AuditLogService auditLogService;
    private final BudgetControlService budgetControlService;
    private final ModuleControlService moduleControlService;

    public GeneralLedgerDTO createEntry(GeneralLedgerDTO dto, Long userId) {
        ChartOfAccounts account = chartOfAccountsRepository.findById(dto.getAccountId())
                .orElseThrow(() -> new RuntimeException("Account not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        GeneralLedger entry = GeneralLedger.builder()
                .account(account)
                .journalNumber(dto.getJournalNumber())
                .transactionDate(dto.getTransactionDate())
                .debitCredit(GeneralLedger.DebitCredit.valueOf(dto.getDebitCredit()))
                .amount(dto.getAmount())
                .description(dto.getDescription())
                .referenceNumber(dto.getReferenceNumber())
                .createdByUser(user)
                .notes(dto.getNotes())
                .status(GeneralLedger.TransactionStatus.PENDING)
                .sourceType(GeneralLedger.SourceType.MANUAL_JOURNAL)
                .sourceId(0L)
                .postingBatchId("DIRECT-" + System.nanoTime())
                .build();

        GeneralLedger saved = generalLedgerRepository.save(entry);
        return mapToDTO(saved);
    }

    public List<GeneralLedgerDTO> postJournalEntriesFromManualJournal(Long journalId, String postedBy) {
        ManualJournal journal = manualJournalRepository.findById(journalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Journal not found"));

        if (!ManualJournal.JournalStatus.APPROVED.equals(journal.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only APPROVED journals can be posted to GL");
        }

        User postingUser = null;
        if (postedBy != null) {
            postingUser = userRepository.findByUsername(postedBy).orElse(null);
        }

        ensureNotAlreadyPosted(GeneralLedger.SourceType.MANUAL_JOURNAL, journalId);

        List<JournalLine> lines = journalLineRepository.findByManualJournalId(journalId);
        if (lines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Journal must have lines before posting");
        }
        requireTransactionWithinLimit(
                journal.getOrganization().getId(),
                GeneralLedger.SourceType.MANUAL_JOURNAL,
                totalDebits(lines));
        consumeBudgetForManualJournal(journal, lines, GeneralLedger.SourceType.MANUAL_JOURNAL, journalId);

        String journalNumber = "MJ-" + journal.getId();
        String postingBatchId = postingBatchId(GeneralLedger.SourceType.MANUAL_JOURNAL, journalId);
        LocalDateTime postedAt = LocalDateTime.now();
        List<GeneralLedger> postedEntries = new java.util.ArrayList<>();
        for (JournalLine line : lines) {
            if (line.getDebitAmount() != null && line.getDebitAmount().compareTo(BigDecimal.ZERO) > 0) {
                GeneralLedger debit = postedEntryBuilder(
                                line.getChartOfAccounts(),
                                journalNumber,
                                journal.getJournalDate(),
                                GeneralLedger.DebitCredit.DEBIT,
                                line.getDebitAmount(),
                                journal.getDescription() != null ? journal.getDescription() : line.getNarration(),
                                line.getNarration(),
                                postingUser,
                                GeneralLedger.SourceType.MANUAL_JOURNAL,
                                journalId,
                                postingBatchId,
                                postedBy,
                                postedAt)
                        .branch(journal.getBranch())
                        .build();
                postedEntries.add(generalLedgerRepository.save(debit));
            }

            if (line.getCreditAmount() != null && line.getCreditAmount().compareTo(BigDecimal.ZERO) > 0) {
                GeneralLedger credit = postedEntryBuilder(
                                line.getChartOfAccounts(),
                                journalNumber,
                                journal.getJournalDate(),
                                GeneralLedger.DebitCredit.CREDIT,
                                line.getCreditAmount(),
                                journal.getDescription() != null ? journal.getDescription() : line.getNarration(),
                                line.getNarration(),
                                postingUser,
                                GeneralLedger.SourceType.MANUAL_JOURNAL,
                                journalId,
                                postingBatchId,
                                postedBy,
                                postedAt)
                        .branch(journal.getBranch())
                        .build();
                postedEntries.add(generalLedgerRepository.save(credit));
            }
        }

        try {
            AuditLogDTO log = AuditLogDTO.builder()
                    .organizationId(journal.getOrganization().getId())
                    .entityType("GeneralLedger")
                    .entityId(journal.getId())
                    .action("POST")
                    .newValue(journalNumber)
                    .description("Posted " + postedEntries.size() + " general ledger entries for ManualJournal " + journal.getId())
                    .build();
            auditLogService.createAuditLog(journal.getOrganization().getId(), log);
        } catch (Exception ex) {
        }

        return postedEntries.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public GeneralLedgerDTO getEntry(Long entryId) {
        GeneralLedger entry = generalLedgerRepository.findById(entryId)
                .orElseThrow(() -> new RuntimeException("Entry not found"));
        return mapToDTO(entry);
    }

    public List<GeneralLedgerDTO> getEntriesByAccount(Long accountId) {
        return generalLedgerRepository.findByAccountIdOrderByTransactionDateDesc(accountId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<GeneralLedgerDTO> getEntriesByJournalId(Long journalId) {
        return generalLedgerRepository.findBySourceTypeAndSourceId(GeneralLedger.SourceType.MANUAL_JOURNAL, journalId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<GeneralLedgerDTO> postCustomerInvoice(
            Long invoiceId,
            String invoiceNumber,
            LocalDate transactionDate,
            ChartOfAccounts receivableAccount,
            List<GeneralLedger> creditEntries,
            String description,
            String postedBy) {
        ensureNotAlreadyPosted(GeneralLedger.SourceType.CUSTOMER_INVOICE, invoiceId);
        User postingUser = postedBy != null ? userRepository.findByUsername(postedBy).orElse(null) : null;
        String postingBatchId = postingBatchId(GeneralLedger.SourceType.CUSTOMER_INVOICE, invoiceId);
        LocalDateTime postedAt = LocalDateTime.now();
        BigDecimal totalCredits = creditEntries.stream()
                .map(GeneralLedger::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        requireTransactionWithinLimit(receivableAccount.getOrganization().getId(), GeneralLedger.SourceType.CUSTOMER_INVOICE, totalCredits);
        List<GeneralLedger> entries = new ArrayList<>();
        entries.add(generalLedgerRepository.save(postedEntryBuilder(
                receivableAccount, "AR-" + invoiceNumber, transactionDate, GeneralLedger.DebitCredit.DEBIT,
                totalCredits, description, null, postingUser, GeneralLedger.SourceType.CUSTOMER_INVOICE,
                invoiceId, postingBatchId, postedBy, postedAt).build()));
        for (GeneralLedger creditEntry : creditEntries) {
            entries.add(generalLedgerRepository.save(postedEntryBuilder(
                    creditEntry.getAccount(), "AR-" + invoiceNumber, transactionDate, GeneralLedger.DebitCredit.CREDIT,
                    creditEntry.getAmount(), creditEntry.getDescription(), creditEntry.getNotes(), postingUser,
                    GeneralLedger.SourceType.CUSTOMER_INVOICE, invoiceId, postingBatchId, postedBy, postedAt).build()));
        }
        return entries.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    public List<GeneralLedgerDTO> postPurchaseInvoice(
            Long invoiceId,
            String purchaseOrderNumber,
            LocalDate transactionDate,
            ChartOfAccounts payableAccount,
            List<GeneralLedger> debitEntries,
            String description,
            String postedBy,
            List<BudgetControlService.ExpenseBudgetLine> budgetLines) {
        return postPayableDocument(
                GeneralLedger.SourceType.PURCHASE_INVOICE,
                invoiceId,
                purchaseOrderNumber,
                transactionDate,
                payableAccount,
                debitEntries,
                description,
                postedBy,
                budgetLines);
    }

    public List<GeneralLedgerDTO> postExpense(
            Long expenseId,
            String expenseNumber,
            LocalDate transactionDate,
            ChartOfAccounts payableAccount,
            List<GeneralLedger> debitEntries,
            String description,
            String postedBy,
            List<BudgetControlService.ExpenseBudgetLine> budgetLines) {
        return postPayableDocument(
                GeneralLedger.SourceType.EXPENSE,
                expenseId,
                expenseNumber,
                transactionDate,
                payableAccount,
                debitEntries,
                description,
                postedBy,
                budgetLines);
    }

    private List<GeneralLedgerDTO> postPayableDocument(
            GeneralLedger.SourceType sourceType,
            Long sourceId,
            String documentNumber,
            LocalDate transactionDate,
            ChartOfAccounts payableAccount,
            List<GeneralLedger> debitEntries,
            String description,
            String postedBy,
            List<BudgetControlService.ExpenseBudgetLine> budgetLines) {
        ensureNotAlreadyPosted(sourceType, sourceId);
        consumeBudgetIfRequired(
                payableAccount.getOrganization().getId(),
                sourceType,
                sourceId,
                transactionDate,
                budgetLines);
        User postingUser = postedBy != null ? userRepository.findByUsername(postedBy).orElse(null) : null;
        String postingBatchId = postingBatchId(sourceType, sourceId);
        LocalDateTime postedAt = LocalDateTime.now();
        BigDecimal totalDebits = debitEntries.stream()
                .map(GeneralLedger::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        requireTransactionWithinLimit(payableAccount.getOrganization().getId(), sourceType, totalDebits);
        List<GeneralLedger> entries = new ArrayList<>();
        for (GeneralLedger debitEntry : debitEntries) {
            entries.add(generalLedgerRepository.save(postedEntryBuilder(
                    debitEntry.getAccount(), "AP-" + documentNumber, transactionDate, GeneralLedger.DebitCredit.DEBIT,
                    debitEntry.getAmount(), debitEntry.getDescription(), debitEntry.getNotes(), postingUser,
                    sourceType, sourceId, postingBatchId, postedBy, postedAt).build()));
        }
        entries.add(generalLedgerRepository.save(postedEntryBuilder(
                payableAccount, "AP-" + documentNumber, transactionDate, GeneralLedger.DebitCredit.CREDIT,
                totalDebits, description, null, postingUser, sourceType,
                sourceId, postingBatchId, postedBy, postedAt).build()));
        return entries.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    public List<GeneralLedgerDTO> postCustomerPayment(
            Long invoiceId,
            String invoiceNumber,
            LocalDate paymentDate,
            ChartOfAccounts bankAccount,
            ChartOfAccounts receivableAccount,
            BigDecimal amount,
            String description,
            String postedBy) {
        return postPayment(
                GeneralLedger.SourceType.CUSTOMER_PAYMENT,
                invoiceId,
                "AR-PAY-" + invoiceNumber,
                paymentDate,
                bankAccount,
                receivableAccount,
                amount,
                description,
                postedBy);
    }

    public List<GeneralLedgerDTO> postSupplierPayment(
            Long invoiceId,
            String purchaseOrderNumber,
            LocalDate paymentDate,
            ChartOfAccounts payableAccount,
            ChartOfAccounts bankAccount,
            BigDecimal amount,
            String description,
            String postedBy) {
        return postPayment(
                GeneralLedger.SourceType.SUPPLIER_PAYMENT,
                invoiceId,
                "AP-PAY-" + purchaseOrderNumber,
                paymentDate,
                payableAccount,
                bankAccount,
                amount,
                description,
                postedBy);
    }

    public List<GeneralLedgerDTO> postExpensePayment(
            Long expenseId,
            String expenseNumber,
            LocalDate paymentDate,
            ChartOfAccounts payableAccount,
            ChartOfAccounts bankAccount,
            BigDecimal amount,
            String description,
            String postedBy) {
        return postPayment(
                GeneralLedger.SourceType.EXPENSE_PAYMENT,
                expenseId,
                "EXP-PAY-" + expenseNumber,
                paymentDate,
                payableAccount,
                bankAccount,
                amount,
                description,
                postedBy);
    }

    public List<GeneralLedgerDTO> postYearEndClose(
            Long organizationId,
            Integer fiscalYear,
            LocalDate closingDate,
            List<GeneralLedger> debitEntries,
            List<GeneralLedger> creditEntries,
            String postedBy) {
        Long sourceId = Long.valueOf(String.valueOf(organizationId) + fiscalYear);
        User postingUser = postedBy != null ? userRepository.findByUsername(postedBy).orElse(null) : null;
        String postingBatchId = postingBatchId(GeneralLedger.SourceType.YEAR_END_CLOSE, sourceId);
        LocalDateTime postedAt = LocalDateTime.now();
        String journalNumber = "YEC-" + fiscalYear + "-" + organizationId;
        BigDecimal totalDebits = debitEntries.stream()
                .map(GeneralLedger::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        requireTransactionWithinLimit(organizationId, GeneralLedger.SourceType.YEAR_END_CLOSE, totalDebits);
        List<GeneralLedger> entries = new ArrayList<>();
        for (GeneralLedger debitEntry : debitEntries) {
            entries.add(generalLedgerRepository.save(postedEntryBuilder(
                    debitEntry.getAccount(), journalNumber, closingDate, GeneralLedger.DebitCredit.DEBIT,
                    debitEntry.getAmount(), debitEntry.getDescription(), debitEntry.getNotes(), postingUser,
                    GeneralLedger.SourceType.YEAR_END_CLOSE, sourceId, postingBatchId, postedBy, postedAt).build()));
        }
        for (GeneralLedger creditEntry : creditEntries) {
            entries.add(generalLedgerRepository.save(postedEntryBuilder(
                    creditEntry.getAccount(), journalNumber, closingDate, GeneralLedger.DebitCredit.CREDIT,
                    creditEntry.getAmount(), creditEntry.getDescription(), creditEntry.getNotes(), postingUser,
                    GeneralLedger.SourceType.YEAR_END_CLOSE, sourceId, postingBatchId, postedBy, postedAt).build()));
        }
        return entries.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    public List<GeneralLedgerDTO> postDepreciationJournalEntriesFromManualJournal(
            Long journalId,
            Long depreciationImportId,
            String externalBatchId,
            String postedBy) {
        ManualJournal journal = manualJournalRepository.findById(journalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Journal not found"));

        if (!ManualJournal.JournalStatus.APPROVED.equals(journal.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only APPROVED journals can be posted to GL");
        }

        ensureNotAlreadyPosted(GeneralLedger.SourceType.FIXED_ASSET_DEPRECIATION, depreciationImportId);

        List<JournalLine> lines = journalLineRepository.findByManualJournalId(journalId);
        if (lines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Journal must have lines before posting");
        }

        requireTransactionWithinLimit(
                journal.getOrganization().getId(),
                GeneralLedger.SourceType.FIXED_ASSET_DEPRECIATION,
                totalDebits(lines));
        consumeBudgetForManualJournal(journal, lines, GeneralLedger.SourceType.FIXED_ASSET_DEPRECIATION, depreciationImportId);

        User postingUser = postedBy != null ? userRepository.findByUsername(postedBy).orElse(null) : null;
        String normalizedBatchId = externalBatchId != null && !externalBatchId.isBlank()
                ? externalBatchId.trim()
                : String.valueOf(depreciationImportId);
        String journalNumber = "DEP-" + normalizedBatchId;
        String postingBatchId = postingBatchId(GeneralLedger.SourceType.FIXED_ASSET_DEPRECIATION, depreciationImportId);
        LocalDateTime postedAt = LocalDateTime.now();
        List<GeneralLedger> postedEntries = new ArrayList<>();

        for (JournalLine line : lines) {
            if (line.getDebitAmount() != null && line.getDebitAmount().compareTo(BigDecimal.ZERO) > 0) {
                postedEntries.add(generalLedgerRepository.save(postedEntryBuilder(
                        line.getChartOfAccounts(), journalNumber, journal.getJournalDate(), GeneralLedger.DebitCredit.DEBIT,
                        line.getDebitAmount(), journal.getDescription(), line.getNarration(), postingUser,
                        GeneralLedger.SourceType.FIXED_ASSET_DEPRECIATION, depreciationImportId, postingBatchId, postedBy, postedAt)
                        .branch(journal.getBranch())
                        .build()));
            }

            if (line.getCreditAmount() != null && line.getCreditAmount().compareTo(BigDecimal.ZERO) > 0) {
                postedEntries.add(generalLedgerRepository.save(postedEntryBuilder(
                        line.getChartOfAccounts(), journalNumber, journal.getJournalDate(), GeneralLedger.DebitCredit.CREDIT,
                        line.getCreditAmount(), journal.getDescription(), line.getNarration(), postingUser,
                        GeneralLedger.SourceType.FIXED_ASSET_DEPRECIATION, depreciationImportId, postingBatchId, postedBy, postedAt)
                        .branch(journal.getBranch())
                        .build()));
            }
        }

        return postedEntries.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<GeneralLedgerDTO> getPostedEntriesByOrganizationAndDateRange(Long organizationId, LocalDate fromDate, LocalDate toDate) {
        // Validate dates
        if (fromDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From date is required");
        }
        if (toDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "To date is required");
        }
        if (fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From date must not be after to date");
        }

        return generalLedgerRepository.findPostedEntriesByOrganizationAndDateRange(organizationId, fromDate, toDate)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<GeneralLedgerDTO> getPostedEntriesByOrganizationAsOf(Long organizationId, LocalDate asOfDate) {
        // Validate date
        if (asOfDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "As of date is required");
        }

        return generalLedgerRepository.findPostedEntriesByOrganizationAsOf(organizationId, asOfDate)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<GeneralLedgerDTO> getEntriesByDateRange(LocalDate startDate, LocalDate endDate) {
        return generalLedgerRepository.findByTransactionDateBetween(startDate, endDate)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public GeneralLedgerDTO approveEntry(Long entryId) {
        GeneralLedger entry = generalLedgerRepository.findById(entryId)
                .orElseThrow(() -> new RuntimeException("Entry not found"));
        entry.setStatus(GeneralLedger.TransactionStatus.APPROVED);
        GeneralLedger updated = generalLedgerRepository.save(entry);
        return mapToDTO(updated);
    }

    private GeneralLedgerDTO mapToDTO(GeneralLedger entry) {
        return GeneralLedgerDTO.builder()
                .id(entry.getId())
                .accountId(entry.getAccount().getId())
                .journalNumber(entry.getJournalNumber())
                .transactionDate(entry.getTransactionDate())
                .debitCredit(entry.getDebitCredit().toString())
                .amount(entry.getAmount())
                .description(entry.getDescription())
                .referenceNumber(entry.getReferenceNumber())
                .createdByUserId(entry.getCreatedByUser() != null ? entry.getCreatedByUser().getId() : null)
                .notes(entry.getNotes())
                .status(entry.getStatus().toString())
                .fiscalPeriodId(entry.getFiscalPeriod() != null ? entry.getFiscalPeriod().getId() : null)
                .sourceType(entry.getSourceType() != null ? entry.getSourceType().toString() : null)
                .sourceId(entry.getSourceId())
                .postingBatchId(entry.getPostingBatchId())
                .postedBy(entry.getPostedBy())
                .postedAt(entry.getPostedAt())
                .createdAt(entry.getCreatedAt())
                .updatedAt(entry.getUpdatedAt())
                .build();
    }

    private void ensureNotAlreadyPosted(GeneralLedger.SourceType sourceType, Long sourceId) {
        List<GeneralLedger> existing = generalLedgerRepository.findBySourceTypeAndSourceId(sourceType, sourceId);
        if (existing != null && !existing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "General ledger entries already exist for this source document");
        }
    }

    private void consumeBudgetForManualJournal(
            ManualJournal journal,
            List<JournalLine> lines,
            GeneralLedger.SourceType sourceType,
            Long sourceId) {
        List<BudgetControlService.ExpenseBudgetLine> budgetLines = lines.stream()
                .filter(line -> line.getDebitAmount() != null && line.getDebitAmount().compareTo(BigDecimal.ZERO) > 0)
                .map(line -> new BudgetControlService.ExpenseBudgetLine(
                        line.getId(),
                        line.getChartOfAccounts(),
                        line.getDebitAmount(),
                        line.getNarration()))
                .collect(Collectors.toList());
        consumeBudgetIfRequired(journal.getOrganization().getId(), sourceType, sourceId, journal.getJournalDate(), budgetLines);
    }

    private BigDecimal totalDebits(List<JournalLine> lines) {
        return lines.stream()
                .map(line -> line.getDebitAmount() != null ? line.getDebitAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void consumeBudgetIfRequired(
            Long organizationId,
            GeneralLedger.SourceType sourceType,
            Long sourceId,
            LocalDate transactionDate,
            List<BudgetControlService.ExpenseBudgetLine> budgetLines) {
        if (!isBudgetControlledSource(sourceType) || budgetLines == null || budgetLines.isEmpty()) {
            return;
        }
        budgetControlService.consumeExpenseLines(organizationId, sourceType, sourceId, transactionDate, budgetLines);
    }

    private boolean isBudgetControlledSource(GeneralLedger.SourceType sourceType) {
        return GeneralLedger.SourceType.MANUAL_JOURNAL.equals(sourceType)
                || GeneralLedger.SourceType.PURCHASE_INVOICE.equals(sourceType)
                || GeneralLedger.SourceType.EXPENSE.equals(sourceType);
    }

    private void requireTransactionWithinLimit(Long organizationId, GeneralLedger.SourceType sourceType, BigDecimal amount) {
        ModuleControl.ModuleType moduleType = moduleTypeForSource(sourceType);
        if (moduleType != null) {
            moduleControlService.requireTransactionWithinLimit(organizationId, moduleType, amount);
        }
    }

    private ModuleControl.ModuleType moduleTypeForSource(GeneralLedger.SourceType sourceType) {
        return switch (sourceType) {
            case MANUAL_JOURNAL, YEAR_END_CLOSE -> ModuleControl.ModuleType.GENERAL_LEDGER;
            case PURCHASE_INVOICE, EXPENSE, SUPPLIER_PAYMENT, EXPENSE_PAYMENT -> ModuleControl.ModuleType.ACCOUNTS_PAYABLE;
            case CUSTOMER_INVOICE, CUSTOMER_PAYMENT -> ModuleControl.ModuleType.ACCOUNTS_RECEIVABLE;
            case FIXED_ASSET, FIXED_ASSET_DEPRECIATION -> ModuleControl.ModuleType.FIXED_ASSETS;
            case BANK_TRANSACTION -> ModuleControl.ModuleType.BANKING;
        };
    }

    private String postingBatchId(GeneralLedger.SourceType sourceType, Long sourceId) {
        return sourceType.name() + "-" + sourceId + "-" + System.nanoTime();
    }

    private List<GeneralLedgerDTO> postPayment(
            GeneralLedger.SourceType sourceType,
            Long sourceId,
            String journalNumber,
            LocalDate paymentDate,
            ChartOfAccounts debitAccount,
            ChartOfAccounts creditAccount,
            BigDecimal amount,
            String description,
            String postedBy) {
        User postingUser = postedBy != null ? userRepository.findByUsername(postedBy).orElse(null) : null;
        String postingBatchId = postingBatchId(sourceType, sourceId);
        LocalDateTime postedAt = LocalDateTime.now();
        requireTransactionWithinLimit(debitAccount.getOrganization().getId(), sourceType, amount);
        List<GeneralLedger> entries = new ArrayList<>();
        entries.add(generalLedgerRepository.save(postedEntryBuilder(
                debitAccount, journalNumber, paymentDate, GeneralLedger.DebitCredit.DEBIT, amount,
                description, null, postingUser, sourceType, sourceId, postingBatchId, postedBy, postedAt).build()));
        entries.add(generalLedgerRepository.save(postedEntryBuilder(
                creditAccount, journalNumber, paymentDate, GeneralLedger.DebitCredit.CREDIT, amount,
                description, null, postingUser, sourceType, sourceId, postingBatchId, postedBy, postedAt).build()));
        return entries.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    private GeneralLedger.GeneralLedgerBuilder postedEntryBuilder(
            ChartOfAccounts account,
            String journalNumber,
            LocalDate transactionDate,
            GeneralLedger.DebitCredit debitCredit,
            BigDecimal amount,
            String description,
            String notes,
            User postingUser,
            GeneralLedger.SourceType sourceType,
            Long sourceId,
            String postingBatchId,
            String postedBy,
            LocalDateTime postedAt) {
        return GeneralLedger.builder()
                .account(account)
                .journalNumber(journalNumber)
                .transactionDate(transactionDate)
                .debitCredit(debitCredit)
                .amount(amount)
                .description(description)
                .createdByUser(postingUser)
                .notes(notes)
                .status(GeneralLedger.TransactionStatus.POSTED)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .postingBatchId(postingBatchId)
                .postedBy(postedBy)
                .postedAt(postedAt);
    }
}
