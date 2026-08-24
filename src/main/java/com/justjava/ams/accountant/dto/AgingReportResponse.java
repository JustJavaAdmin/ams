package com.justjava.ams.accountant.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgingReportResponse {
    private Long organizationId;
    private String reportType;
    private LocalDate asOfDate;
    private List<AgingReportRowDTO> rows;
    private Map<String, BigDecimal> bucketTotals;
    private BigDecimal grandTotal;
}
