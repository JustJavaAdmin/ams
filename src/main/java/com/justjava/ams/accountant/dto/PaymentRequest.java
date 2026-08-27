package com.justjava.ams.accountant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRequest {
    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    private BigDecimal amount;

    @NotNull
    private Long bankAccountId;

    private LocalDate paymentDate;
    private String paidBy;
    private String notes;

    // Optional. Set only by PaymentRunService when this payment is being recorded as part of
    // an already-approved payment run's execution. If present, recordPayment() will verify the
    // referenced run is APPROVED, belongs to the same organization, and actually contains this
    // invoice before treating the payment-run's own approval as sufficient - a forged/mismatched
    // id gets no special treatment and falls back to the normal direct-payment approval check.
    private Long paymentRunId;
}