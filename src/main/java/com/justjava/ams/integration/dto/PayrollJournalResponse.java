package com.justjava.ams.integration.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PayrollJournalResponse(
        Long journalId,
        String status,
        String idempotencyKey,
        LocalDate journalDate,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        LocalDateTime postedAt,
        boolean duplicate) {
}
