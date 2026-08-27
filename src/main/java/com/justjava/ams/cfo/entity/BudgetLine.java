package com.justjava.ams.cfo.entity;

import com.justjava.ams.accountant.entity.ChartOfAccounts;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "budget_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "budget_id", nullable = false)
    private Budget budget;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "chart_account_id", nullable = false)
    private ChartOfAccounts chartAccount;

    @Column(length = 50)
    private String departmentCode;

    @Column(length = 50)
    private String projectCode;

    @Column(length = 50)
    private String branchCode;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal allocatedAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal warningThresholdPercent = new BigDecimal("90.00");

    @Column(nullable = false)
    @Builder.Default
    private Boolean hardStopEnabled = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column
    private String notes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}