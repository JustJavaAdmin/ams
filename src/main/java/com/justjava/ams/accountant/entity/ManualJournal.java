package com.justjava.ams.accountant.entity;

import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.entity.Branch;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "manual_journals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = "journalLines")
@ToString(exclude = "journalLines")
public class ManualJournal {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "organization_id", nullable = false)
	private Organization organization;

	@ManyToOne
	@JoinColumn(name = "branch_id")
	private Branch branch;

	@Column(columnDefinition = "TEXT", nullable = false)
	private String description;

	@Column(nullable = false)
	private LocalDate journalDate;

	private LocalDate postingDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private JournalStatus status = JournalStatus.DRAFT;

	@Column(nullable = false)
	private String createdBy;

	private String submittedBy;

	private String approvedBy;

	private Long approvalRequestId;

	private Long approvalRuleId;

	private String approvalRuleName;

	private Integer requiredApprovals = 1;

	@Column(columnDefinition = "TEXT")
	private String rejectionReason;

	@CreationTimestamp
	private LocalDateTime createdAt;

	private LocalDateTime submittedAt;

	private LocalDateTime approvedAt;

	@UpdateTimestamp
	private LocalDateTime updatedAt;

	@Version
	private Long version;

	@OneToMany(mappedBy = "manualJournal", cascade = CascadeType.ALL, orphanRemoval = true)
	@Builder.Default
	private Set<JournalLine> journalLines = new HashSet<>();

	public enum JournalStatus {
		DRAFT,
		SUBMITTED,
		APPROVED,
		POSTED,
		REJECTED
	}

	public boolean isBalanced() {
		if (journalLines.isEmpty()) return false;
		java.math.BigDecimal totalDebits = journalLines.stream()
				.map(JournalLine::getDebitAmount)
				.reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
		java.math.BigDecimal totalCredits = journalLines.stream()
				.map(JournalLine::getCreditAmount)
				.reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
		return totalDebits.compareTo(totalCredits) == 0 && totalDebits.compareTo(java.math.BigDecimal.ZERO) > 0;
	}

	public java.math.BigDecimal getTotalDebits() {
		return journalLines.stream()
				.map(JournalLine::getDebitAmount)
				.reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
	}

	public java.math.BigDecimal getTotalCredits() {
		return journalLines.stream()
				.map(JournalLine::getCreditAmount)
				.reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
	}
}


