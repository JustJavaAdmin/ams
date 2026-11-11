package com.justjava.ams.einvoicing.nrs.aggregator.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class AggregatorRequests {
    private AggregatorRequests() {}

    @Data @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MerchantLink {
        @NotBlank private String aggregatorId;
        @Valid @NotNull private Merchant merchant;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Merchant {
        @NotBlank private String name;
        @NotBlank private String merchantId;
        @NotBlank private String firsBusinessId;
        @NotBlank private String firsServiceId;
        @NotBlank private String tin;
        @NotBlank private String email;
        @NotBlank private String phone;
        private String webhookUrl;
        @Valid @NotEmpty private List<Address> addresses;
        @NotBlank private String serviceMode;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Address {
        @NotBlank private String line;
        @NotBlank private String city;
        private String postalZone;
        @NotBlank private String country;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProcessInvoice {
        @NotBlank private String invoiceRef;
        @NotBlank private String aggregatorId;
        @NotBlank private String merchantId;
        @NotBlank private String currency;
        @NotBlank private String issueDate;
        private String dueDate;
        private String status;
        private String orderReference;
        @Valid @NotNull private Customer customer;
        @Valid @NotEmpty private List<LineItem> lineItems;
        @Valid @NotNull private Totals totals;
        private Map<String, Object> firsSpecific;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Customer {
        private String customerId;
        @NotBlank private String name;
        private String email;
        private String phone;
        private Map<String, Object> identifiers;
        @Valid private Address address;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LineItem {
        @NotBlank private String name;
        private String description;
        private String sellersItemIdentification;
        private String hsnCode;
        private String productCategory;
        private String isicCode;
        private String serviceCategory;
        @NotNull private BigDecimal unitPrice;
        private BigDecimal discountRate;
        private BigDecimal discountAmount;
        private BigDecimal feeRate;
        private BigDecimal feeAmount;
        @NotNull private BigDecimal quantity;
        @NotNull private BigDecimal totalAmount;
        private String taxCode;
        private BigDecimal taxRate;
        private BigDecimal taxAmount;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Totals {
        @NotNull private BigDecimal totalLineAmount;
        @NotNull private BigDecimal totalTax;
        @NotNull private BigDecimal grandTotal;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class InvoiceLookup {
        @NotBlank private String merchantId;
        private String irn;
        private String invoiceReference;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PaymentNotification {
        @NotBlank private String merchantId;
        @NotBlank private String irn;
        @NotBlank private String paymentStatus;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MerchantUpdate {
        @NotBlank private String merchantId;
        private String name;
        private String firsBusinessId;
        private String firsServiceId;
        private String tin;
        private String email;
        private String phone;
        private String webhookUrl;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ServiceModeUpdate {
        @NotBlank private String aggregatorId;
        @NotBlank private String serviceMode;
    }

    @Data
    public static class Envelope<T> {
        private String status;
        private String message;
        private T data;
    }
}
