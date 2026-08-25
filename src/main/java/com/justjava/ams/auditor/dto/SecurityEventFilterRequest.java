package com.justjava.ams.auditor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityEventFilterRequest {

    private String eventType;
    private String severity;
    private Long userId;
    private Boolean acknowledged;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}
