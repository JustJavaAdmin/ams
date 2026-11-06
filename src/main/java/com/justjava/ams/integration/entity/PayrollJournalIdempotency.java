package com.justjava.ams.integration.entity;

import com.justjava.ams.common.entity.Organization;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "payroll_journal_idempotency", uniqueConstraints = {
        @UniqueConstraint(name = "uk_pji_org_key", columnNames = {"organization_id", "idempotency_key"}),
        @UniqueConstraint(name = "uk_pji_business_key",
                columnNames = {"organization_id", "source_system", "external_reference", "revision"})
}, indexes = {
        @Index(name = "ix_pji_journal_id", columnList = "payroll_journal_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollJournalIdempotency {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "idempotency_key", nullable = false, length = 300)
    private String idempotencyKey;

    @Column(name = "source_system", nullable = false, length = 30)
    private String sourceSystem;

    @Column(name = "external_reference", nullable = false, length = 200)
    private String externalReference;

    @Column(nullable = false, length = 30)
    private String revision;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payroll_journal_id")
    private PayrollJournal payrollJournal;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "client_id", nullable = false, length = 150)
    private String clientId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
