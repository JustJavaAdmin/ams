package com.justjava.ams.accountant.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public interface PostedJournalSummaryProjection {
    String getPostingBatchId();

    String getJournalNumber();

    String getSourceType();

    Long getSourceId();

    LocalDate getJournalDate();

    String getReferenceNumber();

    String getPostedBy();

    LocalDateTime getPostedAt();

    Long getLineCount();

    BigDecimal getTotalDebits();

    BigDecimal getTotalCredits();
}
