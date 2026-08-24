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
public class CustomerStatementDTO {
    private Long id;
    private Long organizationId;
    private Long customerId;
    private String customerName;
    private LocalDate statementDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private BigDecimal totalInvoiced;
    private BigDecimal totalPaid;
    private String status;
    private List<CustomerStatementLineDTO> lines;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
