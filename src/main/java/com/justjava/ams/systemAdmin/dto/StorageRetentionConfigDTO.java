package com.justjava.ams.systemAdmin.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorageRetentionConfigDTO {
    private Long id;
    private String artifactStorageLocation;
    private String archiveStorageLocation;
    private Boolean encryptionEnabled;
    private String encryptionKeyReference;
    private Integer retentionYears;
    private Boolean wormRequired;
    private Boolean immutableArchiveEnabled;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
