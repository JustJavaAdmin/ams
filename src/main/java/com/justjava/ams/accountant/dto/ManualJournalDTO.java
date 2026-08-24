package com.justjava.ams.accountant.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManualJournalDTO {

	private Long id;
	private Long organizationId;
	private Long branchId;
	private String description;
	private LocalDate journalDate;
	private LocalDate postingDate;
	private String status;
	private String createdBy;
	private String submittedBy;
	private String approvedBy;
	private Long approvalRequestId;
	private Long approvalRuleId;
	private String approvalRuleName;
	private Integer requiredApprovals;
	private String rejectionReason;
	private LocalDateTime createdAt;
	private LocalDateTime submittedAt;
	private LocalDateTime approvedAt;
	private LocalDateTime updatedAt;
	private List<JournalLineDTO> journalLines;
	private BigDecimal totalDebits;
	private BigDecimal totalCredits;
	private boolean balanced;
}


