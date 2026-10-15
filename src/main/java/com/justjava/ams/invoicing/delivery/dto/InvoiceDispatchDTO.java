package com.justjava.ams.invoicing.delivery.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceDispatchDTO {
    private Long id;
    private Long invoiceId;
    private String channel;
    private String target;
    private String status;
    private Integer attemptCount;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime nextRetryAt;
    private Integer responseCode;
    private String responseBody;
    private String errorMessage;
    private String idempotencyKey;
    private Long documentId;
    private String createdBy;
    private LocalDateTime createdAt;
}
