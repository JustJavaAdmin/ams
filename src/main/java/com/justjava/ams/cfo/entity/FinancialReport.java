package com.justjava.ams.cfo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.justjava.ams.common.entity.Organization;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "financial_reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ReportType reportType;

    @Column(nullable = false)
    private String reportName;

    @Column(nullable = false)
    private LocalDate reportDate;

    @Column(nullable = false)
    private LocalDate fromDate;

    @Column(nullable = false)
    private LocalDate toDate;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ReportStatus status = ReportStatus.DRAFT;

    @Column(columnDefinition = "TEXT")
    private String reportContent;

    @Column(nullable = false)
    private String generatedBy;

    @Column
    private LocalDate approvedDate;

    @Column
    private String approvedBy;

    @Column
    private Long approvalRequestId;

    @Column
    private Long approvalRuleId;

    @Column
    private String approvalRuleName;

    @Column
    private Integer requiredApprovals = 1;

    @Column
    private String notes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public enum ReportType {
        INCOME_STATEMENT,
        BALANCE_SHEET,
        CASH_FLOW,
        EQUITY,
        BUDGET_VARIANCE,
        CUSTOM
    }

    public enum ReportStatus {
        DRAFT,
        PENDING_REVIEW,
        APPROVED,
        REJECTED,
        PUBLISHED,
        ARCHIVED
    }
}

