package com.justjava.ams.financeAdmin;

import com.justjava.ams.accountant.dto.ChartOfAccountsDTO;
import com.justjava.ams.accountant.dto.FiscalPeriodDTO;
import com.justjava.ams.accountant.service.ChartOfAccountsService;
import com.justjava.ams.accountant.service.FiscalPeriodService;
import com.justjava.ams.financeAdmin.service.ModuleControlService;
import com.justjava.ams.financeAdmin.service.ApprovalRuleService;
import com.justjava.ams.financeAdmin.service.BulkImportService;
import com.justjava.ams.financeAdmin.service.TaxJurisdictionService;
import com.justjava.ams.financeAdmin.dto.ApprovalRuleDTO;
import com.justjava.ams.financeAdmin.dto.BulkImportConfirmResponse;
import com.justjava.ams.financeAdmin.dto.BulkImportPreviewResponse;
import com.justjava.ams.financeAdmin.dto.ModuleControlDTO;
import com.justjava.ams.financeAdmin.dto.ModuleControlDefaultsResponse;
import com.justjava.ams.financeAdmin.dto.ModuleControlUpdateRequest;
import com.justjava.ams.financeAdmin.dto.ModuleControlToggleRequest;
import com.justjava.ams.financeAdmin.dto.TaxJurisdictionDTO;
import com.justjava.ams.financeAdmin.dto.ChartOfAccountCreateRequest;
import com.justjava.ams.financeAdmin.dto.ChartOfAccountUpdateRequest;
import com.justjava.ams.financeAdmin.dto.FiscalPeriodCreateRequest;
import com.justjava.ams.financeAdmin.dto.FiscalPeriodStatusRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.security.Principal;

import java.util.List;

@RestController
@RequestMapping("/api/financeAdmin")
@RequiredArgsConstructor
public class FinanceAdminApiController {
    private final ChartOfAccountsService chartOfAccountsService;
    private final FiscalPeriodService fiscalPeriodService;
    private final ModuleControlService moduleControlService;
    private final ApprovalRuleService approvalRuleService;
    private final TaxJurisdictionService taxJurisdictionService;
    private final BulkImportService bulkImportService;

    @GetMapping("/chartOfAccounts/org/{organizationId}")
    public List<ChartOfAccountsDTO> getChartOfAccounts(@PathVariable Long organizationId) {
        return chartOfAccountsService.getAccountsByOrganization(organizationId);
    }

    @PostMapping("/chartOfAccounts/org/{organizationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ChartOfAccountsDTO createChartOfAccount(
            @PathVariable Long organizationId,
            @Valid @RequestBody ChartOfAccountCreateRequest request) {
        return chartOfAccountsService.createAccount(organizationId, toDTO(request));
    }

    @GetMapping("/chartOfAccounts/{accountId}")
    public ChartOfAccountsDTO getChartOfAccount(@PathVariable Long accountId) {
        return chartOfAccountsService.getAccount(accountId);
    }

    @PutMapping("/chartOfAccounts/{accountId}")
    public ChartOfAccountsDTO updateChartOfAccount(
            @PathVariable Long accountId,
            @Valid @RequestBody ChartOfAccountUpdateRequest request) {
        return chartOfAccountsService.updateAccount(accountId, toDTO(request));
    }

    @DeleteMapping("/chartOfAccounts/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateChartOfAccount(@PathVariable Long accountId) {
        chartOfAccountsService.deleteAccount(accountId);
    }

    @GetMapping(value = "/imports/templates/branches", produces = "text/csv")
    public ResponseEntity<String> downloadBranchImportTemplate() {
        return csvDownload("branches-import-template.csv", bulkImportService.branchTemplate());
    }

    @GetMapping(value = "/imports/templates/chart-of-accounts", produces = "text/csv")
    public ResponseEntity<String> downloadChartOfAccountsImportTemplate() {
        return csvDownload("chart-of-accounts-import-template.csv", bulkImportService.chartOfAccountsTemplate());
    }

    @PostMapping(value = "/imports/branches/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BulkImportPreviewResponse validateBranchImport(
            @RequestParam Long organizationId,
            @RequestParam(defaultValue = "false") boolean updateExisting,
            @RequestParam MultipartFile file,
            Principal principal) {
        return bulkImportService.validateBranches(organizationId, file, updateExisting, principalName(principal));
    }

