package com.justjava.ams.invoicing.delivery.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "invoice_dispatches")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceDispatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Column
    private String target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private Integer attemptCount;

    @Column
    private LocalDateTime lastAttemptAt;

    @Column
    private LocalDateTime nextRetryAt;

    @Column
    private Integer responseCode;

    @Column(length = 2000)
    private String responseBody;

    @Column(length = 2000)
    private String errorMessage;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column
    private Long documentId;

    @Column
    private String createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum Channel {
        EMAIL,
        WEBHOOK,
        MANUAL_DOWNLOAD,
        API_PULL
    }

    public enum Status {
        PENDING,
        SUCCESS,
        FAILED,
        RETRYING,
        DEAD_LETTER
    }
}
