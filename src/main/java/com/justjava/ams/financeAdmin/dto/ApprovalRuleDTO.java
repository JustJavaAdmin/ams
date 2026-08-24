package com.justjava.ams.financeAdmin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalRuleDTO {
    private Long id;
    private Long organizationId;
    private String ruleName;
    private String moduleType;
    private String transactionType;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private String accountType;
    private Long branchId;
    private String departmentCode;
    private Integer requiredApprovals;
    private String approverRole;
    private Integer priority;
    private Boolean active;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
