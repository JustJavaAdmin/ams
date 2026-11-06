package com.justjava.ams.integration.service;

import com.justjava.ams.accountant.entity.ChartOfAccounts;
import com.justjava.ams.accountant.entity.FiscalPeriod;
import com.justjava.ams.accountant.repository.FiscalPeriodRepository;
import com.justjava.ams.auditor.dto.AuditLogDTO;
import com.justjava.ams.auditor.service.AuditLogService;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import com.justjava.ams.integration.dto.PayrollJournalLineRequest;
import com.justjava.ams.integration.dto.PayrollJournalRequest;
import com.justjava.ams.integration.entity.PayrollJournal;
import com.justjava.ams.integration.entity.PayrollJournalIdempotency;
import com.justjava.ams.integration.entity.PayrollJournalLine;
import com.justjava.ams.integration.repository.PayrollJournalIdempotencyRepository;
import com.justjava.ams.integration.repository.PayrollJournalRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PayrollJournalPostingWorker {
    private final EntityManager entityManager;
    private final OrganizationRepository organizationRepository;
    private final FiscalPeriodRepository fiscalPeriodRepository;
    private final PayrollJournalRepository journalRepository;
    private final PayrollJournalIdempotencyRepository idempotencyRepository;
    private final com.justjava.ams.accountant.service.GeneralLedgerService generalLedgerService;
    private final AuditLogService auditLogService;

    @Transactional
    public PayrollJournal post(
            Long organizationId,
            String idempotencyKey,
            String requestHash,
            String clientId,
            PayrollJournalRequest request,
            Long fiscalPeriodId,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            List<ChartOfAccounts> accounts) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Payroll journal posting requires an active transaction");
        }

        Organization organization = organizationRepository.getReferenceById(organizationId);
        PayrollJournalIdempotency claim = PayrollJournalIdempotency.builder()
                .organization(organization)
                .idempotencyKey(idempotencyKey)
                .sourceSystem(request.getSourceSystem())
                .externalReference(request.getJournalReference())
                .revision(request.getRevision())
                .requestHash(requestHash)
                .status("PROCESSING")
                .clientId(clientId)
                .build();
        idempotencyRepository.saveAndFlush(claim);

        PayrollJournal journal = PayrollJournal.builder()
                .organization(organization)
                .sourceSystem(request.getSourceSystem())
                .externalReference(request.getJournalReference())
                .revision(request.getRevision())
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .payrollPeriodId(request.getPayrollPeriodId())
                .clientId(clientId)
                .currency(request.getCurrency())
                .journalDate(request.getPeriodEnd())
                .periodStart(request.getPeriodStart())
                .periodEnd(request.getPeriodEnd())
                .narration(request.getNarration())
                .totalDebit(totalDebit)
                .totalCredit(totalCredit)
                .status("POSTED")
                .postedAt(LocalDateTime.now())
                .build();
        journalRepository.saveAndFlush(journal);
        claim.setPayrollJournal(journal);

        List<PayrollJournalLine> persistedLines = new ArrayList<>();
        for (int i = 0; i < request.getLines().size(); i++) {
            PayrollJournalLineRequest requestLine = request.getLines().get(i);
            PayrollJournalLine line = PayrollJournalLine.builder()
                    .payrollJournal(journal)
                    .account(accounts.get(i))
                    .accountCode(requestLine.getAccountCode())
                    .accountName(requestLine.getAccountName())
                    .lineType(requestLine.getLineType())
                    .debitAmount(requestLine.getDebitAmount())
                    .creditAmount(requestLine.getCreditAmount())
                    .narration(requestLine.getNarration())
                    .build();
            persistedLines.add(line);
        }
        journal.getLines().addAll(persistedLines);
        entityManager.flush();

        FiscalPeriod period = fiscalPeriodRepository.getReferenceById(fiscalPeriodId);
        generalLedgerService.postPayrollJournalEntries(journal, persistedLines, period);
        claim.setStatus("POSTED");

        AuditLogDTO audit = AuditLogDTO.builder()
                .organizationId(organizationId)
                .entityType("PayrollJournal")
                .entityId(journal.getId())
                .action("POST")
                .newValue("POSTED")
                .description("Payroll journal posted by client " + clientId + " with idempotency key " + idempotencyKey)
                .build();
        auditLogService.createAuditLog(organizationId, audit);
        entityManager.flush();
        return journal;
    }
}
