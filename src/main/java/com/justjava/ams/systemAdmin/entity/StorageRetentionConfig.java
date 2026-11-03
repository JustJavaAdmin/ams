package com.justjava.ams.systemAdmin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_storage_retention_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorageRetentionConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String artifactStorageLocation;

    @Column
    private String archiveStorageLocation;

    @Builder.Default
    @Column(nullable = false)
    private Boolean encryptionEnabled = true;

    @Column
    private String encryptionKeyReference;

    @Builder.Default
    @Column(nullable = false)
    private Integer retentionYears = 6;

    @Builder.Default
    @Column(nullable = false)
    private Boolean wormRequired = true;

    @Builder.Default
    @Column(nullable = false)
    private Boolean immutableArchiveEnabled = false;

    @Column
    private String updatedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
