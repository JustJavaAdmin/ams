package com.justjava.ams.accountant.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerStatementLineDTO {
    private Long id;
    private Long customerStatementId;
    private Long customerInvoiceId;
    private LocalDate transactionDate;
    private String referenceNumber;
    private String lineType;
    private String description;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private LocalDateTime createdAt;
}
