package com.justjava.ams.systemAdmin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_api_client_registrations", uniqueConstraints = {
        @UniqueConstraint(columnNames = "client_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiClientRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String clientName;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column
    private String clientSecretReference;

    @Column(columnDefinition = "TEXT")
    private String scopes;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Column
    private LocalDateTime revokedAt;

    @Column
    private String updatedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
