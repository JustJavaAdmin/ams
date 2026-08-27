package com.justjava.ams.accountant.entity;

import com.justjava.ams.common.entity.Organization;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "payment_runs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRun {

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
    private LocalDate runDate;

    @Column(nullable = false)
    private LocalDate cutoffDate;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private Integer itemCount = 0;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentRunStatus status = PaymentRunStatus.DRAFT;

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
    @Builder.Default
    private Integer requiredApprovals = 1;

    @Column
    private LocalDateTime approvedAt;

    @Column
    private String executedBy;

    @Column
    private LocalDateTime executedAt;

    @OneToMany(mappedBy = "paymentRun", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PaymentRunItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum PaymentRunStatus {
        DRAFT,
        READY_FOR_APPROVAL,
        APPROVED,
        EXECUTED,
        PARTIALLY_EXECUTED,
        REJECTED,
        CANCELLED
    }
}