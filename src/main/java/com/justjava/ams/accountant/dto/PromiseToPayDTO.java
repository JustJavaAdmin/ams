package com.justjava.ams.accountant.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromiseToPayDTO {
    private Long id;
    private Long collectionCaseId;
    private BigDecimal promisedAmount;
    private LocalDate promisedDate;
    private String notes;
    private String createdBy;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
