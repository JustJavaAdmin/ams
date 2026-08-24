package com.justjava.ams.accountant.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRunItemDTO {
    private Long id;
    private Long paymentRunId;
    private Long paymentScheduleId;
    private Long purchaseInvoiceId;
    private Long vendorId;
    private String vendorName;
    private String purchaseOrderNumber;
    private BigDecimal amount;
    private String status;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
