package com.justjava.ams.systemAdmin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_signing_key_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SigningKeyConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = false;

    @Builder.Default
    @Column(nullable = false)
    private String algorithm = "ECDSA";

    @Column
    private String keyProvider;

    @Column
    private String keyAlias;

    @Column
    private String keyReference;

    @Column
    private LocalDateTime lastRotatedAt;

    @Column
    private LocalDateTime nextRotationDueAt;

    @Column
    private String updatedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
