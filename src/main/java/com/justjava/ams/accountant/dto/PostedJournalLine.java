package com.justjava.ams.accountant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PostedJournalLine(
        Long id,
        String accountCode,
        String accountName,
        String debitCredit,
        BigDecimal amount,
        String description,
        String referenceNumber,
        Long fiscalPeriodId,
        LocalDate transactionDate,
        String postedBy) {
}
