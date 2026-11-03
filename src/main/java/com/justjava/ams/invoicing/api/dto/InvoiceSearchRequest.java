package com.justjava.ams.invoicing.api.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceSearchRequest {
    private Long organizationId;
    private String status;
    private String complianceStatus;
    private String customerTaxId;
    private String transactionClassification;
    private LocalDate fromDate;
    private LocalDate toDate;
    private LocalDateTime updatedSince;
    private Integer page;
    private Integer size;
    private String sort;
}
