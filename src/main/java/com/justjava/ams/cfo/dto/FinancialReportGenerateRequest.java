package com.justjava.ams.cfo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialReportGenerateRequest {

    private String reportType;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String generatedBy;
    private Boolean persist;
    private List<String> accountTypes;
}
