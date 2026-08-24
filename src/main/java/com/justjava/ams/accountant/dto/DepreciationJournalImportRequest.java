package com.justjava.ams.accountant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepreciationJournalImportRequest {
    @NotBlank(message = "External system is required")
    private String externalSystem;

    @NotBlank(message = "External batch ID is required")
    private String externalBatchId;

    @NotNull(message = "Journal date is required")
    private LocalDate journalDate;

    private String description;
    private String branchCode;
    private Boolean autoSubmit;

    @Valid
    private List<DepreciationJournalLineRequest> lines;
}
