package com.justjava.ams.financeAdmin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justjava.ams.accountant.entity.ChartOfAccounts;
import com.justjava.ams.accountant.repository.ChartOfAccountsRepository;
import com.justjava.ams.auditor.service.AuditLogService;
import com.justjava.ams.common.entity.Branch;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.BranchRepository;
import com.justjava.ams.common.repository.OrganizationRepository;
import com.justjava.ams.financeAdmin.dto.BulkImportConfirmResponse;
import com.justjava.ams.financeAdmin.dto.BulkImportPreviewResponse;
import com.justjava.ams.financeAdmin.dto.BulkImportRowDTO;
import com.justjava.ams.financeAdmin.entity.BulkImport;
import com.justjava.ams.financeAdmin.entity.BulkImportRow;
import com.justjava.ams.financeAdmin.repository.BulkImportRepository;
import com.justjava.ams.financeAdmin.repository.BulkImportRowRepository;
import lombok.RequiredArgsConstructor;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class BulkImportService {

    private static final List<String> BRANCH_HEADERS = List.of("branch_code", "branch_name", "is_active");
    private static final List<String> COA_HEADERS = List.of(
            "account_code",
            "account_name",
            "description",
            "account_type",
            "account_subtype",
            "normal_balance",
            "is_active");
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    private final BulkImportRepository bulkImportRepository;
    private final BulkImportRowRepository bulkImportRowRepository;
    private final OrganizationRepository organizationRepository;
    private final BranchRepository branchRepository;
    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public String branchTemplate() {
        return String.join(",", BRANCH_HEADERS) + "\nBR-LOS-01,Lagos Island Operations,true\n";
    }

    public String chartOfAccountsTemplate() {
        return String.join(",", COA_HEADERS)
                + "\n1000,Cash at Bank,Primary operating bank account,ASSET,CURRENT_ASSET,DEBIT,true\n";
    }

    public BulkImportPreviewResponse validateBranches(Long organizationId, MultipartFile file, boolean updateExisting, String uploadedBy) {
        Organization organization = findOrganization(organizationId);
        List<CsvRecord> records = parseCsv(file, BRANCH_HEADERS);
        BulkImport bulkImport = newImport(organization, BulkImport.ImportType.BRANCHES, file, updateExisting, uploadedBy);

        Set<String> seenCodes = new LinkedHashSet<>();
        for (CsvRecord record : records) {
            RowValidation validation = validateBranch(organizationId, record.values(), seenCodes, updateExisting);
            bulkImport.addRow(toRow(record.rowNumber(), record.values(), validation));
        }

        finalizeValidation(bulkImport);
        return mapToPreview(bulkImportRepository.save(bulkImport));
    }

    public BulkImportPreviewResponse validateChartOfAccounts(Long organizationId, MultipartFile file, boolean updateExisting, String uploadedBy) {
        Organization organization = findOrganization(organizationId);
        List<CsvRecord> records = parseCsv(file, COA_HEADERS);
        BulkImport bulkImport = newImport(organization, BulkImport.ImportType.CHART_OF_ACCOUNTS, file, updateExisting, uploadedBy);

        Set<String> seenCodes = new LinkedHashSet<>();
        for (CsvRecord record : records) {
            RowValidation validation = validateChartOfAccountsRow(organizationId, record.values(), seenCodes, updateExisting);
            bulkImport.addRow(toRow(record.rowNumber(), record.values(), validation));
        }

        finalizeValidation(bulkImport);
        return mapToPreview(bulkImportRepository.save(bulkImport));
    }

    @Transactional(readOnly = true)
    public BulkImportPreviewResponse getImport(Long importId) {
        return mapToPreview(findImport(importId));
    }

    public BulkImportConfirmResponse confirm(Long importId) {
        BulkImport bulkImport = findImport(importId);
        if (bulkImport.getStatus() != BulkImport.ImportStatus.VALIDATED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a fully validated import can be confirmed");
        }

        bulkImport.setStatus(BulkImport.ImportStatus.PROCESSING);
        bulkImportRepository.save(bulkImport);

        int created = 0;
        int updated = 0;
        int failed = 0;

        for (BulkImportRow row : bulkImport.getRows()) {
            if (row.getStatus() != BulkImportRow.RowStatus.VALID) {
                row.setStatus(BulkImportRow.RowStatus.SKIPPED);
                continue;
            }

            try {
                Map<String, String> normalized = readJson(row.getNormalizedDataJson());
                if (bulkImport.getImportType() == BulkImport.ImportType.BRANCHES) {
                    boolean wasUpdate = upsertBranch(bulkImport.getOrganization(), bulkImport.getUpdateExisting(), normalized, row);
                    if (wasUpdate) {
                        updated++;
                    } else {
                        created++;
                    }
                } else {
                    boolean wasUpdate = upsertChartOfAccounts(bulkImport.getOrganization(), bulkImport.getUpdateExisting(), normalized, row);
                    if (wasUpdate) {
                        updated++;
                    } else {
                        created++;
                    }
                }
            } catch (Exception ex) {
                failed++;
                row.setStatus(BulkImportRow.RowStatus.FAILED);
                row.setErrorMessage(messageOf(ex));
            }
        }

        int skipped = (int) bulkImport.getRows().stream()
                .filter(row -> row.getStatus() == BulkImportRow.RowStatus.SKIPPED)
                .count();
        bulkImport.setCreatedCount(created);
        bulkImport.setUpdatedCount(updated);
        bulkImport.setSkippedCount(skipped);
        bulkImport.setFailedCount(failed);
        bulkImport.setCompletedAt(LocalDateTime.now());
        bulkImport.setStatus(failed > 0 || skipped > 0
                ? BulkImport.ImportStatus.COMPLETED_WITH_ERRORS
                : BulkImport.ImportStatus.COMPLETED);

        BulkImport saved = bulkImportRepository.save(bulkImport);
        auditLogService.log(
                saved.getOrganization().getId(),
                saved.getImportType() == BulkImport.ImportType.BRANCHES ? "Branch" : "ChartOfAccounts",
                null,
                "CREATE",
                null,
                summary(saved),
                "Bulk import " + saved.getId() + " completed");

        return BulkImportConfirmResponse.builder()
                .importId(saved.getId())
                .status(saved.getStatus().toString())
                .totalRows(saved.getTotalRows())
                .createdCount(saved.getCreatedCount())
                .updatedCount(saved.getUpdatedCount())
                .skippedCount(saved.getSkippedCount())
                .failedCount(saved.getFailedCount())
                .build();
    }

    @Transactional(readOnly = true)
    public String errorReport(Long importId) {
        BulkImport bulkImport = findImport(importId);
        StringBuilder csv = new StringBuilder("row_number,status,error_message\n");
        bulkImport.getRows().stream()
                .filter(row -> row.getErrorMessage() != null && !row.getErrorMessage().isBlank())
                .forEach(row -> csv.append(row.getRowNumber())
                        .append(',')
                        .append(row.getStatus())
                        .append(',')
                        .append(csvEscape(row.getErrorMessage()))
                        .append('\n'));
        return csv.toString();
    }

    private RowValidation validateBranch(Long organizationId, Map<String, String> row, Set<String> seenCodes, boolean updateExisting) {
        List<String> errors = new ArrayList<>();
        Map<String, String> normalized = new LinkedHashMap<>();
        String code = required(row, "branch_code", "Branch code is required", errors);
        String name = required(row, "branch_name", "Branch name is required", errors);
        Boolean active = parseBoolean(row.get("is_active"), "is_active", errors);

        if (code != null) {
            String key = code.toLowerCase(Locale.ROOT);
            if (!seenCodes.add(key)) {
                errors.add("Duplicate branch_code in file");
            }
            branchRepository.findByCodeIgnoreCase(code).ifPresent(existing -> {
                if (!Objects.equals(existing.getOrganization().getId(), organizationId)) {
                    errors.add("Branch code belongs to another organization");
                } else if (!updateExisting) {
                    errors.add("Branch code already exists");
                }
            });
        }

        normalized.put("code", code);
        normalized.put("name", name);
        normalized.put("active", String.valueOf(active == null || active));
        return new RowValidation(errors, normalized);
    }

    private RowValidation validateChartOfAccountsRow(Long organizationId, Map<String, String> row, Set<String> seenCodes, boolean updateExisting) {
        List<String> errors = new ArrayList<>();
        Map<String, String> normalized = new LinkedHashMap<>();
        String accountCode = required(row, "account_code", "Account code is required", errors);
        String accountName = required(row, "account_name", "Account name is required", errors);
        String accountType = parseEnum(row.get("account_type"), ChartOfAccounts.AccountType.class, "account_type", errors);
        if ("INCOME".equalsIgnoreCase(trimToNull(row.get("account_type")))) {
            accountType = ChartOfAccounts.AccountType.REVENUE.toString();
        }
        String accountSubtype = parseEnum(row.get("account_subtype"), ChartOfAccounts.AccountSubtype.class, "account_subtype", errors);
        String normalBalance = parseEnum(row.get("normal_balance"), ChartOfAccounts.DebitCredit.class, "normal_balance", errors);
        Boolean active = parseBoolean(row.get("is_active"), "is_active", errors);

        if (accountCode != null) {
            String key = accountCode.toLowerCase(Locale.ROOT);
            if (!seenCodes.add(key)) {
                errors.add("Duplicate account_code in file");
            }
            if (!updateExisting && chartOfAccountsRepository.existsByOrganizationIdAndAccountCodeIgnoreCase(organizationId, accountCode)) {
                errors.add("Account code already exists for this organization");
            }
        }

        if (accountType != null && accountSubtype != null && !isCompatibleSubtype(accountType, accountSubtype)) {
            errors.add("Account subtype does not match account type");
        }
        if (accountType != null && normalBalance != null && !expectedNormalBalance(accountType).equals(normalBalance)) {
            errors.add("Normal balance should be " + expectedNormalBalance(accountType) + " for " + accountType + " accounts");
        }

        normalized.put("accountCode", accountCode);
        normalized.put("accountName", accountName);
        normalized.put("description", trimToNull(row.get("description")));
        normalized.put("accountType", accountType);
        normalized.put("accountSubtype", accountSubtype);
        normalized.put("normalBalance", normalBalance);
        normalized.put("active", String.valueOf(active == null || active));
        return new RowValidation(errors, normalized);
    }

    private boolean upsertBranch(Organization organization, boolean updateExisting, Map<String, String> row, BulkImportRow importRow) {
        String code = row.get("code");
        Branch branch = branchRepository.findByCodeIgnoreCase(code).orElse(null);
        boolean updating = branch != null;

        if (updating && !updateExisting) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Branch code already exists");
        }
        if (updating && !Objects.equals(branch.getOrganization().getId(), organization.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Branch code belongs to another organization");
        }
        if (branch == null) {
            branch = Branch.builder()
                    .organization(organization)
                    .code(code)
                    .build();
        }

        branch.setName(row.get("name"));
        branch.setActive(Boolean.parseBoolean(row.get("active")));
        Branch saved = branchRepository.save(branch);
        importRow.setStatus(updating ? BulkImportRow.RowStatus.UPDATED : BulkImportRow.RowStatus.CREATED);
        if (updating) {
            importRow.setUpdatedRecordId(saved.getId());
        } else {
            importRow.setCreatedRecordId(saved.getId());
        }
        return updating;
    }

    private boolean upsertChartOfAccounts(Organization organization, boolean updateExisting, Map<String, String> row, BulkImportRow importRow) {
        String accountCode = row.get("accountCode");
        ChartOfAccounts account = chartOfAccountsRepository
                .findByOrganizationIdAndAccountCodeIgnoreCase(organization.getId(), accountCode)
                .orElse(null);
        boolean updating = account != null;

        if (updating && !updateExisting) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account code already exists for this organization");
        }
        if (account == null) {
            account = ChartOfAccounts.builder()
                    .organization(organization)
                    .accountCode(accountCode)
                    .balance(BigDecimal.ZERO)
                    .build();
        }

        account.setAccountName(row.get("accountName"));
        account.setDescription(trimToNull(row.get("description")));
        account.setAccountType(ChartOfAccounts.AccountType.valueOf(row.get("accountType")));
        account.setAccountSubtype(ChartOfAccounts.AccountSubtype.valueOf(row.get("accountSubtype")));
        account.setNormalBalance(ChartOfAccounts.DebitCredit.valueOf(row.get("normalBalance")));
        account.setActive(Boolean.parseBoolean(row.get("active")));

        ChartOfAccounts saved = chartOfAccountsRepository.save(account);
        importRow.setStatus(updating ? BulkImportRow.RowStatus.UPDATED : BulkImportRow.RowStatus.CREATED);
        if (updating) {
            importRow.setUpdatedRecordId(saved.getId());
        } else {
            importRow.setCreatedRecordId(saved.getId());
        }
        return updating;
    }

    private List<CsvRecord> parseCsv(MultipartFile file, List<String> requiredHeaders) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV file is required");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".csv")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only CSV uploads are currently supported");
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV file is empty");
            }

            List<String> headers = parseCsvLine(headerLine).stream()
                    .map(this::normalizeHeader)
                    .toList();
            Set<String> headerSet = new LinkedHashSet<>(headers);
            List<String> missing = requiredHeaders.stream()
                    .filter(required -> !headerSet.contains(required))
                    .toList();
            if (!missing.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing required columns: " + String.join(", ", missing));
            }

            List<CsvRecord> records = new ArrayList<>();
            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                List<String> values = parseCsvLine(line);
                if (values.stream().allMatch(value -> trimToNull(value) == null)) {
                    continue;
                }
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(headers.get(i), i < values.size() ? trimToNull(values.get(i)) : null);
                }
                records.add(new CsvRecord(rowNumber, row));
            }

            if (records.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV file has no data rows");
            }
            return records;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read CSV file");
        }
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private BulkImport newImport(Organization organization, BulkImport.ImportType importType, MultipartFile file, boolean updateExisting, String uploadedBy) {
        return BulkImport.builder()
                .organization(organization)
                .importType(importType)
                .status(BulkImport.ImportStatus.VALIDATED)
                .fileName(file.getOriginalFilename() == null ? "upload.csv" : file.getOriginalFilename())
                .updateExisting(updateExisting)
                .uploadedBy(uploadedBy)
                .totalRows(0)
                .validRows(0)
                .invalidRows(0)
                .createdCount(0)
                .updatedCount(0)
                .skippedCount(0)
                .failedCount(0)
                .build();
    }

    private BulkImportRow toRow(int rowNumber, Map<String, String> raw, RowValidation validation) {
        boolean valid = validation.errors().isEmpty();
        return BulkImportRow.builder()
                .rowNumber(rowNumber)
                .status(valid ? BulkImportRow.RowStatus.VALID : BulkImportRow.RowStatus.ERROR)
                .rawDataJson(writeJson(raw))
                .normalizedDataJson(writeJson(validation.normalized()))
                .errorMessage(valid ? null : String.join("; ", validation.errors()))
                .build();
    }

    private void finalizeValidation(BulkImport bulkImport) {
        int total = bulkImport.getRows().size();
        int invalid = (int) bulkImport.getRows().stream()
                .filter(row -> row.getStatus() == BulkImportRow.RowStatus.ERROR)
                .count();
        bulkImport.setTotalRows(total);
        bulkImport.setInvalidRows(invalid);
        bulkImport.setValidRows(total - invalid);
        bulkImport.setStatus(invalid > 0
                ? BulkImport.ImportStatus.VALIDATION_FAILED
                : BulkImport.ImportStatus.VALIDATED);
    }

    private BulkImportPreviewResponse mapToPreview(BulkImport bulkImport) {
        List<BulkImportRow> rows = bulkImportRowRepository.findByBulkImportIdOrderByRowNumber(bulkImport.getId());
        if (rows.isEmpty() && bulkImport.getRows() != null) {
            rows = bulkImport.getRows();
        }
        return BulkImportPreviewResponse.builder()
                .importId(bulkImport.getId())
                .organizationId(bulkImport.getOrganization().getId())
                .importType(bulkImport.getImportType().toString())
                .status(bulkImport.getStatus().toString())
                .fileName(bulkImport.getFileName())
                .updateExisting(bulkImport.getUpdateExisting())
                .totalRows(defaultZero(bulkImport.getTotalRows()))
                .validRows(defaultZero(bulkImport.getValidRows()))
                .invalidRows(defaultZero(bulkImport.getInvalidRows()))
                .createdCount(defaultZero(bulkImport.getCreatedCount()))
                .updatedCount(defaultZero(bulkImport.getUpdatedCount()))
                .skippedCount(defaultZero(bulkImport.getSkippedCount()))
                .failedCount(defaultZero(bulkImport.getFailedCount()))
                .createdAt(bulkImport.getCreatedAt())
                .completedAt(bulkImport.getCompletedAt())
                .rows(rows.stream().map(this::mapRow).toList())
                .build();
    }

    private BulkImportRowDTO mapRow(BulkImportRow row) {
        return BulkImportRowDTO.builder()
                .rowNumber(row.getRowNumber())
                .status(row.getStatus().toString())
                .rawData(readJson(row.getRawDataJson()))
                .normalizedData(readJson(row.getNormalizedDataJson()))
                .errorMessage(row.getErrorMessage())
                .createdRecordId(row.getCreatedRecordId())
                .updatedRecordId(row.getUpdatedRecordId())
                .build();
    }

    private Organization findOrganization(Long organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
    }

    private BulkImport findImport(Long importId) {
        return bulkImportRepository.findById(importId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import not found"));
    }

    private String required(Map<String, String> row, String key, String message, List<String> errors) {
        String value = trimToNull(row.get(key));
        if (value == null) {
            errors.add(message);
        }
        return value;
    }

    private <E extends Enum<E>> String parseEnum(String value, Class<E> enumType, String field, List<String> errors) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            errors.add(field + " is required");
            return null;
        }
        normalized = normalized.trim().toUpperCase(Locale.ROOT);
        if (enumType == ChartOfAccounts.AccountType.class && "INCOME".equals(normalized)) {
            normalized = "REVENUE";
        }
        try {
            Enum.valueOf(enumType, normalized);
            return normalized;
        } catch (IllegalArgumentException ex) {
            errors.add("Invalid " + field + ". Allowed values: " + allowedValues(enumType));
            return null;
        }
    }

    private Boolean parseBoolean(String value, String field, List<String> errors) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return true;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (Set.of("true", "yes", "1", "active").contains(normalized)) {
            return true;
        }
        if (Set.of("false", "no", "0", "inactive").contains(normalized)) {
            return false;
        }
        errors.add("Invalid " + field + ". Use true or false");
        return null;
    }

    private boolean isCompatibleSubtype(String accountType, String accountSubtype) {
        Map<String, Set<String>> compatibility = new HashMap<>();
        compatibility.put("ASSET", Set.of("CURRENT_ASSET", "FIXED_ASSET"));
        compatibility.put("LIABILITY", Set.of("CURRENT_LIABILITY", "LONG_TERM_LIABILITY"));
        compatibility.put("EQUITY", Set.of("RETAINED_EARNINGS"));
        compatibility.put("REVENUE", Set.of("REVENUE", "OTHER_INCOME"));
        compatibility.put("EXPENSE", Set.of("COST_OF_GOODS_SOLD", "OPERATING_EXPENSE", "OTHER_EXPENSE"));
        return compatibility.getOrDefault(accountType, Set.of()).contains(accountSubtype);
    }

    private String expectedNormalBalance(String accountType) {
        return Set.of("ASSET", "EXPENSE").contains(accountType) ? "DEBIT" : "CREDIT";
    }

    private String allowedValues(Class<? extends Enum<?>> enumType) {
        return Arrays.stream(enumType.getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.joining(", "));
    }

    private String normalizeHeader(String value) {
        return trimToNull(value) == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String writeJson(Map<String, String> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not serialize import row");
        }
    }

    private Map<String, String> readJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (IOException ex) {
            return Map.of();
        }
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String messageOf(Exception ex) {
        if (ex instanceof ResponseStatusException responseStatusException) {
            return responseStatusException.getReason();
        }
        return ex.getMessage() == null ? "Import row failed" : ex.getMessage();
    }

    private String summary(BulkImport bulkImport) {
        return "importId=" + bulkImport.getId()
                + ", type=" + bulkImport.getImportType()
                + ", created=" + bulkImport.getCreatedCount()
                + ", updated=" + bulkImport.getUpdatedCount()
                + ", skipped=" + bulkImport.getSkippedCount()
                + ", failed=" + bulkImport.getFailedCount();
    }

    private int defaultZero(Integer value) {
        return value == null ? 0 : value;
    }

    private record CsvRecord(int rowNumber, Map<String, String> values) {
    }

    private record RowValidation(List<String> errors, Map<String, String> normalized) {
    }
}
