package com.justjava.ams.einvoicing.nrs.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "e_invoice_submissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EInvoiceSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubmissionType submissionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayloadFormat payloadFormat;

    @Column(nullable = false, length = 64)
    private String payloadHash;

    @Column(length = 2000)
    private String signature;

    @Column
    private String nrsRequestId;

    @Column
    private String nrsIrn;

    @Column
    private String nrsResponseCode;

    @Column(length = 2000)
    private String nrsResponseMessage;

    @Column
    private LocalDateTime submittedAt;

    @Column
    private LocalDateTime clearedAt;

    @Column
    private LocalDateTime failedAt;

    @Column(nullable = false)
    private Integer retryCount;

    @Column
    private LocalDateTime nextRetryAt;

    @Column
    private String createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum SubmissionType {
        PRE_CLEARANCE,
        ASYNC_B2C,
        CREDIT_NOTE
    }

    public enum Status {
        PENDING,
        SUBMITTED,
        CLEARED,
        FAILED,
        BREACH,
        REJECTED,
        FINAL_LOCKED
    }

    public enum PayloadFormat {
        JSON,
        XML
    }
}
