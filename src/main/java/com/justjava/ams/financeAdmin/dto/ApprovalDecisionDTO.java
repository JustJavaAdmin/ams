package com.justjava.ams.financeAdmin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalDecisionDTO {
    private Boolean approvalRequired;
    private Long approvalRequestId;
    private Long approvalRuleId;
    private String approvalRuleName;
    private Integer requiredApprovals;
    private String approverRole;
    private BigDecimal evaluatedAmount;
    private String reason;
}
