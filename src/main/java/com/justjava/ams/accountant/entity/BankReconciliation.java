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
@Table(name = "bank_reconciliations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankReconciliation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bank_account_id", nullable = false)
    private BankAccount bankAccount;

    @Column(nullable = false)
    private LocalDate statementDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal openingBalance;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal closingBalance;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal clearedAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal unresolvedDifference;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ReconciliationStatus status;

    @Column(nullable = false)
    private Integer importedLineCount = 0;

    @Column(nullable = false)
    private Integer matchedLineCount = 0;

    @Column
    private String reconciledBy;

    @Column
    private LocalDateTime reconciledAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum ReconciliationStatus {
        DRAFT,
        COMPLETED
    }
}
