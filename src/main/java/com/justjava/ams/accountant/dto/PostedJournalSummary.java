package com.justjava.ams.accountant.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PostedJournalSummary(
        String postingBatchId,
        String journalNumber,
        String sourceType,
        Long sourceId,
        LocalDate journalDate,
        String referenceNumber,
        String postedBy,
        LocalDateTime postedAt,
        Long lineCount,
        BigDecimal totalDebits,
        BigDecimal totalCredits) {
}
