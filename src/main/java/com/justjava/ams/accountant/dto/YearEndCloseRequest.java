package com.justjava.ams.accountant.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class YearEndCloseRequest {
    @NotNull
    private Integer fiscalYear;
    @NotNull
    private LocalDate fromDate;
    @NotNull
    private LocalDate toDate;
    @NotNull
    private Long retainedEarningsAccountId;
    private String closedBy;
}
