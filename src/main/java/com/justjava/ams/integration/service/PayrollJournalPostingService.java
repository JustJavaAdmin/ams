package com.justjava.ams.integration.service;

import com.justjava.ams.accountant.dto.FiscalPeriodDTO;
import com.justjava.ams.accountant.entity.ChartOfAccounts;
import com.justjava.ams.accountant.repository.ChartOfAccountsRepository;
import com.justjava.ams.accountant.service.FiscalPeriodService;
import com.justjava.ams.accountant.service.GeneralLedgerService;
import com.justjava.ams.integration.dto.PayrollJournalLineRequest;
import com.justjava.ams.integration.dto.PayrollJournalRequest;
import com.justjava.ams.integration.dto.PayrollJournalResponse;
import com.justjava.ams.integration.entity.PayrollJournal;
import com.justjava.ams.integration.entity.PayrollJournalIdempotency;
import com.justjava.ams.integration.exception.IntegrationApiException;
import com.justjava.ams.integration.repository.PayrollJournalIdempotencyRepository;
import com.justjava.ams.integration.repository.PayrollJournalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollJournalPostingService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final int MAX_LINES = 200;
    private static final int MAX_INTEGER_DIGITS = 17;

    private final PayrollJournalIdempotencyRepository idempotencyRepository;
    private final PayrollJournalRepository journalRepository;
    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final FiscalPeriodService fiscalPeriodService;
    private final GeneralLedgerService generalLedgerService;
    private final PayrollJournalPostingWorker postingWorker;

    public PayrollJournalResponse post(
            Long organizationId,
            String idempotencyKey,
            PayrollJournalRequest request,
            Jwt caller) {
        String clientId = caller.getClaimAsString("azp");
        validateHeader(idempotencyKey, request);
        String requestHash = hashRequest(request);

        Optional<PayrollJournalIdempotency> existing =
                idempotencyRepository.findByOrganizationIdAndIdempotencyKey(organizationId, idempotencyKey);
        if (existing.isPresent()) {
            return resolveExisting(existing.get(), requestHash, idempotencyKey, organizationId, clientId);
        }

        ValidatedRequest validated = validateRequest(organizationId, request);
        generalLedgerService.validatePayrollTransactionLimit(organizationId, validated.totalDebit());
        try {
            PayrollJournal journal = postingWorker.post(
                    organizationId,
                    idempotencyKey,
                    requestHash,
                    clientId,
                    request,
                    validated.fiscalPeriodId(),
                    validated.totalDebit(),
                    validated.totalCredit(),
                    validated.accounts());
            log.info("Payroll journal outcome=POSTED organizationId={} clientId={} idempotencyKey={}",
                    organizationId, clientId, idempotencyKey);
            return response(journal, idempotencyKey, false);
        } catch (DataIntegrityViolationException ex) {
            Optional<PayrollJournalIdempotency> winner =
                    idempotencyRepository.findByOrganizationIdAndIdempotencyKey(organizationId, idempotencyKey);
            if (winner.isPresent()) {
                return resolveExisting(winner.get(), requestHash, idempotencyKey, organizationId, clientId);
            }
            log.warn("Payroll journal outcome=CONSTRAINT_CONFLICT organizationId={} clientId={} idempotencyKey={}",
                    organizationId, clientId, idempotencyKey);
            throw new IntegrationApiException(HttpStatus.CONFLICT, "DATA_CONFLICT",
                    "The request conflicts with stored data", false);
        }
    }

    public PayrollJournalResponse findByKey(Long organizationId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IntegrationApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                    "Idempotency key is required", false);
        }
        PayrollJournalIdempotency record = idempotencyRepository
                .findByOrganizationIdAndIdempotencyKey(organizationId, idempotencyKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
        if (!"POSTED".equals(record.getStatus()) || record.getPayrollJournal() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
        PayrollJournal journal = journalRepository.findById(record.getPayrollJournal().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
        return response(journal, idempotencyKey, false);
    }

    private PayrollJournalResponse resolveExisting(
            PayrollJournalIdempotency record,
            String requestHash,
            String idempotencyKey,
            Long organizationId,
            String clientId) {
        if (!record.getRequestHash().equals(requestHash)) {
            log.info("Payroll journal outcome=IDEMPOTENCY_CONFLICT organizationId={} clientId={} idempotencyKey={}",
                    organizationId, clientId, idempotencyKey);
            throw new IntegrationApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT",
                    "Idempotency key was already used with a different request", false);
        }
        if (!"POSTED".equals(record.getStatus()) || record.getPayrollJournal() == null) {
            throw new IntegrationApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                    "The prior request did not complete", false);
        }
        PayrollJournal journal = journalRepository.findById(record.getPayrollJournal().getId())
                .orElseThrow(() -> new IntegrationApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "INTERNAL_SERVER_ERROR", "The prior request result is unavailable", false));
        log.info("Payroll journal outcome=DUPLICATE organizationId={} clientId={} idempotencyKey={}",
                organizationId, clientId, idempotencyKey);
        return response(journal, idempotencyKey, true);
    }

    private void validateHeader(String idempotencyKey, PayrollJournalRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IntegrationApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                    "Idempotency key is required", false);
        }
        if (request == null) {
            throw new IntegrationApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                    "Request body is required", false);
        }
        String expected = String.valueOf(request.getSourceSystem()) + ":"
                + String.valueOf(request.getJournalReference()) + ":"
                + String.valueOf(request.getRevision());
        if (!expected.equals(idempotencyKey)) {
            throw new IntegrationApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_MISMATCH",
                    "Idempotency key does not match the request", false);
        }
        if (idempotencyKey.length() > 300) {
            throw new IntegrationApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                    "Idempotency key is too long", false);
        }
    }

    private ValidatedRequest validateRequest(Long organizationId, PayrollJournalRequest request) {
        List<Object> issues = new ArrayList<>();
        if (!"JUSTHR".equals(request.getSourceSystem())) {
            issues.add(issue("sourceSystem", "Must be JUSTHR"));
        }
        if (!"ORIGINAL".equals(request.getRevision())) {
            issues.add(issue("revision", "Only ORIGINAL revisions are supported"));
        }
        if (blank(request.getJournalReference()) || request.getJournalReference().length() > 200) {
            issues.add(issue("journalReference", "A journal reference of at most 200 characters is required"));
        }
        if (blank(request.getPayrollPeriodId()) || request.getPayrollPeriodId().length() > 200) {
            issues.add(issue("payrollPeriodId", "A payroll period identifier of at most 200 characters is required"));
        }
        if (request.getPeriodStart() == null) {
            issues.add(issue("periodStart", "Period start is required"));
        }
        if (request.getPeriodEnd() == null) {
            issues.add(issue("periodEnd", "Period end is required"));
        } else if (request.getPeriodStart() != null && request.getPeriodStart().isAfter(request.getPeriodEnd())) {
            issues.add(issue("periodStart", "Period start must not be after period end"));
        }
        if (!validCurrency(request.getCurrency())) {
            issues.add(issue("currency", "Currency must be a supported three-letter ISO 4217 code"));
        }

        List<PayrollJournalLineRequest> lines = request.getLines();
        if (lines == null || lines.size() < 2) {
            issues.add(issue("lines", "At least two journal lines are required"));
            lines = lines == null ? List.of() : lines;
        } else if (lines.size() > MAX_LINES) {
            throw new IntegrationApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_LINES",
                    "A payroll journal may contain no more than 200 lines", false);
        }

        BigDecimal totalDebit = ZERO;
        BigDecimal totalCredit = ZERO;
        List<ChartOfAccounts> accounts = new ArrayList<>();
        List<Object> accountIssues = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            PayrollJournalLineRequest line = lines.get(i);
            if (line == null) {
                issues.add(issue("lines[" + i + "]", "Line is required"));
                accounts.add(null);
                continue;
            }
            BigDecimal debit = line.getDebitAmount();
            BigDecimal credit = line.getCreditAmount();
            if (debit == null || credit == null) {
                issues.add(issue("lines[" + i + "].amount", "Both debit and credit amounts are required"));
            } else {
                if (debit.compareTo(ZERO) < 0 || credit.compareTo(ZERO) < 0) {
                    issues.add(issue("lines[" + i + "].amount", "Amounts must not be negative"));
                }
                if (debit.scale() > 2 || credit.scale() > 2) {
                    issues.add(issue("lines[" + i + "].amount", "Amounts must have no more than two decimal places"));
                }
                if (!fitsLedgerAmount(debit) || !fitsLedgerAmount(credit)) {
                    issues.add(issue("lines[" + i + "].amount", "Amount exceeds ledger precision"));
                }
                boolean debitPositive = debit.compareTo(ZERO) > 0;
                boolean creditPositive = credit.compareTo(ZERO) > 0;
                if (debitPositive == creditPositive) {
                    issues.add(issue("lines[" + i + "].amount", "Exactly one side must be positive"));
                }
                totalDebit = totalDebit.add(debit);
                totalCredit = totalCredit.add(credit);
            }

            if (blank(line.getAccountCode())) {
                accountIssues.add(Map.of("accountCode", "", "reason", "unknown"));
                accounts.add(null);
            } else {
                Optional<ChartOfAccounts> account =
                        chartOfAccountsRepository.findByOrganizationIdAndAccountCode(organizationId, line.getAccountCode());
                if (account.isEmpty()) {
                    accountIssues.add(Map.of("accountCode", line.getAccountCode(), "reason", "unknown"));
                    accounts.add(null);
                } else if (!Boolean.TRUE.equals(account.get().getActive())) {
                    accountIssues.add(Map.of("accountCode", line.getAccountCode(), "reason", "inactive"));
                    accounts.add(null);
                } else {
                    accounts.add(account.get());
                }
            }
        }
        boolean journalUnbalanced = lines.size() >= 2 && totalDebit.compareTo(totalCredit) != 0;
        if (journalUnbalanced) {
            issues.add(issue("lines", "Total debits must equal total credits"));
        }
        if (!fitsLedgerAmount(totalDebit) || !fitsLedgerAmount(totalCredit)) {
            issues.add(issue("totals", "Journal total exceeds ledger precision"));
        }
        issues.addAll(accountIssues);

        if (!issues.isEmpty()) {
            String code = "ORIGINAL".equals(request.getRevision()) ? "VALIDATION_ERROR" : "UNSUPPORTED_REVISION";
            boolean retryable = false;
            if (journalUnbalanced && issues.size() == 1) {
                code = "JOURNAL_UNBALANCED";
            } else if (!accountIssues.isEmpty()) {
                code = "ACCOUNT_NOT_FOUND_OR_INACTIVE";
                retryable = true;
            } else if (journalUnbalanced && issues.stream()
                    .allMatch(detail -> detail.toString().contains("Total debits"))) {
                code = "JOURNAL_UNBALANCED";
            }
            throw new IntegrationApiException(HttpStatus.UNPROCESSABLE_ENTITY, code,
                    code.equals("ACCOUNT_NOT_FOUND_OR_INACTIVE")
                            ? "One or more accounts are unknown or inactive"
                            : code.equals("JOURNAL_UNBALANCED")
                            ? "Journal debits and credits are not balanced"
                            : "Payroll journal validation failed",
                    retryable, issues);
        }

        LocalDate journalDate = request.getPeriodEnd();
        FiscalPeriodDTO period;
        try {
            period = fiscalPeriodService.requireOpenPeriod(organizationId, journalDate);
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                throw new IntegrationApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "FISCAL_PERIOD_CLOSED", "Journal date is not in an open fiscal period", true);
            }
            throw ex;
        }
        return new ValidatedRequest(totalDebit, totalCredit, period.getId(), accounts);
    }

    private boolean validCurrency(String currency) {
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            return false;
        }
        try {
            Currency.getInstance(currency);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean fitsLedgerAmount(BigDecimal amount) {
        return amount == null || amount.precision() - amount.scale() <= MAX_INTEGER_DIGITS;
    }

    private Map<String, String> issue(String field, String message) {
        return Map.of("field", field, "message", message);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String hashRequest(PayrollJournalRequest request) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, request.getSourceSystem());
        append(canonical, request.getJournalReference());
        append(canonical, request.getRevision());
        append(canonical, request.getPayrollPeriodId());
        append(canonical, request.getPeriodStart() == null ? null : request.getPeriodStart().toString());
        append(canonical, request.getPeriodEnd() == null ? null : request.getPeriodEnd().toString());
        append(canonical, request.getCurrency());
        append(canonical, request.getNarration());
        List<PayrollJournalLineRequest> lines = request.getLines() == null
                ? List.of()
                : request.getLines().stream().sorted(Comparator.nullsFirst(lineComparator())).toList();
        append(canonical, Integer.toString(lines.size()));
        for (PayrollJournalLineRequest line : lines) {
            if (line == null) {
                append(canonical, null);
                continue;
            }
            append(canonical, line.getAccountCode());
            append(canonical, line.getAccountName());
            append(canonical, line.getLineType());
            append(canonical, normalized(line.getDebitAmount()));
            append(canonical, normalized(line.getCreditAmount()));
            append(canonical, line.getNarration());
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private Comparator<PayrollJournalLineRequest> lineComparator() {
        Comparator<String> strings = Comparator.nullsFirst(String::compareTo);
        return Comparator.comparing(PayrollJournalLineRequest::getAccountCode, strings)
                .thenComparing(PayrollJournalLineRequest::getAccountName, strings)
                .thenComparing(PayrollJournalLineRequest::getLineType, strings)
                .thenComparing(line -> normalized(line.getDebitAmount()), strings)
                .thenComparing(line -> normalized(line.getCreditAmount()), strings)
                .thenComparing(PayrollJournalLineRequest::getNarration, strings);
    }

    private String normalized(BigDecimal amount) {
        return amount == null ? null
                : amount.compareTo(ZERO) == 0 ? "0" : amount.stripTrailingZeros().toPlainString();
    }

    private void append(StringBuilder canonical, String value) {
        if (value == null) {
            canonical.append("-1:");
        } else {
            canonical.append(value.length()).append(':').append(value);
        }
    }

    private PayrollJournalResponse response(PayrollJournal journal, String key, boolean duplicate) {
        return new PayrollJournalResponse(
                journal.getId(),
                journal.getStatus(),
                key,
                journal.getJournalDate(),
                journal.getTotalDebit(),
                journal.getTotalCredit(),
                journal.getPostedAt(),
                duplicate);
    }

    private record ValidatedRequest(
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            Long fiscalPeriodId,
            List<ChartOfAccounts> accounts) {
    }
}
