package com.justjava.ams.accountant.service;

import com.justjava.ams.accountant.dto.ManualJournalDTO;
import com.justjava.ams.accountant.entity.ChartOfAccounts;
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
import com.justjava.ams.financeAdmin.service.ApprovalWorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualJournalServiceTest {

    @Mock
    private ManualJournalRepository manualJournalRepository;

    @Mock
    private JournalLineRepository journalLineRepository;

    @Mock
    private ChartOfAccountsRepository chartOfAccountsRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private FiscalPeriodService fiscalPeriodService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private GeneralLedgerService generalLedgerService;

    @Mock
    private ApprovalWorkflowService approvalWorkflowService;

    @Mock
    private DepreciationJournalImportRepository depreciationJournalImportRepository;

    @InjectMocks
    private ManualJournalService manualJournalService;

    @Test
    void approveJournalWithoutApprovalRequestDoesNotLookupPendingWorkflow() {
        Organization organization = Organization.builder().id(10L).name("Test Org").build();
        Branch branch = Branch.builder().id(20L).organization(organization).name("Main").code("MAIN").build();
        ManualJournal journal = ManualJournal.builder()
                .id(30L)
                .organization(organization)
                .branch(branch)
                .description("Manual accrual")
                .journalDate(LocalDate.now())
                .status(ManualJournal.JournalStatus.SUBMITTED)
                .createdBy("accountant")
                .approvalRequestId(null)
                .build();

        ChartOfAccounts debitAccount = account(organization, 40L, "1000", ChartOfAccounts.AccountType.ASSET);
        ChartOfAccounts creditAccount = account(organization, 41L, "4000", ChartOfAccounts.AccountType.REVENUE);
        List<JournalLine> lines = List.of(
                line(1L, journal, debitAccount, new BigDecimal("250.00"), BigDecimal.ZERO),
                line(2L, journal, creditAccount, BigDecimal.ZERO, new BigDecimal("250.00")));

        when(manualJournalRepository.findById(30L)).thenReturn(Optional.of(journal));
        when(journalLineRepository.findByManualJournalId(30L)).thenReturn(lines);
        when(manualJournalRepository.save(any(ManualJournal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ManualJournalDTO approved = manualJournalService.approveJournal(30L, "cfo");

        assertThat(approved.getStatus()).isEqualTo("APPROVED");
        verify(approvalWorkflowService, never()).approvePending(any(), any(), any());
    }

    private ChartOfAccounts account(Organization organization, Long id, String code, ChartOfAccounts.AccountType type) {
        return ChartOfAccounts.builder()
                .id(id)
                .organization(organization)
                .accountCode(code)
                .accountName(code)
                .accountType(type)
                .active(true)
                .build();
    }

    private JournalLine line(Long id, ManualJournal journal, ChartOfAccounts account, BigDecimal debit, BigDecimal credit) {
        return JournalLine.builder()
                .id(id)
                .manualJournal(journal)
                .chartOfAccounts(account)
                .debitAmount(debit)
                .creditAmount(credit)
                .lineSequence(id.intValue())
                .narration("Line " + id)
                .build();
    }
}
