package com.justjava.ams.systemAdmin.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemConfigAuditLogDTO {
    private Long id;
    private String configType;
    private Long configId;
    private String action;
    private String changedBy;
    private String summary;
    private LocalDateTime createdAt;
}
