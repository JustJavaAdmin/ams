package com.justjava.ams.accountant.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRunDTO {
    private Long id;
    private Long organizationId;
    private Long bankAccountId;
    private String bankName;
    private LocalDate runDate;
    private LocalDate cutoffDate;
    private BigDecimal totalAmount;
    private Integer itemCount;
    private String status;
    private String createdBy;
    private String approvedBy;
    private Long approvalRequestId;
    private Long approvalRuleId;
    private String approvalRuleName;
    private Integer requiredApprovals;
    private LocalDateTime approvedAt;
    private String executedBy;
    private LocalDateTime executedAt;
    private List<PaymentRunItemDTO> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
