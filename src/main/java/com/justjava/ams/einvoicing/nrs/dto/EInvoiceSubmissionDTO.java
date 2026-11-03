package com.justjava.ams.einvoicing.nrs.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EInvoiceSubmissionDTO {
    private Long id;
    private Long invoiceId;
    private String submissionType;
    private String status;
    private String payloadFormat;
    private String payloadHash;
    private String signature;
    private String nrsRequestId;
    private String nrsIrn;
    private String nrsResponseCode;
    private String nrsResponseMessage;
    private LocalDateTime submittedAt;
    private LocalDateTime clearedAt;
    private LocalDateTime failedAt;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private EInvoicePayloadArchiveDTO archive;
}
