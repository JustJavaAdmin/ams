package com.justjava.ams.integration.entity;

import com.justjava.ams.accountant.entity.ChartOfAccounts;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "payroll_journal_lines", indexes = {
        @Index(name = "ix_payroll_journal_line_journal", columnList = "payroll_journal_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollJournalLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_journal_id", nullable = false)
    private PayrollJournal payrollJournal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chart_of_account_id", nullable = false)
    private ChartOfAccounts account;

    @Column(name = "account_code", nullable = false, length = 100)
    private String accountCode;

    @Column(name = "account_name", length = 200)
    private String accountName;

    @Column(name = "line_type", length = 100)
    private String lineType;

    @Column(name = "debit_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal debitAmount;

    @Column(name = "credit_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal creditAmount;

    @Column(columnDefinition = "TEXT")
    private String narration;
}
