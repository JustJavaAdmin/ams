package com.justjava.ams.integration.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PayrollJournalLineRequest {
    private String accountCode;
    private String accountName;
    private String lineType;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private String narration;
}
