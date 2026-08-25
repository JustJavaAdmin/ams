package com.justjava.ams.accountant.service;

import com.justjava.ams.accountant.entity.BankReconciliation;
import com.justjava.ams.accountant.entity.CustomerInvoice;
import com.justjava.ams.accountant.entity.FiscalPeriod;
import com.justjava.ams.accountant.entity.ManualJournal;
import com.justjava.ams.accountant.entity.PaymentRun;
import com.justjava.ams.accountant.entity.PurchaseInvoice;
import com.justjava.ams.accountant.repository.BankReconciliationRepository;
import com.justjava.ams.accountant.repository.CustomerInvoiceRepository;
import com.justjava.ams.accountant.repository.FiscalPeriodRepository;
import com.justjava.ams.accountant.repository.ManualJournalRepository;
import com.justjava.ams.accountant.repository.PaymentRunRepository;
import com.justjava.ams.accountant.repository.PurchaseInvoiceRepository;
import com.justjava.ams.auditor.service.AuditLogService;
import com.justjava.ams.cfo.entity.ApprovalRequest;
import com.justjava.ams.cfo.entity.FinancialReport;
import com.justjava.ams.cfo.repository.ApprovalRequestRepository;
import com.justjava.ams.cfo.repository.FinancialReportRepository;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FiscalPeriodServiceTest {

    @Mock
    private FiscalPeriodRepository fiscalPeriodRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ManualJournalRepository manualJournalRepository;

    @Mock
    private CustomerInvoiceRepository customerInvoiceRepository;

    @Mock
    private PurchaseInvoiceRepository purchaseInvoiceRepository;

    @Mock
    private BankReconciliationRepository bankReconciliationRepository;

    @Mock
    private PaymentRunRepository paymentRunRepository;

    @Mock
    private ApprovalRequestRepository approvalRequestRepository;

    @Mock
    private FinancialReportRepository financialReportRepository;

    @InjectMocks
    private FiscalPeriodService fiscalPeriodService;

    @Test
    void closeBlocksOutstandingBankReconciliations() {
        FiscalPeriod period = period();
        when(fiscalPeriodRepository.findById(9L)).thenReturn(Optional.of(period));
        stubNoLegacyOpenWork();
        when(bankReconciliationRepository.existsOpenWorkInPeriod(
                eq(1L),
                eq(List.of(BankReconciliation.ReconciliationStatus.DRAFT)),
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 1, 31))))
                .thenReturn(true);

        assertThatThrownBy(() -> fiscalPeriodService.closeFiscalPeriod(9L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("bank reconciliations are outstanding");
    }

    @Test
    void closeBlocksUnresolvedPaymentRuns() {
        FiscalPeriod period = period();
        when(fiscalPeriodRepository.findById(9L)).thenReturn(Optional.of(period));
        stubNoLegacyOpenWork();
        when(paymentRunRepository.existsOpenWorkInPeriod(
                eq(1L),
                eq(List.of(
                        PaymentRun.PaymentRunStatus.DRAFT,
                        PaymentRun.PaymentRunStatus.READY_FOR_APPROVAL,
                        PaymentRun.PaymentRunStatus.APPROVED,
                        PaymentRun.PaymentRunStatus.PARTIALLY_EXECUTED)),
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 1, 31))))
                .thenReturn(true);

        assertThatThrownBy(() -> fiscalPeriodService.closeFiscalPeriod(9L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("payment runs are unresolved");
    }

    @Test
    void closeBlocksPendingApprovals() {
        FiscalPeriod period = period();
        when(fiscalPeriodRepository.findById(9L)).thenReturn(Optional.of(period));
        stubNoLegacyOpenWork();
        when(approvalRequestRepository.existsOpenWorkInPeriod(
                eq(1L),
                eq(List.of(ApprovalRequest.ApprovalStatus.PENDING)),
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 1, 31))))
                .thenReturn(true);

        assertThatThrownBy(() -> fiscalPeriodService.closeFiscalPeriod(9L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("approvals are unresolved");
    }

    @Test
    void closeBlocksUnresolvedFinancialReports() {
        FiscalPeriod period = period();
        when(fiscalPeriodRepository.findById(9L)).thenReturn(Optional.of(period));
        stubNoLegacyOpenWork();
        when(financialReportRepository.existsOpenWorkOverlappingPeriod(
                eq(1L),
                eq(List.of(FinancialReport.ReportStatus.DRAFT, FinancialReport.ReportStatus.PENDING_REVIEW)),
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 1, 31))))
                .thenReturn(true);

        assertThatThrownBy(() -> fiscalPeriodService.closeFiscalPeriod(9L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("financial reports are unresolved");
    }

    @Test
    void closeSucceedsWhenAllCloseWorkIsResolved() {
        FiscalPeriod period = period();
        when(fiscalPeriodRepository.findById(9L)).thenReturn(Optional.of(period));
        stubNoOpenWork();
        when(fiscalPeriodRepository.save(any(FiscalPeriod.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = fiscalPeriodService.closeFiscalPeriod(9L);

        assertThat(result.getStatus()).isEqualTo("CLOSED");
        assertThat(period.getClosed()).isTrue();
    }

    private void stubNoLegacyOpenWork() {
        when(manualJournalRepository.existsOpenWorkInPeriod(
                eq(1L),
                eq(List.of(ManualJournal.JournalStatus.DRAFT, ManualJournal.JournalStatus.SUBMITTED, ManualJournal.JournalStatus.APPROVED)),
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 1, 31))))
                .thenReturn(false);
        when(customerInvoiceRepository.existsOpenWorkInPeriod(
                eq(1L),
                eq(List.of(CustomerInvoice.InvoiceStatus.DRAFT, CustomerInvoice.InvoiceStatus.APPROVED, CustomerInvoice.InvoiceStatus.SENT)),
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 1, 31))))
                .thenReturn(false);
        when(purchaseInvoiceRepository.existsOpenWorkInPeriod(
                eq(1L),
                eq(List.of(PurchaseInvoice.PurchaseStatus.DRAFT, PurchaseInvoice.PurchaseStatus.SUBMITTED, PurchaseInvoice.PurchaseStatus.APPROVED)),
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 1, 31))))
                .thenReturn(false);
    }

    private void stubNoOpenWork() {
        stubNoLegacyOpenWork();
        when(bankReconciliationRepository.existsOpenWorkInPeriod(
                eq(1L),
                eq(List.of(BankReconciliation.ReconciliationStatus.DRAFT)),
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 1, 31))))
                .thenReturn(false);
        when(paymentRunRepository.existsOpenWorkInPeriod(
                eq(1L),
                eq(List.of(
                        PaymentRun.PaymentRunStatus.DRAFT,
                        PaymentRun.PaymentRunStatus.READY_FOR_APPROVAL,
                        PaymentRun.PaymentRunStatus.APPROVED,
                        PaymentRun.PaymentRunStatus.PARTIALLY_EXECUTED)),
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 1, 31))))
                .thenReturn(false);
        when(approvalRequestRepository.existsOpenWorkInPeriod(
                eq(1L),
                eq(List.of(ApprovalRequest.ApprovalStatus.PENDING)),
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 1, 31))))
                .thenReturn(false);
        when(financialReportRepository.existsOpenWorkOverlappingPeriod(
                eq(1L),
                eq(List.of(FinancialReport.ReportStatus.DRAFT, FinancialReport.ReportStatus.PENDING_REVIEW)),
                eq(LocalDate.of(2026, 1, 1)),
                eq(LocalDate.of(2026, 1, 31))))
                .thenReturn(false);
    }

    private FiscalPeriod period() {
        Organization organization = Organization.builder().id(1L).build();
        return FiscalPeriod.builder()
                .id(9L)
                .organization(organization)
                .year(2026)
                .quarter(1)
                .startDate(LocalDateTime.of(2026, 1, 1, 0, 0))
                .endDate(LocalDateTime.of(2026, 1, 31, 23, 59))
                .status(FiscalPeriod.PeriodStatus.OPEN)
                .closed(false)
                .build();
    }
}
