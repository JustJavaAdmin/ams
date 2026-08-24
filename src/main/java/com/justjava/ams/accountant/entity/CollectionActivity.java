package com.justjava.ams.accountant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "collection_activities")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "collection_case_id", nullable = false)
    private ReceivablesCollectionCase collectionCase;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ActivityType activityType;

    @Column
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column
    private String createdBy;

    @Column
    private LocalDateTime sentAt;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ActivityStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum ActivityType {
        NOTE,
        CALL,
        EMAIL,
        DUNNING,
        ASSIGNMENT,
        ESCALATION,
        PROMISE,
        PAYMENT,
        CLOSURE
    }

    public enum ActivityStatus {
        DRAFT,
        SENT,
        COMPLETED,
        FAILED
    }
}
