package com.justjava.ams.systemAdmin.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiClientRegistrationDTO {
    private Long id;
    private String clientName;
    private String clientId;
    private String clientSecretReference;
    private String scopes;
    private Boolean active;
    private LocalDateTime revokedAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
