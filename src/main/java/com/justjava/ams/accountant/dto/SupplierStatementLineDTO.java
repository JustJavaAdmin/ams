package com.justjava.ams.accountant.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierStatementLineDTO {
    private Long id;
    private Long supplierStatementId;
    private LocalDate transactionDate;
    private String referenceNumber;
    private String description;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private Long matchedPurchaseInvoiceId;
    private Long matchedPaymentId;
    private String status;
    private String disputeReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
