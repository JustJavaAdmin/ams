package com.justjava.ams.systemAdmin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_nrs_integration_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NrsIntegrationConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Environment environment = Environment.SANDBOX;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = false;

    @Column
    private String baseUrl;

    @Column
    private String tokenUrl;

    @Column
    private String accessPointProvider;

    @Column
    private String clientId;

    @Column
    private String clientSecretReference;

    @Column
    private String scope;

    @Builder.Default
    @Column(nullable = false)
    private Integer connectTimeoutSeconds = 10;

    @Builder.Default
    @Column(nullable = false)
    private Integer readTimeoutSeconds = 30;

    @Column
    private String updatedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum Environment {
        SANDBOX,
        PRODUCTION
    }
}
