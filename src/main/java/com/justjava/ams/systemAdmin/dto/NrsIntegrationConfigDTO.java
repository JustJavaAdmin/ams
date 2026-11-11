package com.justjava.ams.systemAdmin.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NrsIntegrationConfigDTO {
    private Long id;
    private String environment;
    private Boolean enabled;
    private String baseUrl;
    private String aggregatorId;
    private String secretKeyReference;
    private String linkMerchantPath;
    private String serviceMode;
    private Boolean liveSubmissionEnabled;
    private String tokenUrl;
    private String accessPointProvider;
    private String clientId;
    private String clientSecretReference;
    private String scope;
    private Integer connectTimeoutSeconds;
    private Integer readTimeoutSeconds;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
