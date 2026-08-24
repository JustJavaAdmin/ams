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
@Table(name = "receivables_collection_cases", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"customer_invoice_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceivablesCollectionCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_invoice_id", nullable = false)
    private CustomerInvoice customerInvoice;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal outstandingAmount;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private Long daysOverdue;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private CaseStatus status;

    @Column
    private String collectorUsername;

    @Column
    private LocalDateTime assignedAt;

    @Column
    private String escalatedTo;

    @Column
    private LocalDateTime escalatedAt;

    @Column
    private String escalationReason;

    @Column
    private Integer dunningLevel;

    @Column
    private LocalDate lastDunningDate;

    @Column
    private LocalDate nextActionDate;

    @Column(columnDefinition = "TEXT")
    private String closeReason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum CaseStatus {
        OPEN,
        IN_PROGRESS,
        PROMISED,
        ESCALATED,
        RESOLVED,
        CLOSED
    }
}
