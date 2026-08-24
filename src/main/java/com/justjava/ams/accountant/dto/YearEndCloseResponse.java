package com.justjava.ams.accountant.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YearEndCloseResponse {
    private Long organizationId;
    private Integer fiscalYear;
    private BigDecimal totalRevenueClosed;
    private BigDecimal totalExpensesClosed;
    private BigDecimal netIncome;
    private Long closingJournalId;
    private String status;
    private List<GeneralLedgerDTO> postedEntries;
}
