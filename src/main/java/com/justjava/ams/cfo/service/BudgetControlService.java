package com.justjava.ams.cfo.service;

import com.justjava.ams.accountant.entity.ChartOfAccounts;
import com.justjava.ams.accountant.entity.GeneralLedger;
import com.justjava.ams.cfo.dto.BudgetControlDecision;
import com.justjava.ams.cfo.entity.Budget;
import com.justjava.ams.cfo.entity.BudgetConsumption;
import com.justjava.ams.cfo.entity.BudgetLine;
import com.justjava.ams.cfo.repository.BudgetConsumptionRepository;
import com.justjava.ams.cfo.repository.BudgetLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BudgetControlService {

    private static final List<Budget.BudgetStatus> ENFORCEABLE_STATUSES = List.of(
            Budget.BudgetStatus.ACTIVE,
            Budget.BudgetStatus.APPROVED);

    private final BudgetLineRepository budgetLineRepository;
    private final BudgetConsumptionRepository budgetConsumptionRepository;

    public List<BudgetControlDecision> consumeExpenseLines(
            Long organizationId,
            GeneralLedger.SourceType sourceType,
            Long sourceId,
            LocalDate transactionDate,
            List<ExpenseBudgetLine> lines) {
        List<BudgetControlDecision> decisions = new ArrayList<>();
        for (ExpenseBudgetLine line : lines) {
            if (line.chartAccount() == null
                    || !ChartOfAccounts.AccountType.EXPENSE.equals(line.chartAccount().getAccountType())) {
                continue;
            }
            BudgetControlDecision decision = evaluate(
                    organizationId,
                    line.chartAccount().getId(),
                    transactionDate,
                    line.amount());
            decisions.add(decision);
            if (Boolean.FALSE.equals(decision.getAllowed())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, decision.getMessage());
            }
        }

        for (int index = 0; index < lines.size(); index++) {
            ExpenseBudgetLine line = lines.get(index);
            if (line.chartAccount() == null
                    || !ChartOfAccounts.AccountType.EXPENSE.equals(line.chartAccount().getAccountType())) {
                continue;
            }
            BudgetLine budgetLine = budgetLineRepository.findApplicableLine(
                            organizationId,
                            line.chartAccount().getId(),
                            transactionDate,
                            ENFORCEABLE_STATUSES)
                    .orElse(null);
            if (budgetLine == null) {
                continue;
            }
            Long sourceLineId = line.sourceLineId() != null ? line.sourceLineId() : (long) index;
            if (budgetConsumptionRepository.existsBySourceTypeAndSourceIdAndBudgetLineIdAndSourceLineId(
                    sourceType, sourceId, budgetLine.getId(), sourceLineId)) {
                continue;
            }
            budgetConsumptionRepository.save(BudgetConsumption.builder()
                    .budgetLine(budgetLine)
                    .sourceType(sourceType)
                    .sourceId(sourceId)
                    .sourceLineId(sourceLineId)
                    .amount(line.amount())
                    .transactionDate(transactionDate)
                    .description(line.description())
                    .reversed(false)
                    .build());
        }

        return decisions;
    }

    public BudgetControlDecision evaluate(Long organizationId, Long chartAccountId, LocalDate transactionDate, BigDecimal amount) {
        BigDecimal transactionAmount = amount != null ? amount : BigDecimal.ZERO;
        if (transactionAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return allowed(null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, transactionAmount, "No budget impact");
        }

        BudgetLine line = budgetLineRepository.findApplicableLine(
                        organizationId,
                        chartAccountId,
                        transactionDate,
                        ENFORCEABLE_STATUSES)
                .orElse(null);
        if (line == null) {
            return allowed(null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, transactionAmount, "No active budget line matched");
        }

        BigDecimal spent = spent(line.getId());
        BigDecimal allocated = line.getAllocatedAmount() != null ? line.getAllocatedAmount() : BigDecimal.ZERO;
        BigDecimal projected = spent.add(transactionAmount);
        BigDecimal available = allocated.subtract(spent);

        if (projected.compareTo(allocated) > 0 && Boolean.TRUE.equals(line.getHardStopEnabled())) {
            return BudgetControlDecision.builder()
                    .allowed(false)
                    .severity("BLOCK")
                    .budgetLineId(line.getId())
                    .allocatedAmount(allocated)
                    .spentAmount(spent)
                    .availableAmount(available)
                    .transactionAmount(transactionAmount)
                    .projectedSpend(projected)
                    .message("Budget exceeded for " + line.getChartAccount().getAccountCode()
                            + ": available " + available + ", requested " + transactionAmount)
                    .build();
        }

        BigDecimal utilization = percent(projected, allocated);
        BigDecimal threshold = line.getWarningThresholdPercent() != null
                ? line.getWarningThresholdPercent()
                : new BigDecimal("90.00");
        String severity = utilization.compareTo(threshold) >= 0 ? "WARNING" : "OK";
        return BudgetControlDecision.builder()
                .allowed(true)
                .severity(severity)
                .budgetLineId(line.getId())
                .allocatedAmount(allocated)
                .spentAmount(spent)
                .availableAmount(available)
                .transactionAmount(transactionAmount)
                .projectedSpend(projected)
                .message(severity.equals("WARNING")
                        ? "Budget warning for " + line.getChartAccount().getAccountCode()
                        : "Budget check passed")
                .build();
    }

    public BigDecimal spent(Long budgetLineId) {
        BigDecimal spent = budgetConsumptionRepository.sumActiveByBudgetLineId(budgetLineId);
        return spent != null ? spent : BigDecimal.ZERO;
    }

    public BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return numerator.multiply(new BigDecimal("100")).divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private BudgetControlDecision allowed(Long budgetLineId, BigDecimal allocated, BigDecimal spent, BigDecimal available, BigDecimal amount, String message) {
        return BudgetControlDecision.builder()
                .allowed(true)
                .severity("OK")
                .budgetLineId(budgetLineId)
                .allocatedAmount(allocated)
                .spentAmount(spent)
                .availableAmount(available)
                .transactionAmount(amount)
                .projectedSpend(spent.add(amount))
                .message(message)
                .build();
    }

    public record ExpenseBudgetLine(
            Long sourceLineId,
            ChartOfAccounts chartAccount,
            BigDecimal amount,
            String description) {}
}
