package com.justjava.ams.cfo.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetDTO {

    private Long id;
    private Long organizationId;
    private String budgetCode;
    private String budgetName;
    private Integer budgetYear;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalBudget;
    private BigDecimal allocatedAmount;
    private BigDecimal spentAmount;
    private String status;
    private Boolean approved;
    private Long approvalRequestId;
    private Long approvalRuleId;
    private String approvalRuleName;
    private Integer requiredApprovals;
    private String departmentName;
    private String notes;
    private List<BudgetLineDTO> lines;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

