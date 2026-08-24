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
public class PurchaseInvoiceDTO {

    private Long id;
    private Long organizationId;
    private Long vendorId;
    private String purchaseOrderNumber;
    private String vendorName;
    private String vendorEmail;
    private String vendorPhone;
    private String vendorAddress;
    private LocalDate purchaseDate;
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
    private Long approvalRequestId;
    private Long approvalRuleId;
    private String approvalRuleName;
    private Integer requiredApprovals;
    private String notes;
    private Long createdByUserId;
    private Set<PurchaseLineItemDTO> lineItems;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

