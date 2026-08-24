package com.justjava.ams.accountant.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionCaseActionRequest {
    private String collectorUsername;
    private String escalatedTo;
    private String reason;
    private String activityType;
    private String subject;
    private String notes;
    private BigDecimal promisedAmount;
    private LocalDate promisedDate;
}
