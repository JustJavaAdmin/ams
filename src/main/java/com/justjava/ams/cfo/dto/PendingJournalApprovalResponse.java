package com.justjava.ams.cfo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingJournalApprovalResponse {
    private Long id;
    private Long journalId;
    private LocalDate journalDate;
    private String description;
    private String createdBy;
    private LocalDateTime submittedAt;
    private Long branchId;
    private BigDecimal totalDebits;
    private BigDecimal totalCredits;
    private Long approvalRuleId;
    private String approvalRuleName;
    private Integer requiredApprovals;
    private String status;
}
