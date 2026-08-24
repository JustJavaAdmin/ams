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
public class ReceivablesCollectionCaseDTO {
    private Long id;
    private Long organizationId;
    private Long customerId;
    private String customerName;
    private Long customerInvoiceId;
    private String invoiceNumber;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private BigDecimal invoiceTotal;
    private BigDecimal amountPaid;
    private BigDecimal outstandingAmount;
    private Long daysOverdue;
    private String status;
    private String collectorUsername;
    private LocalDateTime assignedAt;
    private String escalatedTo;
    private LocalDateTime escalatedAt;
    private String escalationReason;
    private Integer dunningLevel;
    private LocalDate lastDunningDate;
    private LocalDate nextActionDate;
    private String closeReason;
    private List<CollectionActivityDTO> activities;
    private List<PromiseToPayDTO> promises;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
