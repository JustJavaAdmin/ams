package com.justjava.ams.accountant.dto;

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
public class BankStatementLineDTO {
    private Long id;
    private Long reconciliationId;
    private LocalDate transactionDate;
    private BigDecimal amount;
    private String referenceNumber;
    private String description;
    private String matchStatus;
    private Long matchedGeneralLedgerId;
    private LocalDateTime matchedAt;
    private String matchedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
