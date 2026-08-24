package com.justjava.ams.accountant.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerDTO {
    private Long id;
    private Long organizationId;
    private String customerCode;
    private String legalName;
    private String email;
    private String phone;
    private String billingAddress;
    private String taxId;
    private String paymentTerms;
    private BigDecimal creditLimit;
    private Boolean creditHold;
    private String creditHoldReason;
    private String creditHoldPlacedBy;
    private LocalDateTime creditHoldPlacedAt;
    private String creditHoldReleasedBy;
    private LocalDateTime creditHoldReleasedAt;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
