package com.justjava.ams.integration.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class PayrollJournalRequest {
    private String sourceSystem;
    private String journalReference;
    private String revision;
    private String payrollPeriodId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String currency;
    private String narration;
    private List<PayrollJournalLineRequest> lines;
}
