package com.justjava.ams.cfo.service;

import com.justjava.ams.accountant.entity.ChartOfAccounts;
import com.justjava.ams.cfo.dto.BudgetControlDecision;
import com.justjava.ams.cfo.entity.Budget;
import com.justjava.ams.cfo.entity.BudgetLine;
import com.justjava.ams.cfo.repository.BudgetConsumptionRepository;
import com.justjava.ams.cfo.repository.BudgetLineRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetControlServiceTest {

    @Mock
    private BudgetLineRepository budgetLineRepository;

    @Mock
    private BudgetConsumptionRepository budgetConsumptionRepository;

    @InjectMocks
    private BudgetControlService budgetControlService;

    @Test
    void blocksWhenHardStopBudgetWouldBeExceeded() {
        BudgetLine line = budgetLine(new BigDecimal("100.00"), true);
        when(budgetLineRepository.findApplicableLine(1L, 20L, LocalDate.of(2026, 1, 15),
                List.of(Budget.BudgetStatus.ACTIVE, Budget.BudgetStatus.APPROVED)))
                .thenReturn(Optional.of(line));
        when(budgetConsumptionRepository.sumActiveByBudgetLineId(30L)).thenReturn(new BigDecimal("80.00"));

        BudgetControlDecision decision = budgetControlService.evaluate(1L, 20L, LocalDate.of(2026, 1, 15), new BigDecimal("25.00"));

        assertThat(decision.getAllowed()).isFalse();
        assertThat(decision.getSeverity()).isEqualTo("BLOCK");
        assertThat(decision.getProjectedSpend()).isEqualByComparingTo("105.00");
    }

    @Test
    void warnsWhenProjectedSpendCrossesThreshold() {
        BudgetLine line = budgetLine(new BigDecimal("100.00"), true);
        when(budgetLineRepository.findApplicableLine(1L, 20L, LocalDate.of(2026, 1, 15),
                List.of(Budget.BudgetStatus.ACTIVE, Budget.BudgetStatus.APPROVED)))
                .thenReturn(Optional.of(line));
        when(budgetConsumptionRepository.sumActiveByBudgetLineId(30L)).thenReturn(new BigDecimal("80.00"));

        BudgetControlDecision decision = budgetControlService.evaluate(1L, 20L, LocalDate.of(2026, 1, 15), new BigDecimal("10.00"));

        assertThat(decision.getAllowed()).isTrue();
        assertThat(decision.getSeverity()).isEqualTo("WARNING");
        assertThat(decision.getProjectedSpend()).isEqualByComparingTo("90.00");
    }

    @Test
    void allowsWhenNoBudgetLineMatches() {
        when(budgetLineRepository.findApplicableLine(1L, 20L, LocalDate.of(2026, 1, 15),
                List.of(Budget.BudgetStatus.ACTIVE, Budget.BudgetStatus.APPROVED)))
                .thenReturn(Optional.empty());

        BudgetControlDecision decision = budgetControlService.evaluate(1L, 20L, LocalDate.of(2026, 1, 15), new BigDecimal("25.00"));

        assertThat(decision.getAllowed()).isTrue();
        assertThat(decision.getMessage()).contains("No active budget line matched");
    }

    private BudgetLine budgetLine(BigDecimal allocation, boolean hardStop) {
        ChartOfAccounts account = ChartOfAccounts.builder()
                .id(20L)
                .accountCode("6000")
                .accountName("Operating Expense")
                .accountType(ChartOfAccounts.AccountType.EXPENSE)
                .build();
        return BudgetLine.builder()
                .id(30L)
                .chartAccount(account)
                .allocatedAmount(allocation)
                .warningThresholdPercent(new BigDecimal("90.00"))
                .hardStopEnabled(hardStop)
                .active(true)
                .build();
    }
}
