package com.justjava.ams.accountant.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgingReportRowDTO {
    private Long documentId;
    private String partyName;
    private String documentNumber;
    private LocalDate documentDate;
    private LocalDate dueDate;
    private BigDecimal originalAmount;
    private BigDecimal paidAmount;
    private BigDecimal outstandingAmount;
    private Long daysOverdue;
    private String bucket;
    private String status;
}
