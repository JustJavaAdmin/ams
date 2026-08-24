package com.justjava.ams.accountant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.justjava.ams.common.entity.Organization;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "fiscal_periods")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FiscalPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false)
    private Integer quarter;

    @Column(nullable = false)
    private LocalDateTime startDate;

    @Column(nullable = false)
    private LocalDateTime endDate;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PeriodStatus status = PeriodStatus.OPEN;

    @Column(nullable = false)
    private Boolean closed = false;

    @Column
    private LocalDateTime closedDate;

    @OneToMany(mappedBy = "fiscalPeriod")
    private Set<GeneralLedger> generalLedgerEntries;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public enum PeriodStatus {
        OPEN,
        CLOSING,
        LOCKED,
        CLOSED
    }
}

