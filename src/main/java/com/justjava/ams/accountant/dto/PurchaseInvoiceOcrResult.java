package com.justjava.ams.accountant.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseInvoiceOcrResult {
    private String purchaseOrderNumber;
    private String vendorName;
    private String vendorEmail;
    private String vendorPhone;
    private String vendorAddress;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate purchaseDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate dueDate;
    private BigDecimal subtotal;
    private BigDecimal totalAmount;
    private String notes;
    private String rawText;
    private List<PurchaseLineItemDTO> lineItems;
}
