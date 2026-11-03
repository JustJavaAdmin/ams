package com.justjava.ams.systemAdmin.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackgroundJobConfigDTO {
    private Long id;
    private String jobName;
    private Boolean enabled;
    private String scheduleExpression;
    private Integer maxRetryAttempts;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
