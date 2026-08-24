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
@Table(name = "payment_schedules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "purchase_invoice_id", nullable = false)
    private PurchaseInvoice purchaseInvoice;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private LocalDate scheduledPaymentDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amountScheduled;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amountRemaining;

    @Column(nullable = false)
    private Integer priority = 5;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ScheduleStatus status = ScheduleStatus.PLANNED;

    @Column
    private String holdReason;

    @Column
    private String createdBy;

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
    private LocalDateTime approvedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum ScheduleStatus {
        PLANNED,
        READY_FOR_APPROVAL,
        APPROVED,
        REJECTED,
        HELD,
        PAID,
        CANCELLED
    }
}
