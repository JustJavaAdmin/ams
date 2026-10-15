package com.justjava.ams.einvoicing.archive.entity;

import com.justjava.ams.einvoicing.nrs.entity.EInvoiceSubmission;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "e_invoice_payload_archives")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EInvoicePayloadArchive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private EInvoiceSubmission submission;

    @Column(nullable = false)
    private Long invoiceId;

    @Column(nullable = false)
    private String payloadStorageKey;

    @Column
    private String responseStorageKey;

    @Column(nullable = false, length = 64)
    private String payloadHash;

    @Column(length = 64)
    private String responseHash;

    @Column(nullable = false)
    private Boolean encrypted;

    @Column(nullable = false)
    private LocalDateTime retentionUntil;

    @Column(nullable = false)
    private Boolean immutable;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    private String payloadContent;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column
    private String responseContent;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
