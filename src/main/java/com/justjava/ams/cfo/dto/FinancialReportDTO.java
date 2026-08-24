package com.justjava.ams.cfo.dto;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialReportDTO {

    private Long id;
    private Long organizationId;
    private String reportType;
    private String reportName;
    private LocalDate reportDate;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String status;
    private String reportContent;
    private String generatedBy;
    private LocalDate approvedDate;
    private String approvedBy;
    private Long approvalRequestId;
    private Long approvalRuleId;
    private String approvalRuleName;
    private Integer requiredApprovals;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

