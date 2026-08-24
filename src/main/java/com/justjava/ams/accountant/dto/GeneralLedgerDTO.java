package com.justjava.ams.accountant.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneralLedgerDTO {

    private Long id;
    private Long accountId;
    private String journalNumber;
    private LocalDate transactionDate;
    private String debitCredit;
    private BigDecimal amount;
    private String description;
    private String referenceNumber;
    private Long createdByUserId;
    private String notes;
    private String status;
    private Long fiscalPeriodId;
    private String sourceType;
    private Long sourceId;
    private String postingBatchId;
    private String postedBy;
    private LocalDateTime postedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

