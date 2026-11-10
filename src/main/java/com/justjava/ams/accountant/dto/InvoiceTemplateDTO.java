package com.justjava.ams.accountant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceTemplateDTO {
    private Long id;
    private Long organizationId;
    private String templateName;
    private String blocks;
    private String theme;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
