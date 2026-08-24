package com.justjava.ams.accountant.service;

import com.justjava.ams.accountant.dto.*;
import com.justjava.ams.accountant.entity.ChartOfAccounts;
import com.justjava.ams.accountant.entity.DepreciationJournalImport;
import com.justjava.ams.accountant.entity.JournalLine;
import com.justjava.ams.accountant.entity.ManualJournal;
import com.justjava.ams.accountant.repository.ChartOfAccountsRepository;
import com.justjava.ams.accountant.repository.DepreciationJournalImportRepository;
import com.justjava.ams.accountant.repository.JournalLineRepository;
import com.justjava.ams.accountant.repository.ManualJournalRepository;
import com.justjava.ams.auditor.service.AuditLogService;
import com.justjava.ams.common.entity.Branch;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.BranchRepository;
import com.justjava.ams.common.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
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
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DepreciationJournalImportService {
    private final DepreciationJournalImportRepository importRepository;
    private final OrganizationRepository organizationRepository;
    private final BranchRepository branchRepository;
    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final ManualJournalRepository manualJournalRepository;
    private final JournalLineRepository journalLineRepository;
    private final ManualJournalService manualJournalService;
    private final FiscalPeriodService fiscalPeriodService;
    private final AuditLogService auditLogService;

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"));

    public DepreciationJournalImportDTO importFromRequest(Long organizationId, DepreciationJournalImportRequest request, String importedBy) {
        return importBatch(organizationId, request, null, null, importedBy);
    }

    public DepreciationJournalImportDTO importFromFile(Long organizationId,
                                                      String externalSystem,
                                                      String externalBatchId,
                                                      LocalDate journalDate,
                                                      String description,
                                                      String branchCode,
                                                      Boolean autoSubmit,
                                                      MultipartFile file,
                                                      String importedBy) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Depreciation journal file is required");
        }
        String payloadHash = hash(file);
        DepreciationJournalImportRequest request = DepreciationJournalImportRequest.builder()
                .externalSystem(externalSystem)
                .externalBatchId(externalBatchId)
                .journalDate(journalDate)
                .description(description)
                .branchCode(branchCode)
                .autoSubmit(autoSubmit)
                .lines(parseFile(file))
                .build();
        return importBatch(organizationId, request, file.getOriginalFilename(), payloadHash, importedBy);
    }

    @Transactional(readOnly = true)
    public List<DepreciationJournalImportDTO> getImports(Long organizationId) {
        organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        return importRepository.findByOrganizationIdOrderByImportedAtDesc(organizationId).stream()
                .map(this::mapToDTO)
                .toList();
    }

    private DepreciationJournalImportDTO importBatch(Long organizationId,
                                                    DepreciationJournalImportRequest request,
                                                    String sourceFileName,
                                                    String payloadHash,
                                                    String importedBy) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
        String externalSystem = required(request.getExternalSystem(), "External system is required");
        String externalBatchId = required(request.getExternalBatchId(), "External batch ID is required");
        LocalDate journalDate = request.getJournalDate();
        if (journalDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Journal date is required");
        }
        fiscalPeriodService.requireOpenPeriod(organizationId, journalDate);
        importRepository.findByOrganizationIdAndExternalBatchIdIgnoreCase(organizationId, externalBatchId)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Depreciation batch has already been imported");
                });
        if (request.getLines() == null || request.getLines().size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Depreciation journal must have at least two lines");
        }

        Branch branch = findBranch(request.getBranchCode(), organizationId);
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        List<ResolvedLine> lines = new ArrayList<>();
        int lineNumber = 0;
        for (DepreciationJournalLineRequest line : request.getLines()) {
            lineNumber++;
            ChartOfAccounts account = findAccount(line.getAccountCode(), organizationId, lineNumber);
            BigDecimal debit = amount(line.getDebitAmount());
            BigDecimal credit = amount(line.getCreditAmount());
            validateLineAmount(debit, credit, lineNumber);
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
            lines.add(new ResolvedLine(line, account, debit, credit));
        }
        if (totalDebit.compareTo(BigDecimal.ZERO) <= 0 || totalDebit.compareTo(totalCredit) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Depreciation journal must be balanced");
        }

        String user = user(importedBy);
        ManualJournal journal = manualJournalRepository.save(ManualJournal.builder()
                .organization(organization)
                .branch(branch)
                .description(defaultDescription(request.getDescription(), externalSystem, externalBatchId))
                .journalDate(journalDate)
                .status(ManualJournal.JournalStatus.DRAFT)
                .createdBy(user)
                .build());

        int sequence = 1;
        for (ResolvedLine line : lines) {
            journalLineRepository.save(JournalLine.builder()
                    .manualJournal(journal)
                    .chartOfAccounts(line.account())
                    .debitAmount(line.debit())
                    .creditAmount(line.credit())
                    .departmentCode(trimToNull(line.request().getDepartmentCode()))
                    .projectCode(trimToNull(line.request().getProjectCode()))
                    .branchCode(trimToNull(line.request().getBranchCode()))
                    .narration(lineNarration(line.request()))
                    .lineSequence(sequence++)
                    .build());
        }

        DepreciationJournalImport savedImport = importRepository.save(DepreciationJournalImport.builder()
                .organization(organization)
                .manualJournal(journal)
                .externalSystem(externalSystem)
                .externalBatchId(externalBatchId)
                .journalDate(journalDate)
                .totalDebit(totalDebit)
                .totalCredit(totalCredit)
                .lineCount(lines.size())
                .sourceFileName(trimToNull(sourceFileName))
                .payloadHash(payloadHash)
                .status(DepreciationJournalImport.ImportStatus.IMPORTED)
                .importedBy(user)
                .build());

        if (Boolean.TRUE.equals(request.getAutoSubmit())) {
            manualJournalService.submitForApproval(journal.getId(), user);
            savedImport.setStatus(DepreciationJournalImport.ImportStatus.SUBMITTED);
            savedImport = importRepository.save(savedImport);
        }

        log(organizationId, savedImport.getId(), "CREATE", null, savedImport.getExternalBatchId(),
                "Depreciation journal imported from " + savedImport.getExternalSystem() + " by " + user);
        return mapToDTO(savedImport);
    }

    private List<DepreciationJournalLineRequest> parseFile(MultipartFile file) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        try {
            if (filename.endsWith(".xlsx")) {
                return parseXlsx(file);
            }
            if (filename.endsWith(".csv") || filename.endsWith(".txt")) {
                return parseCsv(file);
            }
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read depreciation journal file");
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported depreciation journal file. Upload CSV or XLSX");
    }

    private List<DepreciationJournalLineRequest> parseCsv(MultipartFile file) throws IOException {
        List<DepreciationJournalLineRequest> lines = new ArrayList<>();
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
                lines.add(lineFromColumns(columns, rowNumber));
            }
        }
        return lines;
    }

    private List<DepreciationJournalLineRequest> parseXlsx(MultipartFile file) throws IOException {
        List<DepreciationJournalLineRequest> lines = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                int rowNumber = row.getRowNum() + 1;
                List<String> columns = new ArrayList<>();
                for (int i = 0; i < 9; i++) {
                    columns.add(cellValue(row.getCell(i)));
                }
                if (rowNumber == 1 && looksLikeHeader(String.join(",", columns))) {
                    continue;
                }
                if (columns.stream().allMatch(String::isBlank)) {
                    continue;
                }
                lines.add(lineFromColumns(columns, rowNumber));
            }
        }
        return lines;
    }

    private DepreciationJournalLineRequest lineFromColumns(List<String> columns, int rowNumber) {
        if (columns.size() < 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Row " + rowNumber + " must include accountCode, debitAmount, creditAmount, and description");
        }
        return DepreciationJournalLineRequest.builder()
                .accountCode(required(columns.get(0), "Row " + rowNumber + " account code is required"))
                .debitAmount(parseAmount(columns.get(1), rowNumber, "debit"))
                .creditAmount(parseAmount(columns.get(2), rowNumber, "credit"))
                .description(required(columns.get(3), "Row " + rowNumber + " description is required"))
                .referenceNumber(columns.size() > 4 ? trimToNull(columns.get(4)) : null)
                .branchCode(columns.size() > 5 ? trimToNull(columns.get(5)) : null)
                .departmentCode(columns.size() > 6 ? trimToNull(columns.get(6)) : null)
                .projectCode(columns.size() > 7 ? trimToNull(columns.get(7)) : null)
                .assetCode(columns.size() > 8 ? trimToNull(columns.get(8)) : null)
                .build();
    }

    private boolean looksLikeHeader(String row) {
        String normalized = row.toLowerCase();
        return normalized.contains("account") && (normalized.contains("debit") || normalized.contains("credit"));
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

    private BigDecimal parseAmount(String value, int rowNumber, String label) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(normalized.replace(",", "").trim());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Row " + rowNumber + " has an invalid " + label + " amount");
        }
    }

    private ChartOfAccounts findAccount(String accountCode, Long organizationId, int lineNumber) {
        ChartOfAccounts account = chartOfAccountsRepository.findByOrganizationIdAndAccountCodeIgnoreCase(organizationId, accountCode.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Row " + lineNumber + " account code was not found"));
        if (Boolean.FALSE.equals(account.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Row " + lineNumber + " account code is inactive");
        }
        return account;
    }

    private Branch findBranch(String branchCode, Long organizationId) {
        String normalized = trimToNull(branchCode);
        if (normalized == null) {
            return null;
        }
        Branch branch = branchRepository.findByCodeIgnoreCase(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Branch code was not found"));
        if (!branch.getOrganization().getId().equals(organizationId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Branch does not belong to organization");
        }
        return branch;
    }

    private void validateLineAmount(BigDecimal debit, BigDecimal credit, int lineNumber) {
        if (debit.compareTo(BigDecimal.ZERO) < 0 || credit.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Row " + lineNumber + " amounts cannot be negative");
        }
        boolean hasDebit = debit.compareTo(BigDecimal.ZERO) > 0;
        boolean hasCredit = credit.compareTo(BigDecimal.ZERO) > 0;
        if (hasDebit == hasCredit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Row " + lineNumber + " must have either debit or credit amount");
        }
    }

    private String lineNarration(DepreciationJournalLineRequest line) {
        String description = required(line.getDescription(), "Line description is required");
        String assetCode = trimToNull(line.getAssetCode());
        String reference = trimToNull(line.getReferenceNumber());
        List<String> parts = new ArrayList<>();
        parts.add(description);
        if (assetCode != null) parts.add("Asset " + assetCode);
        if (reference != null) parts.add("Ref " + reference);
        return String.join(" | ", parts);
    }

    private String defaultDescription(String description, String externalSystem, String externalBatchId) {
        String normalized = trimToNull(description);
        return normalized != null
                ? normalized
                : "Depreciation journal " + externalBatchId + " from " + externalSystem;
    }

    private String hash(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(file.getBytes()));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to hash depreciation journal file");
        }
    }

    private BigDecimal amount(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
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

    private DepreciationJournalImportDTO mapToDTO(DepreciationJournalImport entity) {
        return DepreciationJournalImportDTO.builder()
                .id(entity.getId())
                .organizationId(entity.getOrganization().getId())
                .manualJournalId(entity.getManualJournal().getId())
                .manualJournalStatus(entity.getManualJournal().getStatus().name())
                .externalSystem(entity.getExternalSystem())
                .externalBatchId(entity.getExternalBatchId())
                .journalDate(entity.getJournalDate())
                .totalDebit(entity.getTotalDebit())
                .totalCredit(entity.getTotalCredit())
                .lineCount(entity.getLineCount())
                .sourceFileName(entity.getSourceFileName())
                .payloadHash(entity.getPayloadHash())
                .status(entity.getStatus().name())
                .importedBy(entity.getImportedBy())
                .importedAt(entity.getImportedAt())
                .updatedAt(entity.getUpdatedAt())
                .validationWarnings(List.of())
                .build();
    }

    private void log(Long organizationId, Long importId, String action, String oldValue, String newValue, String description) {
        try {
            auditLogService.log(organizationId, "DepreciationJournalImport", importId, action, oldValue, newValue, description);
        } catch (Exception ex) {
        }
    }

    private record ResolvedLine(DepreciationJournalLineRequest request, ChartOfAccounts account, BigDecimal debit, BigDecimal credit) {}
}
