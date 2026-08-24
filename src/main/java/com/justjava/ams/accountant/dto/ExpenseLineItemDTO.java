package com.justjava.ams.accountant.dto;

import lombok.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseLineItemDTO {
    private Long id;
    private Long expenseId;
    private Long chartAccountId;
    private String accountCode;
    private String accountName;
    private String description;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
    private String notes;
}
