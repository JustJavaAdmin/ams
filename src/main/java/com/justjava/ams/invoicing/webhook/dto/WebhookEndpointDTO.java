package com.justjava.ams.invoicing.webhook.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEndpointDTO {
    private Long id;
    private Long organizationId;
    private String name;
    private String url;
    private String eventTypes;
    private String authType;
    private String secretReference;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
