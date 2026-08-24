package com.justjava.ams.accountant.entity;

import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.entity.User;
import com.justjava.ams.financeAdmin.entity.TaxJurisdiction;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "expenses", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"organization_id", "expense_number"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private String expenseNumber;

    @Column(nullable = false)
    private String payeeName;

    @Column
    private String payeeEmail;

    @Column
    private String payeePhone;

    @Column
    private String payeeAddress;

    @Column(nullable = false)
    private LocalDate expenseDate;

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
    private ExpenseStatus status = ExpenseStatus.DRAFT;

    @Column
    private LocalDate submittedDate;

    @Column
    private String submittedBy;

    @Column
    private LocalDate approvedDate;

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
    private LocalDate postedDate;

    @Column
    private String postedBy;

    @Column
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ExpenseLineItem> lineItems;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public enum ExpenseStatus {
        DRAFT,
        SUBMITTED,
        APPROVED,
        POSTED,
        PARTIALLY_PAID,
        PAID,
        REJECTED,
        CANCELLED
    }
}
