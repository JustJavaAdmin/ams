package com.justjava.ams.accountant.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BankStatementLineMatchRequest {
    @NotNull(message = "General ledger entry is required")
    private Long generalLedgerId;
}
