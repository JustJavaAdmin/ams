package com.justjava.ams.invoicing.api.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExternalInvoiceDTO {
    private Long id;
    private Long organizationId;
    private Long customerId;
    private String invoiceNumber;
    private String status;
    private String complianceStatus;
    private String transactionClassification;
    private String customerName;
    private String customerEmail;
    private String customerTaxId;
    private String supplierTaxId;
    private String buyerTaxId;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BigDecimal amountPaid;
    private String nrsIrn;
    private String nrsQrCode;
    private String nrsDigitalStamp;
    private String nrsReceiptReference;
    private LocalDateTime nrsClearedAt;
    private LocalDateTime buyerReviewDeadline;
    private LocalDateTime finalLockedAt;
    private LocalDateTime rejectedAt;
    private String rejectionReason;
    private LocalDateTime complianceBreachAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
