package com.justjava.ams.accountant.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditHoldRequest {
    private String reason;
}
