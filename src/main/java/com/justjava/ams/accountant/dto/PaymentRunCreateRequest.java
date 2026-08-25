package com.justjava.ams.accountant.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRunCreateRequest {
    private Long bankAccountId;
    private LocalDate runDate;
    private LocalDate cutoffDate;
    private List<Long> scheduleIds;
}
