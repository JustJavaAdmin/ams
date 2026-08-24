package com.justjava.ams.financeAdmin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkImportPreviewResponse {
    private Long importId;
    private Long organizationId;
    private String importType;
    private String status;
    private String fileName;
    private Boolean updateExisting;
    private Integer totalRows;
    private Integer validRows;
    private Integer invalidRows;
    private Integer createdCount;
    private Integer updatedCount;
    private Integer skippedCount;
    private Integer failedCount;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private List<BulkImportRowDTO> rows;
}
