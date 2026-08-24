package com.justjava.ams.accountant.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorDTO {
    private Long id;
    private Long organizationId;
    private String vendorCode;
    private String legalName;
    private String email;
    private String phone;
    private String billingAddress;
    private String taxId;
    private String paymentTerms;
    private String bankDetails;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
