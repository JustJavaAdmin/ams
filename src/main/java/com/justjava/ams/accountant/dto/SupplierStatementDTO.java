package com.justjava.ams.accountant.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierStatementDTO {
    private Long id;
    private Long organizationId;
    private Long vendorId;
    private String vendorName;
    private LocalDate statementDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private String status;
    private List<SupplierStatementLineDTO> lines;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
