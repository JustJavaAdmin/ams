package com.justjava.ams.cfo.repository;

import com.justjava.ams.accountant.entity.GeneralLedger;
import com.justjava.ams.cfo.entity.BudgetConsumption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;

@Repository
public interface BudgetConsumptionRepository extends JpaRepository<BudgetConsumption, Long> {
    boolean existsBySourceTypeAndSourceIdAndBudgetLineIdAndSourceLineId(
            GeneralLedger.SourceType sourceType,
            Long sourceId,
            Long budgetLineId,
            Long sourceLineId);

    @Query("""
            SELECT COALESCE(SUM(consumption.amount), 0)
            FROM BudgetConsumption consumption
            WHERE consumption.budgetLine.id = :budgetLineId
              AND consumption.reversed = false
            """)
    BigDecimal sumActiveByBudgetLineId(@Param("budgetLineId") Long budgetLineId);

    @Query("""
            SELECT COALESCE(SUM(consumption.amount), 0)
            FROM BudgetConsumption consumption
            WHERE consumption.budgetLine.id = :budgetLineId
              AND consumption.reversed = false
              AND consumption.transactionDate BETWEEN :fromDate AND :toDate
            """)
    BigDecimal sumActiveByBudgetLineIdAndTransactionDateBetween(
            @Param("budgetLineId") Long budgetLineId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}
