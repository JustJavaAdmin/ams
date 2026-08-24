package com.justjava.ams.cfo.entity;

import com.justjava.ams.accountant.entity.GeneralLedger;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "budget_consumptions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"source_type", "source_id", "budget_line_id", "source_line_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetConsumption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "budget_line_id", nullable = false)
    private BudgetLine budgetLine;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private GeneralLedger.SourceType sourceType;

    @Column(nullable = false)
    private Long sourceId;

    @Column(nullable = false)
    private Long sourceLineId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate transactionDate;

    @Column(nullable = false)
    private Boolean reversed = false;

    @Column
    private String description;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
