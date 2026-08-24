package com.justjava.ams.financeAdmin.entity;

import com.justjava.ams.accountant.entity.ChartOfAccounts;
import com.justjava.ams.common.entity.Organization;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "approval_rules", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"organization_id", "rule_name"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ModuleControl.ModuleType moduleType;

    @Column(nullable = false)
    private String transactionType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal minAmount = BigDecimal.ZERO;

    @Column(precision = 19, scale = 2)
    private BigDecimal maxAmount;

    @Column
    @Enumerated(EnumType.STRING)
    private ChartOfAccounts.AccountType accountType;

    @Column
    private Long branchId;

    @Column(length = 50)
    private String departmentCode;

    @Column(nullable = false)
    private Integer requiredApprovals = 1;

    @Column
    private String approverRole;

    @Column(nullable = false)
    private Integer priority = 100;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void defaults() {
        if (transactionType == null || transactionType.trim().isEmpty()) {
            transactionType = "ALL";
        }
        if (minAmount == null) {
            minAmount = BigDecimal.ZERO;
        }
        if (requiredApprovals == null || requiredApprovals < 1) {
            requiredApprovals = 1;
        }
        if (priority == null) {
            priority = 100;
        }
        if (active == null) {
            active = true;
        }
    }
}
