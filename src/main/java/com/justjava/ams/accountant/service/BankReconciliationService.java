package com.justjava.ams.accountant.service;

import com.justjava.ams.accountant.dto.BankReconciliationCreateRequest;
import com.justjava.ams.accountant.dto.BankReconciliationDTO;
import com.justjava.ams.accountant.dto.BankStatementLineDTO;
import com.justjava.ams.accountant.dto.GeneralLedgerDTO;
import com.justjava.ams.accountant.entity.BankAccount;
import com.justjava.ams.accountant.entity.BankReconciliation;
import com.justjava.ams.accountant.entity.BankStatementLine;
import com.justjava.ams.accountant.entity.GeneralLedger;
import com.justjava.ams.accountant.repository.BankAccountRepository;
import com.justjava.ams.accountant.repository.BankReconciliationRepository;
import com.justjava.ams.accountant.repository.BankStatementLineRepository;
import com.justjava.ams.accountant.repository.GeneralLedgerRepository;
import com.justjava.ams.auditor.service.AuditLogService;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class BankReconciliationService {

    private final BankReconciliationRepository reconciliationRepository;
    private final BankStatementLineRepository statementLineRepository;
    private final BankAccountRepository bankAccountRepository;
    private final GeneralLedgerRepository generalLedgerRepository;
    private final OrganizationRepository organizationRepository;
    private final AuditLogService auditLogService;

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"));

    public BankReconciliationDTO createReconciliation(Long organizationId, BankReconciliationCreateRequest request, String importedBy) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        BankAccount bankAccount = findBankAccount(request.getBankAccountId(), organizationId);

        BankReconciliation reconciliation = BankReconciliation.builder()
                .organization(organization)
                .bankAccount(bankAccount)
                .statementDate(requiredDate(request.getStatementDate()))
                .openingBalance(requiredAmount(request.getOpeningBalance(), "Opening balance is required"))
                .closingBalance(requiredAmount(request.getClosingBalance(), "Closing balance is required"))
                .clearedAmount(BigDecimal.ZERO)
                .unresolvedDifference(BigDecimal.ZERO)
                .status(BankReconciliation.ReconciliationStatus.DRAFT)
                .importedLineCount(0)
                .matchedLineCount(0)
                .build();

        BankReconciliation saved = reconciliationRepository.save(reconciliation);
        if (request.getStatementLines() != null) {
            for (BankStatementLineDTO line : request.getStatementLines()) {
                statementLineRepository.save(BankStatementLine.builder()
                        .reconciliation(saved)
                        .transactionDate(requiredDate(line.getTransactionDate()))
                        .amount(requiredAmount(line.getAmount(), "Statement line amount is required"))
                        .referenceNumber(trimToNull(line.getReferenceNumber()))
                        .description(required(line.getDescription(), "Statement line description is required"))
                        .matchStatus(BankStatementLine.MatchStatus.UNMATCHED)
                        .build());
            }
        }

        autoMatch(saved.getId(), importedBy);
        BankReconciliation recalculated = recalculate(saved.getId());
        log(recalculated.getOrganization().getId(), recalculated.getId(), "CREATE", null, "DRAFT",
                "Bank reconciliation imported by " + user(importedBy));
        return mapToDTO(recalculated);
    }

    public BankReconciliationDTO importReconciliation(Long organizationId,
                                                      Long bankAccountId,
                                                      java.time.LocalDate statementDate,
                                                      BigDecimal openingBalance,
                                                      BigDecimal closingBalance,
                                                      MultipartFile file,
                                                      String importedBy) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statement file is required");
        }
        BankReconciliationCreateRequest request = BankReconciliationCreateRequest.builder()
                .bankAccountId(bankAccountId)
                .statementDate(statementDate)
                .openingBalance(openingBalance)
                .closingBalance(closingBalance)
                .statementLines(parseStatementFile(file))
                .build();
        return createReconciliation(organizationId, request, importedBy);
    }

    @Transactional(readOnly = true)
    public List<BankReconciliationDTO> getReconciliations(Long organizationId, Long bankAccountId) {
        organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        findBankAccount(bankAccountId, organizationId);
        return reconciliationRepository.findByOrganizationIdAndBankAccountIdOrderByStatementDateDesc(organizationId, bankAccountId)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public BankReconciliationDTO getReconciliation(Long reconciliationId) {
        return mapToDTO(findReconciliation(reconciliationId));
    }

    public BankReconciliationDTO autoMatch(Long reconciliationId, String matchedBy) {
        BankReconciliation reconciliation = findDraftReconciliation(reconciliationId);
        List<BankStatementLine> lines = statementLineRepository.findByReconciliationIdOrderByTransactionDateAscIdAsc(reconciliationId);
        Set<Long> matchedInRun = new HashSet<>();

        for (BankStatementLine line : lines) {
            if (!BankStatementLine.MatchStatus.UNMATCHED.equals(line.getMatchStatus())) {
                continue;
            }
            List<GeneralLedger> candidates = generalLedgerRepository.findPostedBankEntriesByAccountAndDate(
                    reconciliation.getBankAccount().getChartAccount().getId(),
                    line.getTransactionDate());
            candidates.stream()
                    .filter(gl -> !matchedInRun.contains(gl.getId()))
                    .filter(gl -> !statementLineRepository.existsByMatchedGeneralLedgerId(gl.getId()))
                    .filter(gl -> signedAmount(gl).compareTo(line.getAmount()) == 0)
                    .filter(gl -> referenceMatches(line, gl))
                    .findFirst()
                    .ifPresent(gl -> {
                        line.setMatchedGeneralLedgerId(gl.getId());
                        line.setMatchStatus(BankStatementLine.MatchStatus.AUTO_MATCHED);
                        line.setMatchedAt(LocalDateTime.now());
                        line.setMatchedBy(user(matchedBy));
                        statementLineRepository.save(line);
                        matchedInRun.add(gl.getId());
                    });
        }

        BankReconciliation recalculated = recalculate(reconciliationId);
        log(recalculated.getOrganization().getId(), recalculated.getId(), "UPDATE", null, "AUTO_MATCH",
                "Bank reconciliation auto-match run by " + user(matchedBy));
        return mapToDTO(recalculated);
    }

    public BankReconciliationDTO manuallyMatchLine(Long reconciliationId, Long statementLineId, Long generalLedgerId, String matchedBy) {
        BankReconciliation reconciliation = findDraftReconciliation(reconciliationId);
        BankStatementLine line = statementLineRepository.findById(statementLineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Statement line not found"));
        if (!line.getReconciliation().getId().equals(reconciliationId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statement line does not belong to reconciliation");
        }
        GeneralLedger gl = generalLedgerRepository.findById(generalLedgerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "General ledger entry not found"));
        validateMatchCandidate(reconciliation, line, gl);

        line.setMatchedGeneralLedgerId(gl.getId());
        line.setMatchStatus(BankStatementLine.MatchStatus.MANUAL_MATCHED);
        line.setMatchedAt(LocalDateTime.now());
        line.setMatchedBy(user(matchedBy));
        statementLineRepository.save(line);

        BankReconciliation recalculated = recalculate(reconciliationId);
        log(recalculated.getOrganization().getId(), recalculated.getId(), "UPDATE", null, "MANUAL_MATCH",
                "Statement line " + statementLineId + " manually matched by " + user(matchedBy));
        return mapToDTO(recalculated);
    }

    public BankReconciliationDTO unmatchLine(Long reconciliationId, Long statementLineId, String unmatchedBy) {
        BankReconciliation reconciliation = findDraftReconciliation(reconciliationId);
        BankStatementLine line = statementLineRepository.findById(statementLineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Statement line not found"));
        if (!line.getReconciliation().getId().equals(reconciliationId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statement line does not belong to reconciliation");
        }
        String oldValue = line.getMatchedGeneralLedgerId() != null ? String.valueOf(line.getMatchedGeneralLedgerId()) : null;
        line.setMatchedGeneralLedgerId(null);
        line.setMatchStatus(BankStatementLine.MatchStatus.UNMATCHED);
        line.setMatchedAt(null);
        line.setMatchedBy(null);
        statementLineRepository.save(line);

        BankReconciliation recalculated = recalculate(reconciliation.getId());
        log(recalculated.getOrganization().getId(), recalculated.getId(), "UPDATE", oldValue, "UNMATCHED",
                "Statement line " + statementLineId + " unmatched by " + user(unmatchedBy));
        return mapToDTO(recalculated);
    }

    @Transactional(readOnly = true)
    public List<GeneralLedgerDTO> getMatchCandidates(Long reconciliationId, Long statementLineId) {
        BankReconciliation reconciliation = findReconciliation(reconciliationId);
        BankStatementLine line = statementLineRepository.findById(statementLineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Statement line not found"));
        if (!line.getReconciliation().getId().equals(reconciliationId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statement line does not belong to reconciliation");
        }
        return generalLedgerRepository.findPostedBankEntriesByAccountAndDate(
                        reconciliation.getBankAccount().getChartAccount().getId(),
                        line.getTransactionDate())
                .stream()
                .filter(gl -> signedAmount(gl).compareTo(line.getAmount()) == 0)
                .filter(gl -> referenceMatches(line, gl))
                .filter(gl -> !statementLineRepository.existsByMatchedGeneralLedgerIdAndIdNot(gl.getId(), line.getId()))
                .map(this::mapGeneralLedgerToDTO)
                .toList();
    }

    public BankReconciliationDTO completeReconciliation(Long reconciliationId, String reconciledBy) {
        BankReconciliation reconciliation = findDraftReconciliation(reconciliationId);
        BankReconciliation recalculated = recalculate(reconciliationId);
        List<BankStatementLine> lines = statementLineRepository.findByReconciliationIdOrderByTransactionDateAscIdAsc(reconciliationId);

        if (lines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reconciliation must have statement lines before completion");
        }
        if (recalculated.getMatchedLineCount() == null || recalculated.getMatchedLineCount() < lines.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "All statement lines must be matched before completion");
        }
        if (recalculated.getUnresolvedDifference().compareTo(BigDecimal.ZERO) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unresolved difference must be zero before completion");
        }

        recalculated.setStatus(BankReconciliation.ReconciliationStatus.COMPLETED);
        recalculated.setReconciledBy(user(reconciledBy));
        recalculated.setReconciledAt(LocalDateTime.now());
        BankReconciliation saved = reconciliationRepository.save(recalculated);
        log(saved.getOrganization().getId(), saved.getId(), "APPROVE", "DRAFT", "COMPLETED",
                "Bank reconciliation completed by " + user(reconciledBy));
        return mapToDTO(saved);
    }

    private void validateMatchCandidate(BankReconciliation reconciliation, BankStatementLine line, GeneralLedger gl) {
        if (!gl.getAccount().getId().equals(reconciliation.getBankAccount().getChartAccount().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GL entry is not posted to the selected bank account");
        }
        if (!GeneralLedger.TransactionStatus.POSTED.equals(gl.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only posted GL entries can be matched");
        }
        if (statementLineRepository.existsByMatchedGeneralLedgerIdAndIdNot(gl.getId(), line.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "GL entry is already matched to another statement line");
        }
        if (signedAmount(gl).compareTo(line.getAmount()) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statement line amount does not match GL entry amount");
        }
    }

    private BankReconciliation recalculate(Long reconciliationId) {
        BankReconciliation reconciliation = findReconciliation(reconciliationId);
        List<BankStatementLine> lines = statementLineRepository.findByReconciliationIdOrderByTransactionDateAscIdAsc(reconciliationId);
        BigDecimal clearedAmount = lines.stream()
                .filter(line -> !BankStatementLine.MatchStatus.UNMATCHED.equals(line.getMatchStatus()))
                .map(BankStatementLine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expectedMovement = reconciliation.getClosingBalance().subtract(reconciliation.getOpeningBalance());

        reconciliation.setImportedLineCount(lines.size());
        reconciliation.setMatchedLineCount((int) lines.stream()
                .filter(line -> !BankStatementLine.MatchStatus.UNMATCHED.equals(line.getMatchStatus()))
                .count());
        reconciliation.setClearedAmount(clearedAmount);
        reconciliation.setUnresolvedDifference(expectedMovement.subtract(clearedAmount));
        return reconciliationRepository.save(reconciliation);
    }

    private boolean referenceMatches(BankStatementLine line, GeneralLedger gl) {
        String lineReference = trimToNull(line.getReferenceNumber());
        String glReference = trimToNull(gl.getReferenceNumber());
        return lineReference == null || glReference == null || lineReference.equalsIgnoreCase(glReference);
    }

    private List<BankStatementLineDTO> parseStatementFile(MultipartFile file) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        try {
            if (filename.endsWith(".xlsx")) {
                return parseXlsx(file);
            }
            if (filename.endsWith(".csv") || filename.endsWith(".txt")) {
                return parseCsv(file);
            }
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read statement file");
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported statement file. Upload CSV or XLSX");
    }

    private List<BankStatementLineDTO> parseCsv(MultipartFile file) throws IOException {
        List<BankStatementLineDTO> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String row;
            int rowNumber = 0;
            while ((row = reader.readLine()) != null) {
                rowNumber++;
                if (rowNumber == 1 && looksLikeHeader(row)) {
                    continue;
                }
                List<String> columns = parseCsvRow(row);
                if (columns.stream().allMatch(String::isBlank)) {
                    continue;
                }
                lines.add(statementLineFromColumns(columns, rowNumber));
            }
        }
        return requireParsedLines(lines);
    }

    private List<BankStatementLineDTO> parseXlsx(MultipartFile file) throws IOException {
        List<BankStatementLineDTO> lines = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                int rowNumber = row.getRowNum() + 1;
                List<String> columns = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    columns.add(cellValue(row.getCell(i)));
                }
                if (rowNumber == 1 && looksLikeHeader(String.join(",", columns))) {
                    continue;
                }
                if (columns.stream().allMatch(String::isBlank)) {
                    continue;
                }
                lines.add(statementLineFromColumns(columns, rowNumber));
            }
        }
        return requireParsedLines(lines);
    }

    private List<BankStatementLineDTO> requireParsedLines(List<BankStatementLineDTO> lines) {
        if (lines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statement file has no transaction lines");
        }
        return lines;
    }

    private BankStatementLineDTO statementLineFromColumns(List<String> columns, int rowNumber) {
        if (columns.size() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Row " + rowNumber + " must include date, amount, and description");
        }
        return BankStatementLineDTO.builder()
                .transactionDate(parseDate(columns.get(0), rowNumber))
                .amount(parseAmount(columns.get(1), rowNumber))
                .description(required(columns.get(2), "Row " + rowNumber + " description is required"))
                .referenceNumber(columns.size() > 3 ? trimToNull(columns.get(3)) : null)
                .build();
    }

    private boolean looksLikeHeader(String row) {
        String normalized = row.toLowerCase();
        return normalized.contains("date") && normalized.contains("amount");
    }

    private List<String> parseCsvRow(String row) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < row.length(); i++) {
            char ch = row.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < row.length() && row.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString().trim());
        return values;
    }

    private String cellValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private LocalDate parseDate(String value, int rowNumber) {
        String normalized = required(value, "Row " + rowNumber + " date is required");
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Row " + rowNumber + " has an invalid date");
    }

    private BigDecimal parseAmount(String value, int rowNumber) {
        String normalized = required(value, "Row " + rowNumber + " amount is required")
                .replace(",", "")
                .replace("NGN", "")
                .replace("₦", "")
                .trim();
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Row " + rowNumber + " has an invalid amount");
        }
    }

    private GeneralLedgerDTO mapGeneralLedgerToDTO(GeneralLedger gl) {
        return GeneralLedgerDTO.builder()
                .id(gl.getId())
                .accountId(gl.getAccount().getId())
                .journalNumber(gl.getJournalNumber())
                .transactionDate(gl.getTransactionDate())
                .debitCredit(gl.getDebitCredit().name())
                .amount(gl.getAmount())
                .description(gl.getDescription())
                .referenceNumber(gl.getReferenceNumber())
                .notes(gl.getNotes())
                .status(gl.getStatus().name())
                .sourceType(gl.getSourceType().name())
                .sourceId(gl.getSourceId())
                .postingBatchId(gl.getPostingBatchId())
                .postedBy(gl.getPostedBy())
                .postedAt(gl.getPostedAt())
                .createdAt(gl.getCreatedAt())
                .updatedAt(gl.getUpdatedAt())
                .build();
    }

    private BigDecimal signedAmount(GeneralLedger gl) {
        BigDecimal amount = gl.getAmount() != null ? gl.getAmount() : BigDecimal.ZERO;
        return GeneralLedger.DebitCredit.CREDIT.equals(gl.getDebitCredit()) ? amount.negate() : amount;
    }

    private BankReconciliation findDraftReconciliation(Long reconciliationId) {
        BankReconciliation reconciliation = findReconciliation(reconciliationId);
        if (!BankReconciliation.ReconciliationStatus.DRAFT.equals(reconciliation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT reconciliations can be changed");
        }
        return reconciliation;
    }

    private BankReconciliation findReconciliation(Long reconciliationId) {
        return reconciliationRepository.findById(reconciliationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bank reconciliation not found"));
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

    private BankReconciliationDTO mapToDTO(BankReconciliation reconciliation) {
        List<BankStatementLineDTO> lines = statementLineRepository.findByReconciliationIdOrderByTransactionDateAscIdAsc(reconciliation.getId())
                .stream()
                .map(this::mapLineToDTO)
                .toList();
        return BankReconciliationDTO.builder()
                .id(reconciliation.getId())
                .organizationId(reconciliation.getOrganization().getId())
                .bankAccountId(reconciliation.getBankAccount().getId())
                .bankName(reconciliation.getBankAccount().getBankName())
                .accountNumber(reconciliation.getBankAccount().getAccountNumber())
                .statementDate(reconciliation.getStatementDate())
                .openingBalance(reconciliation.getOpeningBalance())
                .closingBalance(reconciliation.getClosingBalance())
                .clearedAmount(reconciliation.getClearedAmount())
                .unresolvedDifference(reconciliation.getUnresolvedDifference())
                .status(reconciliation.getStatus().name())
                .importedLineCount(reconciliation.getImportedLineCount())
                .matchedLineCount(reconciliation.getMatchedLineCount())
                .reconciledBy(reconciliation.getReconciledBy())
                .reconciledAt(reconciliation.getReconciledAt())
                .statementLines(lines)
                .createdAt(reconciliation.getCreatedAt())
                .updatedAt(reconciliation.getUpdatedAt())
                .build();
    }

    private BankStatementLineDTO mapLineToDTO(BankStatementLine line) {
        return BankStatementLineDTO.builder()
                .id(line.getId())
                .reconciliationId(line.getReconciliation().getId())
                .transactionDate(line.getTransactionDate())
                .amount(line.getAmount())
                .referenceNumber(line.getReferenceNumber())
                .description(line.getDescription())
                .matchStatus(line.getMatchStatus().name())
                .matchedGeneralLedgerId(line.getMatchedGeneralLedgerId())
                .matchedAt(line.getMatchedAt())
                .matchedBy(line.getMatchedBy())
                .createdAt(line.getCreatedAt())
                .updatedAt(line.getUpdatedAt())
                .build();
    }

    private java.time.LocalDate requiredDate(java.time.LocalDate value) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Date is required");
        }
        return value;
    }

    private BigDecimal requiredAmount(BigDecimal value, String message) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value;
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

    private String user(String value) {
        String normalized = trimToNull(value);
        return normalized != null ? normalized : "system";
    }

    private void log(Long organizationId, Long reconciliationId, String action, String oldValue, String newValue, String description) {
        try {
            auditLogService.log(organizationId, "BankReconciliation", reconciliationId, action, oldValue, newValue, description);
        } catch (Exception ex) {
        }
    }
}
