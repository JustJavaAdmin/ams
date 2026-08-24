package com.justjava.ams.accountant.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentScheduleRequest {
    private LocalDate scheduledPaymentDate;
    private BigDecimal amountScheduled;
    private Integer priority;
    private String reason;
}
