package com.justjava.ams.financeAdmin.dto;

import com.justjava.ams.accountant.entity.ChartOfAccounts;
import com.justjava.ams.financeAdmin.entity.ModuleControl;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalEvaluationRequest {
    private Long organizationId;
    private ModuleControl.ModuleType moduleType;
    private String transactionType;
    private String entityType;
    private Long entityId;
    private BigDecimal amount;
    private Long branchId;
    private String departmentCode;
    private Set<ChartOfAccounts.AccountType> accountTypes;
    private String submittedBy;
}
