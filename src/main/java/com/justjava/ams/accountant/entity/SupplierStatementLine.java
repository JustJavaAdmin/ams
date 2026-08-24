package com.justjava.ams.accountant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "supplier_statement_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierStatementLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supplier_statement_id", nullable = false)
    private SupplierStatement supplierStatement;

    @Column(nullable = false)
    private LocalDate transactionDate;

    @Column(nullable = false)
    private String referenceNumber;

    @Column
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal debitAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal creditAmount = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "matched_purchase_invoice_id")
    private PurchaseInvoice matchedPurchaseInvoice;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "matched_general_ledger_id")
    private GeneralLedger matchedPayment;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MatchStatus status = MatchStatus.UNMATCHED;

    @Column
    private String disputeReason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum MatchStatus {
        UNMATCHED,
        MATCHED,
        DISPUTED
    }
}
