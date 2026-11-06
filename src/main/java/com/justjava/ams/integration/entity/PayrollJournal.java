package com.justjava.ams.integration.entity;

import com.justjava.ams.common.entity.Organization;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "payroll_journals", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payroll_journal_business_key",
                columnNames = {"organization_id", "source_system", "external_reference", "revision"})
}, indexes = {
        @Index(name = "ix_payroll_journal_org_date", columnList = "organization_id,journal_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollJournal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "source_system", nullable = false, length = 30)
    private String sourceSystem;

    @Column(name = "external_reference", nullable = false, length = 200)
    private String externalReference;

    @Column(nullable = false, length = 30)
    private String revision;

    @Column(name = "idempotency_key", nullable = false, length = 300)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "payroll_period_id", nullable = false, length = 200)
    private String payrollPeriodId;

    @Column(name = "client_id", nullable = false, length = 150)
    private String clientId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "journal_date", nullable = false)
    private LocalDate journalDate;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(columnDefinition = "TEXT")
    private String narration;

    @Column(name = "total_debit", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalDebit;

    @Column(name = "total_credit", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalCredit;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "POSTED";

    @Column(name = "posted_at", nullable = false)
    private LocalDateTime postedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "payrollJournal", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PayrollJournalLine> lines = new ArrayList<>();
}
