package com.justjava.ams.accountant.service;

import com.justjava.ams.accountant.dto.JournalLineDTO;
import com.justjava.ams.accountant.dto.JournalSubmitRequest;
import com.justjava.ams.accountant.dto.ManualJournalDTO;
import com.justjava.ams.accountant.entity.ChartOfAccounts;
import com.justjava.ams.accountant.entity.DepreciationJournalImport;
import com.justjava.ams.accountant.entity.JournalLine;
import com.justjava.ams.accountant.entity.ManualJournal;
import com.justjava.ams.accountant.repository.ChartOfAccountsRepository;
import com.justjava.ams.accountant.repository.DepreciationJournalImportRepository;
import com.justjava.ams.accountant.repository.ManualJournalRepository;
import com.justjava.ams.accountant.repository.JournalLineRepository;
import com.justjava.ams.common.entity.Branch;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.BranchRepository;
import com.justjava.ams.common.repository.OrganizationRepository;
import com.justjava.ams.auditor.dto.AuditLogDTO;
import com.justjava.ams.auditor.service.AuditLogService;
import com.justjava.ams.cfo.dto.JournalApprovalRequest;
import com.justjava.ams.cfo.dto.JournalRejectionRequest;
import com.justjava.ams.financeAdmin.dto.ApprovalDecisionDTO;
import com.justjava.ams.financeAdmin.dto.ApprovalEvaluationRequest;
import com.justjava.ams.financeAdmin.entity.ModuleControl;
import com.justjava.ams.financeAdmin.service.ApprovalWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ManualJournalService {

    private final ManualJournalRepository manualJournalRepository;
    private final JournalLineRepository journalLineRepository;
    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final OrganizationRepository organizationRepository;
    private final BranchRepository branchRepository;
    private final FiscalPeriodService fiscalPeriodService;
    private final AuditLogService auditLogService;
    private final GeneralLedgerService generalLedgerService;
    private final ApprovalWorkflowService approvalWorkflowService;
    private final DepreciationJournalImportRepository depreciationJournalImportRepository;

    public ManualJournalDTO createManualJournal(Long organizationId, ManualJournalDTO dto, String userName) {
        Organization organization = findOrganization(organizationId);
        Branch branch = findBranchForOrganization(dto.getBranchId(), organizationId);

        if (dto.getJournalDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Journal date is required");
        }

        fiscalPeriodService.requireOpenPeriod(organizationId, dto.getJournalDate());

        ManualJournal journal = ManualJournal.builder()
                .organization(organization)
                .branch(branch)
                .description(normalizeRequired(dto.getDescription(), "Description is required"))
                .journalDate(dto.getJournalDate())
                .status(ManualJournal.JournalStatus.DRAFT)
                .createdBy(normalizeUserName(userName))
                .build();

        ManualJournal saved = manualJournalRepository.save(journal);
        // Audit: record creation of manual journal
        try {
            AuditLogDTO log = AuditLogDTO.builder()
                    .organizationId(saved.getOrganization().getId())
                    .entityType("ManualJournal")
                    .entityId(saved.getId())
                    .action("CREATE")
                    .newValue(saved.getStatus().toString())
                    .description("Manual journal created by " + normalizeUserName(userName))
                    .build();
            auditLogService.createAuditLog(saved.getOrganization().getId(), log);
        } catch (Exception ex) {
            // Swallow audit errors to avoid breaking main flow
        }
        return mapToDTO(saved);
    }

    public JournalLineDTO addJournalLine(Long journalId, JournalLineDTO dto) {
        ManualJournal journal = findJournal(journalId);
        requireDraftJournal(journal);
        validateLineAmounts(dto);
        ChartOfAccounts account = findChartOfAccountForJournal(dto.getChartOfAccountId(), journal);

        JournalLine line = JournalLine.builder()
                .manualJournal(journal)
                .chartOfAccounts(account)
                .debitAmount(dto.getDebitAmount() != null ? dto.getDebitAmount() : BigDecimal.ZERO)
                .creditAmount(dto.getCreditAmount() != null ? dto.getCreditAmount() : BigDecimal.ZERO)
                .departmentCode(dto.getDepartmentCode())
                .projectCode(dto.getProjectCode())
                .branchCode(dto.getBranchCode())
                .narration(dto.getNarration())
                .lineSequence(dto.getLineSequence())
                .build();

        JournalLine saved = journalLineRepository.save(line);
        // Audit: record addition of a journal line
        try {
            AuditLogDTO log = AuditLogDTO.builder()
                    .organizationId(journal.getOrganization().getId())
                    .entityType("JournalLine")
                    .entityId(saved.getId())
                    .action("CREATE")
                    .newValue(saved.getNarration())
                    .description("Added line to ManualJournal " + journal.getId())
                    .build();
            auditLogService.createAuditLog(journal.getOrganization().getId(), log);
        } catch (Exception ex) {
        }
        return mapLineToDTO(saved);
    }

    public JournalLineDTO updateJournalLine(Long lineId, JournalLineDTO dto) {
        JournalLine line = journalLineRepository.findById(lineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Journal line not found"));

        requireDraftJournal(line.getManualJournal());
        validateLineAmounts(dto);
        ChartOfAccounts account = findChartOfAccountForJournal(dto.getChartOfAccountId(), line.getManualJournal());

        line.setChartOfAccounts(account);
        line.setDebitAmount(dto.getDebitAmount() != null ? dto.getDebitAmount() : BigDecimal.ZERO);
        line.setCreditAmount(dto.getCreditAmount() != null ? dto.getCreditAmount() : BigDecimal.ZERO);
        line.setDepartmentCode(dto.getDepartmentCode());
        line.setProjectCode(dto.getProjectCode());
        line.setBranchCode(dto.getBranchCode());
        line.setNarration(dto.getNarration());
        line.setLineSequence(dto.getLineSequence());

        JournalLine saved = journalLineRepository.save(line);
        // Audit: record update of a journal line
        try {
            AuditLogDTO log = AuditLogDTO.builder()
                    .organizationId(line.getManualJournal().getOrganization().getId())
                    .entityType("JournalLine")
                    .entityId(saved.getId())
                    .action("UPDATE")
                    .newValue(saved.getNarration())
                    .description("Updated line " + saved.getId() + " in ManualJournal " + saved.getManualJournal().getId())
                    .build();
            auditLogService.createAuditLog(saved.getManualJournal().getOrganization().getId(), log);
        } catch (Exception ex) {
        }
        return mapLineToDTO(saved);
    }

    public void deleteJournalLine(Long lineId) {
        JournalLine line = journalLineRepository.findById(lineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Journal line not found"));

        requireDraftJournal(line.getManualJournal());

        journalLineRepository.deleteById(lineId);
        // Audit: record deletion of a journal line
        try {
            AuditLogDTO log = AuditLogDTO.builder()
                    .organizationId(line.getManualJournal().getOrganization().getId())
                    .entityType("JournalLine")
                    .entityId(line.getId())
                    .action("DELETE")
                    .description("Deleted line " + line.getId() + " from ManualJournal " + line.getManualJournal().getId())
                    .build();
            auditLogService.createAuditLog(line.getManualJournal().getOrganization().getId(), log);
        } catch (Exception ex) {
        }
    }

    public ManualJournalDTO submitForApproval(Long journalId, String userName) {
        JournalSubmitRequest request = new JournalSubmitRequest();
        request.setSubmittedBy(userName);
        return submitJournal(journalId, request);
    }

    public ManualJournalDTO submitJournal(Long journalId, JournalSubmitRequest request) {
        ManualJournal journal = findJournal(journalId);
        requireDraftJournalForSubmit(journal);

        List<JournalLine> lines = journalLineRepository.findByManualJournalId(journalId);
        validateJournalCanBeSubmitted(lines);
        fiscalPeriodService.requireOpenPeriod(journal.getOrganization().getId(), journal.getJournalDate());

        String submittedBy = normalizeUserName(request != null ? request.getSubmittedBy() : null);
        ApprovalDecisionDTO approvalDecision = approvalWorkflowService.submitForApproval(ApprovalEvaluationRequest.builder()
                .organizationId(journal.getOrganization().getId())
                .moduleType(ModuleControl.ModuleType.GENERAL_LEDGER)
                .transactionType("MANUAL_JOURNAL")
                .entityType("ManualJournal")
                .entityId(journal.getId())
                .amount(totalDebits(lines))
                .branchId(journal.getBranch() != null ? journal.getBranch().getId() : null)
                .departmentCode(firstDepartment(lines))
                .accountTypes(lines.stream()
                        .map(line -> line.getChartOfAccounts().getAccountType())
                        .collect(java.util.stream.Collectors.toSet()))
                .submittedBy(submittedBy)
                .build());
        journal.setStatus(ManualJournal.JournalStatus.SUBMITTED);
        journal.setSubmittedBy(submittedBy);
        journal.setSubmittedAt(LocalDateTime.now());
        journal.setApprovalRequestId(approvalDecision.getApprovalRequestId());
        journal.setApprovalRuleId(approvalDecision.getApprovalRuleId());
        journal.setApprovalRuleName(approvalDecision.getApprovalRuleName());
        journal.setRequiredApprovals(approvalDecision.getRequiredApprovals());

        ManualJournal saved = manualJournalRepository.save(journal);
        try {
            AuditLogDTO log = AuditLogDTO.builder()
                    .organizationId(saved.getOrganization().getId())
                    .entityType("ManualJournal")
                    .entityId(saved.getId())
                    .action("SUBMIT")
                    .newValue(saved.getStatus().toString())
                    .description("Submitted for approval by " + submittedBy + "; " + approvalDecision.getReason())
                    .build();
            auditLogService.createAuditLog(saved.getOrganization().getId(), log);
        } catch (Exception ex) {
        }
        return mapToDTO(saved);
    }

    public ManualJournalDTO approveJournal(Long journalId, String approverName) {
        return approveJournal(journalId, new JournalApprovalRequest(), approverName);
    }

    public ManualJournalDTO approveJournal(Long journalId, JournalApprovalRequest request, String approverName) {
        ManualJournal journal = findJournal(journalId);

        if (!ManualJournal.JournalStatus.SUBMITTED.equals(journal.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only SUBMITTED journals can be approved");
        }

        List<JournalLine> lines = journalLineRepository.findByManualJournalId(journalId);
        validateJournalIsBalanced(lines);

        String normalizedApproverName = normalizeUserName(approverName);
        String availableApproverName = trimToNull(approverName);
        String creatorName = trimToNull(journal.getCreatedBy());
        if (creatorName != null
                && availableApproverName != null
                && creatorName.equalsIgnoreCase(availableApproverName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CFO cannot approve their own journal");
        }

        journal.setStatus(ManualJournal.JournalStatus.APPROVED);
        journal.setApprovedBy(normalizedApproverName);
        journal.setApprovedAt(LocalDateTime.now());
        if (journal.getApprovalRequestId() != null) {
            approvalWorkflowService.approvePending("ManualJournal", journalId, request != null ? trimToNull(request.getApprovalNote()) : null);
        }

        ManualJournal saved = manualJournalRepository.save(journal);
        // Audit: record approval
        try {
            String approvalNote = request != null ? trimToNull(request.getApprovalNote()) : null;
            AuditLogDTO log = AuditLogDTO.builder()
                    .organizationId(saved.getOrganization().getId())
                    .entityType("ManualJournal")
                    .entityId(saved.getId())
                    .action("APPROVE")
                    .newValue(saved.getStatus().toString())
                    .description(approvalNote != null
                            ? "Approved by " + normalizedApproverName + ": " + approvalNote
                            : "Approved by " + normalizedApproverName)
                    .build();
            auditLogService.createAuditLog(saved.getOrganization().getId(), log);
        } catch (Exception ex) {
        }
        return mapToDTO(saved);
    }

    public ManualJournalDTO rejectJournal(Long journalId, String rejectionReason, String rejecterName) {
        JournalRejectionRequest request = new JournalRejectionRequest();
        request.setRejectionReason(rejectionReason);
        request.setRejectedBy(rejecterName);
        return rejectJournal(journalId, request, rejecterName);
    }

    public ManualJournalDTO rejectJournal(Long journalId, JournalRejectionRequest request, String rejecterName) {
        ManualJournal journal = findJournal(journalId);

        if (!ManualJournal.JournalStatus.SUBMITTED.equals(journal.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only SUBMITTED journals can be rejected");
        }

        String rejectionReason = normalizeRequired(
                request != null ? request.getRejectionReason() : null,
                "Rejection reason is required");
        String normalizedRejecterName = normalizeUserName(rejecterName);

        journal.setStatus(ManualJournal.JournalStatus.REJECTED);
        journal.setRejectionReason(rejectionReason);
        if (journal.getApprovalRequestId() != null) {
            approvalWorkflowService.rejectPending("ManualJournal", journalId, rejectionReason);
        }

        ManualJournal saved = manualJournalRepository.save(journal);
        try {
            AuditLogDTO log = AuditLogDTO.builder()
                    .organizationId(saved.getOrganization().getId())
                    .entityType("ManualJournal")
                    .entityId(saved.getId())
                    .action("REJECT")
                    .oldValue(ManualJournal.JournalStatus.SUBMITTED.toString())
                    .newValue(saved.getStatus().toString())
                    .description("Rejected by " + normalizedRejecterName + ": " + rejectionReason)
                    .build();
            auditLogService.createAuditLog(saved.getOrganization().getId(), log);
        } catch (Exception ex) {
        }
        return mapToDTO(saved);
    }

    public ManualJournalDTO postJournal(Long journalId) {
        return postJournal(journalId, null);
    }

    public ManualJournalDTO postJournal(Long journalId, String postedBy) {
        ManualJournal journal = findJournal(journalId);

        if (!ManualJournal.JournalStatus.APPROVED.equals(journal.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only APPROVED journals can be posted");
        }
        approvalWorkflowService.requireApproved("ManualJournal", journalId);

        fiscalPeriodService.requireOpenPeriod(journal.getOrganization().getId(), journal.getJournalDate());

        List<JournalLine> lines = journalLineRepository.findByManualJournalId(journalId);
        validateJournalIsBalanced(lines);

        String normalizedPostedBy = normalizeUserName(postedBy);
        DepreciationJournalImport depreciationImport = depreciationJournalImportRepository.findByManualJournalId(journalId).orElse(null);
        if (depreciationImport != null) {
            generalLedgerService.postDepreciationJournalEntriesFromManualJournal(
                    journalId,
                    depreciationImport.getId(),
                    depreciationImport.getExternalBatchId(),
                    normalizedPostedBy);
            depreciationImport.setStatus(DepreciationJournalImport.ImportStatus.POSTED);
            depreciationJournalImportRepository.save(depreciationImport);
        } else {
            generalLedgerService.postJournalEntriesFromManualJournal(journalId, normalizedPostedBy);
        }

        journal.setStatus(ManualJournal.JournalStatus.POSTED);
        journal.setPostingDate(java.time.LocalDate.now());

        ManualJournal saved = manualJournalRepository.save(journal);
        try {
            AuditLogDTO log = AuditLogDTO.builder()
                    .organizationId(saved.getOrganization().getId())
                    .entityType("ManualJournal")
                    .entityId(saved.getId())
                    .action("POST")
                    .oldValue(ManualJournal.JournalStatus.APPROVED.toString())
                    .newValue(saved.getStatus().toString())
                    .description("Posted to GL by " + normalizedPostedBy)
                    .build();
            auditLogService.createAuditLog(saved.getOrganization().getId(), log);
        } catch (Exception ex) {
        }
        return mapToDTO(saved);
    }

    public ManualJournalDTO getJournal(Long journalId) {
        return mapToDTO(findJournal(journalId));
    }

    @Transactional(readOnly = true)
    public List<ManualJournalDTO> getJournalsByOrganization(Long organizationId) {
        findOrganization(organizationId);

        return manualJournalRepository.findByOrganizationId(organizationId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ManualJournalDTO> getJournalsByStatus(Long organizationId, String status) {
        ManualJournal.JournalStatus journalStatus = ManualJournal.JournalStatus.valueOf(status);
        return manualJournalRepository.findByOrganizationIdAndStatus(organizationId, journalStatus)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<ManualJournalDTO> getPendingJournals(Long organizationId) {
        findOrganization(organizationId);

        return manualJournalRepository.findByOrganizationIdAndStatus(organizationId, ManualJournal.JournalStatus.SUBMITTED)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<JournalLineDTO> getJournalLines(Long journalId) {
        findJournal(journalId);

        return journalLineRepository.findByManualJournalId(journalId)
                .stream()
                .map(this::mapLineToDTO)
                .collect(Collectors.toList());
    }

    private ManualJournalDTO mapToDTO(ManualJournal journal) {
        List<JournalLineDTO> lines = journalLineRepository.findByManualJournalId(journal.getId())
                .stream()
                .map(this::mapLineToDTO)
                .collect(Collectors.toList());
        BigDecimal totalDebits = lines.stream()
                .map(line -> line.getDebitAmount() != null ? line.getDebitAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredits = lines.stream()
                .map(line -> line.getCreditAmount() != null ? line.getCreditAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean balanced = !lines.isEmpty()
                && totalDebits.compareTo(BigDecimal.ZERO) > 0
                && totalDebits.compareTo(totalCredits) == 0;

        return ManualJournalDTO.builder()
                .id(journal.getId())
                .organizationId(journal.getOrganization().getId())
                .branchId(journal.getBranch() != null ? journal.getBranch().getId() : null)
                .description(journal.getDescription())
                .journalDate(journal.getJournalDate())
                .postingDate(journal.getPostingDate())
                .status(journal.getStatus().toString())
                .createdBy(journal.getCreatedBy())
                .submittedBy(journal.getSubmittedBy())
                .approvedBy(journal.getApprovedBy())
                .approvalRequestId(journal.getApprovalRequestId())
                .approvalRuleId(journal.getApprovalRuleId())
                .approvalRuleName(journal.getApprovalRuleName())
                .requiredApprovals(journal.getRequiredApprovals() != null ? journal.getRequiredApprovals() : 1)
                .rejectionReason(journal.getRejectionReason())
                .createdAt(journal.getCreatedAt())
                .submittedAt(journal.getSubmittedAt())
                .approvedAt(journal.getApprovedAt())
                .updatedAt(journal.getUpdatedAt())
                .journalLines(lines)
                .totalDebits(totalDebits)
                .totalCredits(totalCredits)
                .balanced(balanced)
                .build();
    }

    private JournalLineDTO mapLineToDTO(JournalLine line) {
        return JournalLineDTO.builder()
                .id(line.getId())
                .manualJournalId(line.getManualJournal().getId())
                .chartOfAccountId(line.getChartOfAccounts().getId())
                .accountCode(line.getChartOfAccounts().getAccountCode())
                .accountName(line.getChartOfAccounts().getAccountName())
                .debitAmount(line.getDebitAmount())
                .creditAmount(line.getCreditAmount())
                .departmentCode(line.getDepartmentCode())
                .projectCode(line.getProjectCode())
                .branchCode(line.getBranchCode())
                .narration(line.getNarration())
                .lineSequence(line.getLineSequence())
                .createdAt(line.getCreatedAt())
                .build();
    }

    private Organization findOrganization(Long organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Organization not found"));
    }

    private ManualJournal findJournal(Long journalId) {
        return manualJournalRepository.findById(journalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Journal not found"));
    }

    private Branch findBranchForOrganization(Long branchId, Long organizationId) {
        if (branchId == null) {
            return null;
        }

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found"));

        if (!branch.getOrganization().getId().equals(organizationId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Branch does not belong to organization");
        }

        return branch;
    }

    private ChartOfAccounts findChartOfAccountForJournal(Long chartOfAccountId, ManualJournal journal) {
        if (chartOfAccountId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chart of account is required");
        }

        ChartOfAccounts account = chartOfAccountsRepository.findById(chartOfAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chart of account not found"));

        if (!account.getOrganization().getId().equals(journal.getOrganization().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chart of account does not belong to journal organization");
        }

        if (Boolean.FALSE.equals(account.getActive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chart of account is inactive");
        }

        return account;
    }

    private void requireDraftJournal(ManualJournal journal) {
        if (!ManualJournal.JournalStatus.DRAFT.equals(journal.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Can only edit lines in DRAFT journals");
        }
    }

    private void requireDraftJournalForSubmit(ManualJournal journal) {
        if (!ManualJournal.JournalStatus.DRAFT.equals(journal.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT journals can be submitted");
        }
    }

    private void validateJournalCanBeSubmitted(List<JournalLine> lines) {
        if (lines.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Journal must have at least two lines before submission");
        }

        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        boolean hasDebitLine = false;
        boolean hasCreditLine = false;

        for (JournalLine line : lines) {
            BigDecimal debitAmount = line.getDebitAmount() != null ? line.getDebitAmount() : BigDecimal.ZERO;
            BigDecimal creditAmount = line.getCreditAmount() != null ? line.getCreditAmount() : BigDecimal.ZERO;

            if (debitAmount.compareTo(BigDecimal.ZERO) > 0) {
                hasDebitLine = true;
            }
            if (creditAmount.compareTo(BigDecimal.ZERO) > 0) {
                hasCreditLine = true;
            }

            totalDebits = totalDebits.add(debitAmount);
            totalCredits = totalCredits.add(creditAmount);
        }

        if (!hasDebitLine) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Journal must have at least one debit line");
        }

        if (!hasCreditLine) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Journal must have at least one credit line");
        }

        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Journal must be balanced before submission");
        }
    }

    private void validateJournalIsBalanced(List<JournalLine> lines) {
        if (lines.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Journal must have at least two lines before approval");
        }

        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        boolean hasDebitLine = false;
        boolean hasCreditLine = false;

        for (JournalLine line : lines) {
            BigDecimal debitAmount = line.getDebitAmount() != null ? line.getDebitAmount() : BigDecimal.ZERO;
            BigDecimal creditAmount = line.getCreditAmount() != null ? line.getCreditAmount() : BigDecimal.ZERO;

            if (debitAmount.compareTo(BigDecimal.ZERO) > 0) {
                hasDebitLine = true;
            }
            if (creditAmount.compareTo(BigDecimal.ZERO) > 0) {
                hasCreditLine = true;
            }

            totalDebits = totalDebits.add(debitAmount);
            totalCredits = totalCredits.add(creditAmount);
        }

        if (!hasDebitLine || !hasCreditLine || totalDebits.compareTo(totalCredits) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Journal must be balanced before approval");
        }
    }

    private BigDecimal totalDebits(List<JournalLine> lines) {
        return lines.stream()
                .map(line -> line.getDebitAmount() != null ? line.getDebitAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String firstDepartment(List<JournalLine> lines) {
        return lines.stream()
                .map(JournalLine::getDepartmentCode)
                .map(this::trimToNull)
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }

    private void validateLineAmounts(JournalLineDTO dto) {
        BigDecimal debitAmount = dto.getDebitAmount() != null ? dto.getDebitAmount() : BigDecimal.ZERO;
        BigDecimal creditAmount = dto.getCreditAmount() != null ? dto.getCreditAmount() : BigDecimal.ZERO;

        if (debitAmount.compareTo(BigDecimal.ZERO) < 0 || creditAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debit and credit amounts cannot be negative");
        }

        boolean hasDebit = debitAmount.compareTo(BigDecimal.ZERO) > 0;
        boolean hasCredit = creditAmount.compareTo(BigDecimal.ZERO) > 0;

        if (hasDebit && hasCredit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Journal line cannot have both debit and credit amounts");
        }

        if (!hasDebit && !hasCredit) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Journal line must have either a debit or credit amount");
        }
    }

    private String normalizeRequired(String value, String message) {
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

    private String normalizeUserName(String userName) {
        String normalized = trimToNull(userName);
        return normalized != null ? normalized : "system";
    }
}
