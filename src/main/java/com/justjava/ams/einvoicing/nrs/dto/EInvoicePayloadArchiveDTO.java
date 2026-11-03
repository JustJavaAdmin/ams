package com.justjava.ams.einvoicing.nrs.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EInvoicePayloadArchiveDTO {
    private Long id;
    private Long submissionId;
    private Long invoiceId;
    private String payloadStorageKey;
    private String responseStorageKey;
    private String payloadHash;
    private String responseHash;
    private Boolean encrypted;
    private LocalDateTime retentionUntil;
    private Boolean immutable;
    private LocalDateTime createdAt;
}
