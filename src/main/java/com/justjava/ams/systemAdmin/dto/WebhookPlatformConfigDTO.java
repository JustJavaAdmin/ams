package com.justjava.ams.systemAdmin.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookPlatformConfigDTO {
    private Long id;
    private Boolean enabled;
    private String signingAlgorithm;
    private Boolean retryEnabled;
    private Integer maxAttempts;
    private Integer connectTimeoutSeconds;
    private Integer readTimeoutSeconds;
    private String allowedDomainPattern;
    private Integer deadLetterRetentionDays;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
