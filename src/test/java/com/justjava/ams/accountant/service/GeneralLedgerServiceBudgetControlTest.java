package com.justjava.ams.accountant.service;

import com.justjava.ams.accountant.entity.ChartOfAccounts;
import com.justjava.ams.accountant.entity.GeneralLedger;
import com.justjava.ams.accountant.entity.JournalLine;
import com.justjava.ams.accountant.entity.ManualJournal;
import com.justjava.ams.accountant.repository.ChartOfAccountsRepository;
import com.justjava.ams.accountant.repository.GeneralLedgerRepository;
import com.justjava.ams.accountant.repository.JournalLineRepository;
import com.justjava.ams.accountant.repository.ManualJournalRepository;
import com.justjava.ams.auditor.service.AuditLogService;
import com.justjava.ams.cfo.service.BudgetControlService;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.UserRepository;
import com.justjava.ams.financeAdmin.entity.ModuleControl;
import com.justjava.ams.financeAdmin.service.ModuleControlService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeneralLedgerServiceBudgetControlTest {

    @Mock
    private GeneralLedgerRepository generalLedgerRepository;

    @Mock
    private ChartOfAccountsRepository chartOfAccountsRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ManualJournalRepository manualJournalRepository;

    @Mock
    private JournalLineRepository journalLineRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private BudgetControlService budgetControlService;

    @Mock
    private ModuleControlService moduleControlService;

    @InjectMocks
    private GeneralLedgerService generalLedgerService;

    @Test
    void manualJournalExpenseDebitIsBudgetControlledBeforePosting() {
        Organization organization = Organization.builder().id(1L).build();
        ManualJournal journal = ManualJournal.builder()
                .id(10L)
                .organization(organization)
                .journalDate(LocalDate.of(2026, 1, 15))
                .status(ManualJournal.JournalStatus.APPROVED)
                .description("Budgeted manual journal")
                .build();
        ChartOfAccounts expenseAccount = account(20L, organization, ChartOfAccounts.AccountType.EXPENSE);
        ChartOfAccounts liabilityAccount = account(21L, organization, ChartOfAccounts.AccountType.LIABILITY);
        JournalLine expenseDebit = JournalLine.builder()
                .id(30L)
                .chartOfAccounts(expenseAccount)
                .debitAmount(new BigDecimal("125.00"))
                .creditAmount(BigDecimal.ZERO)
                .narration("Expense debit")
                .build();
        JournalLine liabilityCredit = JournalLine.builder()
                .id(31L)
                .chartOfAccounts(liabilityAccount)
                .debitAmount(BigDecimal.ZERO)
                .creditAmount(new BigDecimal("125.00"))
                .narration("Accrual credit")
                .build();

        when(manualJournalRepository.findById(10L)).thenReturn(Optional.of(journal));
        when(generalLedgerRepository.findBySourceTypeAndSourceId(GeneralLedger.SourceType.MANUAL_JOURNAL, 10L))
                .thenReturn(List.of());
        when(journalLineRepository.findByManualJournalId(10L)).thenReturn(List.of(expenseDebit, liabilityCredit));
        when(userRepository.findByUsername("poster")).thenReturn(Optional.empty());
        when(generalLedgerRepository.save(any(GeneralLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        generalLedgerService.postJournalEntriesFromManualJournal(10L, "poster");

        ArgumentCaptor<List<BudgetControlService.ExpenseBudgetLine>> linesCaptor = ArgumentCaptor.forClass(List.class);
        verify(budgetControlService).consumeExpenseLines(
                eq(1L),
                eq(GeneralLedger.SourceType.MANUAL_JOURNAL),
                eq(10L),
                eq(LocalDate.of(2026, 1, 15)),
                linesCaptor.capture());
        assertThat(linesCaptor.getValue()).hasSize(1);
        assertThat(linesCaptor.getValue().getFirst().sourceLineId()).isEqualTo(30L);
        assertThat(linesCaptor.getValue().getFirst().chartAccount()).isSameAs(expenseAccount);
        assertThat(linesCaptor.getValue().getFirst().amount()).isEqualByComparingTo("125.00");
        verify(generalLedgerRepository, times(2)).save(any(GeneralLedger.class));
    }

    @Test
    void manualJournalBudgetBlockPreventsAnyGlRowsBeingSaved() {
        Organization organization = Organization.builder().id(1L).build();
        ManualJournal journal = ManualJournal.builder()
                .id(10L)
                .organization(organization)
                .journalDate(LocalDate.of(2026, 1, 15))
                .status(ManualJournal.JournalStatus.APPROVED)
                .description("Over budget manual journal")
                .build();
        ChartOfAccounts expenseAccount = account(20L, organization, ChartOfAccounts.AccountType.EXPENSE);
        JournalLine expenseDebit = JournalLine.builder()
                .id(30L)
                .chartOfAccounts(expenseAccount)
                .debitAmount(new BigDecimal("125.00"))
                .creditAmount(BigDecimal.ZERO)
                .narration("Expense debit")
                .build();
        JournalLine offsetCredit = JournalLine.builder()
                .id(31L)
                .chartOfAccounts(account(21L, organization, ChartOfAccounts.AccountType.LIABILITY))
                .debitAmount(BigDecimal.ZERO)
                .creditAmount(new BigDecimal("125.00"))
                .narration("Accrual credit")
                .build();

        when(manualJournalRepository.findById(10L)).thenReturn(Optional.of(journal));
        when(generalLedgerRepository.findBySourceTypeAndSourceId(GeneralLedger.SourceType.MANUAL_JOURNAL, 10L))
                .thenReturn(List.of());
        when(journalLineRepository.findByManualJournalId(10L)).thenReturn(List.of(expenseDebit, offsetCredit));
        ResponseStatusException budgetBlock = new ResponseStatusException(HttpStatus.CONFLICT, "Budget exceeded");
        org.mockito.Mockito.doThrow(budgetBlock)
                .when(budgetControlService)
                .consumeExpenseLines(eq(1L), eq(GeneralLedger.SourceType.MANUAL_JOURNAL), eq(10L), eq(LocalDate.of(2026, 1, 15)), any());

        assertThatThrownBy(() -> generalLedgerService.postJournalEntriesFromManualJournal(10L, "poster"))
                .isSameAs(budgetBlock);

        verify(generalLedgerRepository, never()).save(any(GeneralLedger.class));
    }

    @Test
    void manualJournalAmountLimitBlockPreventsBudgetAndGlRows() {
        Organization organization = Organization.builder().id(1L).build();
        ManualJournal journal = ManualJournal.builder()
                .id(10L)
                .organization(organization)
                .journalDate(LocalDate.of(2026, 1, 15))
                .status(ManualJournal.JournalStatus.APPROVED)
                .description("Over limit manual journal")
                .build();
        JournalLine expenseDebit = JournalLine.builder()
                .id(30L)
                .chartOfAccounts(account(20L, organization, ChartOfAccounts.AccountType.EXPENSE))
                .debitAmount(new BigDecimal("125.00"))
                .creditAmount(BigDecimal.ZERO)
                .narration("Expense debit")
                .build();

        when(manualJournalRepository.findById(10L)).thenReturn(Optional.of(journal));
        when(generalLedgerRepository.findBySourceTypeAndSourceId(GeneralLedger.SourceType.MANUAL_JOURNAL, 10L))
                .thenReturn(List.of());
        when(journalLineRepository.findByManualJournalId(10L)).thenReturn(List.of(expenseDebit));
        ResponseStatusException limitBlock = new ResponseStatusException(HttpStatus.CONFLICT, "Transaction amount exceeds limit");
        org.mockito.Mockito.doThrow(limitBlock)
                .when(moduleControlService)
                .requireTransactionWithinLimit(1L, ModuleControl.ModuleType.GENERAL_LEDGER, new BigDecimal("125.00"));

        assertThatThrownBy(() -> generalLedgerService.postJournalEntriesFromManualJournal(10L, "poster"))
                .isSameAs(limitBlock);

        verify(budgetControlService, never()).consumeExpenseLines(any(), any(), any(), any(), any());
        verify(generalLedgerRepository, never()).save(any(GeneralLedger.class));
    }

    @Test
    void expensePostingBudgetBlockPreventsAnyGlRowsBeingSaved() {
        Organization organization = Organization.builder().id(1L).build();
        ChartOfAccounts payableAccount = account(21L, organization, ChartOfAccounts.AccountType.LIABILITY);
        ChartOfAccounts expenseAccount = account(20L, organization, ChartOfAccounts.AccountType.EXPENSE);
        List<GeneralLedger> debits = List.of(GeneralLedger.builder()
                .account(expenseAccount)
                .amount(new BigDecimal("125.00"))
                .description("Expense line")
                .build());
        List<BudgetControlService.ExpenseBudgetLine> budgetLines = List.of(
                new BudgetControlService.ExpenseBudgetLine(30L, expenseAccount, new BigDecimal("125.00"), "Expense line"));

        when(generalLedgerRepository.findBySourceTypeAndSourceId(GeneralLedger.SourceType.EXPENSE, 10L))
                .thenReturn(List.of());
        ResponseStatusException budgetBlock = new ResponseStatusException(HttpStatus.CONFLICT, "Budget exceeded");
        org.mockito.Mockito.doThrow(budgetBlock)
                .when(budgetControlService)
                .consumeExpenseLines(
                        eq(1L),
                        eq(GeneralLedger.SourceType.EXPENSE),
                        eq(10L),
                        eq(LocalDate.of(2026, 1, 15)),
                        eq(budgetLines));

        assertThatThrownBy(() -> generalLedgerService.postExpense(
                10L,
                "EXP-10",
                LocalDate.of(2026, 1, 15),
                payableAccount,
                debits,
                "Expense EXP-10",
                "poster",
                budgetLines))
                .isSameAs(budgetBlock);

        verify(generalLedgerRepository, never()).save(any(GeneralLedger.class));
    }

    @Test
    void expensePaymentPostsWithExpensePaymentSourceType() {
        Organization organization = Organization.builder().id(1L).build();
        ChartOfAccounts payableAccount = account(21L, organization, ChartOfAccounts.AccountType.LIABILITY);
        ChartOfAccounts bankAccount = account(22L, organization, ChartOfAccounts.AccountType.ASSET);
        when(userRepository.findByUsername("payer")).thenReturn(Optional.empty());
        when(generalLedgerRepository.save(any(GeneralLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        generalLedgerService.postExpensePayment(
                10L,
                "EXP-10",
                LocalDate.of(2026, 1, 20),
                payableAccount,
                bankAccount,
                new BigDecimal("125.00"),
                "Expense payment EXP-10",
                "payer");

        ArgumentCaptor<GeneralLedger> entryCaptor = ArgumentCaptor.forClass(GeneralLedger.class);
        verify(generalLedgerRepository, times(2)).save(entryCaptor.capture());
        List<GeneralLedger> entries = entryCaptor.getAllValues();
        assertThat(entries).allSatisfy(entry -> {
            assertThat(entry.getSourceType()).isEqualTo(GeneralLedger.SourceType.EXPENSE_PAYMENT);
            assertThat(entry.getSourceId()).isEqualTo(10L);
            assertThat(entry.getJournalNumber()).isEqualTo("EXP-PAY-EXP-10");
            assertThat(entry.getPostingBatchId()).startsWith("EXPENSE_PAYMENT-10-");
        });
        verify(moduleControlService).requireTransactionWithinLimit(
                1L,
                ModuleControl.ModuleType.ACCOUNTS_PAYABLE,
                new BigDecimal("125.00"));
    }

    private ChartOfAccounts account(Long id, Organization organization, ChartOfAccounts.AccountType accountType) {
        return ChartOfAccounts.builder()
                .id(id)
                .organization(organization)
                .accountCode("ACCT-" + id)
                .accountName("Account " + id)
                .accountType(accountType)
                .accountSubtype(ChartOfAccounts.AccountSubtype.OPERATING_EXPENSE)
                .active(true)
                .build();
    }
}
