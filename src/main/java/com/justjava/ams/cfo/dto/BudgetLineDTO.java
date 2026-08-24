package com.justjava.ams.cfo.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetLineDTO {
    private Long id;
    private Long budgetId;
    private Long chartAccountId;
    private String accountCode;
    private String accountName;
    private String departmentCode;
    private String projectCode;
    private String branchCode;
    private BigDecimal allocatedAmount;
    private BigDecimal spentAmount;
    private BigDecimal remainingAmount;
    private BigDecimal utilizationPercent;
    private BigDecimal warningThresholdPercent;
    private Boolean hardStopEnabled;
    private Boolean active;
    private String status;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
