package com.justjava.ams.accountant.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentScheduleDTO {
    private Long id;
    private Long organizationId;
    private Long purchaseInvoiceId;
    private Long vendorId;
    private String vendorName;
    private String purchaseOrderNumber;
    private LocalDate dueDate;
    private LocalDate scheduledPaymentDate;
    private BigDecimal amountScheduled;
    private BigDecimal amountRemaining;
    private Integer priority;
    private String status;
    private String holdReason;
    private String createdBy;
    private String approvedBy;
    private Long approvalRequestId;
    private Long approvalRuleId;
    private String approvalRuleName;
    private Integer requiredApprovals;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
