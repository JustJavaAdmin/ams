package com.justjava.ams.systemAdmin.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SigningKeyConfigDTO {
    private Long id;
    private Boolean active;
    private String algorithm;
    private String keyProvider;
    private String keyAlias;
    private String keyReference;
    private LocalDateTime lastRotatedAt;
    private LocalDateTime nextRotationDueAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
