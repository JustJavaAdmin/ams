package com.justjava.ams.accountant.service;

import com.justjava.ams.accountant.dto.PaymentRunCreateRequest;
import com.justjava.ams.accountant.dto.PaymentRunDTO;
import com.justjava.ams.accountant.entity.*;
import com.justjava.ams.accountant.repository.*;
import com.justjava.ams.common.entity.Organization;
import com.justjava.ams.common.repository.OrganizationRepository;
import com.justjava.ams.financeAdmin.dto.ApprovalDecisionDTO;
import com.justjava.ams.financeAdmin.service.ApprovalWorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRunServiceTest {

    @Mock
    private PaymentRunRepository paymentRunRepository;

    @Mock
    private PaymentRunItemRepository paymentRunItemRepository;

    @Mock
    private PaymentScheduleRepository paymentScheduleRepository;

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private PurchaseInvoiceService purchaseInvoiceService;

    @Mock
    private PaymentScheduleService paymentScheduleService;

    @Mock
    private ApprovalWorkflowService approvalWorkflowService;

    @InjectMocks
    private PaymentRunService paymentRunService;

    @Test
    void createsDraftRunFromApprovedDueSchedules() {
        Organization organization = Organization.builder().id(1L).build();
        BankAccount bankAccount = BankAccount.builder().id(2L).organization(organization).bankName("Main Bank").active(true).build();
        Vendor vendor = Vendor.builder().id(3L).legalName("Vendor Ltd").organization(organization).build();
        PurchaseInvoice invoice = PurchaseInvoice.builder().id(4L).purchaseOrderNumber("PI-4").vendorName("Vendor Ltd").vendor(vendor).build();
        PaymentSchedule schedule = PaymentSchedule.builder()
                .id(5L)
                .organization(organization)
                .vendor(vendor)
                .purchaseInvoice(invoice)
                .amountRemaining(new BigDecimal("250.00"))
                .status(PaymentSchedule.ScheduleStatus.APPROVED)
                .build();

        PaymentRun run = PaymentRun.builder()
                .id(6L)
                .organization(organization)
                .bankAccount(bankAccount)
                .runDate(LocalDate.of(2026, 2, 1))
                .cutoffDate(LocalDate.of(2026, 2, 1))
                .status(PaymentRun.PaymentRunStatus.DRAFT)
                .createdBy("creator")
                .build();
        PaymentRunItem item = PaymentRunItem.builder()
                .id(7L)
                .paymentRun(run)
                .paymentSchedule(schedule)
                .purchaseInvoice(invoice)
                .vendor(vendor)
                .amount(new BigDecimal("250.00"))
                .status(PaymentRunItem.PaymentRunItemStatus.PENDING)
                .build();

        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
        when(bankAccountRepository.findById(2L)).thenReturn(Optional.of(bankAccount));
        when(paymentScheduleRepository.findByOrganizationIdAndStatusInAndScheduledPaymentDateLessThanEqualOrderByScheduledPaymentDateAscIdAsc(any(), any(), any()))
                .thenReturn(List.of(schedule));
        when(paymentRunItemRepository.existsByPaymentScheduleIdAndStatusIn(5L, List.of(PaymentRunItem.PaymentRunItemStatus.PENDING))).thenReturn(false);
        when(paymentRunRepository.save(any(PaymentRun.class))).thenReturn(run);
        when(paymentRunItemRepository.save(any(PaymentRunItem.class))).thenReturn(item);
        when(paymentRunRepository.findById(6L)).thenReturn(Optional.of(run));
        when(paymentRunItemRepository.findByPaymentRunIdOrderByIdAsc(6L)).thenReturn(List.of(item));

        PaymentRunDTO dto = paymentRunService.createRun(
                1L,
                PaymentRunCreateRequest.builder()
                        .bankAccountId(2L)
                        .runDate(LocalDate.of(2026, 2, 1))
                        .cutoffDate(LocalDate.of(2026, 2, 1))
                        .build(),
                "creator");

        assertThat(dto.getStatus()).isEqualTo("DRAFT");
        assertThat(dto.getItems()).hasSize(1);
        assertThat(dto.getItems().getFirst().getAmount()).isEqualByComparingTo("250.00");
    }

    @Test
    void submitRunStoresApprovalRuleDecision() {
        Organization organization = Organization.builder().id(1L).build();
        BankAccount bankAccount = BankAccount.builder().id(2L).organization(organization).bankName("Main Bank").active(true).build();
        PaymentRun run = PaymentRun.builder()
                .id(6L)
                .organization(organization)
                .bankAccount(bankAccount)
                .runDate(LocalDate.of(2026, 2, 1))
                .cutoffDate(LocalDate.of(2026, 2, 1))
                .totalAmount(new BigDecimal("250.00"))
                .status(PaymentRun.PaymentRunStatus.DRAFT)
                .build();
        Vendor vendor = Vendor.builder().id(3L).legalName("Vendor Ltd").organization(organization).build();
        PurchaseInvoice invoice = PurchaseInvoice.builder().id(4L).purchaseOrderNumber("PI-4").vendorName("Vendor Ltd").vendor(vendor).build();
        PaymentSchedule schedule = PaymentSchedule.builder()
                .id(5L)
                .organization(organization)
                .vendor(vendor)
                .purchaseInvoice(invoice)
                .amountRemaining(new BigDecimal("250.00"))
                .status(PaymentSchedule.ScheduleStatus.APPROVED)
                .build();
        PaymentRunItem item = PaymentRunItem.builder()
                .id(7L)
                .paymentRun(run)
                .paymentSchedule(schedule)
                .purchaseInvoice(invoice)
                .vendor(vendor)
                .amount(new BigDecimal("250.00"))
                .status(PaymentRunItem.PaymentRunItemStatus.PENDING)
                .build();

        when(paymentRunRepository.findById(6L)).thenReturn(Optional.of(run));
        when(paymentRunItemRepository.findByPaymentRunIdOrderByIdAsc(6L)).thenReturn(List.of(item));
        when(approvalWorkflowService.submitForApproval(any())).thenReturn(ApprovalDecisionDTO.builder()
                .approvalRequired(true)
                .approvalRequestId(30L)
                .approvalRuleId(20L)
                .approvalRuleName("Payment run rule")
                .requiredApprovals(2)
                .build());
        when(paymentRunRepository.save(any(PaymentRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentRunDTO dto = paymentRunService.submitRun(6L);

        assertThat(dto.getStatus()).isEqualTo("READY_FOR_APPROVAL");
        assertThat(dto.getApprovalRequestId()).isEqualTo(30L);
        assertThat(dto.getApprovalRuleId()).isEqualTo(20L);
        assertThat(dto.getApprovalRuleName()).isEqualTo("Payment run rule");
        assertThat(dto.getRequiredApprovals()).isEqualTo(2);
    }
}
