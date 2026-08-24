package com.justjava.ams.accountant.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashRequirementForecastRowDTO {
    private LocalDate bucketStart;
    private LocalDate bucketEnd;
    private BigDecimal scheduledAmount;
    private Integer scheduleCount;
}
