package com.justjava.ams.accountant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepreciationJournalImportDTO {
    private Long id;
    private Long organizationId;
    private Long manualJournalId;
    private String manualJournalStatus;
    private String externalSystem;
    private String externalBatchId;
    private LocalDate journalDate;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private Integer lineCount;
    private String sourceFileName;
    private String payloadHash;
    private String status;
    private String importedBy;
    private LocalDateTime importedAt;
    private LocalDateTime updatedAt;
    private List<String> validationWarnings;
}
