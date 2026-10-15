package com.justjava.ams.invoicing.document.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "invoice_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long invoiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceType invoiceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentFormat format;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false, length = 64)
    private String checksum;

    @Column
    private String generatedBy;

    @Column
    private String sourceStatus;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    private byte[] content;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime generatedAt;

    public enum InvoiceType {
        CUSTOMER_INVOICE,
        PURCHASE_INVOICE,
        CREDIT_NOTE
    }

    public enum DocumentFormat {
        PDF,
        JSON,
        XML
    }
}
