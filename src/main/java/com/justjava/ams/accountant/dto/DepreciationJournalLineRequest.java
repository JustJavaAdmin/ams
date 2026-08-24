package com.justjava.ams.accountant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepreciationJournalLineRequest {
    @NotBlank(message = "Account code is required")
    private String accountCode;

    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private String description;
    private String referenceNumber;
    private String branchCode;
    private String departmentCode;
    private String projectCode;
    private String assetCode;
}
