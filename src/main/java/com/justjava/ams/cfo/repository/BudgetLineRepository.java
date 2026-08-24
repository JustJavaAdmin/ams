package com.justjava.ams.cfo.repository;

import com.justjava.ams.cfo.entity.Budget;
import com.justjava.ams.cfo.entity.BudgetLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetLineRepository extends JpaRepository<BudgetLine, Long> {
    List<BudgetLine> findByBudgetIdAndActiveTrue(Long budgetId);
    List<BudgetLine> findByBudgetOrganizationIdAndBudgetBudgetYearAndActiveTrue(Long organizationId, Integer budgetYear);
    List<BudgetLine> findByBudgetOrganizationIdAndActiveTrue(Long organizationId);

    @Query("""
            SELECT line FROM BudgetLine line
            WHERE line.active = true
              AND line.chartAccount.id = :chartAccountId
              AND line.budget.organization.id = :organizationId
              AND line.budget.status IN :statuses
              AND :transactionDate BETWEEN line.budget.startDate AND line.budget.endDate
            ORDER BY line.id ASC
            """)
    Optional<BudgetLine> findApplicableLine(
            @Param("organizationId") Long organizationId,
            @Param("chartAccountId") Long chartAccountId,
            @Param("transactionDate") LocalDate transactionDate,
            @Param("statuses") List<Budget.BudgetStatus> statuses);
}
