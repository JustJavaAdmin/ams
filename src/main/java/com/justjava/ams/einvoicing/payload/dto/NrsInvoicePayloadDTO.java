package com.justjava.ams.einvoicing.payload.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NrsInvoicePayloadDTO {
    private InvoiceHeader invoiceHeader;
    private TaxTotal taxTotal;
    private MonetaryTotal monetaryTotal;
    private List<InvoiceLine> invoiceLines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InvoiceHeader {
        private Long invoiceId;
        private Long organizationId;
        private String invoiceNumber;
        private String supplierTaxID;
        private String buyerTaxID;
        private String invoiceTypeCode;
        private String transactionClassification;
        private String issueDateTime;
        private String currencyCode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TaxTotal {
        private BigDecimal taxAmount;
        private String taxCategoryCode;
        private BigDecimal taxPercentage;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonetaryTotal {
        private BigDecimal lineExtensionAmount;
        private BigDecimal taxExclusiveAmount;
        private BigDecimal taxInclusiveAmount;
        private BigDecimal payableAmount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InvoiceLine {
        private String itemLineID;
        private String description;
        private String nrsUnitOfMeasurement;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineExtensionAmount;
        private BigDecimal vatAmount;
    }
}
