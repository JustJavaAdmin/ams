package com.justjava.ams.cfo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialReportSummaryResponse {

    private Long id;
    private Long organizationId;
    private String reportType;
    private String reportName;
    private LocalDate fromDate;
    private LocalDate toDate;
    private LocalDate reportDate;
    private String status;
    private List<FinancialReportLineDTO> lines;
    private BigDecimal totalRevenue;
    private BigDecimal totalExpenses;
    private BigDecimal netIncome;
    private BigDecimal totalAssets;
    private BigDecimal totalLiabilities;
    private BigDecimal totalEquity;
    private BigDecimal balanceSheetVariance;
    private Map<String, BigDecimal> summaryMetrics;
    private String reportContent;
    private LocalDateTime generatedAt;
}