    @PostMapping(value = "/imports/chart-of-accounts/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BulkImportPreviewResponse validateChartOfAccountsImport(
            @RequestParam Long organizationId,
            @RequestParam(defaultValue = "false") boolean updateExisting,
            @RequestParam MultipartFile file,
            Principal principal) {
        return bulkImportService.validateChartOfAccounts(organizationId, file, updateExisting, principalName(principal));
    }

    @GetMapping("/imports/{importId}")
    public BulkImportPreviewResponse getImport(@PathVariable Long importId) {
        return bulkImportService.getImport(importId);
    }

    @PostMapping("/imports/{importId}/confirm")
    public BulkImportConfirmResponse confirmImport(@PathVariable Long importId) {
        return bulkImportService.confirm(importId);
    }

    @GetMapping(value = "/imports/{importId}/errors", produces = "text/csv")
    public ResponseEntity<String> downloadImportErrors(@PathVariable Long importId) {
        return csvDownload("import-" + importId + "-errors.csv", bulkImportService.errorReport(importId));
    }

    @GetMapping("/fiscalPeriods/org/{organizationId}")
    public List<FiscalPeriodDTO> getFiscalPeriods(@PathVariable Long organizationId) {
        return fiscalPeriodService.getFiscalPeriodsByOrganization(organizationId);
    }

    @GetMapping("/module-controls/org/{organizationId}")
    public List<ModuleControlDTO> getModuleControls(@PathVariable Long organizationId) {
        return moduleControlService.getModulesByOrganization(organizationId);
    }

    @GetMapping("/approval-rules/org/{organizationId}")
    public List<ApprovalRuleDTO> getApprovalRules(@PathVariable Long organizationId) {
        return approvalRuleService.getRulesByOrganization(organizationId);
    }

    @GetMapping("/tax-jurisdictions/org/{organizationId}")
    public List<TaxJurisdictionDTO> getTaxJurisdictions(@PathVariable Long organizationId) {
        return taxJurisdictionService.getJurisdictionsByOrganization(organizationId);
    }

    @PostMapping("/tax-jurisdictions/org/{organizationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public TaxJurisdictionDTO createTaxJurisdiction(
            @PathVariable Long organizationId,
            @Valid @RequestBody TaxJurisdictionDTO request) {
        return taxJurisdictionService.createTaxJurisdiction(organizationId, request);
    }

    @GetMapping("/tax-jurisdictions/{jurisdictionId}")
    public TaxJurisdictionDTO getTaxJurisdiction(@PathVariable Long jurisdictionId) {
        return taxJurisdictionService.getTaxJurisdiction(jurisdictionId);
    }

    @PutMapping("/tax-jurisdictions/{jurisdictionId}")
    public TaxJurisdictionDTO updateTaxJurisdiction(
            @PathVariable Long jurisdictionId,
            @Valid @RequestBody TaxJurisdictionDTO request) {
        return taxJurisdictionService.updateTaxJurisdiction(jurisdictionId, request);
    }

    @DeleteMapping("/tax-jurisdictions/{jurisdictionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateTaxJurisdiction(@PathVariable Long jurisdictionId) {
        taxJurisdictionService.deleteTaxJurisdiction(jurisdictionId);
    }

    @PostMapping("/approval-rules/org/{organizationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApprovalRuleDTO createApprovalRule(
            @PathVariable Long organizationId,
            @Valid @RequestBody ApprovalRuleDTO request,
            Principal principal) {
        String changedBy = principal != null ? principal.getName() : "system";
        return approvalRuleService.createRule(organizationId, request, changedBy);
    }

    @GetMapping("/approval-rules/{ruleId}")
    public ApprovalRuleDTO getApprovalRule(@PathVariable Long ruleId) {
        return approvalRuleService.getRule(ruleId);
    }

    @PutMapping("/approval-rules/{ruleId}")
    public ApprovalRuleDTO updateApprovalRule(
            @PathVariable Long ruleId,
            @Valid @RequestBody ApprovalRuleDTO request,
            Principal principal) {
        String changedBy = principal != null ? principal.getName() : "system";
        return approvalRuleService.updateRule(ruleId, request, changedBy);
    }

