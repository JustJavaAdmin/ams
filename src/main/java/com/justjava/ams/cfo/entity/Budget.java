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
@Table(name = "budgets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private String budgetCode;

    @Column(nullable = false)
    private String budgetName;

    @Column(nullable = false)
    private Integer budgetYear;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalBudget;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal allocatedAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal spentAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private BudgetStatus status = BudgetStatus.DRAFT;

    @Column(nullable = false)
    @Builder.Default
    private Boolean approved = false;

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
    private String departmentName;

    @Column
    private String notes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum BudgetStatus {
        DRAFT,
        SUBMITTED,
        APPROVED,
        ACTIVE,
        EXCEEDED,
        REJECTED,
        CLOSED
    }
}