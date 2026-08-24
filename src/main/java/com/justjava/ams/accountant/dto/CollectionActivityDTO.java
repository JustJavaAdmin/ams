package com.justjava.ams.accountant.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionActivityDTO {
    private Long id;
    private Long collectionCaseId;
    private String activityType;
    private String subject;
    private String notes;
    private String createdBy;
    private LocalDateTime sentAt;
    private String status;
    private LocalDateTime createdAt;
}
