package com.justjava.ams.cfo.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetDashboardResponse {
    private Long organizationId;
    private Integer year;
    private BigDecimal totalBudget;
    private BigDecimal allocatedAmount;
    private BigDecimal spentAmount;
    private BigDecimal remainingAmount;
    private BigDecimal utilizationPercent;
    private List<BudgetLineDTO> lines;
    private List<BudgetControlDecision> alerts;
}
