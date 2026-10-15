package com.justjava.ams.systemAdmin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_webhook_platform_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookPlatformConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = false;

    @Builder.Default
    @Column(nullable = false)
    private String signingAlgorithm = "HMAC_SHA256";

    @Builder.Default
    @Column(nullable = false)
    private Boolean retryEnabled = true;

    @Builder.Default
    @Column(nullable = false)
    private Integer maxAttempts = 5;

    @Builder.Default
    @Column(nullable = false)
    private Integer connectTimeoutSeconds = 10;

    @Builder.Default
    @Column(nullable = false)
    private Integer readTimeoutSeconds = 30;

    @Column
    private String allowedDomainPattern;

    @Builder.Default
    @Column(nullable = false)
    private Integer deadLetterRetentionDays = 90;

    @Column
    private String updatedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
