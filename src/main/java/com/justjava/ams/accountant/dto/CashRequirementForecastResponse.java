package com.justjava.ams.accountant.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashRequirementForecastResponse {
    private Long organizationId;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String bucket;
    private BigDecimal totalScheduled;
    private BigDecimal overdueAmount;
    private BigDecimal dueSoonAmount;
    private List<CashRequirementForecastRowDTO> rows;
}
