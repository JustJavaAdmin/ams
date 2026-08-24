package com.justjava.ams.accountant.entity;

import com.justjava.ams.common.entity.Organization;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "depreciation_journal_imports",
        uniqueConstraints = @UniqueConstraint(name = "uk_depr_import_org_batch", columnNames = {"organization_id", "external_batch_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepreciationJournalImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "manual_journal_id", nullable = false)
    private ManualJournal manualJournal;

    @Column(nullable = false)
    private String externalSystem;

    @Column(nullable = false)
    private String externalBatchId;

    @Column(nullable = false)
    private LocalDate journalDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalDebit;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalCredit;

    @Column(nullable = false)
    private Integer lineCount;

    @Column
    private String sourceFileName;

    @Column(length = 64)
    private String payloadHash;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ImportStatus status;

    @Column(nullable = false)
    private String importedBy;

    @Column(columnDefinition = "TEXT")
    private String errorSummary;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime importedAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum ImportStatus {
        IMPORTED,
        SUBMITTED,
        POSTED,
        FAILED
    }
}
