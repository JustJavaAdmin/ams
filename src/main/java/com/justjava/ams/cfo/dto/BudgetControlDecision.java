package com.justjava.ams.cfo.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetControlDecision {
    private Boolean allowed;
    private String severity;
    private Long budgetLineId;
    private BigDecimal allocatedAmount;
    private BigDecimal spentAmount;
    private BigDecimal availableAmount;
    private BigDecimal transactionAmount;
    private BigDecimal projectedSpend;
    private String message;
}
