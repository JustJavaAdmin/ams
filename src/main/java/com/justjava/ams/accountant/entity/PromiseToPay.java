package com.justjava.ams.accountant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "promise_to_pay")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromiseToPay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "collection_case_id", nullable = false)
    private ReceivablesCollectionCase collectionCase;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal promisedAmount;

    @Column(nullable = false)
    private LocalDate promisedDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column
    private String createdBy;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PromiseStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum PromiseStatus {
        ACTIVE,
        KEPT,
        BROKEN,
        CANCELLED
    }
}
