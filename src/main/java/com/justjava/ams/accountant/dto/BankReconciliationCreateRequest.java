package com.justjava.ams.accountant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankReconciliationCreateRequest {
    @NotNull(message = "Bank account is required")
    private Long bankAccountId;

    @NotNull(message = "Statement date is required")
    private LocalDate statementDate;

    @NotNull(message = "Opening balance is required")
    private BigDecimal openingBalance;

    @NotNull(message = "Closing balance is required")
    private BigDecimal closingBalance;

    private List<@Valid BankStatementLineDTO> statementLines;
}
