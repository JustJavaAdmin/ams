package com.justjava.ams.accountant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankReconciliationDTO {
    private Long id;
    private Long organizationId;
    private Long bankAccountId;
    private String bankName;
    private String accountNumber;
    private LocalDate statementDate;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private BigDecimal clearedAmount;
    private BigDecimal unresolvedDifference;
    private String status;
    private Integer importedLineCount;
    private Integer matchedLineCount;
    private String reconciledBy;
    private LocalDateTime reconciledAt;
    private List<BankStatementLineDTO> statementLines;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
