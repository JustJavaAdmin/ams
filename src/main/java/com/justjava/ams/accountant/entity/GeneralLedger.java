package com.justjava.ams.accountant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.justjava.ams.common.entity.User;
import com.justjava.ams.common.entity.Branch;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "general_ledger")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneralLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "account_id", nullable = false)
    private ChartOfAccounts account;

    @Column(nullable = false)
    private String journalNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(nullable = false)
    private LocalDate transactionDate;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DebitCredit debitCredit;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String description;

    @Column
    private String referenceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @Column
    private String notes;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SourceType sourceType = SourceType.MANUAL_JOURNAL;

    @Column(nullable = false)
    private Long sourceId;

    @Column(nullable = false)
    private String postingBatchId;

    @Column
    private String postedBy;

    @Column
    private LocalDateTime postedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversed_entry_id")
    private GeneralLedger reversedEntry;

    @Column(columnDefinition = "TEXT")
    private String reversalReason;

    @Column
    private String reversedBy;

    @Column
    private LocalDateTime reversedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiscal_period_id")
    private FiscalPeriod fiscalPeriod;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public enum DebitCredit {
        DEBIT,
        CREDIT
    }

    public enum TransactionStatus {
        PENDING,
        APPROVED,
        POSTED,
        REVERSED
    }

    public enum SourceType {
        MANUAL_JOURNAL,
        CUSTOMER_INVOICE,
        PURCHASE_INVOICE,
        EXPENSE,
        CUSTOMER_PAYMENT,
        SUPPLIER_PAYMENT,
        EXPENSE_PAYMENT,
        BANK_TRANSACTION,
        FIXED_ASSET,
        FIXED_ASSET_DEPRECIATION,
        YEAR_END_CLOSE
    }
}

