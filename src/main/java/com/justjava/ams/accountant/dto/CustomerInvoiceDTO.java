package com.justjava.ams.accountant.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerInvoiceDTO {

    private Long id;
    private Long organizationId;
    private Long customerId;
    private String invoiceNumber;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String customerAddress;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private BigDecimal subtotal;
    private Long taxJurisdictionId;
    private String taxCode;
    private BigDecimal taxRate;
    private String taxCalculationType;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BigDecimal amountPaid;
    private String status;
    private String transactionClassification;
    private String complianceStatus;
    private String supplierTaxId;
    private String buyerTaxId;
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
    private String notes;
    private Long createdByUserId;
    private Set<InvoiceLineItemDTO> lineItems;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

