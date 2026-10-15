package com.justjava.ams.invoicing.document.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceDocumentDTO {
    private Long id;
    private Long invoiceId;
    private String invoiceType;
    private String format;
    private String fileName;
    private String contentType;
    private String checksum;
    private String generatedBy;
    private String sourceStatus;
    private LocalDateTime generatedAt;
}
