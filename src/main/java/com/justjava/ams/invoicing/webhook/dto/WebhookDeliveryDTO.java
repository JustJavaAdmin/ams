package com.justjava.ams.invoicing.webhook.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDeliveryDTO {
    private Long id;
    private Long webhookEndpointId;
    private String eventType;
    private String eventId;
    private String payloadHash;
    private String status;
    private Integer attemptCount;
    private String requestBody;
    private Integer responseCode;
    private String responseBody;
    private LocalDateTime nextRetryAt;
    private String signature;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
