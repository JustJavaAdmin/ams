package com.justjava.ams.accountant.service;

import com.justjava.ams.accountant.dto.PaymentRequest;
import com.justjava.ams.accountant.entity.*;
import com.justjava.ams.accountant.repository.*;
import com.justjava.ams.cfo.service.BudgetControlService;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import com.justjava.ams.common.repository.UserRepository;
import com.justjava.ams.financeAdmin.dto.ApprovalDecisionDTO;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServicePaymentSourceTest {

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private ExpenseLineItemRepository expenseLineItemRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChartOfAccountsRepository chartOfAccountsRepository;

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private GeneralLedgerService generalLedgerService;

    @Mock
    private FiscalPeriodService fiscalPeriodService;

    @Mock
    private TaxCalculationService taxCalculationService;

    @Mock
    private BudgetControlService budgetControlService;

    @Mock
    private ApprovalWorkflowService approvalWorkflowService;

    @InjectMocks
    private ExpenseService expenseService;

    @Test
    void expensePaymentUsesDedicatedExpensePaymentPosting() {
        Organization organization = Organization.builder().id(1L).build();
        ChartOfAccounts payableAccount = account(20L, organization, ChartOfAccounts.AccountType.LIABILITY);
        ChartOfAccounts bankChartAccount = account(21L, organization, ChartOfAccounts.AccountType.ASSET);
        BankAccount bankAccount = BankAccount.builder()
                .id(30L)
                .organization(organization)
                .chartAccount(bankChartAccount)
                .active(true)
                .build();
        Expense expense = Expense.builder()
                .id(10L)
                .organization(organization)
                .expenseNumber("EXP-10")
                .expenseDate(LocalDate.of(2026, 1, 10))
                .dueDate(LocalDate.of(2026, 1, 20))
                .totalAmount(new BigDecimal("125.00"))
                .amountPaid(BigDecimal.ZERO)
                .status(Expense.ExpenseStatus.POSTED)
                .build();

        when(expenseRepository.findById(10L)).thenReturn(Optional.of(expense));
        when(bankAccountRepository.findById(30L)).thenReturn(Optional.of(bankAccount));
        when(chartOfAccountsRepository.findByOrganizationIdAndAccountType(1L, ChartOfAccounts.AccountType.LIABILITY))
                .thenReturn(List.of(payableAccount));
        when(approvalWorkflowService.evaluate(any()))
                .thenReturn(ApprovalDecisionDTO.builder().approvalRequired(false).build());
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));

        expenseService.recordPayment(
                10L,
                PaymentRequest.builder()
                        .bankAccountId(30L)
                        .amount(new BigDecimal("125.00"))
                        .paymentDate(LocalDate.of(2026, 1, 20))
                        .notes("Paid")
                        .build(),
                "payer");

        verify(generalLedgerService).postExpensePayment(
                eq(10L),
                eq("EXP-10"),
                eq(LocalDate.of(2026, 1, 20)),
                eq(payableAccount),
                eq(bankChartAccount),
                eq(new BigDecimal("125.00")),
                eq("Paid"),
                eq("payer"));
    }

    private ChartOfAccounts account(Long id, Organization organization, ChartOfAccounts.AccountType accountType) {
        return ChartOfAccounts.builder()
                .id(id)
                .organization(organization)
                .accountCode("ACCT-" + id)
                .accountName("Account " + id)
                .accountType(accountType)
                .accountSubtype(ChartOfAccounts.AccountSubtype.CURRENT_LIABILITY)
                .active(true)
                .build();
    }
}
