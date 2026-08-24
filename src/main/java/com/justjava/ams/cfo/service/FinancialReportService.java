package com.justjava.ams.cfo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.justjava.ams.accountant.entity.ChartOfAccounts;
import com.justjava.ams.accountant.entity.GeneralLedger;
import com.justjava.ams.accountant.repository.GeneralLedgerRepository;
import com.justjava.ams.auditor.service.AuditLogService;
import com.justjava.ams.auditor.service.SecurityEventService;
import com.justjava.ams.cfo.dto.*;
import com.justjava.ams.cfo.entity.BudgetLine;
import com.justjava.ams.cfo.entity.FinancialReport;
import com.justjava.ams.cfo.repository.BudgetConsumptionRepository;
import com.justjava.ams.cfo.repository.BudgetLineRepository;
import com.justjava.ams.cfo.repository.FinancialReportRepository;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import com.justjava.ams.financeAdmin.dto.ApprovalDecisionDTO;
import com.justjava.ams.financeAdmin.dto.ApprovalEvaluationRequest;
import com.justjava.ams.financeAdmin.entity.ModuleControl;
import com.justjava.ams.financeAdmin.service.ApprovalWorkflowService;
import com.justjava.ams.financeAdmin.service.ModuleControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class FinancialReportService {

    private final FinancialReportRepository financialReportRepository;
    private final OrganizationRepository organizationRepository;
    private final GeneralLedgerRepository generalLedgerRepository;
    private final AuditLogService auditLogService;
    private final SecurityEventService securityEventService;
    private final ObjectMapper objectMapper;
    private final ModuleControlService moduleControlService;
    private final BudgetLineRepository budgetLineRepository;
    private final BudgetConsumptionRepository budgetConsumptionRepository;
    private final ApprovalWorkflowService approvalWorkflowService;

    public FinancialReportDTO createReport(Long organizationId, FinancialReportDTO dto) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Organization not found"));

        FinancialReport report = FinancialReport.builder()
                .organization(organization)
                .reportType(FinancialReport.ReportType.valueOf(dto.getReportType()))
                .reportName(dto.getReportName())
                .reportDate(dto.getReportDate())
                .fromDate(dto.getFromDate())
                .toDate(dto.getToDate())
                .generatedBy(dto.getGeneratedBy())
                .reportContent(dto.getReportContent())
                .notes(dto.getNotes())
                .build();

        return mapToDTO(financialReportRepository.save(report));
    }

    public FinancialReportDTO getReport(Long reportId) {
        FinancialReport report = financialReportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Report not found"));
        return mapToDTO(report);
    }

    public List<FinancialReportDTO> getReportsByType(Long organizationId, String reportType) {
        // validate organization
        organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Organization not found"));

        try {
            FinancialReport.ReportType rt = FinancialReport.ReportType.valueOf(reportType);
            return financialReportRepository.findByOrganizationIdAndReportType(organizationId, rt)
                    .stream()
                    .map(this::mapToDTO)
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid report type");
        }
    }

    public List<FinancialReportDTO> getReportsByDateRange(Long organizationId, LocalDate fromDate, LocalDate toDate) {
        return financialReportRepository.findByOrganizationIdAndReportDateBetween(organizationId, fromDate, toDate)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public FinancialReportDTO submitReportForApproval(Long reportId, String submittedBy) {
        FinancialReport report = financialReportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Report not found"));
        if (!FinancialReport.ReportStatus.DRAFT.equals(report.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT reports can be submitted for approval");
        }
        ApprovalDecisionDTO decision = approvalWorkflowService.submitForApproval(ApprovalEvaluationRequest.builder()
                .organizationId(report.getOrganization().getId())
                .moduleType(ModuleControl.ModuleType.REPORTING)
                .transactionType(report.getReportType().name())
                .entityType("FinancialReport")
                .entityId(report.getId())
                .amount(BigDecimal.ZERO)
                .submittedBy(submittedBy)
                .build());
        report.setApprovalRequestId(decision.getApprovalRequestId());
        report.setApprovalRuleId(decision.getApprovalRuleId());
        report.setApprovalRuleName(decision.getApprovalRuleName());
        report.setRequiredApprovals(decision.getRequiredApprovals());
        report.setStatus(Boolean.TRUE.equals(decision.getApprovalRequired())
                ? FinancialReport.ReportStatus.PENDING_REVIEW
                : FinancialReport.ReportStatus.APPROVED);
        if (!Boolean.TRUE.equals(decision.getApprovalRequired())) {
            report.setApprovedBy(submittedBy);
            report.setApprovedDate(LocalDate.now());
        }
        return mapToDTO(financialReportRepository.save(report));
    }

    public FinancialReportDTO approveReport(Long reportId, String approvedBy) {
        FinancialReport report = financialReportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Report not found"));
        if (FinancialReport.ReportStatus.APPROVED.equals(report.getStatus())
                || FinancialReport.ReportStatus.PUBLISHED.equals(report.getStatus())
                || FinancialReport.ReportStatus.ARCHIVED.equals(report.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Report has already been finalized");
        }
        if (!FinancialReport.ReportStatus.PENDING_REVIEW.equals(report.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only PENDING_REVIEW reports can be approved");
        }
        if (report.getApprovalRequestId() != null) {
            approvalWorkflowService.approvePending("FinancialReport", report.getId(), "Approved by " + approvedBy);
        }
        report.setStatus(FinancialReport.ReportStatus.APPROVED);
        report.setApprovedBy(approvedBy);
        report.setApprovedDate(LocalDate.now());
        return mapToDTO(financialReportRepository.save(report));
    }

    public FinancialReportDTO rejectReport(Long reportId, String rejectionReason) {
        FinancialReport report = financialReportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Report not found"));
        if (!FinancialReport.ReportStatus.PENDING_REVIEW.equals(report.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only PENDING_REVIEW reports can be rejected");
        }
        if (report.getApprovalRequestId() != null) {
            approvalWorkflowService.rejectPending("FinancialReport", report.getId(), required(rejectionReason, "Rejection reason is required"));
        }
        report.setStatus(FinancialReport.ReportStatus.REJECTED);
        return mapToDTO(financialReportRepository.save(report));
    }

    // Step 9 implementation: new methods for generating financial reports

    /**
     * Generate a financial report based on the request and optionally persist it.
     */
    public FinancialReportSummaryResponse generateReport(Long organizationId, FinancialReportGenerateRequest request, String generatedBy) {
        // Validate organization
        organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        // Validate report type
        if (request.getReportType() == null || request.getReportType().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Report type is required");
        }

        FinancialReport.ReportType reportType;
        try {
            reportType = FinancialReport.ReportType.valueOf(request.getReportType().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid report type");
        }

        // Validate dates
        if (request.getFromDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From date is required");
        }
        if (request.getToDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "To date is required");
        }
        if (request.getFromDate().isAfter(request.getToDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From date must not be after to date");
        }

        // Default persist to true if not specified
        boolean persist = request.getPersist() != null ? request.getPersist() : true;

        // Use passed generatedBy or fallback to request
        String finalGeneratedBy = generatedBy != null ? generatedBy : (request.getGeneratedBy() != null ? request.getGeneratedBy() : "system");

        // Generate appropriate report
        return switch (reportType) {
            case INCOME_STATEMENT -> generateIncomeStatement(organizationId, request.getFromDate(), request.getToDate(), finalGeneratedBy, persist);
            case BALANCE_SHEET -> generateBalanceSheet(organizationId, request.getFromDate(), request.getToDate(), finalGeneratedBy, persist);
            case CASH_FLOW -> generateCashFlowStatement(organizationId, request.getFromDate(), request.getToDate(), finalGeneratedBy, persist);
            case EQUITY -> generateEquityStatement(organizationId, request.getFromDate(), request.getToDate(), finalGeneratedBy, persist);
            case BUDGET_VARIANCE -> generateBudgetVarianceReport(organizationId, request.getFromDate(), request.getToDate(), finalGeneratedBy, persist);
            case CUSTOM -> generateCustomReport(organizationId, request.getFromDate(), request.getToDate(), finalGeneratedBy, persist, request.getAccountTypes());
        };
    }

    /**
     * Generate an Income Statement from posted GL entries.
     */
    public FinancialReportSummaryResponse generateIncomeStatement(Long organizationId, LocalDate fromDate, LocalDate toDate, String generatedBy, boolean persist) {
        // Validate organization
        organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        // Validate dates
        if (fromDate == null || toDate == null || fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid date range is required");
        }

        // Check module controls if available
        if (moduleControlService != null) {
            try {
                moduleControlService.requireModuleEnabled(organizationId, com.justjava.ams.financeAdmin.entity.ModuleControl.ModuleType.REPORTING);
            } catch (ResponseStatusException ex) {
                // Module control check failed
                throw ex;
            }
        }

        // Fetch posted GL entries for the period
        List<GeneralLedger> postedEntries = generalLedgerRepository.findPostedEntriesByOrganizationAndDateRange(organizationId, fromDate, toDate);

        // Calculate revenue and expenses
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;
        List<FinancialReportLineDTO> lines = new ArrayList<>();

        // Group by account and calculate totals
        Map<Long, List<GeneralLedger>> groupedByAccount = postedEntries.stream()
                .collect(Collectors.groupingBy(gl -> gl.getAccount().getId()));

        for (List<GeneralLedger> glEntries : groupedByAccount.values()) {
            ChartOfAccounts account = glEntries.get(0).getAccount();

            if (account.getAccountType() == ChartOfAccounts.AccountType.REVENUE) {
                // Revenue: credits - debits
                BigDecimal credits = glEntries.stream()
                        .filter(gl -> gl.getDebitCredit() == GeneralLedger.DebitCredit.CREDIT)
                        .map(GeneralLedger::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal debits = glEntries.stream()
                        .filter(gl -> gl.getDebitCredit() == GeneralLedger.DebitCredit.DEBIT)
                        .map(GeneralLedger::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal revenueAmount = credits.subtract(debits);
                if (revenueAmount.compareTo(BigDecimal.ZERO) != 0) {
                    lines.add(FinancialReportLineDTO.builder()
                            .section("REVENUE")
                            .accountId(account.getId())
                            .accountCode(account.getAccountCode())
                            .accountName(account.getAccountName())
                            .amount(revenueAmount)
                            .build());
                    totalRevenue = totalRevenue.add(revenueAmount);
                }
            } else if (account.getAccountType() == ChartOfAccounts.AccountType.EXPENSE) {
                // Expenses: debits - credits
                BigDecimal debits = glEntries.stream()
                        .filter(gl -> gl.getDebitCredit() == GeneralLedger.DebitCredit.DEBIT)
                        .map(GeneralLedger::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal credits = glEntries.stream()
                        .filter(gl -> gl.getDebitCredit() == GeneralLedger.DebitCredit.CREDIT)
                        .map(GeneralLedger::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal expenseAmount = debits.subtract(credits);
                if (expenseAmount.compareTo(BigDecimal.ZERO) != 0) {
                    lines.add(FinancialReportLineDTO.builder()
                            .section("EXPENSE")
                            .accountId(account.getId())
                            .accountCode(account.getAccountCode())
                            .accountName(account.getAccountName())
                            .amount(expenseAmount)
                            .build());
                    totalExpenses = totalExpenses.add(expenseAmount);
                }
            }
        }

        BigDecimal netIncome = totalRevenue.subtract(totalExpenses);

        // Build the response
        FinancialReportSummaryResponse response = FinancialReportSummaryResponse.builder()
                .organizationId(organizationId)
                .reportType("INCOME_STATEMENT")
                .reportName("Income Statement from " + fromDate + " to " + toDate)
                .fromDate(fromDate)
                .toDate(toDate)
                .reportDate(LocalDate.now())
                .status("DRAFT")
                .lines(lines)
                .totalRevenue(totalRevenue)
                .totalExpenses(totalExpenses)
                .netIncome(netIncome)
                .generatedAt(java.time.LocalDateTime.now())
                .build();

        // Persist if requested
        if (persist) {
            try {
                String reportContent = writeReportContent(response);
                FinancialReport report = FinancialReport.builder()
                        .organization(organizationRepository.findById(organizationId).get())
                        .reportType(FinancialReport.ReportType.INCOME_STATEMENT)
                        .reportName(response.getReportName())
                        .reportDate(LocalDate.now())
                        .fromDate(fromDate)
                        .toDate(toDate)
                        .status(FinancialReport.ReportStatus.DRAFT)
                        .reportContent(reportContent)
                        .generatedBy(generatedBy)
                        .build();

                FinancialReport saved = financialReportRepository.save(report);
                response.setId(saved.getId());

                // Create audit log
                try {
                    auditLogService.log(organizationId, "FinancialReport", saved.getId(), "CREATE",
                            null, response.getReportName(),
                            "Income Statement generated for " + fromDate + " to " + toDate);
                } catch (Exception ex) {
                    // Log audit failures but don't block report generation
                }

                // Create security event
                try {
                    securityEventService.logEvent(organizationId, "REPORT_GENERATION", "LOW",
                            "Income Statement Generated",
                            "Income Statement report generated for period " + fromDate + " to " + toDate,
                            null, null);
                } catch (Exception ex) {
                    // Log security event failures but don't block report generation
                }
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to serialize and persist report: " + ex.getMessage(), ex);
            }
        }

        return response;
    }

    /**
     * Generate a Balance Sheet from posted GL entries.
     */
    public FinancialReportSummaryResponse generateBalanceSheet(Long organizationId, LocalDate fromDate, LocalDate toDate, String generatedBy, boolean persist) {
        // Validate organization
        organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        // Validate dates
        if (fromDate == null || toDate == null || fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid date range is required");
        }

        // Check module controls if available
        if (moduleControlService != null) {
            try {
                moduleControlService.requireModuleEnabled(organizationId, com.justjava.ams.financeAdmin.entity.ModuleControl.ModuleType.REPORTING);
            } catch (ResponseStatusException ex) {
                throw ex;
            }
        }

        // Fetch posted GL entries up to toDate
        List<GeneralLedger> postedEntries = generalLedgerRepository.findPostedEntriesByOrganizationAsOf(organizationId, toDate);

        // Calculate balance sheet components
        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalLiabilities = BigDecimal.ZERO;
        BigDecimal totalEquity = BigDecimal.ZERO;
        List<FinancialReportLineDTO> lines = new ArrayList<>();

        // Group by account
        Map<Long, List<GeneralLedger>> groupedByAccount = postedEntries.stream()
                .collect(Collectors.groupingBy(gl -> gl.getAccount().getId()));

        for (List<GeneralLedger> glEntries : groupedByAccount.values()) {
            ChartOfAccounts account = glEntries.get(0).getAccount();

            if (account.getAccountType() == ChartOfAccounts.AccountType.ASSET) {
                // Assets: debits - credits
                BigDecimal debits = glEntries.stream()
                        .filter(gl -> gl.getDebitCredit() == GeneralLedger.DebitCredit.DEBIT)
                        .map(GeneralLedger::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal credits = glEntries.stream()
                        .filter(gl -> gl.getDebitCredit() == GeneralLedger.DebitCredit.CREDIT)
                        .map(GeneralLedger::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal assetAmount = debits.subtract(credits);
                if (assetAmount.compareTo(BigDecimal.ZERO) != 0) {
                    lines.add(FinancialReportLineDTO.builder()
                            .section("ASSET")
                            .accountId(account.getId())
                            .accountCode(account.getAccountCode())
                            .accountName(account.getAccountName())
                            .amount(assetAmount)
                            .build());
                    totalAssets = totalAssets.add(assetAmount);
                }
            } else if (account.getAccountType() == ChartOfAccounts.AccountType.LIABILITY) {
                // Liabilities: credits - debits
                BigDecimal credits = glEntries.stream()
                        .filter(gl -> gl.getDebitCredit() == GeneralLedger.DebitCredit.CREDIT)
                        .map(GeneralLedger::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal debits = glEntries.stream()
                        .filter(gl -> gl.getDebitCredit() == GeneralLedger.DebitCredit.DEBIT)
                        .map(GeneralLedger::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal liabilityAmount = credits.subtract(debits);
                if (liabilityAmount.compareTo(BigDecimal.ZERO) != 0) {
                    lines.add(FinancialReportLineDTO.builder()
                            .section("LIABILITY")
                            .accountId(account.getId())
                            .accountCode(account.getAccountCode())
                            .accountName(account.getAccountName())
                            .amount(liabilityAmount)
                            .build());
                    totalLiabilities = totalLiabilities.add(liabilityAmount);
                }
            } else if (account.getAccountType() == ChartOfAccounts.AccountType.EQUITY) {
                // Equity: credits - debits
                BigDecimal credits = glEntries.stream()
                        .filter(gl -> gl.getDebitCredit() == GeneralLedger.DebitCredit.CREDIT)
                        .map(GeneralLedger::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal debits = glEntries.stream()
                        .filter(gl -> gl.getDebitCredit() == GeneralLedger.DebitCredit.DEBIT)
                        .map(GeneralLedger::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal equityAmount = credits.subtract(debits);
                if (equityAmount.compareTo(BigDecimal.ZERO) != 0) {
                    lines.add(FinancialReportLineDTO.builder()
                            .section("EQUITY")
                            .accountId(account.getId())
                            .accountCode(account.getAccountCode())
                            .accountName(account.getAccountName())
                            .amount(equityAmount)
                            .build());
                    totalEquity = totalEquity.add(equityAmount);
                }
            }
        }

        // Calculate net income for the selected period
        List<GeneralLedger> periodEntries = generalLedgerRepository.findPostedEntriesByOrganizationAndDateRange(organizationId, fromDate, toDate);
        BigDecimal periodNetIncome = calculatePeriodNetIncome(periodEntries);

        // Calculate variance: Assets - (Liabilities + Equity + NetIncome)
        BigDecimal balanceSheetVariance = totalAssets.subtract(totalLiabilities).subtract(totalEquity).subtract(periodNetIncome);

        // Build the response
        FinancialReportSummaryResponse response = FinancialReportSummaryResponse.builder()
                .organizationId(organizationId)
                .reportType("BALANCE_SHEET")
                .reportName("Balance Sheet as of " + toDate)
                .fromDate(fromDate)
                .toDate(toDate)
                .reportDate(LocalDate.now())
                .status("DRAFT")
                .lines(lines)
                .totalAssets(totalAssets)
                .totalLiabilities(totalLiabilities)
                .totalEquity(totalEquity)
                .netIncome(periodNetIncome)
                .balanceSheetVariance(balanceSheetVariance)
                .generatedAt(java.time.LocalDateTime.now())
                .build();

        // Persist if requested
        if (persist) {
            try {
                String reportContent = writeReportContent(response);
                FinancialReport report = FinancialReport.builder()
                        .organization(organizationRepository.findById(organizationId).get())
                        .reportType(FinancialReport.ReportType.BALANCE_SHEET)
                        .reportName(response.getReportName())
                        .reportDate(LocalDate.now())
                        .fromDate(fromDate)
                        .toDate(toDate)
                        .status(FinancialReport.ReportStatus.DRAFT)
                        .reportContent(reportContent)
                        .generatedBy(generatedBy)
                        .build();

                FinancialReport saved = financialReportRepository.save(report);
                response.setId(saved.getId());

                // Create audit log
                try {
                    auditLogService.log(organizationId, "FinancialReport", saved.getId(), "CREATE",
                            null, response.getReportName(),
                            "Balance Sheet generated as of " + toDate);
                } catch (Exception ex) {
                    // Log audit failures but don't block report generation
                }

                // Create security event
                try {
                    securityEventService.logEvent(organizationId, "REPORT_GENERATION", "LOW",
                            "Balance Sheet Generated",
                            "Balance Sheet report generated as of " + toDate,
                            null, null);
                } catch (Exception ex) {
                    // Log security event failures but don't block report generation
                }
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to serialize and persist report: " + ex.getMessage(), ex);
            }
        }

        return response;
    }

    /**
     * Generate a Cash Flow Statement from posted cash-account movements.
     */
    public FinancialReportSummaryResponse generateCashFlowStatement(Long organizationId, LocalDate fromDate, LocalDate toDate, String generatedBy, boolean persist) {
        validateReportContext(organizationId, fromDate, toDate);

        List<GeneralLedger> postedEntries = generalLedgerRepository.findPostedEntriesByOrganizationAndDateRange(organizationId, fromDate, toDate);
        List<FinancialReportLineDTO> lines = new ArrayList<>();
        BigDecimal operating = BigDecimal.ZERO;
        BigDecimal investing = BigDecimal.ZERO;
        BigDecimal financing = BigDecimal.ZERO;

        Map<String, List<GeneralLedger>> entriesByJournal = postedEntries.stream()
                .collect(Collectors.groupingBy(GeneralLedger::getJournalNumber));
        Map<String, BigDecimal> cashFlowBySectionAndAccount = new LinkedHashMap<>();
        Map<String, ChartOfAccounts> cashAccountsByKey = new LinkedHashMap<>();
        Map<String, String> sectionsByKey = new LinkedHashMap<>();

        for (List<GeneralLedger> journalEntries : entriesByJournal.values()) {
            String section = cashFlowSection(journalEntries);
            for (GeneralLedger gl : journalEntries) {
                if (!isCashAccount(gl.getAccount())) {
                    continue;
                }
                BigDecimal amount = signedByNormalDebit(List.of(gl));
                String key = section + "|" + gl.getAccount().getId();
                cashFlowBySectionAndAccount.merge(key, amount, BigDecimal::add);
                cashAccountsByKey.putIfAbsent(key, gl.getAccount());
                sectionsByKey.putIfAbsent(key, section);
            }
        }

        for (Map.Entry<String, BigDecimal> entry : cashFlowBySectionAndAccount.entrySet()) {
            BigDecimal amount = entry.getValue();
            if (amount.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            String section = sectionsByKey.get(entry.getKey());
            lines.add(reportLine(section, cashAccountsByKey.get(entry.getKey()), amount));
            if ("OPERATING".equals(section)) {
                operating = operating.add(amount);
            } else if ("INVESTING".equals(section)) {
                investing = investing.add(amount);
            } else {
                financing = financing.add(amount);
            }
        }

        BigDecimal netChange = operating.add(investing).add(financing);
        Map<String, BigDecimal> metrics = orderedMetrics(
                "netOperatingCashFlow", operating,
                "netInvestingCashFlow", investing,
                "netFinancingCashFlow", financing,
                "netChangeInCash", netChange);

        FinancialReportSummaryResponse response = FinancialReportSummaryResponse.builder()
                .organizationId(organizationId)
                .reportType("CASH_FLOW")
                .reportName("Cash Flow Statement from " + fromDate + " to " + toDate)
                .fromDate(fromDate)
                .toDate(toDate)
                .reportDate(LocalDate.now())
                .status("DRAFT")
                .lines(lines)
                .summaryMetrics(metrics)
                .generatedAt(java.time.LocalDateTime.now())
                .build();

        persistGeneratedReportIfRequested(organizationId, FinancialReport.ReportType.CASH_FLOW, response, generatedBy, persist);
        return response;
    }

    /**
     * Generate an Equity Statement from equity account movements plus period net income.
     */
    public FinancialReportSummaryResponse generateEquityStatement(Long organizationId, LocalDate fromDate, LocalDate toDate, String generatedBy, boolean persist) {
        validateReportContext(organizationId, fromDate, toDate);

        LocalDate openingAsOf = fromDate.minusDays(1);
        List<GeneralLedger> openingEntries = generalLedgerRepository.findPostedEntriesByOrganizationAsOf(organizationId, openingAsOf);
        List<GeneralLedger> periodEntries = generalLedgerRepository.findPostedEntriesByOrganizationAndDateRange(organizationId, fromDate, toDate);

        BigDecimal openingEquity = accountTypeBalance(openingEntries, ChartOfAccounts.AccountType.EQUITY);
        BigDecimal equityMovements = accountTypeBalance(periodEntries, ChartOfAccounts.AccountType.EQUITY);
        BigDecimal netIncome = calculatePeriodNetIncome(periodEntries);
        BigDecimal closingEquity = openingEquity.add(equityMovements).add(netIncome);

        List<FinancialReportLineDTO> lines = new ArrayList<>();
        lines.add(simpleLine("OPENING_EQUITY", "Opening Equity", openingEquity));

        Map<Long, List<GeneralLedger>> equityEntriesByAccount = periodEntries.stream()
                .filter(gl -> gl.getAccount().getAccountType() == ChartOfAccounts.AccountType.EQUITY)
                .collect(Collectors.groupingBy(gl -> gl.getAccount().getId()));
        for (List<GeneralLedger> glEntries : equityEntriesByAccount.values()) {
            BigDecimal amount = signedByNormalCredit(glEntries);
            if (amount.compareTo(BigDecimal.ZERO) != 0) {
                lines.add(reportLine("PERIOD_MOVEMENTS", glEntries.get(0).getAccount(), amount));
            }
        }
        lines.add(simpleLine("PERIOD_MOVEMENTS", "Period Net Income", netIncome));
        lines.add(simpleLine("CLOSING_EQUITY", "Closing Equity", closingEquity));

        Map<String, BigDecimal> metrics = orderedMetrics(
                "openingEquity", openingEquity,
                "equityMovements", equityMovements,
                "netIncome", netIncome,
                "closingEquity", closingEquity);

        FinancialReportSummaryResponse response = FinancialReportSummaryResponse.builder()
                .organizationId(organizationId)
                .reportType("EQUITY")
                .reportName("Statement of Changes in Equity from " + fromDate + " to " + toDate)
                .fromDate(fromDate)
                .toDate(toDate)
                .reportDate(LocalDate.now())
                .status("DRAFT")
                .lines(lines)
                .netIncome(netIncome)
                .totalEquity(closingEquity)
                .summaryMetrics(metrics)
                .generatedAt(java.time.LocalDateTime.now())
                .build();

        persistGeneratedReportIfRequested(organizationId, FinancialReport.ReportType.EQUITY, response, generatedBy, persist);
        return response;
    }

    /**
     * Generate a Budget Variance report from active budget lines and budget consumption.
     */
    public FinancialReportSummaryResponse generateBudgetVarianceReport(Long organizationId, LocalDate fromDate, LocalDate toDate, String generatedBy, boolean persist) {
        validateReportContext(organizationId, fromDate, toDate);

        List<BudgetLine> budgetLines = budgetLineRepository.findByBudgetOrganizationIdAndActiveTrue(organizationId).stream()
                .filter(line -> rangesOverlap(line.getBudget().getStartDate(), line.getBudget().getEndDate(), fromDate, toDate))
                .collect(Collectors.toList());

        List<FinancialReportLineDTO> lines = new ArrayList<>();
        BigDecimal totalBudget = BigDecimal.ZERO;
        BigDecimal totalActual = BigDecimal.ZERO;

        for (BudgetLine budgetLine : budgetLines) {
            BigDecimal budgeted = defaultZero(budgetLine.getAllocatedAmount());
            BigDecimal actual = defaultZero(budgetConsumptionRepository.sumActiveByBudgetLineIdAndTransactionDateBetween(
                    budgetLine.getId(), fromDate, toDate));
            BigDecimal variance = budgeted.subtract(actual);
            totalBudget = totalBudget.add(budgeted);
            totalActual = totalActual.add(actual);

            ChartOfAccounts account = budgetLine.getChartAccount();
            lines.add(FinancialReportLineDTO.builder()
                    .section(variance.compareTo(BigDecimal.ZERO) < 0 ? "UNFAVORABLE_VARIANCE" : "FAVORABLE_VARIANCE")
                    .accountId(account.getId())
                    .accountCode(account.getAccountCode())
                    .accountName(account.getAccountName() + " (Budget " + budgeted + ", Actual " + actual + ")")
                    .amount(variance)
                    .build());
        }

        BigDecimal totalVariance = totalBudget.subtract(totalActual);
        Map<String, BigDecimal> metrics = orderedMetrics(
                "totalBudget", totalBudget,
                "totalActual", totalActual,
                "totalVariance", totalVariance,
                "variancePercent", percent(totalVariance, totalBudget));

        FinancialReportSummaryResponse response = FinancialReportSummaryResponse.builder()
                .organizationId(organizationId)
                .reportType("BUDGET_VARIANCE")
                .reportName("Budget Variance Report from " + fromDate + " to " + toDate)
                .fromDate(fromDate)
                .toDate(toDate)
                .reportDate(LocalDate.now())
                .status("DRAFT")
                .lines(lines)
                .totalExpenses(totalActual)
                .summaryMetrics(metrics)
                .generatedAt(java.time.LocalDateTime.now())
                .build();

        persistGeneratedReportIfRequested(organizationId, FinancialReport.ReportType.BUDGET_VARIANCE, response, generatedBy, persist);
        return response;
    }

    /**
     * Generate a custom account summary from posted GL entries, optionally filtered by account type.
     */
    public FinancialReportSummaryResponse generateCustomReport(Long organizationId, LocalDate fromDate, LocalDate toDate, String generatedBy, boolean persist, List<String> accountTypes) {
        validateReportContext(organizationId, fromDate, toDate);

        Set<ChartOfAccounts.AccountType> allowedTypes = parseAccountTypes(accountTypes);
        List<GeneralLedger> postedEntries = generalLedgerRepository.findPostedEntriesByOrganizationAndDateRange(organizationId, fromDate, toDate).stream()
                .filter(gl -> allowedTypes.isEmpty() || allowedTypes.contains(gl.getAccount().getAccountType()))
                .collect(Collectors.toList());

        List<FinancialReportLineDTO> lines = accountSummaryLines(postedEntries);
        BigDecimal total = lines.stream()
                .map(FinancialReportLineDTO::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        FinancialReportSummaryResponse response = FinancialReportSummaryResponse.builder()
                .organizationId(organizationId)
                .reportType("CUSTOM")
                .reportName("Custom Financial Report from " + fromDate + " to " + toDate)
                .fromDate(fromDate)
                .toDate(toDate)
                .reportDate(LocalDate.now())
                .status("DRAFT")
                .lines(lines)
                .summaryMetrics(orderedMetrics("total", total))
                .generatedAt(java.time.LocalDateTime.now())
                .build();

        persistGeneratedReportIfRequested(organizationId, FinancialReport.ReportType.CUSTOM, response, generatedBy, persist);
        return response;
    }

    /**
     * Get a report summary by report ID.
     */
    public FinancialReportSummaryResponse getReportSummary(Long reportId) {
        FinancialReport report = financialReportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));

        try {
            JsonNode content = objectMapper.readTree(report.getReportContent());
            List<FinancialReportLineDTO> lines = new ArrayList<>();
            JsonNode lineNodes = content.get("lines");
            if (lineNodes != null && lineNodes.isArray()) {
                for (JsonNode line : lineNodes) {
                    lines.add(FinancialReportLineDTO.builder()
                            .section(text(line, "section"))
                            .accountId(line.hasNonNull("accountId") ? line.get("accountId").asLong() : null)
                            .accountCode(text(line, "accountCode"))
                            .accountName(text(line, "accountName"))
                            .amount(decimal(line, "amount"))
                            .build());
                }
            }
            return FinancialReportSummaryResponse.builder()
                    .id(report.getId())
                    .organizationId(report.getOrganization().getId())
                    .reportType(report.getReportType().name())
                    .reportName(report.getReportName())
                    .fromDate(report.getFromDate())
                    .toDate(report.getToDate())
                    .reportDate(report.getReportDate())
                    .status(report.getStatus() != null ? report.getStatus().name() : "DRAFT")
                    .lines(lines)
                    .totalRevenue(decimal(content, "totalRevenue"))
                    .totalExpenses(decimal(content, "totalExpenses"))
                    .netIncome(decimal(content, "netIncome"))
                    .totalAssets(decimal(content, "totalAssets"))
                    .totalLiabilities(decimal(content, "totalLiabilities"))
                    .totalEquity(decimal(content, "totalEquity"))
                    .balanceSheetVariance(decimal(content, "balanceSheetVariance"))
                    .summaryMetrics(decimalMap(content.get("summaryMetrics")))
                    .reportContent(report.getReportContent())
                    .generatedAt(report.getCreatedAt())
                    .build();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to deserialize report content");
        }
    }

    /**
     * Get reports for an organization with optional filtering.
     */
    public List<FinancialReportDTO> getReports(Long organizationId, String reportType, LocalDate fromDate, LocalDate toDate) {
        // Validate organization
        organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));

        List<FinancialReport> reports = financialReportRepository.findByOrganizationIdOrderByReportDateDesc(organizationId);

        // Filter by type if provided
        if (reportType != null && !reportType.trim().isEmpty()) {
            try {
                FinancialReport.ReportType rt = FinancialReport.ReportType.valueOf(reportType.toUpperCase());
                reports = reports.stream()
                        .filter(r -> r.getReportType() == rt)
                        .collect(Collectors.toList());
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid report type");
            }
        }

        // Filter by date range if provided
        if (fromDate != null || toDate != null) {
            if ((fromDate == null) != (toDate == null)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Both from date and to date are required");
            }
            if (fromDate != null && fromDate.isAfter(toDate)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From date must not be after to date");
            }

            final LocalDate finalFromDate = fromDate;
            final LocalDate finalToDate = toDate;
            reports = reports.stream()
                    .filter(r -> !r.getReportDate().isBefore(finalFromDate) && !r.getReportDate().isAfter(finalToDate))
                    .collect(Collectors.toList());
        }

        return reports.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Helper method to calculate net income for a period.
     */
    private void validateReportContext(Long organizationId, LocalDate fromDate, LocalDate toDate) {
        organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        if (fromDate == null || toDate == null || fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid date range is required");
        }
        if (moduleControlService != null) {
            moduleControlService.requireModuleEnabled(organizationId, com.justjava.ams.financeAdmin.entity.ModuleControl.ModuleType.REPORTING);
        }
    }

    private void persistGeneratedReportIfRequested(
            Long organizationId,
            FinancialReport.ReportType reportType,
            FinancialReportSummaryResponse response,
            String generatedBy,
            boolean persist) {
        if (!persist) {
            return;
        }

        try {
            String reportContent = writeReportContent(response);
            Organization organization = organizationRepository.findById(organizationId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
            FinancialReport report = FinancialReport.builder()
                    .organization(organization)
                    .reportType(reportType)
                    .reportName(response.getReportName())
                    .reportDate(LocalDate.now())
                    .fromDate(response.getFromDate())
                    .toDate(response.getToDate())
                    .status(FinancialReport.ReportStatus.DRAFT)
                    .reportContent(reportContent)
                    .generatedBy(generatedBy)
                    .build();

            FinancialReport saved = financialReportRepository.save(report);
            response.setId(saved.getId());

            try {
                auditLogService.log(organizationId, "FinancialReport", saved.getId(), "CREATE",
                        null, response.getReportName(),
                        reportLabel(reportType) + " generated for " + response.getFromDate() + " to " + response.getToDate());
            } catch (Exception ex) {
                // Log audit failures but don't block report generation
            }

            try {
                securityEventService.logEvent(organizationId, "REPORT_GENERATION", "LOW",
                        reportLabel(reportType) + " Generated",
                        response.getReportName(),
                        null, null);
            } catch (Exception ex) {
                // Log security event failures but don't block report generation
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to serialize and persist report: " + ex.getMessage(), ex);
        }
    }

    private List<FinancialReportLineDTO> accountSummaryLines(List<GeneralLedger> entries) {
        Map<Long, List<GeneralLedger>> groupedByAccount = entries.stream()
                .collect(Collectors.groupingBy(gl -> gl.getAccount().getId(), LinkedHashMap::new, Collectors.toList()));
        List<FinancialReportLineDTO> lines = new ArrayList<>();
        for (List<GeneralLedger> glEntries : groupedByAccount.values()) {
            ChartOfAccounts account = glEntries.get(0).getAccount();
            BigDecimal amount = signedByAccountType(glEntries, account.getAccountType());
            if (amount.compareTo(BigDecimal.ZERO) != 0) {
                lines.add(reportLine(account.getAccountType().name(), account, amount));
            }
        }
        return lines;
    }

    private FinancialReportLineDTO reportLine(String section, ChartOfAccounts account, BigDecimal amount) {
        return FinancialReportLineDTO.builder()
                .section(section)
                .accountId(account.getId())
                .accountCode(account.getAccountCode())
                .accountName(account.getAccountName())
                .amount(amount)
                .build();
    }

    private FinancialReportLineDTO simpleLine(String section, String name, BigDecimal amount) {
        return FinancialReportLineDTO.builder()
                .section(section)
                .accountCode("")
                .accountName(name)
                .amount(amount)
                .build();
    }

    private BigDecimal accountTypeBalance(List<GeneralLedger> entries, ChartOfAccounts.AccountType accountType) {
        return entries.stream()
                .filter(gl -> gl.getAccount().getAccountType() == accountType)
                .collect(Collectors.groupingBy(gl -> gl.getAccount().getId()))
                .values()
                .stream()
                .map(glEntries -> signedByAccountType(glEntries, accountType))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal signedByAccountType(List<GeneralLedger> entries, ChartOfAccounts.AccountType accountType) {
        if (accountType == ChartOfAccounts.AccountType.ASSET || accountType == ChartOfAccounts.AccountType.EXPENSE) {
            return signedByNormalDebit(entries);
        }
        return signedByNormalCredit(entries);
    }

    private BigDecimal signedByNormalDebit(List<GeneralLedger> entries) {
        BigDecimal debits = entries.stream()
                .filter(gl -> gl.getDebitCredit() == GeneralLedger.DebitCredit.DEBIT)
                .map(GeneralLedger::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = entries.stream()
                .filter(gl -> gl.getDebitCredit() == GeneralLedger.DebitCredit.CREDIT)
                .map(GeneralLedger::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return debits.subtract(credits);
    }

    private BigDecimal signedByNormalCredit(List<GeneralLedger> entries) {
        BigDecimal credits = entries.stream()
                .filter(gl -> gl.getDebitCredit() == GeneralLedger.DebitCredit.CREDIT)
                .map(GeneralLedger::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal debits = entries.stream()
                .filter(gl -> gl.getDebitCredit() == GeneralLedger.DebitCredit.DEBIT)
                .map(GeneralLedger::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return credits.subtract(debits);
    }

    private boolean isCashAccount(ChartOfAccounts account) {
        String text = ((account.getAccountCode() != null ? account.getAccountCode() : "") + " "
                + (account.getAccountName() != null ? account.getAccountName() : "")).toLowerCase(Locale.ROOT);
        return account.getAccountType() == ChartOfAccounts.AccountType.ASSET
                && (text.contains("cash") || text.contains("bank"));
    }

    private String cashFlowSection(List<GeneralLedger> journalEntries) {
        boolean investing = false;
        boolean financing = false;
        for (GeneralLedger entry : journalEntries) {
            ChartOfAccounts account = entry.getAccount();
            if (isCashAccount(account)) {
                continue;
            }
            if (account.getAccountSubtype() == ChartOfAccounts.AccountSubtype.FIXED_ASSET) {
                investing = true;
            }
            if (account.getAccountType() == ChartOfAccounts.AccountType.EQUITY
                    || account.getAccountSubtype() == ChartOfAccounts.AccountSubtype.LONG_TERM_LIABILITY) {
                financing = true;
            }
        }
        if (investing) {
            return "INVESTING";
        }
        if (financing) {
            return "FINANCING";
        }
        return "OPERATING";
    }

    private boolean rangesOverlap(LocalDate leftStart, LocalDate leftEnd, LocalDate rightStart, LocalDate rightEnd) {
        return !leftStart.isAfter(rightEnd) && !leftEnd.isBefore(rightStart);
    }

    private Set<ChartOfAccounts.AccountType> parseAccountTypes(List<String> accountTypes) {
        if (accountTypes == null || accountTypes.isEmpty()) {
            return Collections.emptySet();
        }
        Set<ChartOfAccounts.AccountType> parsed = EnumSet.noneOf(ChartOfAccounts.AccountType.class);
        for (String accountType : accountTypes) {
            if (accountType == null || accountType.trim().isEmpty()) {
                continue;
            }
            try {
                parsed.add(ChartOfAccounts.AccountType.valueOf(accountType.trim().toUpperCase()));
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid account type: " + accountType);
            }
        }
        return parsed;
    }

    private Map<String, BigDecimal> orderedMetrics(Object... values) {
        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            metrics.put((String) values[i], defaultZero((BigDecimal) values[i + 1]));
        }
        return metrics;
    }

    private BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.multiply(new BigDecimal("100")).divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultZero(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    private String reportLabel(FinancialReport.ReportType reportType) {
        return switch (reportType) {
            case INCOME_STATEMENT -> "Income Statement";
            case BALANCE_SHEET -> "Balance Sheet";
            case CASH_FLOW -> "Cash Flow Statement";
            case EQUITY -> "Statement of Changes in Equity";
            case BUDGET_VARIANCE -> "Budget Variance Report";
            case CUSTOM -> "Custom Financial Report";
        };
    }

    private BigDecimal calculatePeriodNetIncome(List<GeneralLedger> periodEntries) {
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalExpenses = BigDecimal.ZERO;

        Map<Long, List<GeneralLedger>> groupedByAccount = periodEntries.stream()
                .collect(Collectors.groupingBy(gl -> gl.getAccount().getId()));

        for (List<GeneralLedger> glEntries : groupedByAccount.values()) {
            ChartOfAccounts account = glEntries.get(0).getAccount();

            if (account.getAccountType() == ChartOfAccounts.AccountType.REVENUE) {
                BigDecimal credits = glEntries.stream()
                        .filter(gl -> gl.getDebitCredit() == GeneralLedger.DebitCredit.CREDIT)
                        .map(GeneralLedger::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal debits = glEntries.stream()
                        .filter(gl -> gl.getDebitCredit() == GeneralLedger.DebitCredit.DEBIT)
                        .map(GeneralLedger::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                totalRevenue = totalRevenue.add(credits.subtract(debits));
            } else if (account.getAccountType() == ChartOfAccounts.AccountType.EXPENSE) {
                BigDecimal debits = glEntries.stream()
                        .filter(gl -> gl.getDebitCredit() == GeneralLedger.DebitCredit.DEBIT)
                        .map(GeneralLedger::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal credits = glEntries.stream()
                        .filter(gl -> gl.getDebitCredit() == GeneralLedger.DebitCredit.CREDIT)
                        .map(GeneralLedger::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                totalExpenses = totalExpenses.add(debits.subtract(credits));
            }
        }

        return totalRevenue.subtract(totalExpenses);
    }

    private String writeReportContent(FinancialReportSummaryResponse response) throws com.fasterxml.jackson.core.JsonProcessingException {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("id", response.getId());
        content.put("organizationId", response.getOrganizationId());
        content.put("reportType", response.getReportType());
        content.put("reportName", response.getReportName());
        content.put("fromDate", response.getFromDate() != null ? response.getFromDate().toString() : null);
        content.put("toDate", response.getToDate() != null ? response.getToDate().toString() : null);
        content.put("reportDate", response.getReportDate() != null ? response.getReportDate().toString() : null);
        content.put("status", response.getStatus());
        content.put("lines", response.getLines());
        content.put("totalRevenue", response.getTotalRevenue());
        content.put("totalExpenses", response.getTotalExpenses());
        content.put("netIncome", response.getNetIncome());
        content.put("totalAssets", response.getTotalAssets());
        content.put("totalLiabilities", response.getTotalLiabilities());
        content.put("totalEquity", response.getTotalEquity());
        content.put("balanceSheetVariance", response.getBalanceSheetVariance());
        content.put("summaryMetrics", response.getSummaryMetrics());
        content.put("generatedAt", response.getGeneratedAt() != null ? response.getGeneratedAt().toString() : null);
        return objectMapper.writeValueAsString(content);
    }

    private Map<String, BigDecimal> decimalMap(JsonNode node) {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if (entry.getValue() != null && entry.getValue().isNumber()) {
                    values.put(entry.getKey(), entry.getValue().decimalValue());
                }
            });
        }
        return values;
    }

    private BigDecimal decimal(JsonNode node, String fieldName) {
        return node != null && node.hasNonNull(fieldName) ? node.get(fieldName).decimalValue() : BigDecimal.ZERO;
    }

    private String text(JsonNode node, String fieldName) {
        return node != null && node.hasNonNull(fieldName) ? node.get(fieldName).asText() : null;
    }

    private String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private FinancialReportDTO mapToDTO(FinancialReport report) {
        return FinancialReportDTO.builder()
                .id(report.getId())
                .organizationId(report.getOrganization().getId())
                .reportType(report.getReportType().toString())
                .reportName(report.getReportName())
                .reportDate(report.getReportDate())
                .fromDate(report.getFromDate())
                .toDate(report.getToDate())
                .status(report.getStatus() != null ? report.getStatus().toString() : "DRAFT")
                .reportContent(report.getReportContent())
                .generatedBy(report.getGeneratedBy())
                .approvedDate(report.getApprovedDate())
                .approvedBy(report.getApprovedBy())
                .approvalRequestId(report.getApprovalRequestId())
                .approvalRuleId(report.getApprovalRuleId())
                .approvalRuleName(report.getApprovalRuleName())
                .requiredApprovals(report.getRequiredApprovals())
                .notes(report.getNotes())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();
    }
}
