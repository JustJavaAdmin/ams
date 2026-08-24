package com.justjava.ams.financeAdmin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkImportRowDTO {
    private Integer rowNumber;
    private String status;
    private Map<String, String> rawData;
    private Map<String, String> normalizedData;
    private String errorMessage;
    private Long createdRecordId;
    private Long updatedRecordId;
}
