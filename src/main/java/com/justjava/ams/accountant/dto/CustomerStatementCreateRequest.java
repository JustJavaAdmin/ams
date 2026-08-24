package com.justjava.ams.accountant.dto;

import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerStatementCreateRequest {
    private Long customerId;
    private LocalDate statementDate;
    private LocalDate startDate;
    private LocalDate endDate;
}
