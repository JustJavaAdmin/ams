package com.justjava.ams.accountant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.entity.User;
import com.justjava.ams.financeAdmin.entity.TaxJurisdiction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "customer_invoices", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"organization_id", "invoice_number"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(nullable = false)
    private String invoiceNumber;

    @Column(nullable = false)
    private String customerName;

    @Column
    private String customerEmail;

    @Column
    private String customerPhone;

    @Column
    private String customerAddress;

    @Column(nullable = false)
    private LocalDate invoiceDate;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_jurisdiction_id")
    private TaxJurisdiction taxJurisdiction;

    @Column
    private String taxCode;

    @Column(precision = 5, scale = 2)
    private BigDecimal taxRate;

    @Column
    private String taxCalculationType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(precision = 19, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column
    @Enumerated(EnumType.STRING)
    private TransactionClassification transactionClassification;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ComplianceStatus complianceStatus = ComplianceStatus.NOT_REQUIRED;

    @Column
    private String supplierTaxId;

    @Column
    private String buyerTaxId;

    @Column
    private String nrsIrn;

    @Column
    private String nrsQrCode;

    @Column
    private String nrsDigitalStamp;

    @Column
    private String nrsReceiptReference;

    @Column
    private LocalDateTime nrsClearedAt;

    @Column
    private LocalDateTime buyerReviewDeadline;

    @Column
    private LocalDateTime finalLockedAt;

    @Column
    private LocalDateTime rejectedAt;

    @Column
    private String rejectionReason;

    @Column
    private LocalDateTime complianceBreachAt;

    @Column
    private LocalDate postedDate;

    @Column
    private String postedBy;

    @Column
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<InvoiceLineItem> lineItems;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public enum InvoiceStatus {
        DRAFT,
        APPROVED,
        SENT,
        POSTED,
        PARTIALLY_PAID,
        PAID,
        OVERDUE,
        CANCELLED
    }

    public enum TransactionClassification {
        B2B,
        B2G,
        B2C
    }

    public enum ComplianceStatus {
        NOT_REQUIRED,
        PENDING_CLEARANCE,
        CLEARED,
        FAILED,
        BREACH,
        REJECTED_VOID,
        FINAL_LOCKED
    }
}