    @DeleteMapping("/approval-rules/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateApprovalRule(@PathVariable Long ruleId, Principal principal) {
        String changedBy = principal != null ? principal.getName() : "system";
        approvalRuleService.deactivateRule(ruleId, changedBy);
    }

    @PostMapping("/module-controls/org/{organizationId}/defaults")
    @ResponseStatus(HttpStatus.CREATED)
    public ModuleControlDefaultsResponse initializeModuleDefaults(@PathVariable Long organizationId) {
        return moduleControlService.initializeDefaultModules(organizationId);
    }

    @PutMapping("/module-controls/{moduleId}")
    public ModuleControlDTO updateModuleControl(
            @PathVariable Long moduleId,
            @Valid @RequestBody ModuleControlUpdateRequest request,
            Principal principal) {
        ModuleControlDTO dto = ModuleControlDTO.builder()
                .enabled(request.getEnabled())
                .allowUserConfiguration(request.getAllowUserConfiguration())
                .requiresApproval(request.getRequiresApproval())
                .enableAuditTrail(request.getEnableAuditTrail())
                .enableNotifications(request.getEnableNotifications())
                .maxTransactionAmountLimit(request.getMaxTransactionAmountLimit())
                .configurationJson(request.getConfigurationJson())
                .notes(request.getNotes())
                .build();

        String changedBy = principal != null ? principal.getName() : "system";
        return moduleControlService.updateModuleControl(moduleId, dto, changedBy);
    }

    @PatchMapping("/module-controls/{moduleId}/toggle")
    public ModuleControlDTO toggleModuleControl(
            @PathVariable Long moduleId,
            @Valid @RequestBody ModuleControlToggleRequest request,
            Principal principal) {
        String changedBy = principal != null ? principal.getName() : "system";
        return moduleControlService.toggleModule(moduleId, request.getEnabled(), request.getReason(), changedBy);
    }

    @GetMapping("/module-controls/org/{organizationId}/enabled")
    public List<ModuleControlDTO> getEnabledModuleControls(@PathVariable Long organizationId) {
        return moduleControlService.getEnabledModulesByOrganization(organizationId);
    }

    @PostMapping("/fiscalPeriods/org/{organizationId}")
    @ResponseStatus(HttpStatus.CREATED)
    public FiscalPeriodDTO createFiscalPeriod(
            @PathVariable Long organizationId,
            @Valid @RequestBody FiscalPeriodCreateRequest request) {
        return fiscalPeriodService.createFiscalPeriod(
                organizationId,
                request.getYear(),
                request.getQuarter(),
                request.getStartDate(),
                request.getEndDate());
    }

    @PatchMapping("/fiscalPeriods/{periodId}/close")
    public FiscalPeriodDTO closeFiscalPeriod(
            @PathVariable Long periodId,
            @Valid @RequestBody(required = false) FiscalPeriodStatusRequest request) {
        return fiscalPeriodService.closeFiscalPeriod(periodId);
    }

    @PatchMapping("/fiscalPeriods/{periodId}/lock")
    public FiscalPeriodDTO lockFiscalPeriod(
            @PathVariable Long periodId,
            @Valid @RequestBody(required = false) FiscalPeriodStatusRequest request) {
        return fiscalPeriodService.lockFiscalPeriod(periodId);
    }

    private ChartOfAccountsDTO toDTO(ChartOfAccountCreateRequest request) {
        return ChartOfAccountsDTO.builder()
                .accountCode(request.getAccountCode())
                .accountName(request.getAccountName())
                .description(request.getDescription())
                .accountType(request.getAccountType())
                .accountSubtype(request.getAccountSubtype())
                .normalBalance(request.getNormalBalance())
                .build();
    }

    private ChartOfAccountsDTO toDTO(ChartOfAccountUpdateRequest request) {
        return ChartOfAccountsDTO.builder()
                .accountName(request.getAccountName())
                .description(request.getDescription())
                .accountType(request.getAccountType())
                .accountSubtype(request.getAccountSubtype())
                .normalBalance(request.getNormalBalance())
                .active(request.getActive())
                .build();
    }

    private ResponseEntity<String> csvDownload(String fileName, String body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }

    private String principalName(Principal principal) {
        return principal != null ? principal.getName() : "system";
    }
}
