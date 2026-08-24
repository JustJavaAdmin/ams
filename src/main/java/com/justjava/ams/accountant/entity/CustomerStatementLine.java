package com.justjava.ams.accountant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_statement_lines")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerStatementLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_statement_id", nullable = false)
    private CustomerStatement customerStatement;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_invoice_id")
    private CustomerInvoice customerInvoice;

    @Column(nullable = false)
    private LocalDate transactionDate;

    @Column(nullable = false)
    private String referenceNumber;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private LineType lineType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal debitAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal creditAmount;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum LineType {
        INVOICE,
        PAYMENT
    }
}
