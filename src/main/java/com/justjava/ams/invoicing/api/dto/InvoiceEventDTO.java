package com.justjava.ams.invoicing.api.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceEventDTO {
    private String eventType;
    private Long invoiceId;
    private LocalDateTime occurredAt;
    private Map<String, Object> data;
}
